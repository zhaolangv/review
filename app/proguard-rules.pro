# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep data classes and models
-keep class com.gongkao.cuotifupan.data.** { *; }

# 重要：保留所有 API 相关类（防止 ClassCastException）
-keep class com.gongkao.cuotifupan.api.** { *; }
-keepclassmembers class com.gongkao.cuotifupan.api.** { *; }
-keepnames class com.gongkao.cuotifupan.api.** { *; }
-keepclassmembernames class com.gongkao.cuotifupan.api.** { *; }

# 保留所有 API 接口的泛型签名（关键：防止 Retrofit 泛型擦除）
-keep,allowobfuscation interface com.gongkao.cuotifupan.api.QuestionApiService
-keep,allowobfuscation interface com.gongkao.cuotifupan.api.RedemptionApiService
-keep,allowobfuscation interface com.gongkao.cuotifupan.api.MigrationApiService

# Keep API response models (important for Gson deserialization)
-keep class com.gongkao.cuotifupan.api.VersionResponse { *; }
-keep class com.gongkao.cuotifupan.api.VersionInfo { *; }
-keep class com.gongkao.cuotifupan.api.UpdateInfo { *; }
-keep class com.gongkao.cuotifupan.api.QuestionContentResponse { *; }
-keep class com.gongkao.cuotifupan.api.QuestionDetailResponse { *; }

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Retrofit interfaces and classes (完整规则)
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**

# OkHttp
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 保留 Retrofit 的泛型类型信息（关键规则）
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit annotations and type information
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep generic signatures for Retrofit (防止 ClassCastException)
-keepattributes Signature
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit suspend function support (Kotlin Coroutines)
-keep class kotlin.coroutines.Continuation { *; }
-keepclassmembers class * {
    ** Continuation;
}
-keep class retrofit2.KotlinExtensions { *; }
-keep class retrofit2.KotlinExtensions$* { *; }

# 保留所有 Retrofit 接口方法的泛型签名
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowobfuscation interface <1> { *; }

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep ML Kit classes (包括 internal 类)
-keep class com.google.mlkit.** { *; }
-keep class com.google.mlkit.common.sdkinternal.** { *; }
-dontwarn com.google.mlkit.**

# Keep Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**

# Keep WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Remove logging in release builds (but keep error logs)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    # Keep error and warning logs for debugging
    # public static *** e(...);
    # public static *** w(...);
}

# Keep application class
-keep class com.gongkao.cuotifupan.SnapReviewApplication { *; }
-keep class com.gongkao.cuotifupan.MainActivity { *; }

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}