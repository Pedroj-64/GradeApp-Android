package com.notasapp.data.local.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caché persistente de recomendaciones de IA basada en archivos JSON.
 *
 * Almacena las recomendaciones generadas junto con un "fingerprint"
 * del estado de notas de la materia. Solo se regeneran las recomendaciones
 * cuando el fingerprint cambia (es decir, cuando el estudiante tiene
 * notas nuevas o modificadas).
 *
 * Archivo: `{cacheDir}/ai_recs/{materiaId}.json`
 */
@Singleton
class RecomendacionesCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheDir: File
        get() = File(context.cacheDir, "ai_recs").also { it.mkdirs() }

    /**
     * Genera un fingerprint del estado de notas de la materia.
     * Si el fingerprint no cambió desde la última vez, las recomendaciones
     * siguen siendo válidas y no necesitan regenerarse.
     */
    fun buildFingerprint(
        materiaId: Long,
        promedio: Float?,
        porcentajeEvaluado: Float?,
        componentesInfo: List<Pair<String, Float?>>?
    ): String = buildString {
        append("m$materiaId|")
        append("p${promedio?.let { "%.2f".format(it) } ?: "null"}|")
        append("e${porcentajeEvaluado?.let { "%.2f".format(it) } ?: "null"}|")
        componentesInfo?.sortedBy { it.first }?.forEach { (name, nota) ->
            append("$name=${nota?.let { "%.1f".format(it) } ?: "x"},")
        }
    }

    /**
     * Recupera recomendaciones cacheadas si existen y el fingerprint coincide.
     * @return JSON string del array de recomendaciones, o null si hay cache miss.
     */
    fun get(materiaId: Long, currentFingerprint: String): String? {
        val file = File(cacheDir, "$materiaId.json")
        if (!file.exists()) return null

        return try {
            val json = JSONObject(file.readText())
            val storedFingerprint = json.getString("fingerprint")
            if (storedFingerprint == currentFingerprint) {
                Timber.d("Cache HIT para materia $materiaId")
                json.getString("recomendaciones")
            } else {
                Timber.d("Cache STALE para materia $materiaId (fingerprint cambió)")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Error leyendo cache de materia $materiaId")
            null
        }
    }

    /**
     * Guarda las recomendaciones en cache con el fingerprint actual.
     */
    fun put(materiaId: Long, fingerprint: String, recomendacionesJson: String) {
        val file = File(cacheDir, "$materiaId.json")
        try {
            val json = JSONObject().apply {
                put("fingerprint", fingerprint)
                put("recomendaciones", recomendacionesJson)
                put("timestamp", System.currentTimeMillis())
            }
            file.writeText(json.toString())
            Timber.d("Cache STORED para materia $materiaId")
        } catch (e: Exception) {
            Timber.w(e, "Error escribiendo cache de materia $materiaId")
        }
    }

    /**
     * Invalida el cache de una materia específica.
     */
    fun invalidate(materiaId: Long) {
        File(cacheDir, "$materiaId.json").delete()
    }

    /**
     * Limpia todo el cache de recomendaciones.
     */
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    // ── Serialización ──────────────────────────────────────────────

    fun serializeRecomendaciones(recs: List<com.notasapp.data.remote.ai.Recomendacion>): String {
        val array = JSONArray()
        recs.forEach { rec ->
            array.put(JSONObject().apply {
                put("tipo", rec.tipo.name)
                put("titulo", rec.titulo)
                put("descripcion", rec.descripcion)
                put("url", rec.url)
                put("autor", rec.autor ?: JSONObject.NULL)
            })
        }
        return array.toString()
    }

    fun deserializeRecomendaciones(json: String): List<com.notasapp.data.remote.ai.Recomendacion> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            com.notasapp.data.remote.ai.Recomendacion(
                tipo = try {
                    com.notasapp.data.remote.ai.TipoRecomendacion.valueOf(obj.getString("tipo"))
                } catch (_: Exception) {
                    com.notasapp.data.remote.ai.TipoRecomendacion.RECURSO
                },
                titulo = obj.getString("titulo"),
                descripcion = obj.getString("descripcion"),
                url = obj.getString("url"),
                autor = obj.optString("autor", "").takeIf { it != "null" && it.isNotBlank() }
            )
        }
    }
}
