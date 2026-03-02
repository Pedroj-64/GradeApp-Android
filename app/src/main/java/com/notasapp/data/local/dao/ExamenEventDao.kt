package com.notasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.notasapp.data.local.entities.ExamenEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones CRUD de eventos de exámenes/calendario.
 */
@Dao
interface ExamenEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ExamenEventEntity): Long

    @Update
    suspend fun update(event: ExamenEventEntity)

    @Delete
    suspend fun delete(event: ExamenEventEntity)

    @Query("DELETE FROM examen_events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)

    /** Todos los eventos de un usuario (via materias), ordenados por fecha. */
    @Query("""
        SELECT e.* FROM examen_events e
        INNER JOIN materias m ON e.materiaId = m.id
        WHERE m.usuarioId = :usuarioId
        ORDER BY e.fechaEpochMs ASC
    """)
    fun getEventsByUsuario(usuarioId: String): Flow<List<ExamenEventEntity>>

    /** Eventos de una materia específica, ordenados por fecha. */
    @Query("SELECT * FROM examen_events WHERE materiaId = :materiaId ORDER BY fechaEpochMs ASC")
    fun getEventsByMateria(materiaId: Long): Flow<List<ExamenEventEntity>>

    /** Eventos en un rango de fechas (para vista mensual del calendario). */
    @Query("""
        SELECT e.* FROM examen_events e
        INNER JOIN materias m ON e.materiaId = m.id
        WHERE m.usuarioId = :usuarioId
          AND e.fechaEpochMs >= :startMs
          AND e.fechaEpochMs < :endMs
        ORDER BY e.fechaEpochMs ASC
    """)
    fun getEventsInRange(usuarioId: String, startMs: Long, endMs: Long): Flow<List<ExamenEventEntity>>

    /** Un solo evento por ID. */
    @Query("SELECT * FROM examen_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): ExamenEventEntity?

    /** Eventos próximos (futuros a partir de ahora), para widgets/notificaciones. */
    @Query("""
        SELECT e.* FROM examen_events e
        INNER JOIN materias m ON e.materiaId = m.id
        WHERE m.usuarioId = :usuarioId
          AND e.fechaEpochMs > :nowMs
        ORDER BY e.fechaEpochMs ASC
        LIMIT :limit
    """)
    fun getUpcomingEvents(usuarioId: String, nowMs: Long, limit: Int = 10): Flow<List<ExamenEventEntity>>

    /** Eventos que necesitan recordatorio (no programados aún y fecha en el futuro). */
    @Query("""
        SELECT e.* FROM examen_events e
        WHERE e.recordatorioMinutos > 0
          AND e.recordatorioProgramado = 0
          AND e.fechaEpochMs > :nowMs
    """)
    suspend fun getEventsPendingReminder(nowMs: Long): List<ExamenEventEntity>

    /** Marca un evento como con recordatorio programado. */
    @Query("UPDATE examen_events SET recordatorioProgramado = 1 WHERE id = :eventId")
    suspend fun markReminderScheduled(eventId: Long)
}
