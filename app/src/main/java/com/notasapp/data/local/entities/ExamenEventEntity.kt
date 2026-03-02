package com.notasapp.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para eventos académicos (exámenes, entregas, quizzes, etc.)
 * asociados a una materia.
 *
 * Cada evento puede programar una notificación de recordatorio
 * mediante [AlarmManager] usando [recordatorioMinutos] antes de la fecha.
 */
@Entity(
    tableName = "examen_events",
    foreignKeys = [
        ForeignKey(
            entity = MateriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["materiaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["materiaId"]), Index(value = ["fechaEpochMs"])]
)
data class ExamenEventEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID de la materia asociada. */
    val materiaId: Long,

    /** Título del evento, ej: "Parcial 1", "Entrega proyecto", "Quiz". */
    val titulo: String,

    /** Descripción opcional con detalles adicionales. */
    val descripcion: String = "",

    /**
     * Tipo de evento académico.
     * Valores: "PARCIAL", "FINAL", "QUIZ", "TAREA", "PROYECTO", "EXPOSICION", "OTRO"
     */
    val tipoEvento: String = "PARCIAL",

    /**
     * Fecha y hora del evento en milisegundos epoch UTC.
     * Se usa para ordenar y mostrar en el calendario.
     */
    val fechaEpochMs: Long,

    /**
     * Minutos de anticipación para el recordatorio.
     * - 0 = sin recordatorio
     * - 30 = 30 min antes
     * - 60 = 1 hora antes
     * - 1440 = 1 día antes
     */
    val recordatorioMinutos: Int = 60,

    /** True si la notificación de recordatorio ya fue programada. */
    val recordatorioProgramado: Boolean = false,

    /** Color personalizado del evento (ARGB int). Null = usar color de la materia. */
    val colorInt: Int? = null,

    /** Timestamp de creación. */
    val creadoEnMs: Long = System.currentTimeMillis()
)
