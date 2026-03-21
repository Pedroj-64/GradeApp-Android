package com.notasapp.utils

import android.content.Context
import androidx.annotation.StringRes
import com.notasapp.R
import com.notasapp.domain.model.TipoEvento
import com.notasapp.domain.model.TipoEscala
import com.notasapp.domain.model.ModoRedondeo

/**
 * Extensiones para obtener strings localizados de los enums.
 * Esto separa la lógica de UI de los modelos de dominio.
 */

// ════════════════════════════════════════════════════════════════════════════
// TipoEvento
// ════════════════════════════════════════════════════════════════════════════

/**
 * Obtiene el string resource ID para el tipo de evento.
 */
@StringRes
fun TipoEvento.getDisplayNameRes(): Int = when (this) {
    TipoEvento.PARCIAL -> R.string.tipo_evento_parcial
    TipoEvento.FINAL -> R.string.tipo_evento_final
    TipoEvento.QUIZ -> R.string.tipo_evento_quiz
    TipoEvento.TAREA -> R.string.tipo_evento_tarea
    TipoEvento.PROYECTO -> R.string.tipo_evento_proyecto
    TipoEvento.EXPOSICION -> R.string.tipo_evento_exposicion
    TipoEvento.OTRO -> R.string.tipo_evento_otro
}

/**
 * Obtiene el nombre localizado del tipo de evento.
 */
fun TipoEvento.getDisplayName(context: Context): String =
    context.getString(getDisplayNameRes())

// ════════════════════════════════════════════════════════════════════════════
// TipoEscala
// ════════════════════════════════════════════════════════════════════════════

/**
 * Obtiene el string resource ID para el tipo de escala.
 */
@StringRes
fun TipoEscala.getDisplayNameRes(): Int = when (this) {
    TipoEscala.NUMERICO_5 -> R.string.escala_numerico_5
    TipoEscala.NUMERICO_10 -> R.string.escala_numerico_10
    TipoEscala.NUMERICO_100 -> R.string.escala_numerico_100
    TipoEscala.LETRAS -> R.string.escala_letras
    TipoEscala.PERSONALIZADO -> R.string.escala_personalizado
}

/**
 * Obtiene el nombre localizado del tipo de escala.
 */
fun TipoEscala.getDisplayName(context: Context): String =
    context.getString(getDisplayNameRes())

// ════════════════════════════════════════════════════════════════════════════
// ModoRedondeo
// ════════════════════════════════════════════════════════════════════════════

/**
 * Obtiene el string resource ID para el modo de redondeo.
 */
@StringRes
fun ModoRedondeo.getDisplayNameRes(): Int = when (this) {
    ModoRedondeo.MATEMATICO -> R.string.modo_redondeo_matematico
    ModoRedondeo.TRUNCAR -> R.string.modo_redondeo_truncar
    ModoRedondeo.ACADEMICO -> R.string.modo_redondeo_academico
}

/**
 * Obtiene el nombre localizado del modo de redondeo.
 */
fun ModoRedondeo.getDisplayName(context: Context): String =
    context.getString(getDisplayNameRes())

// ════════════════════════════════════════════════════════════════════════════
// Recordatorios
// ════════════════════════════════════════════════════════════════════════════

/**
 * Obtiene la descripción localizada del recordatorio.
 */
fun getRecordatorioDisplay(context: Context, recordatorioMinutos: Int): String = when (recordatorioMinutos) {
    0 -> context.getString(R.string.calendar_no_reminder)
    15 -> context.getString(R.string.calendar_15min)
    30 -> context.getString(R.string.calendar_30min)
    60 -> context.getString(R.string.calendar_1h)
    120 -> context.getString(R.string.calendar_2h)
    1440 -> context.getString(R.string.calendar_1d)
    2880 -> context.getString(R.string.calendar_2d)
    else -> context.getString(R.string.calendar_n_min_before, recordatorioMinutos)
}