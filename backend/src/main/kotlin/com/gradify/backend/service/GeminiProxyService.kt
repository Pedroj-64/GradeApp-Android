package com.gradify.backend.service

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.gradify.backend.dto.RecomendacionDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Servicio que envuelve las llamadas a la API REST de Gemini.
 *
 * Usa la REST API directa (no el SDK de Android) para máxima
 * compatibilidad en el servidor JVM. Incluye fallback automático
 * entre modelos.
 */
@Service
class GeminiProxyService(
    @Value("\${gemini.api-key}") private val apiKey: String,
    @Value("\${gemini.model}") private val primaryModel: String,
    @Value("\${gemini.fallback-models}") private val fallbackModels: List<String>
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val allModels: List<String> by lazy {
        listOf(primaryModel) + fallbackModels
    }

    private val normalizedApiKey: String by lazy {
        apiKey.trim().takeUnless {
            it.isBlank() || it.equals("null", ignoreCase = true)
        } ?: ""
    }

    /**
     * Genera recomendaciones usando la REST API de Gemini con fallback.
     *
     * @return Pair<List<RecomendacionDto>, String> → recomendaciones + nombre del modelo usado
     */
    fun generarRecomendaciones(prompt: String): Pair<List<RecomendacionDto>, String> {
        require(normalizedApiKey.isNotBlank()) {
            "GEMINI_API_KEY no está configurada en el servidor"
        }

        var lastException: Exception? = null

        for (modelName in allModels) {
            try {
                log.debug("Intentando modelo: {}", modelName)
                val responseText = callGeminiApi(modelName, prompt)
                val recomendaciones = parseRecomendaciones(responseText)

                if (recomendaciones.isEmpty()) {
                    log.warn("Modelo {} devolvió 0 recomendaciones, probando siguiente", modelName)
                    lastException = Exception("Sin recomendaciones de $modelName")
                    continue
                }

                log.info("Modelo {} generó {} recomendaciones", modelName, recomendaciones.size)
                return recomendaciones to modelName
            } catch (e: Exception) {
                log.warn("Modelo {} falló: {}", modelName, e.message)
                lastException = e

                // Si la API key es inválida, no reintentar con otro modelo
                if (e.message?.contains("API_KEY_INVALID", ignoreCase = true) == true ||
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                    throw e
                }
            }
        }

        throw lastException ?: Exception("Todos los modelos de Gemini fallaron")
    }

    /**
     * Llama a la REST API de Gemini (/v1beta/models/{model}:generateContent).
     */
    private fun callGeminiApi(modelName: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

        val requestBody = gson.toJson(mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.4,
                "maxOutputTokens" to 2048
            )
        ))

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", normalizedApiKey)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                log.error("Gemini API error {}: {}", response.code, body)
                throw Exception("Gemini API error ${response.code}: $body")
            }

            // Extraer el texto de la respuesta
            val json = JsonParser.parseString(body).asJsonObject
            val candidates = json.getAsJsonArray("candidates")
                ?: throw Exception("Sin candidates en la respuesta")
            val firstCandidate = candidates[0].asJsonObject
            val content = firstCandidate.getAsJsonObject("content")
            val parts = content.getAsJsonArray("parts")
            parts[0].asJsonObject.get("text").asString
        }
    }

    /**
     * Parsea el JSON de recomendaciones de la respuesta de Gemini.
     */
    private fun parseRecomendaciones(rawText: String): List<RecomendacionDto> {
        val jsonStr = extractJsonArray(rawText)
        return try {
            val array = JsonParser.parseString(jsonStr).asJsonArray
            array.map { element ->
                val obj = element.asJsonObject
                RecomendacionDto(
                    tipo = obj.get("tipo")?.asString ?: "RECURSO",
                    titulo = obj.get("titulo")?.asString ?: "",
                    descripcion = obj.get("descripcion")?.asString ?: "",
                    url = obj.get("url")?.asString ?: "",
                    autor = obj.get("autor")?.asString
                )
            }.filter { it.titulo.isNotBlank() && it.url.isNotBlank() }
        } catch (e: Exception) {
            log.error("Error parseando recomendaciones: {}", e.message)
            throw Exception("Error parseando respuesta de IA: ${e.message}")
        }
    }

    /**
     * Extrae el primer array JSON [...] del texto de respuesta.
     */
    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) {
            throw Exception("No se encontró un array JSON en la respuesta")
        }
        return text.substring(start, end + 1)
    }
}
