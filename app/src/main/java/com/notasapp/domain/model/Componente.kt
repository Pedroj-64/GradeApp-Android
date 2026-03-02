package com.notasapp.domain.model

/**
 * Modelo de dominio de un componente de evaluación.
 *
 * Agrega las sub-notas y provee propiedades calculadas.
 *
 * @property id        ID en Room.
 * @property materiaId ID de la materia padre.
 * @property nombre    Nombre del corte, ej: "Primer corte".
 * @property porcentaje Peso en la nota final (0.0 a 1.0), ej: 0.30 = 30%.
 * @property orden     Posición en la lista (para drag & drop).
 * @property subNotas  Lista de sub-notas que componen este corte.
 */
data class Componente(
    val id: Long = 0,
    val materiaId: Long,
    val nombre: String,
    val porcentaje: Float,
    val orden: Int = 0,
    val subNotas: List<SubNota> = emptyList(),
    /** Epoch ms de la fecha límite de evaluación. Null = sin fecha. */
    val fechaLimite: Long? = null
) {
    /**
     * Promedio ponderado de las sub-notas **ingresadas** de este componente.
     *
     * Calcula: Σ(subNota.valor * subNota.porcentajeDelComponente) sobre las ingresadas.
     * Si ninguna sub-nota tiene valor, retorna null.
     */
    val promedio: Float?
        get() {
            val ingresadas = subNotas.filter { it.ingresada }
            if (ingresadas.isEmpty()) return null
            return ingresadas.sumOf {
                (it.valorEfectivo!! * it.porcentajeDelComponente).toDouble()
            }.toFloat()
        }

    /**
     * Aporte ponderado de este componente a la nota final de la materia.
     * Null si el componente no tiene ninguna sub-nota ingresada.
     */
    val aporteAlFinal: Float? get() = promedio?.times(porcentaje)

    /**
     * True si todas las sub-notas del componente fueron ingresadas.
     */
    val completo: Boolean get() = subNotas.isNotEmpty() && subNotas.all { it.ingresada }

    /**
     * Porcentaje (0–100) del componente expresado en entero, para la UI.
     */
    val porcentajeDisplay: Int get() = kotlin.math.round(porcentaje * 100).toInt()

    /**
     * Porcentaje de sub-notas ya ingresadas dentro de este componente (0.0 – 1.0).
     */
    val porcentajeIngresado: Float
        get() {
            if (subNotas.isEmpty()) return 0f
            return subNotas.filter { it.ingresada }
                .sumOf { it.porcentajeDelComponente.toDouble() }
                .toFloat()
        }

    /**
     * Texto de progreso del componente: «2 de 4 evaluados».
     */
    val progresoDisplay: String
        get() {
            val total = subNotas.size
            val ingresados = subNotas.count { it.ingresada }
            return "$ingresados de $total evaluado${if (total != 1) "s" else ""}"
        }
}
