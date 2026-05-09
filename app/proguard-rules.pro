# Max ProGuard Rules

# Keep all Max core classes - these are critical for rule enforcement
-keep class com.max.core.** { *; }
-keep class com.max.rules.** { *; }
-keep class com.max.memory.** { *; }
-keep class com.max.log.** { *; }

# Keep AI backend interfaces
-keep interface com.max.ai.AIBackend { *; }

# Keep all Compose-related code
-keep class * extends androidx.compose.** { *; }
-keepclassmembers class * extends androidx.compose.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
