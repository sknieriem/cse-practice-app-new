# ============================================================
# CSE Practice App — R8 / ProGuard configuration
# Inherits from `proguard-android-optimize.txt`; rules here are
# additive overrides for libraries the app uses with reflection.
# ============================================================

# ---------- General ----------
# Preserve generic signatures (Gson needs them for TypeToken<List<Question>>).
-keepattributes Signature
# Preserve annotations (Room, Gson use them).
-keepattributes *Annotation*
# Preserve source-file line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable

# Kotlin metadata so reflection on Kotlin classes works after shrinking.
-keep class kotlin.Metadata { *; }

# ---------- App data classes used with Gson reflection ----------
# Question is deserialized from questions.json on first launch.
# PracticeSession + SessionCategoryResult are Room entities; rules below cover them too,
# but explicit keeps make the intent obvious if someone wires up Gson for them later.
-keep class com.pjcinc.csepractice.data.** { *; }
-keep class com.pjcinc.csepractice.domain.** { *; }

# Generic rule: any field annotated with @SerializedName must keep its original name.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- Gson ----------
# TypeToken needs reflection on its anonymous subclasses.
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
# Custom TypeAdapter / serializer / deserializer subclasses (none in this app today,
# but cheap insurance for the future).
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-dontwarn sun.misc.**

# ---------- Room ----------
# Generated DAO and Database implementations are referenced via reflection by
# Room's runtime. The default AGP rules cover most of this; these are defensive.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ---------- Compose ----------
# Composable functions and previews — keeping their signatures helps tooling
# and avoids accidental obfuscation of inline param metadata.
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclasseswithmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}
# Allow shrinking of unused Composable bodies; we only want to keep signatures.
-dontwarn androidx.compose.**

# ---------- Vico ----------
# Vico uses no reflection internally — default rules suffice. Add suppressions
# for transitive warnings only.
-dontwarn com.patrykandpatrick.vico.**

# ---------- AndroidX / Lifecycle / DataStore ----------
# Default AGP rules cover these, but suppress remaining R8 warnings.
-dontwarn kotlinx.coroutines.flow.**
