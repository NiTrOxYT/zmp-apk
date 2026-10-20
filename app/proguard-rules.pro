# ProGuard rules for SiteLink Manager
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class com.jcraft.jsch.** { *; }
