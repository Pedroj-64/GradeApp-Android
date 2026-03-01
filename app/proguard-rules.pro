# Reglas ProGuard para NotasApp
# https://developer.android.com/guide/developing/tools/proguard

# ── Room Database ────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *

# ── Hilt ─────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ── Google Sign-In / Credential Manager ─────────────────────────
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ── Google Sheets API ────────────────────────────────────────────
-keep class com.google.api.services.sheets.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

# ── Kotlin Serialization / Coroutines ───────────────────────────
-keep class kotlin.coroutines.** { *; }
-dontwarn kotlin.coroutines.**

# ── Timber ───────────────────────────────────────────────────────
-dontwarn org.slf4j.**

# Eliminar logs de Timber en release
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
