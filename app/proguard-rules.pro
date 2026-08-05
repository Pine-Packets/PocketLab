# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard-defaults.txt file.

# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * extends com.pineandpackets.pocketlab.core.model.** {
    *;
}

# Keep model classes for serialization
-keep class com.pineandpackets.pocketlab.core.model.** { *; }
-keep class com.pineandpackets.pocketlab.engine.api.** { *; }

# Kotlin serialization
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
