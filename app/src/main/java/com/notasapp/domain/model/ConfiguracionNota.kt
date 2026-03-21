package com.notasapp.domain.model

/**
 * Configuración de redondeo y presentación de notas.
 *
 * Se almacena en DataStore y se aplica a todos los cálculos de la app.
 *
 * @property decimales        Número de decimales a mostrar (1 o 2).
 * @property modoRedondeo     Estrategia de redondeo ([ModoRedondeo]).
 */
data class ConfiguracionNota(
    val decimales: Int = 2,
    val modoRedondeo: ModoRedondeo = ModoRedondeo.MATEMATICO
)

/**
 * Estrategias de redondeo disponibles.
 *
 * - [MATEMATICO]: redondeo estándar (≥5 sube, <5 baja).
 * - [TRUNCAR]: siempre corta; ej: 3.456 → 3.45 con 2 dec.
 * - [ACADEMICO]: redondea con la regla "≥ x.x5 sube al siguiente décimo".
 *   En Colombia, por ejemplo: 2.95 → 3.0, 2.94 → 2.9 (con 1 decimal).
 */
enum class ModoRedondeo {
    MATEMATICO,
    TRUNCAR,
    ACADEMICO
}
