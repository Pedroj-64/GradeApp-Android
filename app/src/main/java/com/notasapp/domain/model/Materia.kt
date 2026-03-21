package com.notasapp.domain.model

/**
 * Modelo de dominio de una materia/asignatura.
 *
 * Agrega todos los componentes con sus sub-notas y provee cálculos de promedio.
 *
 * @property id              ID en Room.
 * @property usuarioId       ID del usuario propietario.
 * @property nombre          Nombre de la materia, ej: "Matemáticas".
 * @property periodo         Período académico, ej: "2026-1".
 * @property profesor        Nombre del profesor (opcional).
 * @property escalaMin       Valor mínimo de la escala de notas.
 * @property escalaMax       Valor máximo de la escala de notas.
 * @property notaAprobacion  Nota mínima para aprobar.
 * @property tipoEscala      Tipo de escala de la materia.
 * @property googleSheetsId  ID de la hoja de Sheets vinculada (null si no sincronizado).
 * @property notaMeta        Nota meta que el usuario desea alcanzar (opcional).
 * @property notas           Notas o comentarios del usuario sobre la materia.
 * @property componentes     Componentes de evaluación con sus sub-notas.
 */
data class Materia(
    val id: Long = 0,
    val usuarioId: String,
    val nombre: String,
    val periodo: String,
    val profesor: String? = null,
    val escalaMin: Float = 0f,
    val escalaMax: Float = 5f,
    val notaAprobacion: Float = 3f,
    val creditos: Int = 0,
    val tipoEscala: TipoEscala = TipoEscala.NUMERICO_5,
    val googleSheetsId: String? = null,
    val notaMeta: Float? = null,
    val notas: String? = null,
    val componentes: List<Componente> = emptyList()
) {
    /**
     * Promedio ponderado actual de la materia.
     *
     * Suma los aportes de los componentes que ya tienen nota.
     * Retorna null si ningún componente tiene notas ingresadas.
     */
    val promedio: Float?
        get() {
            val conNota = componentes.filter { it.aporteAlFinal != null }
            if (conNota.isEmpty()) return null
            return conNota.sumOf { it.aporteAlFinal!!.toDouble() }.toFloat()
        }

    /**
     * Promedio redondeado a 2 decimales, para mostrar en la UI.
     */
    val promedioDisplay: String
        get() = promedio?.let { "%.2f".format(it) } ?: "--"

    /**
     * True si el promedio actual supera la nota mínima de aprobación.
     */
    val aprobado: Boolean get() = (promedio ?: 0f) >= notaAprobacion

    /**
     * Puntos acumulados hasta ahora hacia la nota final.
     * A diferencia de [promedio], solo suma lo que ya fue evaluado (sin proyectar).
     */
    val acumulado: Float
        get() = componentes
            .mapNotNull { it.aporteAlFinal }
            .sumOf { it.toDouble() }
            .toFloat()

    /**
     * Porcentaje del curso que ya fue evaluado (0.0 – 1.0).
     * Considera un componente "evaluado" si tiene al menos una sub-nota ingresada.
     */
    val porcentajeEvaluado: Float
        get() = componentes
            .filter { it.promedio != null }
            .sumOf { it.porcentaje.toDouble() }
            .toFloat()

    /**
     * Acumulado redondeado a 2 decimales, para la UI.
     */
    val acumuladoDisplay: String
        get() = if (acumulado > 0f) "%.2f".format(acumulado) else "--"

    /**
     * True si los puntos acumulados **ya** superan la nota mínima de
     * aprobación, independientemente de cuánto falta por evaluar.
     * Sirve para felicitar al estudiante.
     */
    val yaAprobo: Boolean
        get() = acumulado >= notaAprobacion

    /**
     * Nota que el estudiante necesitaría promediar en lo restante
     * para alcanzar justo la nota de aprobación.  Null si ya aprobó
     * o no queda porcentaje por evaluar.
     */
    val notaNecesariaParaAprobar: Float?
        get() {
            if (yaAprobo) return null
            val restante = 1f - porcentajeEvaluado
            if (restante <= 0f) return null
            val necesita = (notaAprobacion - acumulado) / restante
            return if (necesita in 0f..escalaMax) necesita else null
        }

    /**
     * True si la materia tiene vinculación con Google Sheets.
     */
    val sincronizadaConSheets: Boolean get() = googleSheetsId != null

    /**
     * Suma total de los porcentajes de los componentes.
     * Debería ser siempre 1.0 (100%).
     */
    val sumaPorcentajes: Float
        get() = componentes.sumOf { it.porcentaje.toDouble() }.toFloat()

    /**
     * True si todos los componentes están completos (todas las sub-notas ingresadas).
     */
    val completa: Boolean
        get() = componentes.isNotEmpty() && componentes.all { it.completo }

    // ══════════════════════════════════════════════════════════════════════════
    //  Tracking de Meta
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * True si el usuario ha definido una meta para esta materia.
     */
    val tieneMeta: Boolean get() = notaMeta != null

    /**
     * True si el promedio actual ya alcanzó o superó la meta definida.
     */
    val metaAlcanzada: Boolean
        get() = notaMeta != null && (promedio ?: 0f) >= notaMeta

    /**
     * Progreso hacia la meta (0.0 – 1.0).
     * Retorna null si no hay meta definida o si la meta es 0.
     */
    val progresoMeta: Float?
        get() {
            val meta = notaMeta ?: return null
            if (meta <= 0f) return null
            val actual = promedio ?: 0f
            return (actual / meta).coerceIn(0f, 1f)
        }

    /**
     * Nota que el estudiante necesitaría promediar en lo restante
     * para alcanzar exactamente la meta. Null si ya alcanzó la meta
     * o no queda porcentaje por evaluar.
     */
    val notaNecesariaParaMeta: Float?
        get() {
            val meta = notaMeta ?: return null
            if (metaAlcanzada) return null
            val restante = 1f - porcentajeEvaluado
            if (restante <= 0f) return null
            val necesita = (meta - acumulado) / restante
            return if (necesita in 0f..escalaMax) necesita else null
        }

    /**
     * Estado del progreso hacia la meta.
     */
    val estadoMeta: EstadoMeta
        get() = when {
            notaMeta == null -> EstadoMeta.SIN_META
            metaAlcanzada -> EstadoMeta.ALCANZADA
            notaNecesariaParaMeta == null -> EstadoMeta.INALCANZABLE
            (notaNecesariaParaMeta ?: 0f) <= notaAprobacion -> EstadoMeta.EN_CAMINO
            else -> EstadoMeta.REQUIERE_ESFUERZO
        }
}

/**
 * Estado del progreso hacia la meta académica definida por el usuario.
 */
enum class EstadoMeta {
    /** No hay meta definida. */
    SIN_META,
    /** La meta ya fue alcanzada. */
    ALCANZADA,
    /** El estudiante va bien encaminado (necesita nota ≤ aprobación). */
    EN_CAMINO,
    /** El estudiante necesita esforzarse más (necesita nota > aprobación). */
    REQUIERE_ESFUERZO,
    /** La meta ya es matemáticamente inalcanzable. */
    INALCANZABLE
}
