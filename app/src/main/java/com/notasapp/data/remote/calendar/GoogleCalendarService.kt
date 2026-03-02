package com.notasapp.data.remote.calendar

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.notasapp.domain.model.ExamenEvent
import com.notasapp.domain.model.TipoEvento
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado de la importación de eventos de Google Calendar.
 */
data class CalendarImportResult(
    val imported: Int,
    val skipped: Int
)

/**
 * Servicio para importar eventos desde Google Calendar.
 *
 * Lee los próximos eventos del calendario del usuario autenticado
 * y los convierte en [ExamenEvent] para mostrarlos en la app.
 *
 * Requiere el scope `https://www.googleapis.com/auth/calendar.readonly`.
 */
@Singleton
class GoogleCalendarService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val SCOPES = listOf(
            "https://www.googleapis.com/auth/calendar.readonly"
        )
        private const val MAX_RESULTS = 50
    }

    /**
     * Construye un cliente de Google Calendar autenticado.
     */
    private fun buildCalendarClient(userEmail: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(context, SCOPES)
            .apply { selectedAccountName = userEmail }

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Gradify")
            .build()
    }

    /**
     * Obtiene los próximos eventos del calendario principal del usuario.
     *
     * @param userEmail Email de la cuenta Google autenticada.
     * @param materiaId ID de la materia a la que se asociarán los eventos importados.
     * @param daysAhead Número de días hacia adelante para buscar eventos.
     * @return Lista de [ExamenEvent] importados.
     * @throws UserRecoverableAuthIOException si necesita permiso del usuario.
     */
    suspend fun fetchUpcomingEvents(
        userEmail: String,
        materiaId: Long,
        daysAhead: Int = 90
    ): List<ExamenEvent> = withContext(Dispatchers.IO) {
        val calendarClient = buildCalendarClient(userEmail)

        val now = Instant.now()
        val future = now.plusSeconds(daysAhead.toLong() * 86400)

        val events = calendarClient.events().list("primary")
            .setMaxResults(MAX_RESULTS)
            .setTimeMin(DateTime(now.toEpochMilli()))
            .setTimeMax(DateTime(future.toEpochMilli()))
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .execute()

        val items = events.items ?: emptyList()
        Timber.i("Google Calendar: ${items.size} eventos encontrados")

        items.mapNotNull { event ->
            try {
                val start = event.start ?: return@mapNotNull null
                val startMs = when {
                    start.dateTime != null -> start.dateTime.value
                    start.date != null -> start.date.value
                    else -> return@mapNotNull null
                }

                val title = event.summary ?: "Sin título"
                val description = event.description ?: ""
                val tipo = inferTipoEvento(title, description)

                ExamenEvent(
                    id = 0, // Nuevo, Room asignará ID
                    materiaId = materiaId,
                    titulo = title,
                    descripcion = description.take(500),
                    tipoEvento = tipo,
                    fechaEpochMs = startMs,
                    recordatorioMinutos = 60
                )
            } catch (e: Exception) {
                Timber.w(e, "Error mapeando evento de Google Calendar: ${event.summary}")
                null
            }
        }
    }

    /**
     * Infiere el tipo de evento basándose en palabras clave del título y descripción.
     */
    private fun inferTipoEvento(title: String, description: String): TipoEvento {
        val text = "$title $description".lowercase()
        return when {
            text.contains("parcial") || text.contains("midterm") -> TipoEvento.PARCIAL
            text.contains("final") || text.contains("examen final") -> TipoEvento.FINAL
            text.contains("quiz") || text.contains("prueba") || text.contains("test") -> TipoEvento.QUIZ
            text.contains("tarea") || text.contains("homework") || text.contains("entrega") -> TipoEvento.TAREA
            text.contains("proyecto") || text.contains("project") -> TipoEvento.PROYECTO
            text.contains("exposición") || text.contains("exposicion") || text.contains("presentación") || text.contains("presentacion") -> TipoEvento.EXPOSICION
            else -> TipoEvento.OTRO
        }
    }
}
