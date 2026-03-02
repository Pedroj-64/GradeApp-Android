package com.notasapp.data.repository

import com.notasapp.data.local.dao.ComponenteDao
import com.notasapp.data.local.dao.MateriaDao
import com.notasapp.data.local.dao.SubNotaDao
import com.notasapp.data.local.dao.SubNotaDetailDao
import com.notasapp.data.mapper.toEntity
import com.notasapp.data.mapper.toDomain
import com.notasapp.domain.model.Componente
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.domain.model.SubNotaDetalle
import com.notasapp.domain.repository.MateriaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación concreta de [MateriaRepository].
 *
 * Actúa como fuente única de verdad (Single Source of Truth): lee y escribe
 * en Room, y transforma entre entidades y modelos de dominio mediante [EntityMapper].
 *
 * Provista como singleton por Hilt en [RepositoryModule].
 */
@Singleton
class MateriaRepositoryImpl @Inject constructor(
    private val materiaDao: MateriaDao,
    private val componenteDao: ComponenteDao,
    private val subNotaDao: SubNotaDao,
    private val subNotaDetailDao: SubNotaDetailDao
) : MateriaRepository {

    // ── Materias ────────────────────────────────────────────────

    override fun getMateriasByUsuario(usuarioId: String): Flow<List<Materia>> {
        // Carga componentes + sub-notas para que promedio se calcule correctamente en Home.
        return materiaDao.getMateriasConComponentesByUsuario(usuarioId).map { relations ->
            relations.map { it.toDomain() }
        }
    }

    override fun getMateriaConComponentes(materiaId: Long): Flow<Materia?> =
        materiaDao.getMateriaConComponentes(materiaId).map { it?.toDomain() }

    override suspend fun insertMateria(materia: Materia): Long {
        val id = materiaDao.insert(materia.toEntity())
        Timber.d("Materia insertada: id=$id, nombre=${materia.nombre}")
        return id
    }

    override suspend fun updateMateria(materia: Materia) {
        materiaDao.update(materia.toEntity())
        materiaDao.touchUltimaModificacion(materia.id)
    }

    override suspend fun deleteMateria(materiaId: Long) {
        val entity = materiaDao.getMateriaByIdOnce(materiaId) ?: return
        materiaDao.delete(entity)
    }

    // ── Componentes ─────────────────────────────────────────────

    override suspend fun insertComponentes(componentes: List<Componente>) {
        componenteDao.insertAll(componentes.map { it.toEntity() })
    }

    override suspend fun insertComponente(componente: Componente): Long =
        componenteDao.insert(componente.toEntity())

    override suspend fun updateComponentes(componentes: List<Componente>) {
        componenteDao.updateAll(componentes.map { it.toEntity() })
    }

    override suspend fun deleteComponentesByMateria(materiaId: Long) {
        componenteDao.deleteByMateria(materiaId)
    }

    // ── Sub-Notas ────────────────────────────────────────────────

    override suspend fun insertSubNotas(subNotas: List<SubNota>) {
        subNotaDao.insertAll(subNotas.map { it.toEntity() })
    }

    override suspend fun insertSubNota(subNota: SubNota): Long =
        subNotaDao.insert(subNota.toEntity())

    override suspend fun updateSubNotaValor(subNotaId: Long, valor: Float?) {
        subNotaDao.updateValor(subNotaId, valor)
    }

    override suspend fun deleteSubNotasByComponente(componenteId: Long) {
        subNotaDao.deleteByComponente(componenteId)
    }

    override suspend fun deleteSubNota(subNotaId: Long) {
        val entity = subNotaDao.getSubNotaById(subNotaId) ?: return
        subNotaDao.delete(entity)
    }

    // ── Sub-Nota Detalles ────────────────────────────────────────

    override suspend fun insertSubNotaDetalle(detalle: SubNotaDetalle): Long =
        subNotaDetailDao.insert(detalle.toEntity())

    override suspend fun updateSubNotaDetalleValor(detalleId: Long, valor: Float?) {
        subNotaDetailDao.updateValor(detalleId, valor)
    }

    override suspend fun deleteSubNotaDetalle(detalleId: Long) {
        val entity = subNotaDetailDao.getById(detalleId) ?: return
        subNotaDetailDao.delete(entity)
    }

    // ── Sheets ID ────────────────────────────────────────────────

    override suspend fun updateSheetsId(materiaId: Long, sheetsId: String?) {
        materiaDao.updateSheetsId(materiaId, sheetsId)
    }
}
