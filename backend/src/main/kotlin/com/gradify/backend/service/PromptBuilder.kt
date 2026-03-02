package com.gradify.backend.service

import org.springframework.stereotype.Component

/**
 * Construye el prompt de recomendaciones igual que en la app Android.
 * Centralizado aquí para mantener consistencia.
 */
@Component
class PromptBuilder {

    fun build(
        nombreMateria: String,
        periodo: String?,
        componentes: List<Pair<String, Float?>>?,
        promedio: Float?,
        porcentajeEvaluado: Float?,
        notaAprobacion: Float?,
        aprobado: Boolean?
    ): String {
        val sb = StringBuilder()

        sb.appendLine("Eres un tutor educativo experto que ayuda a estudiantes universitarios hispanohablantes.")
        sb.appendLine("Tu tarea: recomendar recursos de estudio REALES, VERIFICABLES y ESPECÍFICOS para la materia indicada.")
        sb.appendLine()

        sb.appendLine("## Materia: \"$nombreMateria\"")
        periodo?.let { sb.appendLine("Período académico: $it") }

        if (promedio != null && notaAprobacion != null) {
            sb.appendLine()
            sb.appendLine("## Situación del estudiante:")
            sb.appendLine("- Promedio actual: %.2f / %.2f (nota mínima para aprobar)".format(promedio, notaAprobacion))

            if (aprobado == true) {
                sb.appendLine("- Estado: APROBANDO")
                if (promedio >= notaAprobacion * 1.2f) {
                    sb.appendLine("- Buen desempeño → recomienda recursos avanzados para profundizar y destacar.")
                } else {
                    sb.appendLine("- Aprobando con margen justo → recomienda recursos de refuerzo para consolidar.")
                }
            } else {
                sb.appendLine("- Estado: REPROBANDO")
                sb.appendLine("- Necesita mejorar urgentemente → recomienda recursos fundamentales y claros.")
            }

            porcentajeEvaluado?.let {
                val restante = 100f - it
                sb.appendLine("- Evaluado: %.1f%% | Pendiente: %.1f%%".format(it, restante))
            }
        }

        if (!componentes.isNullOrEmpty()) {
            sb.appendLine()
            sb.appendLine("## Componentes de evaluación:")
            componentes.forEach { (nombre, nota) ->
                val estado = when {
                    nota == null -> "sin calificar"
                    notaAprobacion != null && nota >= notaAprobacion -> "✓ aprobado (%.2f)".format(nota)
                    else -> "✗ necesita mejorar (%.2f)".format(nota)
                }
                sb.appendLine("  - $nombre: $estado")
            }
        }

        sb.appendLine()
        sb.appendLine("## Instrucciones:")
        sb.appendLine("Genera exactamente 6 recomendaciones en JSON array puro (sin markdown, sin ```json):")
        sb.appendLine("- 2 videos de YouTube (tipo: \"YOUTUBE\")")
        sb.appendLine("- 2 libros (tipo: \"LIBRO\")")
        sb.appendLine("- 2 recursos web (tipo: \"RECURSO\")")
        sb.appendLine()
        sb.appendLine("Formato EXACTO de cada elemento:")
        sb.appendLine("""[{"tipo":"YOUTUBE","titulo":"...","descripcion":"...","url":"https://youtube.com/...","autor":"..."}]""")
        sb.appendLine()
        sb.appendLine("Reglas OBLIGATORIAS:")
        sb.appendLine("1. URLs deben ser REALES y accesibles.")
        sb.appendLine("2. Videos de YouTube con URLs completas (no acortadas).")
        sb.appendLine("3. Libros con enlace a Google Books, Amazon o editorial.")
        sb.appendLine("4. Recursos web a sitios educativos reconocidos.")
        sb.appendLine("5. Descripciones en español, claras y útiles (1-2 oraciones).")
        sb.appendLine("6. SOLO el JSON array, nada más.")

        return sb.toString()
    }
}
