# Standalone HTTP and serialization
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**

# kotlinx-serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ashareai.app.standalone.**$$serializer { *; }
-keepclassmembers class com.ashareai.app.standalone.** { *** Companion; }
-keepclasseswithmembers class com.ashareai.app.standalone.** { kotlinx.serialization.KSerializer serializer(...); }

# Room loads generated implementations by name.
-keep class * extends androidx.room.RoomDatabase
