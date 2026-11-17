# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve AndroidManifest components (Default SMS App receivers, service, activity, FileProvider)
-keep public class com.example.MainActivity { *; }
-keep public class com.example.telephony.SmsReceiver { *; }
-keep public class com.example.telephony.MmsReceiver { *; }
-keep public class com.example.telephony.HeadlessSmsSendService { *; }
-keep public class androidx.core.content.FileProvider { *; }

# Preserve Room database & generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public ** cleanSmsDao();
}

# Preserve ViewModel constructors invoked by ViewModelProvider
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# OkHttp optional TLS platform providers not bundled in APK
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

