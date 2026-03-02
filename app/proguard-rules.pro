# Reglas ProGuard para Gradify
# https://developer.android.com/guide/developing/tools/proguard

# ── Atributos generales (CRÍTICO para @Key, @Inject, etc.) ──────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod

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

# ── Google API Client (base para Drive y Sheets) ────────────────
# Mantener TODAS las clases del API Client: transporte HTTP, JSON,
# autenticación, extensiones Android y las anotaciones @Key que
# permiten la serialización/deserialización de modelos.
-keep class com.google.api.client.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.util.** { *; }
-dontwarn com.google.api.client.**

# Campos anotados con @Key son CRÍTICOS para que la serialización
# JSON funcione (sin esto, Drive envía name=null en las requests)
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# ── Google Drive API ─────────────────────────────────────────────
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class com.google.api.services.drive.model.** { *; }
-dontwarn com.google.api.services.drive.**

# ── Google Calendar API ──────────────────────────────────────────
-keep class com.google.api.services.calendar.** { *; }
-keepclassmembers class com.google.api.services.calendar.model.** { *; }
-dontwarn com.google.api.services.calendar.**

# ── Google Generative AI (Gemini SDK) ────────────────────────────
# El SDK usa Ktor + kotlinx.serialization internamente; sin estas
# reglas, R8 elimina clases internas y la generación de contenido
# falla en runtime con ClassNotFoundException / NoSuchMethodError.
-keep class com.google.ai.client.generativeai.** { *; }
-keep class com.google.ai.client.generativeai.type.** { *; }
-keep class com.google.ai.client.generativeai.internal.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# ── Ktor (dependencia del Gemini SDK) ───────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Kotlinx Serialization (dependencia del Gemini SDK) ──────────
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-dontwarn kotlinx.serialization.**

# ── Kotlin Coroutines ───────────────────────────────────────────
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.coroutines.**
-dontwarn kotlinx.coroutines.**

# ── Gson (usado por google-api-client para JSON) ────────────────
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ── Timber ───────────────────────────────────────────────────────
-dontwarn org.slf4j.**

# Eliminar logs de Timber en release
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# ── Warnings comunes que se puede ignorar ────────────────────────
-dontwarn org.apache.http.**
-dontwarn com.sun.net.httpserver.**
-dontwarn javax.naming.**
