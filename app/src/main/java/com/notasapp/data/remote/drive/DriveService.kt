package com.notasapp.data.remote.drive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.notasapp.domain.model.Materia
import com.notasapp.utils.ExcelExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado de una subida exitosa a Google Drive.
 */
data class DriveUploadResult(
    val fileId: String,
    val webViewLink: String
)

/**
 * Servicio de subida de archivos Excel a Google Drive usando
 * **la REST API v3 directamente** (sin google-api-services-drive).
 *
 * Esto evita problemas de serialización con R8/@Key que causaban
 * "the name must not be empty null" en la librería oficial.
 *
 * Reutiliza [ExcelExporter] para generar el .xlsx y lo sube con
 * upload multipart (metadata JSON + contenido binario).
 */
@Singleton
class DriveService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excelExporter: ExcelExporter
) {

    companion object {
        private const val XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files"
        private const val FIELDS = "id,webViewLink"
        private val SCOPES = listOf("https://www.googleapis.com/auth/drive.file")
    }

    /**
     * Obtiene un access token OAuth2 para la cuenta dada.
     * Lanza [UserRecoverableAuthIOException] si falta consentimiento.
     */
    private fun getAccessToken(userEmail: String): String {
        // Validación preventiva: Account(null/empty, ...) lanza IllegalArgumentException
        if (userEmail.isBlank()) {
            throw IOException(
                "No se puede autenticar: el email del usuario está vacío. " +
                "Debes iniciar sesión con Google antes de sincronizar."
            )
        }
        Timber.d("Obteniendo token OAuth2 para: $userEmail")

        val credential = GoogleAccountCredential.usingOAuth2(context, SCOPES)
            .apply { selectedAccountName = userEmail }

        return try {
            credential.token
                ?: throw IOException("El token OAuth2 es null para $userEmail")
        } catch (e: UserRecoverableAuthIOException) {
            Timber.w("Se requiere consentimiento de Drive para $userEmail")
            throw e
        }
    }

    /**
     * Sube (o actualiza) un archivo .xlsx con los datos de [materia] a Google Drive.
     *
     * - Si [existingFileId] es `null`, crea un archivo nuevo.
     * - Si tiene valor, actualiza el contenido del archivo existente.
     * - Si el archivo fue eliminado externamente (404), lanza [DriveFileNotFoundException].
     *
     * @return [DriveUploadResult] con el ID del archivo y un link para abrirlo.
     * @throws UserRecoverableAuthIOException si falta permiso OAuth2.
     * @throws DriveFileNotFoundException     si el archivo ya no existe en Drive.
     * @throws IOException                    cualquier otro error de red/API.
     */
    suspend fun uploadMateria(
        materia: Materia,
        userEmail: String,
        existingFileId: String? = null
    ): DriveUploadResult = withContext(Dispatchers.IO) {

        // ── 1. Validar email ────────────────────────────────────────────
        Timber.d("uploadMateria: email='$userEmail', existing=$existingFileId, materia='${materia.nombre}'")

        if (userEmail.isBlank()) {
            throw IOException(
                "No se puede sincronizar: el email del usuario está vacío. " +
                "Inicia sesión con Google primero."
            )
        }

        // ── 2. Obtener token OAuth2 ─────────────────────────────────────
        val token = try {
            getAccessToken(userEmail)
        } catch (e: UserRecoverableAuthIOException) {
            throw e // UI debe pedir consentimiento
        } catch (e: IOException) {
            throw e // Ya tiene mensaje descriptivo
        } catch (e: Exception) {
            Timber.e(e, "Error inesperado de auth: ${e.javaClass.simpleName}")
            throw IOException(
                "Error de autenticación con Google (${e.javaClass.simpleName}): ${e.message}",
                e
            )
        }

        // ── 2. Generar Excel ────────────────────────────────────────────
        val xlsxBytes = excelExporter.buildWorkbookBytes(materia)
        if (xlsxBytes.isEmpty()) {
            throw IOException("El archivo Excel generado tiene 0 bytes")
        }

        val fileName = buildFileName(materia)
        Timber.d("Drive upload: ${xlsxBytes.size} bytes para '$fileName'")

        require(fileName.isNotBlank()) {
            "El nombre del archivo no puede estar vacío. Materia: ${materia.nombre}"
        }

        // ── 3. Construir metadata JSON (texto plano, sin @Key/R8) ───────
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            if (existingFileId == null) put("mimeType", XLSX_MIME)
        }.toString()

        Timber.d("Metadata JSON: $metadataJson")

        // ── 4. Construir body multipart ─────────────────────────────────
        val boundary = "gradify_${System.nanoTime()}"
        val bodyBytes = buildMultipartBody(boundary, metadataJson, XLSX_MIME, xlsxBytes)

        // ── 5. Enviar petición HTTP ─────────────────────────────────────
        val url = if (existingFileId != null) {
            URL("$UPLOAD_URL/$existingFileId?uploadType=multipart&fields=$FIELDS")
        } else {
            URL("$UPLOAD_URL?uploadType=multipart&fields=$FIELDS")
        }

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (existingFileId != null) "PATCH" else "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
        }

        try {
            conn.outputStream.use { it.write(bodyBytes) }

            val responseCode = conn.responseCode
            Timber.d("Drive API response: $responseCode")

            // ── Archivo eliminado externamente ──────────────────────────
            if (responseCode == 404 && existingFileId != null) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw DriveFileNotFoundException(
                    "El archivo fue eliminado de Google Drive. Se creará uno nuevo. ($errorBody)",
                    IOException("HTTP 404")
                )
            }

            // ── Otro error ──────────────────────────────────────────────
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw IOException(
                    "Drive API error $responseCode: ${errorBody.take(500)}"
                )
            }

            // ── Éxito ───────────────────────────────────────────────────
            val responseBody = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)
            val fileId = json.getString("id")
            val webViewLink = json.optString("webViewLink", "")
                .ifBlank { "https://drive.google.com/file/d/$fileId/view" }

            Timber.i("Drive upload OK: fileId=$fileId link=$webViewLink")
            DriveUploadResult(fileId = fileId, webViewLink = webViewLink)

        } finally {
            conn.disconnect()
        }
    }

    /**
     * Construye el body multipart/related con metadata JSON + contenido binario.
     */
    private fun buildMultipartBody(
        boundary: String,
        metadataJson: String,
        contentMimeType: String,
        fileBytes: ByteArray
    ): ByteArray {
        val crlf = "\r\n"
        val baos = ByteArrayOutputStream(fileBytes.size + metadataJson.length + 256)

        // Parte 1: metadata JSON
        baos.write("--$boundary$crlf".toByteArray())
        baos.write("Content-Type: application/json; charset=UTF-8$crlf".toByteArray())
        baos.write(crlf.toByteArray())
        baos.write(metadataJson.toByteArray(Charsets.UTF_8))
        baos.write(crlf.toByteArray())

        // Parte 2: contenido del archivo
        baos.write("--$boundary$crlf".toByteArray())
        baos.write("Content-Type: $contentMimeType$crlf".toByteArray())
        baos.write(crlf.toByteArray())
        baos.write(fileBytes)
        baos.write(crlf.toByteArray())

        // Final
        baos.write("--$boundary--".toByteArray())

        return baos.toByteArray()
    }

    private fun buildFileName(materia: Materia): String {
        val nombre = materia.nombre.ifBlank { "Materia" }
        val periodo = if (materia.periodo.isNotBlank()) " – ${materia.periodo}" else ""
        return "$nombre$periodo | Gradify.xlsx"
    }
}

/**
 * Excepción lanzada cuando el archivo ya no existe en Drive (404 en update).
 */
class DriveFileNotFoundException(message: String, cause: Throwable) : IOException(message, cause)
