package com.notasapp.domain.repository

import com.notasapp.domain.model.ExamenEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para el manejo de eventos académicos (calendario).
 */
interface CalendarRepository {

    /** Todos los eventos de un usuario, ordenados por fecha. */
    fun getEventsByUsuario(usuarioId: String): Flow<List<ExamenEvent>>

    /** Eventos de una materia específica. */
    fun getEventsByMateria(materiaId: Long): Flow<List<ExamenEvent>>

    /** Eventos en un rango de fechas (mes). */
    fun getEventsInRange(usuarioId: String, startMs: Long, endMs: Long): Flow<List<ExamenEvent>>

    /** Próximos eventos futuros. */
    fun getUpcomingEvents(usuarioId: String, limit: Int = 10): Flow<List<ExamenEvent>>

    /** Crear o actualizar un evento. Retorna el ID. */
    suspend fun saveEvent(event: ExamenEvent): Long

    /** Eliminar un evento. */
    suspend fun deleteEvent(eventId: Long)

    /** Obtener un evento por ID. */
    suspend fun getEventById(eventId: Long): ExamenEvent?
}
