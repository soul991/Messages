# R8 rules for the release build. Library-shipped consumer rules (Room,
# WorkManager, Compose, play-services-auth, kotlinx-serialization runtime)
# cover most of the tree; the rules here cover what those can't see.

# --- kotlinx-serialization ---
# @Serializable models live in :protection-engine (patterns.json — a broken
# parse would kill classification at startup) and :core-messaging (backup
# envelope/payload). The generated $$serializer classes and Companion
# serializer() lookups must survive shrinking; neither library module ships
# consumer rules (protection-engine is a pure JVM jar).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.messages.** {
    *** Companion;
}
-keepclasseswithmembers class com.messages.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.messages.**$$serializer { *; }

# --- Keep crash stack traces readable ---
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- GoogleAuthUtil (Drive backup) ---
# Token fetch goes through GMS dynamite pieces that play-services consumer
# rules keep; nothing extra needed, listed here as a checked assumption.
