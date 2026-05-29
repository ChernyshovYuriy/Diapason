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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep line numbers and source file names for readable Crashlytics stack traces.
# Without this, crash reports show obfuscated line numbers which are very hard
# to diagnose. The source file name is replaced with "SourceFile" (above) so
# the original filename is not leaked, but line numbers remain accurate.
-keepattributes SourceFile,LineNumberTable

# WorkManager persists worker class names as strings in its SQLite DB; the
# persisted name from APK v(N) must still resolve in APK v(N+1). R8 is only
# deterministic within a single build, so without an explicit keep an obfuscated
# rename across releases would leave already-scheduled reminders unresolvable
# (ClassNotFoundException at fire time, silent drop). Keep the class name and
# the constructor WorkManager invokes via reflection.
-keep class com.yuriy.diapason.reminder.ReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}