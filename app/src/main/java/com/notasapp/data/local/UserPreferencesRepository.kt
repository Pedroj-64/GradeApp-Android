package com.notasapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notasapp.domain.model.ConfiguracionNota
import com.notasapp.domain.model.ModoRedondeo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Extension property para crear el DataStore una sola vez por proceso. */
private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "notas_app_prefs")

/**
 * Repositorio de preferencias del usuario usando DataStore.
 *
 * Almacena datos ligeros de configuración que no requieren SQL:
 *  - Email del usuario activo (usado por GoogleAccountCredential para Sheets)
 *  - Última sync exitosa (timestamp)
 *  - Configuración de redondeo de notas
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_USER_EMAIL        = stringPreferencesKey("user_email")
        private val KEY_LAST_SYNC_MS      = stringPreferencesKey("last_sync_ms")
        private val KEY_LAST_BACKUP_MS    = stringPreferencesKey("last_backup_ms")
        private val KEY_REDONDEO_DECIMALES = intPreferencesKey("redondeo_decimales")
        private val KEY_REDONDEO_MODO      = stringPreferencesKey("redondeo_modo")
        private val KEY_HAS_SEEN_ONBOARDING       = booleanPreferencesKey("has_seen_onboarding")
        private val KEY_HAS_EXPLAINED_NOTIFICATIONS = booleanPreferencesKey("has_explained_notifications")
    }

    // ── Email del usuario activo ─────────────────────────────────

    /** Flow del email del usuario activo, o null si nadie ha iniciado sesión. */
    val userEmail: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_EMAIL] }

    /** Guarda el email del usuario activo. */
    suspend fun setUserEmail(email: String) {
        context.dataStore.edit { prefs -> prefs[KEY_USER_EMAIL] = email }
    }

    /** Borra el email activo (logout). */
    suspend fun clearUserEmail() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_USER_EMAIL) }
    }

    // ── Timestamp de última sync ─────────────────────────────────

    /** Flow de la última sync exitosa como milisegundos epoch. */
    val lastSyncMs: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[KEY_LAST_SYNC_MS]?.toLongOrNull() }

    /** Actualiza el timestamp de la última sync. */
    suspend fun touchLastSync() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_MS] = System.currentTimeMillis().toString()
        }
    }

    // ── Timestamp de último backup ────────────────────────────────

    /** Flow del último backup automático como milisegundos epoch. */
    val lastBackupMs: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[KEY_LAST_BACKUP_MS]?.toLongOrNull() }

    /** Actualiza el timestamp del último backup. */
    suspend fun touchLastBackup() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP_MS] = System.currentTimeMillis().toString()
        }
    }

    // ── Configuración de redondeo ────────────────────────────────

    /** Flow de la configuración de redondeo de notas. */
    val configuracionNota: Flow<ConfiguracionNota> = context.dataStore.data
        .map { prefs ->
            ConfiguracionNota(
                decimales = prefs[KEY_REDONDEO_DECIMALES] ?: 2,
                modoRedondeo = prefs[KEY_REDONDEO_MODO]?.let {
                    try { ModoRedondeo.valueOf(it) } catch (_: Exception) { ModoRedondeo.MATEMATICO }
                } ?: ModoRedondeo.MATEMATICO
            )
        }

    /** Guarda la configuración de redondeo. */
    suspend fun setConfiguracionNota(config: ConfiguracionNota) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REDONDEO_DECIMALES] = config.decimales
            prefs[KEY_REDONDEO_MODO] = config.modoRedondeo.name
        }
    }

    // ── Onboarding ───────────────────────────────────────────────

    /** Flow que indica si el usuario ya vio el onboarding. */
    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_HAS_SEEN_ONBOARDING] ?: false }

    /** Marca el onboarding como completado. */
    suspend fun setHasSeenOnboarding() {
        context.dataStore.edit { prefs -> prefs[KEY_HAS_SEEN_ONBOARDING] = true }
    }

    // ── Permisos explicados ──────────────────────────────────────

    /** Flow que indica si ya se explicó el permiso de notificaciones. */
    val hasExplainedNotifications: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_HAS_EXPLAINED_NOTIFICATIONS] ?: false }

    /** Marca que ya se explicó el permiso de notificaciones. */
    suspend fun setHasExplainedNotifications() {
        context.dataStore.edit { prefs -> prefs[KEY_HAS_EXPLAINED_NOTIFICATIONS] = true }
    }
}
