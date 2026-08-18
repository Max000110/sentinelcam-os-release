# Keep WebRTC Native JNI bindings
-keep class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Gson Models and Serialized Fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.sentinelcam.node.data.** { *; }
-keep class com.sentinelcam.node.ai.DetectedObject { *; }
-keep class com.sentinelcam.node.face.RecognizedFace { *; }

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
