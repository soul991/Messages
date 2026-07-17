# Keep Retrofit/Moshi model classes used via reflection.
-keep class com.personal.detectivedialer.data.remote.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Hilt generated
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Moshi reflective adapters (moshi-kotlin) read Kotlin metadata at runtime.
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers class * { @com.squareup.moshi.FromJson <methods>; @com.squareup.moshi.ToJson <methods>; }

# Room entities/DAOs referenced from generated code.
-keep class com.personal.detectivedialer.data.local.** { *; }

# OS/Firebase entry points (CallScreeningService, FCM, SMS role components).
-keep class com.personal.detectivedialer.service.** { *; }
