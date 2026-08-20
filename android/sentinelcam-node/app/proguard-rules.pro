# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.sentinelcam.node.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# CameraX
-keep class androidx.camera.** { *; }
