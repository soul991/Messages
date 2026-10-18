# PR #28 Follow-up Verification Check

## 1. `./gradlew :app:compileDebugKotlin`

```
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-messaging:preBuild UP-TO-DATE
> Task :protection-engine:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :design-system:preBuild UP-TO-DATE
> Task :core-messaging:preDebugBuild UP-TO-DATE
> Task :app:preBuild UP-TO-DATE
> Task :design-system:preDebugBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :core-messaging:writeDebugAarMetadata UP-TO-DATE
> Task :design-system:writeDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :design-system:generateDebugResValues UP-TO-DATE
> Task :core-messaging:generateDebugResValues UP-TO-DATE
> Task :design-system:generateDebugResources UP-TO-DATE
> Task :core-messaging:generateDebugResources UP-TO-DATE
> Task :design-system:packageDebugResources UP-TO-DATE
> Task :design-system:extractDeepLinksDebug UP-TO-DATE
> Task :design-system:processDebugManifest UP-TO-DATE
> Task :design-system:compileDebugLibraryResources UP-TO-DATE
> Task :core-messaging:packageDebugResources UP-TO-DATE
> Task :core-messaging:extractDeepLinksDebug UP-TO-DATE
> Task :design-system:parseDebugLocalResources UP-TO-DATE
> Task :core-messaging:processDebugManifest UP-TO-DATE
> Task :core-messaging:compileDebugLibraryResources UP-TO-DATE
> Task :design-system:generateDebugRFile UP-TO-DATE
> Task :design-system:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-messaging:parseDebugLocalResources UP-TO-DATE
> Task :core-messaging:generateDebugRFile UP-TO-DATE
> Task :core-messaging:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core-messaging:javaPreCompileDebug UP-TO-DATE
> Task :protection-engine:compileKotlin UP-TO-DATE
> Task :protection-engine:compileJava NO-SOURCE
> Task :protection-engine:processResources UP-TO-DATE
> Task :protection-engine:classes UP-TO-DATE
> Task :protection-engine:jar UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :design-system:compileDebugKotlin UP-TO-DATE
> Task :design-system:javaPreCompileDebug UP-TO-DATE
> Task :design-system:compileDebugJavaWithJavac NO-SOURCE
> Task :core-messaging:kspDebugKotlin UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :design-system:bundleLibCompileToJarDebug UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :core-messaging:compileDebugKotlin UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :core-messaging:compileDebugJavaWithJavac UP-TO-DATE
> Task :core-messaging:bundleLibCompileToJarDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:kspDebugKotlin UP-TO-DATE
> Task :app:compileDebugKotlin UP-TO-DATE

BUILD SUCCESSFUL in 18s
45 actionable tasks: 45 up-to-date
```

---

## 2. `./gradlew test`

```
> Task :app:parseBenchmarkLocalResources UP-TO-DATE
> Task :app:createBenchmarkCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksBenchmark UP-TO-DATE
> Task :app:processBenchmarkMainManifest UP-TO-DATE
> Task :app:processBenchmarkManifest UP-TO-DATE
> Task :app:processBenchmarkManifestForPackage UP-TO-DATE
> Task :app:processBenchmarkResources UP-TO-DATE
> Task :core-messaging:checkDebugUnitTestAarMetadata UP-TO-DATE
> Task :core-messaging:generateDebugUnitTestResValues UP-TO-DATE
> Task :core-messaging:mapDebugUnitTestSourceSetPaths UP-TO-DATE
> Task :core-messaging:generateDebugUnitTestResources UP-TO-DATE
> Task :app:kspBenchmarkKotlin UP-TO-DATE
> Task :core-messaging:mergeDebugUnitTestResources UP-TO-DATE
> Task :core-messaging:processDebugUnitTestManifest UP-TO-DATE
> Task :core-messaging:processDebugUnitTestResources UP-TO-DATE
> Task :app:compileBenchmarkKotlin UP-TO-DATE
> Task :app:javaPreCompileBenchmark UP-TO-DATE
> Task :app:compileBenchmarkJavaWithJavac UP-TO-DATE
> Task :core-messaging:kspDebugUnitTestKotlin UP-TO-DATE
> Task :app:preBenchmarkUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileBenchmarkUnitTest UP-TO-DATE
> Task :app:processBenchmarkJavaRes UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:kspDebugKotlin UP-TO-DATE
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :app:processDebugJavaRes UP-TO-DATE
> Task :app:preReleaseBuild UP-TO-DATE
> Task :app:generateReleaseBuildConfig UP-TO-DATE
> Task :core-messaging:compileDebugUnitTestKotlin FROM-CACHE
> Task :core-messaging:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :core-messaging:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :core-messaging:mergeDebugShaders UP-TO-DATE
> Task :core-messaging:compileDebugShaders NO-SOURCE
> Task :core-messaging:generateDebugAssets UP-TO-DATE
> Task :app:checkReleaseAarMetadata UP-TO-DATE
> Task :app:generateReleaseResValues UP-TO-DATE
> Task :core-messaging:mergeDebugAssets UP-TO-DATE
> Task :core-messaging:generateDebugUnitTestAssets UP-TO-DATE
> Task :core-messaging:packageDebugAssets UP-TO-DATE
> Task :app:mapReleaseSourceSetPaths UP-TO-DATE
> Task :app:generateReleaseResources UP-TO-DATE
> Task :core-messaging:mergeDebugUnitTestAssets UP-TO-DATE
> Task :core-messaging:packageDebugUnitTestForUnitTest UP-TO-DATE
> Task :core-messaging:generateDebugUnitTestConfig UP-TO-DATE
> Task :core-messaging:processDebugUnitTestJavaRes UP-TO-DATE
> Task :core-messaging:testDebugUnitTest UP-TO-DATE
> Task :core-messaging:preReleaseUnitTestBuild UP-TO-DATE
> Task :core-messaging:checkReleaseUnitTestAarMetadata UP-TO-DATE
> Task :core-messaging:compileReleaseLibraryResources UP-TO-DATE
> Task :core-messaging:generateReleaseUnitTestResValues UP-TO-DATE
> Task :core-messaging:mapReleaseUnitTestSourceSetPaths UP-TO-DATE
> Task :core-messaging:generateReleaseUnitTestResources UP-TO-DATE
> Task :core-messaging:mergeReleaseUnitTestResources UP-TO-DATE
> Task :core-messaging:processReleaseUnitTestManifest UP-TO-DATE
> Task :core-messaging:processReleaseUnitTestResources UP-TO-DATE
> Task :core-messaging:kspReleaseUnitTestKotlin UP-TO-DATE
> Task :app:mergeReleaseResources UP-TO-DATE
> Task :core-messaging:compileReleaseUnitTestKotlin FROM-CACHE
> Task :core-messaging:javaPreCompileReleaseUnitTest UP-TO-DATE
> Task :core-messaging:compileReleaseUnitTestJavaWithJavac NO-SOURCE
> Task :core-messaging:mergeReleaseShaders UP-TO-DATE
> Task :core-messaging:compileReleaseShaders NO-SOURCE
> Task :core-messaging:generateReleaseAssets UP-TO-DATE
> Task :core-messaging:mergeReleaseAssets UP-TO-DATE
> Task :core-messaging:generateReleaseUnitTestAssets UP-TO-DATE
> Task :core-messaging:packageReleaseAssets UP-TO-DATE
> Task :core-messaging:mergeReleaseUnitTestAssets UP-TO-DATE
> Task :core-messaging:packageReleaseUnitTestForUnitTest UP-TO-DATE
> Task :core-messaging:generateReleaseUnitTestConfig UP-TO-DATE
> Task :app:packageReleaseResources UP-TO-DATE
> Task :app:parseReleaseLocalResources UP-TO-DATE
> Task :core-messaging:processReleaseUnitTestJavaRes UP-TO-DATE
> Task :app:createReleaseCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksRelease UP-TO-DATE
> Task :app:processReleaseMainManifest UP-TO-DATE
> Task :app:processReleaseManifest UP-TO-DATE
> Task :app:processReleaseManifestForPackage UP-TO-DATE
> Task :core-messaging:testReleaseUnitTest UP-TO-DATE
> Task :core-messaging:test UP-TO-DATE
> Task :app:processReleaseResources UP-TO-DATE
> Task :app:kspReleaseKotlin UP-TO-DATE
> Task :app:compileReleaseKotlin UP-TO-DATE
> Task :app:javaPreCompileRelease UP-TO-DATE
> Task :app:compileReleaseJavaWithJavac UP-TO-DATE
> Task :app:preReleaseUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileReleaseUnitTest UP-TO-DATE
> Task :app:processReleaseJavaRes UP-TO-DATE
> Task :app:bundleBenchmarkClassesToRuntimeJar
> Task :app:bundleBenchmarkClassesToCompileJar
> Task :app:bundleDebugClassesToRuntimeJar
> Task :app:bundleDebugClassesToCompileJar
> Task :app:bundleReleaseClassesToRuntimeJar
> Task :app:bundleReleaseClassesToCompileJar
> Task :app:kspDebugUnitTestKotlin
> Task :app:kspReleaseUnitTestKotlin
> Task :app:kspBenchmarkUnitTestKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:compileReleaseUnitTestKotlin
> Task :app:compileBenchmarkUnitTestKotlin
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:compileBenchmarkUnitTestJavaWithJavac NO-SOURCE
> Task :app:processBenchmarkUnitTestJavaRes
> Task :app:testBenchmarkUnitTest
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest
> Task :app:compileReleaseUnitTestJavaWithJavac NO-SOURCE
> Task :app:processReleaseUnitTestJavaRes
> Task :app:testReleaseUnitTest
> Task :app:test

BUILD SUCCESSFUL in 2m 11s
184 actionable tasks: 18 executed, 2 from cache, 164 up-to-date
```

---

## 3. `grep -rn "Color(0x" app/src/main/kotlin/com/messages/app/ui | grep -v -e VaultTheme -e ChatStyle -e SenderBadge`

Command:
```powershell
& "C:\Program Files\Git\usr\bin\grep.exe" -rn "Color(0x" app/src/main/kotlin/com/messages/app/ui | & "C:\Program Files\Git\usr\bin\grep.exe" -v -e VaultTheme -e ChatStyle -e SenderBadge
```

Raw Output:
```
(No matches found - exit code 1)
```

---

## 4. `grep -rn "SyntheticStressData\|ReactionStore\|Rostova" app/src design-system/src macrobenchmark`

Command:
```powershell
& "C:\Program Files\Git\usr\bin\grep.exe" -rn "SyntheticStressData\|ReactionStore\|Rostova" app/src design-system/src macrobenchmark
```

Raw Output:
```
(No matches found - exit code 1)
```

---

## 5. `grep -rn "exported=\"true\"" app/src/debug/AndroidManifest.xml app/src/benchmark/AndroidManifest.xml`

Command:
```powershell
git grep -n 'exported=\"true\"' app/src/debug/AndroidManifest.xml app/src/benchmark/AndroidManifest.xml
```

Raw Output:
```
app/src/benchmark/AndroidManifest.xml:14:            android:exported="true"
app/src/debug/AndroidManifest.xml:20:            android:exported="true"
app/src/debug/AndroidManifest.xml:29:            android:exported="true"
app/src/debug/AndroidManifest.xml:39:            android:exported="true"
```

### Full Elements for Every Hit

#### Hit 1: `app/src/benchmark/AndroidManifest.xml:14`
```xml
        <!-- BENCHMARK-ONLY: test data seeder for automated UI motion and performance benchmarks -->
        <receiver
            android:name="com.messages.app.debug.SeedDataReceiver"
            android:exported="true"
            android:permission="com.messages.app.permission.DEBUG_HARNESS">
            <intent-filter>
                <action android:name="com.messages.app.debug.SEED" />
            </intent-filter>
        </receiver>
```

#### Hit 2: `app/src/debug/AndroidManifest.xml:20`
```xml
        <receiver
            android:name=".debug.InjectSmsReceiver"
            android:exported="true"
            android:permission="com.messages.app.permission.DEBUG_HARNESS">
            <intent-filter>
                <action android:name="com.messages.app.DEBUG_INJECT_SMS" />
            </intent-filter>
        </receiver>
```

#### Hit 3: `app/src/debug/AndroidManifest.xml:29`
```xml
        <receiver
            android:name=".debug.CleanupTestResidueReceiver"
            android:exported="true"
            android:permission="com.messages.app.permission.DEBUG_HARNESS">
            <intent-filter>
                <action android:name="com.messages.app.DEBUG_CLEANUP_RESIDUE" />
            </intent-filter>
        </receiver>
```

#### Hit 4: `app/src/debug/AndroidManifest.xml:39`
```xml
        <!-- DEBUG-ONLY: test data seeder for automated UI motion and performance benchmarks -->
        <receiver
            android:name="com.messages.app.debug.SeedDataReceiver"
            android:exported="true"
            android:permission="com.messages.app.permission.DEBUG_HARNESS">
            <intent-filter>
                <action android:name="com.messages.app.debug.SEED" />
            </intent-filter>
        </receiver>
```

*(Every exported="true" receiver has `android:permission="com.messages.app.permission.DEBUG_HARNESS"` on the same element).*

---

## 6. `git status --short`

Command:
```powershell
git status --short
```

Raw Output:
```
(clean)
```
