package com.notasapp.domain.model

/**
 * Tipos de escala de calificación soportados por la app.
 *
 * Cada tipo define un rango predeterminado y la nota mínima para aprobar.
 *
 * @property displayName Nombre para mostrar en la UI.
 * @property escalaMin   Valor mínimo predeterminado de la escala.
 * @property escalaMax   Valor máximo predeterminado de la escala.
 * @property notaAprobacion Nota mínima predeterminada para aprobar.
 */
enum class TipoEscala(
    val escalaMin: Float,
    val escalaMax: Float,
    val notaAprobacion: Float
) {
    /** Sistema colombiano típico (0.0 – 5.0, aprueba con 3.0). */
    NUMERICO_5(0f, 5f, 3f),

    /** Sistema mexicano / español típico (0 – 10, aprueba con 6.0). */
    NUMERICO_10(0f, 10f, 6f),

    /** Escala porcentual (0 – 100, aprueba con 60). */
    NUMERICO_100(0f, 100f, 60f),

    /** Letras (A, B, C, D). La lógica de conversión es interna. */
    LETRAS(0f, 4f, 1f),

    /** El usuario define libremente min, max y nota de aprobación. */
    PERSONALIZADO(0f, 10f, 5f);
}
