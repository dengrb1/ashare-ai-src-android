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

# Keep enums and data classes with serialization
-keepclassmembers enum * { *; }
-keep class * implements java.io.Serializable { *; }
-keepnames class * implements android.os.Parcelable { *; }

# Room loads generated implementations by name.
-keep class * extends androidx.room.RoomDatabase

# Keep Application class and critical components
-keep class com.ashareai.app.HybridApp { *; }
-keep class com.ashareai.app.MainActivity { *; }

# Keep workspace related classes
-keep class com.ashareai.app.workspace.** { *; }

# Keep all data classes and containers
-keep class com.ashareai.app.data.** { *; }
-keep class com.ashareai.app.standalone.** { *; }
-keep class com.ashareai.app.ui.** { *; }
-keep class com.ashareai.app.island.** { *; }

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,includedescriptorclasses class com.ashareai.app.**$$serializer { *; }
-keepclassmembers class com.ashareai.app.** { *** Companion; }
-keepclasseswithmembers class com.ashareai.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.ashareai.app.island.MiPushReceiver { *; }
-keep class com.xiaomi.mipush.** { *; }
-dontwarn com.xiaomi.**
