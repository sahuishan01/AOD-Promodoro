# Strip all logging in release builds (MASVS-CODE-4: no debug/log leaks)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
-assumenosideeffects class timber.log.Timber {
    public *** d(...);
    public *** v(...);
    public *** i(...);
    public *** w(...);
    public *** e(...);
}

# Hilt / Kotlin metadata
-keep class dagger.hilt.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlinx coroutines
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
