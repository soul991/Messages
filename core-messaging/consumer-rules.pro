# R8 rules contributed by :core-messaging to whatever consumes it.
#
# R-28: build.gradle.kts has declared `consumerProguardFiles("consumer-rules.pro")`
# since the module was created, but the file did not exist. AGP treats a missing
# consumer file as empty rather than as an error, so the release build silently
# relied on :app's proguard-rules.pro carrying these rules on the module's
# behalf — correct only for as long as :app is the sole consumer.
#
# The rules below are the ones that belong to THIS module's public behaviour, so
# they travel with it.

# --- kotlinx-serialization: the backup envelope ---
# BackupCrypto/BackupManager serialize the encrypted-backup header and payload.
# A restore reads JSON written by an older build, so the field names in these
# models are a wire format: renaming them breaks restore of existing backups,
# and stripping the generated serializer breaks it outright.
-keepattributes *Annotation*, InnerClasses

-keepclassmembers class com.messages.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.messages.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.messages.core.**$$serializer { *; }

# --- Room ---
# The Room compiler's own consumer rules cover generated DAO/database
# implementations. Entities are only touched reflectively through generated
# code, so nothing extra is required here — recorded as a checked assumption
# rather than an omission.
