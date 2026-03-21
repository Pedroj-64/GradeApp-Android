package com.notasapp.data.remote.ai

import com.notasapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modelo de una recomendación generada por IA.
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
 * Servicio de IA multi-proveedor para generar recomendaciones de estudio.
 *
 * Cadena de fallback:
 * 1. **Backend proxy** (no expone API key, configurable)
 * 2. **Gemini REST API** (`generativelanguage.googleapis.com`) — API key gratuita
 * 3. **Groq REST API** (`api.groq.com`) — API key gratuita, modelos Llama 3
 * 4. **OpenRouter REST API** (`openrouter.ai`) — modelos gratuitos disponibles
 *
 * Todas las llamadas son REST puro (sin SDKs), para evitar problemas con R8.
 *
 * Claves necesarias en `local.properties`:
 * - `GEMINI_API_KEY`    → https://aistudio.google.com/app/apikey
 * - `GROQ_API_KEY`      → https://console.groq.com/keys (gratis)
 * - `OPENROUTER_API_KEY` → https://openrouter.ai/keys (gratis, modelos free)
 */
@Singleton
class GeminiService @Inject constructor() {

    companion object {
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 2_000L

        // ── Gemini ──────────────────────────────────────────────────
        private const val GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta"
        private val GEMINI_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-pro"
        )

        // ── Groq ────────────────────────────────────────────────────
        private const val GROQ_API_BASE =
            "https://api.groq.com/openai/v1/chat/completions"
        private val GROQ_MODELS = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768"
        )

        // ── OpenRouter ──────────────────────────────────────────────
        private const val OPENROUTER_API_BASE =
            "https://openrouter.ai/api/v1/chat/completions"
        private val OPENROUTER_MODELS = listOf(
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "mistralai/mistral-7b-instruct:free"
        )

        /** URL base del backend proxy (configurada en local.properties). */
        private val BACKEND_URL: String = BuildConfig.BACKEND_URL
            .trim()
            .takeUnless { it.equals("null", ignoreCase = true) || it.isBlank() }
            ?: ""

        private fun normalizeApiKey(rawValue: String): String = rawValue
            .trim()
            .takeUnless {
                it.isBlank() ||
                    it.equals("null", ignoreCase = true) ||
                    it.startsWith("YOUR_", ignoreCase = true)
            }
            ?: ""
    }

    /** Throttle básico: ~13 RPM global. */
    @Volatile private var lastCallTimestamp = 0L
    private val minIntervalMs = 4_500L

    // ═══════════════════════════════════════════════════════════════════
    //  Flujo principal
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Genera recomendaciones de estudio para una materia dada.
     * Prueba múltiples proveedores de IA en cascada hasta que uno funcione.
     */
    suspend fun generarRecomendaciones(
        nombreMateria: String,
        periodo: String? = null,
        componentesInfo: List<Pair<String, Float?>>? = null,
        promedio: Float? = null,
        porcentajeEvaluado: Float? = null,
        notaAprobacion: Float? = null,
        aprobado: Boolean? = null
    ): Result<List<Recomendacion>> = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(
            nombreMateria, periodo, componentesInfo,
            promedio, porcentajeEvaluado, notaAprobacion, aprobado
        )
        val errors = mutableListOf<String>()

        // ── 1. Backend proxy ───────────────────────────────────────
        if (BACKEND_URL.isNotBlank()) {
            try {
                Timber.d("Estrategia 1: backend proxy → $BACKEND_URL")
                val result = callBackendProxy(
                    nombreMateria, periodo, componentesInfo,
                    promedio, porcentajeEvaluado, notaAprobacion, aprobado
                )
                if (result.isNotEmpty()) {
                    Timber.i("Backend proxy → ${result.size} recomendaciones")
                    return@withContext Result.success(result)
                }
            } catch (e: Exception) {
                errors += "Backend: ${e.message}"
                Timber.w(e, "Backend proxy falló")
            }
        }

        // Throttle global
        val elapsed = System.currentTimeMillis() - lastCallTimestamp
        if (elapsed < minIntervalMs) delay(minIntervalMs - elapsed)

        // ── 2. Gemini REST API ─────────────────────────────────────
        val geminiKey = normalizeApiKey(BuildConfig.GEMINI_API_KEY)
        if (geminiKey.isNotBlank()) {
            try {
                Timber.d("Estrategia 2: Gemini REST API")
                val text = tryProviderModels("Gemini", GEMINI_MODELS) { model ->
                    callGeminiRest(geminiKey, model, prompt)
                }
                lastCallTimestamp = System.currentTimeMillis()
                return@withContext Result.success(parseRecomendaciones(text))
            } catch (e: Exception) {
                errors += "Gemini: ${e.message}"
                Timber.w(e, "Gemini REST falló")
            }
        }

        // ── 3. Groq REST API ───────────────────────────────────────
        val groqKey = normalizeApiKey(BuildConfig.GROQ_API_KEY)
        if (groqKey.isNotBlank()) {
            try {
                Timber.d("Estrategia 3: Groq REST API")
                val text = tryProviderModels("Groq", GROQ_MODELS) { model ->
                    callOpenAICompatible(GROQ_API_BASE, groqKey, model, prompt)
                }
                lastCallTimestamp = System.currentTimeMillis()
                return@withContext Result.success(parseRecomendaciones(text))
            } catch (e: Exception) {
                errors += "Groq: ${e.message}"
                Timber.w(e, "Groq REST falló")
            }
        }

        // ── 4. OpenRouter REST API ─────────────────────────────────
        val orKey = normalizeApiKey(BuildConfig.OPENROUTER_API_KEY)
        if (orKey.isNotBlank()) {
            try {
                Timber.d("Estrategia 4: OpenRouter REST API")
                val text = tryProviderModels("OpenRouter", OPENROUTER_MODELS) { model ->
                    callOpenAICompatible(OPENROUTER_API_BASE, orKey, model, prompt)
                }
                lastCallTimestamp = System.currentTimeMillis()
                return@withContext Result.success(parseRecomendaciones(text))
            } catch (e: Exception) {
                errors += "OpenRouter: ${e.message}"
                Timber.w(e, "OpenRouter REST falló")
            }
        }

        // ── Todos agotados ─────────────────────────────────────────
        val summary = errors.joinToString(" | ")
        val noKeysMsg = buildString {
            if (geminiKey.isBlank()) append("GEMINI_API_KEY, ")
            if (groqKey.isBlank()) append("GROQ_API_KEY, ")
            if (orKey.isBlank()) append("OPENROUTER_API_KEY, ")
        }.trimEnd(',', ' ')

        val errorMsg = if (noKeysMsg.isNotBlank())
            "No se configuraron claves de IA ($noKeysMsg). Agrega al menos una en local.properties. Errores: $summary"
        else
            "Todos los proveedores de IA fallaron. $summary"

        Result.failure(IllegalStateException(errorMsg))
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Proveedores de IA
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Prueba múltiples modelos de un proveedor con reintentos y backoff.
     */
    private suspend fun tryProviderModels(
        providerName: String,
        models: List<String>,
        apiCall: (model: String) -> String
    ): String {
        var lastException: Exception? = null

        for (model in models) {
            for (attempt in 1..MAX_RETRIES) {
                try {
                    Timber.d("$providerName → $model (intento $attempt)")
                    val text = apiCall(model)
                    if (text.isBlank()) {
                        lastException = Exception("$model devolvió respuesta vacía")
                        continue
                    }
                    Timber.i("$providerName → $model OK (${text.length} chars)")
                    return text
                } catch (e: AiApiException) {
                    Timber.w("$providerName → $model HTTP ${e.statusCode}")
                    lastException = e

                    // API key inválida → no reintentar este proveedor
                    if (e.isAuthError) throw e

                    // Modelo no existe → siguiente modelo
                    if (e.isNotFound) break

                    // Rate limit → backoff
                    if (e.statusCode == 429 && attempt < MAX_RETRIES) {
                        delay(INITIAL_BACKOFF_MS * attempt * 2)
                        continue
                    }

                    if (attempt < MAX_RETRIES) delay(INITIAL_BACKOFF_MS * attempt)
                } catch (e: Exception) {
                    lastException = e
                    Timber.w("$providerName → $model error: ${e.message}")
                    if (attempt < MAX_RETRIES) delay(INITIAL_BACKOFF_MS * attempt)
                }
            }
        }

        throw lastException ?: Exception("$providerName: todos los modelos agotados")
    }

    // ── Gemini REST ─────────────────────────────────────────────────

    /**
     * POST /v1beta/models/{model}:generateContent?key={apiKey}
     */
    private fun callGeminiRest(apiKey: String, modelName: String, prompt: String): String {
        val url = URL("$GEMINI_API_BASE/models/$modelName:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 2048)
            })
        }

        conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val code = conn.responseCode

        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            throw AiApiException(code, err, "Gemini")
        }

        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = JSONObject(resp)
        val candidates = json.optJSONArray("candidates")
            ?: throw Exception("Sin 'candidates' en respuesta de $modelName. Resp: ${resp.take(200)}")
        if (candidates.length() == 0) throw Exception("candidates vacío de $modelName")

        return candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    // ── OpenAI-compatible (Groq, OpenRouter, etc.) ──────────────────

    /**
     * POST a endpoint compatible con formato OpenAI Chat Completions.
     * Funciona con Groq, OpenRouter, Together AI, etc.
     */
    private fun callOpenAICompatible(
        apiUrl: String,
        apiKey: String,
        modelName: String,
        prompt: String
    ): String {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
        }

        val body = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Eres un tutor educativo experto. Responde SOLO con JSON array válido, sin texto adicional.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.4)
            put("max_tokens", 2048)
        }

        conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val code = conn.responseCode

        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            throw AiApiException(code, err, "OpenAI-compat")
        }

        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = JSONObject(resp)
        val choices = json.optJSONArray("choices")
            ?: throw Exception("Sin 'choices' en respuesta. Resp: ${resp.take(200)}")
        if (choices.length() == 0) throw Exception("choices vacío")

        return choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Backend proxy
    // ═══════════════════════════════════════════════════════════════════

    private fun callBackendProxy(
        nombreMateria: String,
        periodo: String?,
        componentesInfo: List<Pair<String, Float?>>?,
        promedio: Float?,
        porcentajeEvaluado: Float?,
        notaAprobacion: Float?,
        aprobado: Boolean?
    ): List<Recomendacion> {
        val url = URL("${BACKEND_URL.trimEnd('/')}/api/v1/recomendaciones")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
        }

        val requestJson = JSONObject().apply {
            put("nombreMateria", nombreMateria)
            periodo?.let { put("periodo", it) }
            promedio?.let { put("promedio", it.toDouble()) }
            porcentajeEvaluado?.let { put("porcentajeEvaluado", it.toDouble()) }
            notaAprobacion?.let { put("notaAprobacion", it.toDouble()) }
            aprobado?.let { put("aprobado", it) }
            if (!componentesInfo.isNullOrEmpty()) {
                val compArray = JSONArray()
                componentesInfo.forEach { (nombre, nota) ->
                    compArray.put(JSONObject().apply {
                        put("nombre", nombre)
                        if (nota != null) put("nota", nota.toDouble()) else put("nota", JSONObject.NULL)
                    })
                }
                put("componentes", compArray)
            }
        }

        conn.outputStream.bufferedWriter().use { it.write(requestJson.toString()) }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("Backend proxy error $responseCode: ${extractBackendErrorMessage(errorBody)}")
        }

        val json = JSONObject(responseBody)
        val recsArray = json.getJSONArray("recomendaciones")
        return (0 until recsArray.length()).map { i ->
            val obj = recsArray.getJSONObject(i)
            Recomendacion(
                tipo = try {
                    TipoRecomendacion.valueOf(obj.getString("tipo"))
                } catch (_: Exception) {
                    TipoRecomendacion.RECURSO
                },
                titulo = obj.getString("titulo"),
                descripcion = obj.getString("descripcion"),
                url = obj.getString("url"),
                autor = obj.optString("autor", "").takeIf { it != "null" && it.isNotBlank() }
            )
        }
    }

    private fun extractBackendErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return "Sin detalle de error"
        return try {
            val obj = JSONObject(rawBody)
            obj.optString("message").ifBlank { rawBody.take(300) }
        } catch (_: Exception) {
            rawBody.take(300)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Prompt
    // ═══════════════════════════════════════════════════════════════════

    private fun buildPrompt(
        nombreMateria: String,
        periodo: String?,
        componentesInfo: List<Pair<String, Float?>>?,
        promedio: Float?,
        porcentajeEvaluado: Float?,
        notaAprobacion: Float?,
        aprobado: Boolean?
    ): String {
        val sb = StringBuilder()

        sb.appendLine("Tutor educativo: recomienda recursos REALES para la materia \"$nombreMateria\".")
        periodo?.let { sb.appendLine("Período: $it") }

        if (promedio != null && notaAprobacion != null) {
            sb.appendLine("Promedio: %.2f/%.2f. Estado: %s.".format(
                promedio, notaAprobacion,
                if (aprobado == true) "APROBANDO" else "EN RIESGO"
            ))
        }
        porcentajeEvaluado?.let {
            sb.appendLine("Progreso: ${(it * 100).toInt()}% evaluado.")
        }

        if (!componentesInfo.isNullOrEmpty()) {
            sb.append("Componentes: ")
            sb.appendLine(componentesInfo.take(6).joinToString(", ") { (n, nota) ->
                "$n=${nota?.let { "%.1f".format(it) } ?: "?"}"
            })
        }

        sb.appendLine()
        sb.appendLine("Genera 5 recomendaciones personalizadas:")
        sb.appendLine("- 2 YOUTUBE: canales reales en español. URL: https://www.youtube.com/results?search_query=<tema>")
        sb.appendLine("- 2 LIBRO: libros reales universitarios. URL: https://www.google.com/search?tbm=bks&q=<titulo+autor>")
        sb.appendLine("- 1 RECURSO: plataforma real (Khan Academy, Coursera, edX, etc). URL real.")
        sb.appendLine()
        sb.appendLine("""Responde SOLO JSON array:
[{"tipo":"YOUTUBE","titulo":"...","descripcion":"...","url":"https://...","autor":"..."},{"tipo":"LIBRO","titulo":"...","descripcion":"...","url":"https://...","autor":"..."},{"tipo":"RECURSO","titulo":"...","descripcion":"...","url":"https://...","autor":null}]""")

        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Parseo
    // ═══════════════════════════════════════════════════════════════════

    private fun parseRecomendaciones(jsonText: String): List<Recomendacion> {
        val cleanJson = extractJsonArray(jsonText)

        val array = try {
            JSONArray(cleanJson)
        } catch (e: Exception) {
            Timber.e(e, "Error parseando JSON. Respuesta: ${cleanJson.take(300)}")
            throw Exception(
                "JSON parse error: ${e.message}. Respuesta: ${cleanJson.take(200)}", e
            )
        }

        val lista = (0 until array.length()).map { i ->
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
                autor = obj.optString("autor", "")
                    .takeIf { it != "null" && it.isNotBlank() }
            )
        }

        if (lista.isEmpty()) {
            throw Exception("IA devolvió 0 recomendaciones. Raw: ${cleanJson.take(200)}")
        }
        return lista
    }

    /**
     * Extrae el primer array JSON [...] del texto, descartando
     * markdown fences u otro texto antes/después.
     */
    private fun extractJsonArray(text: String): String {
        val trimmed = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start != -1 && end > start) return trimmed.substring(start, end + 1)
        return trimmed
    }
}

/** Excepción estructurada para errores HTTP de API de IA. */
private class AiApiException(
    val statusCode: Int,
    val body: String,
    provider: String
) : Exception("$provider API error $statusCode: ${body.take(300)}") {
    val isAuthError: Boolean get() =
        body.contains("API_KEY_INVALID", true) ||
        body.contains("PERMISSION_DENIED", true) ||
        body.contains("invalid_api_key", true) ||
        body.contains("Unauthorized", true) ||
        statusCode == 401 || statusCode == 403
    val isNotFound: Boolean get() =
        statusCode == 404 ||
        body.contains("not found", true) ||
        body.contains("is not supported", true) ||
        body.contains("does not exist", true)
}
