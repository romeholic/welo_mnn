# ================================
# 基础配置
# ================================
-optimizationpasses 5           # 优化次数
-dontusemixedcaseclassnames     # 不使用大小写混合的类名
-dontskipnonpubliclibraryclasses # 不跳过非公共库类
-verbose                        # 输出详细信息

# 保留关键Android组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService

# 保留注解和反射相关
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# 保留Parcelable和Serializable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留资源引用
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留JNI方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留自定义View构造方法
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留WebView JavaScript接口
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
    public *;
}

# ================================
# AndroidX 配置
# ================================
-keep class androidx.** { *; }
-dontwarn androidx.**

# AndroidX Lifecycle
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.LiveData {
    <methods>;
}
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.ViewModelProvider.Factory { *; }


# AndroidX Navigation
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# AndroidX Fragment和Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# AndroidX ConstraintLayout
-keep class androidx.constraintlayout.widget.** { *; }
-dontwarn androidx.constraintlayout.widget.**

# ================================
# Kotlin 配置
# ================================
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Kotlin 协程
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin Android 扩展
-keep class androidx.core.kotlin.** { *; }

# Markwon keep rules
-keep class io.noties.markwon.** { *; }
-keep class org.commonmark.** { *; }

# SVG keep rules
-keep class com.caverock.androidsvg.** { *; }
-keep class io.noties.markwon.image.svg.** { *; }

# GIF keep rules
-keep class pl.droidsonroids.gif.** { *; }
-keep class io.noties.markwon.image.gif.** { *; }

# Coil keep rules (如果使用Coil)
-keep class coil.** { *; }
-keep class kotlinx.coroutines.** { *; }

# 或者 Glide keep rules (如果使用Glide)
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

-dontwarn pl.droidsonroids.gif.GifDrawable

# ================================
# 常见第三方库配置
# ================================
# GSON
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keepattributes Signature

# Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ================================
# Ktor 基础配置
# ================================
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.**

# 保留 Ktor 日志类（避免混淆导致日志失效）
-keep class io.ktor.client.plugins.logging.** { *; }
-keep class ch.qos.logback.** { *; }

# 保留Kotlin反射和协程
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ================================
# 客户端引擎配置
# ================================
# Android 引擎 (OkHttp)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**


# ================================
# 序列化配置
# ================================
# Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

# ================================
# 日志配置
# ================================
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# ================================
# 自定义配置（根据项目调整）
# ================================
# -keep class com.example.MyCustomSerializer { *; }
# 忽略FindBugs注解
-dontwarn edu.umd.cs.findbugs.annotations.**

# 忽略SLF4J实现
-dontwarn org.slf4j.impl.**
-keep class org.slf4j.LoggerFactory { *; }
-keep class org.slf4j.Logger { *; }

# ================================
# 特殊场景配置
# ================================
# 保留自定义实体类（根据项目调整包名）
-keep class com.welo.model.** { *; }
-keep class com.alibaba.mls.api.**{*;}
-keep class com.welo.service.FloatingWindowService { *; }
-keep class com.welo.service.RecordingForegroundService { *; }

-keep class com.iflytek.sparkchain.** {*;}
-keep class com.iflytek.sparkchain.**


# 保留特定类和方法


# 保留动态加载的库


# ================================
# 调试配置
# ================================
-printmapping mapping.txt
-printseeds seeds.txt
-printusage usage.txt