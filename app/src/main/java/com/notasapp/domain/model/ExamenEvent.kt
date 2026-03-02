package com.notasapp.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tipo de evento académico para el calendario.
 */
enum class TipoEvento(val displayName: String, val emoji: String) {
    PARCIAL("Parcial", "\uD83D\uDCDD"),         // 📝
    FINAL("Final", "\uD83C\uDFAF"),             // 🎯
    QUIZ("Quiz", "\u2753"),                       // ❓
    TAREA("Tarea", "\uD83D\uDCCB"),             // 📋
    PROYECTO("Proyecto", "\uD83D\uDEE0"),        // 🛠
    EXPOSICION("Exposición", "\uD83C\uDFA4"),   // 🎤
    OTRO("Otro", "\uD83D\uDCC5");               // 📅

    companion object {
        fun fromString(s: String): TipoEvento =
            entries.find { it.name == s } ?: OTRO
    }
}

/**
 * Modelo de dominio de un evento académico (examen, entrega, quiz, etc.).
 */
data class ExamenEvent(
    val id: Long = 0,
    val materiaId: Long,
    val materiaNombre: String = "",
    val titulo: String,
    val descripcion: String = "",
    val tipoEvento: TipoEvento = TipoEvento.PARCIAL,
    val fechaEpochMs: Long,
    val recordatorioMinutos: Int = 60,
    val recordatorioProgramado: Boolean = false,
    val colorInt: Int? = null
) {
    /** Fecha como [LocalDate] en la zona horaria local. */
    val fecha: LocalDate
        get() = Instant.ofEpochMilli(fechaEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    /** Hora como [LocalTime] en la zona horaria local. */
    val hora: LocalTime
        get() = Instant.ofEpochMilli(fechaEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

    /** Fecha y hora como [LocalDateTime]. */
    val fechaHora: LocalDateTime
        get() = Instant.ofEpochMilli(fechaEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

    /** Formato legible: "15 mar 2026, 10:30 AM". */
    val fechaDisplay: String
        get() = fechaHora.format(
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale("es"))
        )

    /** Formato corto: "15 mar". */
    val fechaCorta: String
        get() = fecha.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))

    /** Formato hora: "10:30 AM". */
    val horaDisplay: String
        get() = hora.format(DateTimeFormatter.ofPattern("h:mm a", Locale("es")))

    /** True si el evento ya pasó. */
    val yaPaso: Boolean
        get() = fechaEpochMs < System.currentTimeMillis()

    /** Descripción del recordatorio. */
    val recordatorioDisplay: String
        get() = when (recordatorioMinutos) {
            0 -> "Sin recordatorio"
            15 -> "15 minutos antes"
            30 -> "30 minutos antes"
            60 -> "1 hora antes"
            120 -> "2 horas antes"
            1440 -> "1 día antes"
            2880 -> "2 días antes"
            else -> "$recordatorioMinutos min antes"
        }
}
