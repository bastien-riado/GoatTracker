# GoatTracker R8 / ProGuard rules.
#
# AGP's default optimized rules plus library consumer rules (Jetpack Compose, Navigation3,
# kotlinx.serialization) cover most of the app. The kotlinx.serialization plugin already ships
# consumer rules, but the canonical keep rules below are added defensively so the @Serializable
# persistence model (WorkoutStateDto and friends) and the @Serializable nav3 NavKeys survive
# minification regardless of how their serializers are resolved.

# Keep annotations used at runtime by the serialization framework.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects (e.g. the `data object` NavKeys).
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Domain/DTO enums are (de)serialized by name and read back via valueOf() in the DTO mappers;
# keep values()/valueOf() so R8 can't strip them.
-keepclassmembers enum com.example.goattracker.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── SceneView / Filament (3D muscle heatmap) ─────────────────────────────────
# Filament & gltfio resolve classes/methods from native (JNI) code by name. SceneView
# and Filament ship consumer rules, but these are added defensively so R8 can't strip
# the native bridge (which would crash only in minified release builds).
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
