# Keep MediaPipe / Google AI Edge GenAI native bindings.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
