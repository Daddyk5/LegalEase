# Release builds use R8 full mode (the AGP default) with obfuscation. Library keep rules ship as
# consumer rules (Hilt, Room, WorkManager, Firebase, kotlinx.serialization, and :core:ml for LiteRT-LM).

# Strip debug and verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Readable Crashlytics stack traces without revealing original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
