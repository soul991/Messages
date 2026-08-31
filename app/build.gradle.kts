import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing (docs/ops/RELEASE_SIGNING.md): keystore.properties at the repo
// root (gitignored) points at the keystore outside the repo.
//
// V2-04: credentials come from the ENVIRONMENT first and the file only as a
// local-developer fallback. CI and any shared machine set the four env vars and
// never materialize a plaintext credentials file at all. When the file IS used,
// its mode is enforced below — a world-readable secret is refused, not warned
// about, because every other user on the machine can read it.
val keystoreProps = Properties().apply {
    val fromEnv = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { System.getenv("MESSAGES_${it.uppercase()}") }
        .filterValues { !it.isNullOrBlank() }
    if (fromEnv.size == 4) {
        fromEnv.forEach { (k, v) -> setProperty(k, v) }
    } else {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) {
            requireOwnerOnly(f)
            f.inputStream().use { load(it) }
        }
    }
}

/**
 * Refuse to read signing credentials out of a file that other local accounts can
 * read. POSIX-only check; on filesystems without POSIX permissions (Windows,
 * some mounts) it is skipped rather than guessed at.
 */
fun requireOwnerOnly(f: File) {
    val perms: Set<PosixFilePermission> = try {
        Files.getPosixFilePermissions(f.toPath())
    } catch (_: UnsupportedOperationException) {
        return
    }
    val leaked = perms.map { it.name }
        .filter { it.startsWith("GROUP_") || it.startsWith("OTHERS_") }
    if (leaked.isNotEmpty()) {
        throw GradleException(
            "${f.name} holds plaintext signing credentials but is readable beyond its " +
                "owner ($leaked). Run: chmod 600 ${f.absolutePath}\n" +
                "Or drop the file entirely and export MESSAGES_STOREFILE, " +
                "MESSAGES_STOREPASSWORD, MESSAGES_KEYALIAS, MESSAGES_KEYPASSWORD instead. " +
                "See docs/ops/RELEASE_SIGNING.md."
        )
    }
}

// R-28: signing intent must be EXPLICIT per environment.
//
// An absent keystore still produces an unsigned release build, because that is
// what a contributor without the signing key needs. What changed is that the
// outcome is no longer silent: a build that intends to be distributable passes
// -PrequireSigning=true (CI's release job does) and FAILS here rather than
// emitting an unsigned APK that looks like a shippable artifact.
val requireSigning = (findProperty("requireSigning") as String?)?.toBoolean() ?: false
if (requireSigning && keystoreProps.isEmpty()) {
    throw GradleException(
        "requireSigning=true but no signing credentials were found (neither the " +
            "MESSAGES_* environment variables nor keystore.properties). " +
            "A release build cannot be signed — refusing to produce an unsigned artifact. " +
            "See docs/ops/RELEASE_SIGNING.md."
    )
}
// V2-04: credentials that point at a keystore which is not there are worse than
// no credentials — they read as "signing is configured" while every release
// build fails deep inside AGP. Fail here, where the message is actionable.
if (requireSigning) {
    val store = rootProject.file(keystoreProps.getProperty("storeFile") ?: "")
    if (!store.isFile) {
        throw GradleException(
            "requireSigning=true but the keystore is missing: ${store.absolutePath}. " +
                "See docs/ops/RELEASE_SIGNING.md."
        )
    }
}

android {
    namespace = "com.messages.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.messages.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.2.0"
    }

    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Lets a debug build sit alongside a signed release install of this app
        // on the same device. Without it the two share com.messages.app and the
        // debug install fails on the signing-cert mismatch, so the only way to
        // test on a daily driver would be to uninstall the real app and its data.
        // Only applicationId shifts; `namespace` stays com.messages.app, so class
        // names, the DEBUG_HARNESS permission and share_targets are unaffected.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // V2-13: buildConfig is needed for BuildConfig.DEBUG, which gates the
    // verbose Drive diagnostics that must never reach a release log.
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-messaging"))
    implementation(project(":design-system"))
    implementation(project(":protection-engine"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    // V2-08/09: link previews need a client with a DNS override, so the
    // connection goes only to validated addresses while TLS SNI and hostname
    // verification still use the real hostname.
    implementation(libs.okhttp)
    implementation(libs.play.services.auth)
    // Installs merged baseline profiles (ours + library-shipped, e.g. Compose)
    // on devices without Play Store profile delivery.
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
    // V2-34: the receiver work budget is a coroutine policy — tested on virtual
    // time rather than by sleeping for eight seconds.
    testImplementation(libs.kotlinx.coroutines.test)
}

// ---------------------------------------------------------------------------
// V2-18: the inventory a vulnerability scan needs.
//
// `scripts/scan-dependencies.py` has to answer one question before it can rank
// anything: does this artifact ship, or does it only ever run on the build
// machine? A netty advisory inside the Android Gradle Plugin and the same
// advisory inside the APK are not the same finding — one needs a hostile build
// host, the other needs a hostile SMS.
//
// `releaseRuntimeClasspath` is exactly the shipped set: `compileOnly` and
// annotation processors are absent from it, project dependencies contribute
// their transitives, and R8 only ever removes from it. Everything else Gradle
// resolves — the superset recorded in `gradle/verification-metadata.xml` — is
// build-time by subtraction.
//
// Emitted from Gradle rather than scraped out of `./gradlew :app:dependencies`
// because that report prints the losing side of a version conflict next to the
// winner ("room-ktx:2.5.0 -> 2.6.1"). Scanning the losing side reports
// vulnerabilities in code that is not in the build.
// ---------------------------------------------------------------------------
val shippedDependenciesReport: Provider<RegularFile> =
    layout.buildDirectory.file("reports/shipped-dependencies.txt")

tasks.register("shippedDependencies") {
    group = "verification"
    description = "Lists the external coordinates packaged into the release APK."
    outputs.file(shippedDependenciesReport)
    // Resolution can change — a new transitive, a version bump — without this
    // task's own inputs changing, and a stale inventory scans the wrong build.
    outputs.upToDateWhen { false }

    val coordinates = provider {
        configurations.getByName("releaseRuntimeClasspath")
            .incoming
            .resolutionResult
            .allComponents
            // Project components (":core-messaging") are ours and have no
            // upstream advisories; their external dependencies are already in
            // this graph on their own.
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .map { "${it.group}:${it.module}:${it.version}" }
            .distinct()
            .sorted()
    }

    doLast {
        val report = shippedDependenciesReport.get().asFile
        report.parentFile.mkdirs()
        report.writeText(coordinates.get().joinToString("\n", postfix = "\n"))
        logger.lifecycle("shipped coordinates: ${coordinates.get().size} -> $report")
    }
}
