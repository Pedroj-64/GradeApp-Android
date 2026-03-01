package com.notasapp.data.remote.sheets

import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import com.notasapp.domain.model.Materia
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acceso directo a la API de Google Sheets v4.
 *
 * Todas las operaciones son **suspendibles** y se ejecutan en [Dispatchers.IO].
 * La autenticación se realiza con [GoogleAccountCredential] que usa el
 * AccountManager del dispositivo — el usuario debe tener su cuenta Google
 * registrada y haber concedido el scope [SheetsScopes.SPREADSHEETS].
 *
 * Si el usuario no ha concedido permisos, la llamada lanza una
 * [UserRecoverableAuthIOException]; el llamador debe capturarla y lanzar
 * el [Intent] de recuperación para obtener consentimiento.
 */
@Singleton
class SheetsService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val APP_NAME = "Gradify"
        private val SCOPES = listOf(
            SheetsScopes.SPREADSHEETS,
            "https://www.googleapis.com/auth/drive.file"
        )
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1500L
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    /**
     * Construye un cliente [Sheets] autenticado con la cuenta [userEmail].
     * Debe llamarse dentro de un contexto de IO.
     */
    private fun buildSheetsService(userEmail: String): Sheets {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, SCOPES)
            .also { it.selectedAccountName = userEmail }

        val httpTransport = NetHttpTransport.Builder()
            .build()

        return Sheets.Builder(
            httpTransport,
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    /**
     * Ejecuta [block] con reintentos para errores transitorios de red.
     * Los errores de autenticación ([UserRecoverableAuthIOException]) se
     * propagan inmediatamente sin reintentar.
     */
    private suspend fun <T> withRetry(
        operationName: String,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                return block()
            } catch (e: UserRecoverableAuthIOException) {
                // No reintentar errores de autenticación
                throw e
            } catch (e: Exception) {
                lastException = e
                val isTransient = e is SocketTimeoutException ||
                        e is UnknownHostException ||
                        (e is IOException && e.message?.contains("503") == true) ||
                        (e is IOException && e.message?.contains("rate", ignoreCase = true) == true)

                if (isTransient && attempt < MAX_RETRIES) {
                    val delayMs = RETRY_DELAY_MS * attempt
                    Timber.w("$operationName: intento $attempt falló (${e.message}), reintentando en ${delayMs}ms…")
                    delay(delayMs)
                } else {
                    Timber.e(e, "$operationName: falló tras $attempt intento(s)")
                    throw e
                }
            }
        }
        throw lastException ?: IOException("$operationName: falló tras $MAX_RETRIES intentos")
    }

    // ── Operaciones públicas ──────────────────────────────────────────────────

    /**
     * Crea un nuevo spreadsheet con el nombre de la [materia].
     *
     * @return ID del spreadsheet creado.
     * @throws UserRecoverableAuthIOException si se necesita consentimiento OAuth2.
     */
    suspend fun createSpreadsheet(materia: Materia, userEmail: String): String =
        withContext(Dispatchers.IO) {
            withRetry("createSpreadsheet") {
                val service = buildSheetsService(userEmail)
                val spreadsheet = service.spreadsheets().create(
                    Spreadsheet().setProperties(
                        SpreadsheetProperties()
                            .setTitle("${materia.nombre} – ${materia.periodo} | Gradify")
                    )
                ).execute()

                val id = spreadsheet?.spreadsheetId
                if (id.isNullOrBlank()) {
                    throw IOException("Google Sheets no devolvió un ID válido al crear la hoja")
                }
                id
            }
        }

    /**
     * Escribe o reemplaza todos los datos de la [materia] en el spreadsheet
     * identificado por [spreadsheetId].
     *
     * La estructura de filas es:
     * 1. Encabezado: nombre, período, promedio final, estado de aprobación
     * 2. Fila de acumulado y progreso
     * 3. Fila de títulos de columnas
     * 4–N. Una fila por sub-nota (Componente, Sub-nota, % corte, Nota, Promedio comp., Aporte)
     * N+1. Fila de totales resumen
     *
     * @throws UserRecoverableAuthIOException si se necesita consentimiento OAuth2.
     */
    suspend fun writeMateria(
        spreadsheetId: String,
        materia: Materia,
        userEmail: String
    ): Unit = withContext(Dispatchers.IO) {
        withRetry("writeMateria(${materia.nombre})") {
            val service = buildSheetsService(userEmail)
            val sheetName = "Sheet1"
            val range = "$sheetName!A1"

            // Limpiar hoja antes de escribir
            try {
                service.spreadsheets().values()
                    .clear(spreadsheetId, sheetName, ClearValuesRequest())
                    .execute()
            } catch (e: Exception) {
                Timber.w(e, "No se pudo limpiar la hoja (puede ser nueva), continuando…")
            }

            // Construir todas las filas
            val rows: MutableList<List<Any>> = mutableListOf()

            // Fila 1: Encabezado de la materia
            rows += listOf(
                "Materia",
                materia.nombre,
                "Período",
                materia.periodo,
                "Profesor",
                materia.profesor ?: "—"
            )

            // Fila 2: Promedio y estado
            rows += listOf(
                "Promedio actual",
                materia.promedioDisplay,
                "Escala",
                "0 – ${materia.escalaMax.toInt()}",
                "Mínimo para aprobar",
                materia.notaAprobacion,
                "Estado",
                if (materia.aprobado) "APROBADO" else "EN RIESGO"
            )

            // Fila 3: Acumulado y progreso
            val evaluadoPct = "${(materia.porcentajeEvaluado * 100).toInt()}%"
            val mensajeAcum = if (materia.yaAprobo) {
                "¡Felicidades! Ya superaste el mínimo para aprobar"
            } else {
                materia.notaNecesariaParaAprobar?.let {
                    "Necesitas ≈ ${"%.2f".format(it)} en lo restante"
                } ?: ""
            }
            rows += listOf(
                "Acumulado",
                materia.acumuladoDisplay,
                "Evaluado",
                evaluadoPct,
                mensajeAcum,
                "",
                "",
                ""
            )

            // Fila 4: Vacía (separador)
            rows += listOf("", "", "", "", "", "", "", "")

            // Fila 5: Títulos de columnas de notas
            rows += listOf(
                "Componente",
                "Sub-nota",
                "% del componente",
                "% del total",
                "Nota",
                "Promedio componente",
                "Aporte al final",
                "Progreso"
            )

            // Filas de datos por sub-nota
            for (comp in materia.componentes) {
                if (comp.subNotas.isEmpty()) {
                    rows += listOf(
                        comp.nombre,
                        "(sin actividades)",
                        "—",
                        "${comp.porcentajeDisplay}%",
                        "—",
                        "—",
                        "—",
                        comp.progresoDisplay
                    )
                } else {
                    for ((idx, sub) in comp.subNotas.withIndex()) {
                        val compName = if (idx == 0) comp.nombre else ""
                        rows += listOf(
                            compName,
                            sub.descripcion,
                            "${(sub.porcentajeDelComponente * 100).toInt()}%",
                            "${(sub.porcentajeDelComponente * comp.porcentaje * 100).toInt()}%",
                            sub.valorEfectivo?.let { "%.2f".format(it) } ?: "—",
                            if (idx == 0) (comp.promedio?.let { "%.2f".format(it) } ?: "—") else "",
                            if (idx == 0) (comp.aporteAlFinal?.let { "%.2f".format(it) } ?: "—") else "",
                            if (idx == 0) comp.progresoDisplay else ""
                        )

                        // Si la sub-nota es compuesta, agregar filas de detalles
                        if (sub.esCompuesta) {
                            for (detalle in sub.detalles) {
                                rows += listOf(
                                    "",
                                    "  · ${detalle.descripcion}",
                                    "${(detalle.porcentaje * 100).toInt()}%",
                                    "",
                                    detalle.valor?.let { "%.2f".format(it) } ?: "—",
                                    "",
                                    "",
                                    ""
                                )
                            }
                        }
                    }
                }
            }

            // Fila final vacía + Totales
            rows += listOf("", "", "", "", "", "", "", "")
            rows += listOf(
                "PROMEDIO FINAL",
                materia.promedioDisplay,
                "Acumulado",
                materia.acumuladoDisplay,
                "",
                "",
                "Exportado con Gradify",
                ""
            )

            // Escribir en batch
            val body = ValueRange().setValues(rows)
            service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            Timber.d("SheetsService: materia '${materia.nombre}' escrita en $spreadsheetId (${rows.size} filas)")
        }
    }

    /**
     * Verifica que la cuenta [userEmail] tiene acceso a Sheets realizando
     * una lectura mínima. Útil para detectar si se necesita consentimiento.
     *
     * @throws UserRecoverableAuthIOException si se necesita consentimiento OAuth2.
     */
    suspend fun verifyAccess(userEmail: String): Unit = withContext(Dispatchers.IO) {
        val service = buildSheetsService(userEmail)
        // Llamada ligera: obtener info de un spreadsheet inexistente generará un 404,
        // pero si el token no es válido lanzará UserRecoverableAuthIOException.
        // Usamos una lectura a un spreadsheet ficticio que simplemente falla con 404
        // pero valida la autenticación.
        try {
            service.spreadsheets()
                .get("__verify_access_dummy__")
                .setFields("spreadsheetId")
                .execute()
        } catch (e: UserRecoverableAuthIOException) {
            throw e  // propagar para que la UI pida consentimiento
        } catch (e: Exception) {
            // Cualquier otro error (404, etc.) significa que el token SÍ es válido
            Timber.d("SheetsService: acceso verificado (error esperado: ${e.message})")
        }
    }
}

/**
 * Datos extraídos de una [UserRecoverableAuthIOException] para exponerlos a la UI.
 *
 * @param intent  Intent que la UI debe lanzar con `startActivityForResult`
 *                para obtener consentimiento OAuth2.
 */
data class UserRecoveryData(val intent: Intent)
