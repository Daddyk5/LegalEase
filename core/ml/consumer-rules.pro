# LiteRT-LM calls into Kotlin classes from native code (JNI) and builds messages reflectively
# with Gson; keep its public API and the classes it instantiates.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# TFLite / LiteRT interpreter (native callbacks).
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.gpu.**
