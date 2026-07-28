# R8 rules for the release build. Library-shipped consumer rules (Room,
# WorkManager, Compose, play-services-auth, kotlinx-serialization runtime)
# cover most of the tree; the rules here cover what those can't see.

# --- kotlinx-serialization ---
# @Serializable models live in :protection-engine (patterns.json — a broken
# parse would kill classification at startup) and :core-messaging (backup
# envelope/payload). The generated $$serializer classes and Companion
# serializer() lookups must survive shrinking.
#
# :core-messaging now ships its own consumer-rules.pro (R-28), so its half is
# covered twice — deliberately; R8 unions rule sets, and the duplication keeps
# this build correct even if that module's rules change. :protection-engine is
# a pure JVM jar and CANNOT ship consumer rules, so the com.messages.** scope
# below is what keeps it safe.
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
