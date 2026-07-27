# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**

# kotlinx-serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ashareai.app.**$$serializer { *; }
-keepclassmembers class com.ashareai.app.** { *** Companion; }
-keepclasseswithmembers class com.ashareai.app.** { kotlinx.serialization.KSerializer serializer(...); }

# MiPush AAR is optional in local builds. Keep its receiver bridge when packaged.
-keep class com.ashareai.app.island.MiPushReceiver { *; }
-keep class com.xiaomi.mipush.** { *; }
-dontwarn com.xiaomi.**
