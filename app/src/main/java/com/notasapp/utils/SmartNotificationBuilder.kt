package com.notasapp.utils

import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.TipoEvento

/**
 * Genera mensajes de notificación personalizados basados en el rendimiento
 * del estudiante en la materia asociada al evento.
 *
 * Los mensajes se generan con lógica de palabras clave (no IA),
 * pero parecen inteligentes y contextuales.
 */
object SmartNotificationBuilder {

    /**
     * Contenido personalizado para una notificación de evento.
     */
    data class SmartContent(
        val title: String,
        val message: String
    )

    /**
     * Genera título y mensaje personalizados para un evento académico.
     *
     * @param eventTitle   Título del evento (e.g., "Parcial 1 de Cálculo")
     * @param eventType    Tipo del evento
     * @param materia      Materia asociada (puede ser null si no se encuentra)
     * @param minutesBefore Minutos de antelación del recordatorio
     */
    fun buildContent(
        eventTitle: String,
        eventType: TipoEvento,
        materia: Materia?,
        minutesBefore: Int = 60
    ): SmartContent {
        val emoji = eventType.emoji
        val timeLabel = formatMinutes(minutesBefore)

        // Sin datos de materia → notificación genérica
        if (materia == null) {
            return SmartContent(
                title = "$emoji $eventTitle",
                message = "Tienes un evento académico $timeLabel. ¡Prepárate!"
            )
        }

        val promedio = materia.promedio
        val notaAprobacion = materia.notaAprobacion
        val yaAprobo = materia.yaAprobo
        val porcentajeEvaluado = materia.porcentajeEvaluado
        val notaNecesaria = materia.notaNecesariaParaAprobar
        val materiaNombre = materia.nombre

        // ── Clasificación por palabras clave de rendimiento ─────────────

        val (tag, motivationalMsg) = when {
            // Sin notas aún
            promedio == null -> "INICIO" to
                    "Es tu primera evaluación en $materiaNombre. ¡Da lo mejor de ti!"

            // Ya aprobó la materia
            yaAprobo -> "EXCELENTE" to
                    "Llevas ${formatNota(promedio)}/${formatNota(notaAprobacion)} en $materiaNombre. ¡Ya la tienes aprobada, sigue así!"

            // Aprobando con buen margen (>20% arriba de la nota mínima)
            promedio >= notaAprobacion * 1.2f -> "MUY_BIEN" to
                    "Vas muy bien en $materiaNombre con ${formatNota(promedio)}. ¡Mantén el ritmo!"

            // Aprobando justo
            promedio >= notaAprobacion -> "APROBANDO" to
                    "Llevas ${formatNota(promedio)} en $materiaNombre, justo en el límite. Este ${eventType.displayName.lowercase()} es clave para asegurar."

            // Cerca de aprobar (falta poco)
            notaNecesaria != null && notaNecesaria <= materia.escalaMax * 0.7f -> "RECUPERABLE" to
                    "Necesitas ${formatNota(notaNecesaria)} en lo que queda de $materiaNombre. ¡Aún es posible, enfoca tu estudio!"

            // En riesgo alto
            notaNecesaria != null && notaNecesaria <= materia.escalaMax -> "RIESGO" to
                    "Necesitas ${formatNota(notaNecesaria)} para aprobar $materiaNombre. Este ${eventType.displayName.lowercase()} es crucial — ¡dale con todo!"

            // Situación muy difícil
            else -> "CRITICO" to
                    "La situación en $materiaNombre es difícil (${formatNota(promedio)}/${formatNota(notaAprobacion)}). Cada punto cuenta, ¡no te rindas!"
        }

        // ── Título dinámico ──────────────────────────────────────────────
        val title = when (tag) {
            "EXCELENTE", "MUY_BIEN" -> "$emoji $eventTitle ⭐"
            "RIESGO", "CRITICO" -> "$emoji ¡Atención! $eventTitle"
            else -> "$emoji $eventTitle"
        }

        // ── Mensaje compuesto ────────────────────────────────────────────
        val progressInfo = if (porcentajeEvaluado > 0f) {
            "\n📊 Progreso: ${kotlin.math.round(porcentajeEvaluado * 100).toInt()}% del curso evaluado."
        } else ""

        val message = "$motivationalMsg$progressInfo"

        return SmartContent(title = title, message = message)
    }

    private fun formatNota(nota: Float): String = "%.1f".format(nota)

    private fun formatMinutes(minutes: Int): String = when {
        minutes <= 0 -> "ahora"
        minutes < 60 -> "en $minutes minutos"
        minutes == 60 -> "en 1 hora"
        minutes < 1440 -> "en ${minutes / 60} horas"
        minutes == 1440 -> "mañana"
        else -> "en ${minutes / 1440} días"
    }
}
