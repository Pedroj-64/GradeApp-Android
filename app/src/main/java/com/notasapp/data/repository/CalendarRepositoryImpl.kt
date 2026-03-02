package com.notasapp.data.repository

import com.notasapp.data.local.dao.ExamenEventDao
import com.notasapp.data.local.dao.MateriaDao
import com.notasapp.data.local.entities.ExamenEventEntity
import com.notasapp.domain.model.ExamenEvent
import com.notasapp.domain.model.TipoEvento
import com.notasapp.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de calendario usando Room.
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val examenEventDao: ExamenEventDao,
    private val materiaDao: MateriaDao
) : CalendarRepository {

    override fun getEventsByUsuario(usuarioId: String): Flow<List<ExamenEvent>> =
        examenEventDao.getEventsByUsuario(usuarioId).mapToEvents()

    override fun getEventsByMateria(materiaId: Long): Flow<List<ExamenEvent>> =
        examenEventDao.getEventsByMateria(materiaId).mapToEvents()

    override fun getEventsInRange(
        usuarioId: String,
        startMs: Long,
        endMs: Long
    ): Flow<List<ExamenEvent>> =
        examenEventDao.getEventsInRange(usuarioId, startMs, endMs).mapToEvents()

    override fun getUpcomingEvents(usuarioId: String, limit: Int): Flow<List<ExamenEvent>> =
        examenEventDao.getUpcomingEvents(usuarioId, System.currentTimeMillis(), limit).mapToEvents()

    override suspend fun saveEvent(event: ExamenEvent): Long {
        val entity = event.toEntity()
        return if (event.id > 0) {
            examenEventDao.update(entity)
            event.id
        } else {
            examenEventDao.insert(entity)
        }
    }

    override suspend fun deleteEvent(eventId: Long) {
        examenEventDao.deleteById(eventId)
    }

    override suspend fun getEventById(eventId: Long): ExamenEvent? =
        examenEventDao.getEventById(eventId)?.toDomain()

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun Flow<List<ExamenEventEntity>>.mapToEvents(): Flow<List<ExamenEvent>> =
        map { entities -> entities.map { it.toDomain() } }

    private fun ExamenEventEntity.toDomain() = ExamenEvent(
        id = id,
        materiaId = materiaId,
        titulo = titulo,
        descripcion = descripcion,
        tipoEvento = TipoEvento.fromString(tipoEvento),
        fechaEpochMs = fechaEpochMs,
        recordatorioMinutos = recordatorioMinutos,
        recordatorioProgramado = recordatorioProgramado,
        colorInt = colorInt
    )

    private fun ExamenEvent.toEntity() = ExamenEventEntity(
        id = id,
        materiaId = materiaId,
        titulo = titulo,
        descripcion = descripcion,
        tipoEvento = tipoEvento.name,
        fechaEpochMs = fechaEpochMs,
        recordatorioMinutos = recordatorioMinutos,
        recordatorioProgramado = recordatorioProgramado,
        colorInt = colorInt
    )
}
