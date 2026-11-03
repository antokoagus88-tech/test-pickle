# ProGuard rules for Pickle Assistant

# Keep DataStore preferences
-keep class androidx.datastore.** { *; }

# Keep Retrofit and OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }

# Keep Kotlinx Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Material3
-keep class androidx.compose.material3.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep our app classes
-keep class com.pickle.assistant.** { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable { *; }

# Keep Enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}