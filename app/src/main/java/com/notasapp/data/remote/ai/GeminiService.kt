package com.notasapp.data.remote.ai

import com.notasapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modelo de una recomendación generada por IA.
 *
 * @param tipo         YOUTUBE, LIBRO o RECURSO
 * @param titulo       Título del recurso recomendado
 * @param descripcion  Breve descripción de por qué es útil
 * @param url          Enlace directo (YouTube URL o link de búsqueda)
 * @param autor        Autor o canal (opcional)
 */
data class Recomendacion(
    val tipo: TipoRecomendacion,
    val titulo: String,
    val descripcion: String,
    val url: String,
    val autor: String? = null
)

enum class TipoRecomendacion {
    YOUTUBE, LIBRO, RECURSO
}

/**
 * Servicio de IA que usa la API gratuita de Gemini (Google Generative AI)
 * para analizar el nombre de una materia y generar recomendaciones de
 * videos de YouTube, libros y recursos de estudio.
 *
 * Requiere que `GEMINI_API_KEY` esté configurado en `local.properties`.
 * Obtén una clave gratuita en: https://aistudio.google.com/app/apikey
 */
@Singleton
class GeminiService @Inject constructor() {

    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private const val TIMEOUT_MS = 30_000
    }

    /**
     * Genera recomendaciones de estudio para una materia dada.
     *
     * @param nombreMateria  Nombre de la materia (ej: "Morfología", "Cálculo Integral")
     * @param periodo        Período académico (opcional, para contexto)
     * @return Lista de recomendaciones o lista vacía si hay error
     */
    suspend fun generarRecomendaciones(
        nombreMateria: String,
        periodo: String? = null
    ): Result<List<Recomendacion>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("GEMINI_API_KEY no configurada. Agrega GEMINI_API_KEY=tu_clave en local.properties")
            )
        }

        try {
            val prompt = buildPrompt(nombreMateria, periodo)
            val responseText = callGeminiApi(apiKey, prompt)
            val recomendaciones = parseRecomendaciones(responseText)
            Result.success(recomendaciones)
        } catch (e: Exception) {
            Timber.e(e, "Error al generar recomendaciones para '$nombreMateria'")
            Result.failure(e)
        }
    }

    private fun buildPrompt(nombreMateria: String, periodo: String?): String {
        val contexto = periodo?.let { " del período $it" } ?: ""
        return """
Eres un asistente educativo experto. Analiza el nombre de esta materia universitaria$contexto y genera recomendaciones de estudio.

Materia: "$nombreMateria"

Genera EXACTAMENTE 6 recomendaciones en formato JSON, siguiendo esta estructura:
- 3 videos de YouTube (canales educativos reales y populares en español o inglés)
- 2 libros de referencia (títulos reales y reconocidos)
- 1 recurso web gratuito (plataforma educativa, sitio web, herramienta)

Para los videos de YouTube, genera URLs de búsqueda de YouTube con el tema específico.
Para los libros, genera URLs de búsqueda en Google Books.
Para recursos web, usa URLs reales de plataformas educativas conocidas.

Responde SOLO con un JSON array válido, sin markdown ni texto adicional:
[
  {
    "tipo": "YOUTUBE",
    "titulo": "Título descriptivo del video/canal",
    "descripcion": "Por qué este recurso es útil para la materia",
    "url": "https://www.youtube.com/results?search_query=tema+específico",
    "autor": "Nombre del canal o creador"
  },
  {
    "tipo": "LIBRO",
    "titulo": "Título del Libro",
    "descripcion": "Por qué este libro es recomendado",
    "url": "https://www.google.com/search?tbm=bks&q=titulo+del+libro",
    "autor": "Autor del libro"
  },
  {
    "tipo": "RECURSO",
    "titulo": "Nombre del recurso",
    "descripcion": "Qué ofrece este recurso",
    "url": "https://url-real-del-recurso.com",
    "autor": null
  }
]
""".trimIndent()
    }

    private fun callGeminiApi(apiKey: String, prompt: String): String {
        val url = URL("$BASE_URL?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true

            // Construir el body de la request
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 2048)
                    put("responseMimeType", "application/json")
                })
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.let { stream ->
                    BufferedReader(InputStreamReader(stream)).use { it.readText() }
                } ?: "Sin detalles"
                Timber.e("Gemini API error $responseCode: $errorStream")
                throw Exception("Error de la API de Gemini ($responseCode): $errorStream")
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            // Extraer el texto de la respuesta de Gemini
            val jsonResponse = JSONObject(response)
            val candidates = jsonResponse.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecomendaciones(jsonText: String): List<Recomendacion> {
        // Limpiar posible markdown wrapping
        val cleanJson = jsonText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val array = JSONArray(cleanJson)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Recomendacion(
                    tipo = try {
                        TipoRecomendacion.valueOf(obj.getString("tipo"))
                    } catch (_: Exception) {
                        TipoRecomendacion.RECURSO
                    },
                    titulo = obj.getString("titulo"),
                    descripcion = obj.getString("descripcion"),
                    url = obj.getString("url"),
                    autor = obj.optString("autor", null)
                        ?.takeIf { it != "null" && it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al parsear recomendaciones: $cleanJson")
            emptyList()
        }
    }
}
