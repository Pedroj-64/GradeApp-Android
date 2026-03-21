package com.notasapp.domain.repository

import com.notasapp.domain.model.Componente
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.domain.model.SubNotaDetalle
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de materias.
 *
 * Define las operaciones de datos disponibles para la capa de dominio.
 * La implementación concreta ([MateriaRepositoryImpl]) vive en la capa de datos
 * y es provista por Hilt vía [RepositoryModule].
 */
interface MateriaRepository {

    // ── Materias ────────────────────────────────────────────────

    fun getMateriasByUsuario(usuarioId: String): Flow<List<Materia>>

    fun getMateriaConComponentes(materiaId: Long): Flow<Materia?>

    suspend fun insertMateria(materia: Materia): Long

    suspend fun updateMateria(materia: Materia)

    suspend fun deleteMateria(materiaId: Long)

    // ── Componentes ─────────────────────────────────────────────

    suspend fun insertComponentes(componentes: List<Componente>)

    /** Inserta un único componente y devuelve su ID generado. */
    suspend fun insertComponente(componente: Componente): Long

    suspend fun updateComponentes(componentes: List<Componente>)

    suspend fun deleteComponentesByMateria(materiaId: Long)

    // ── Sub-Notas ────────────────────────────────────────────────

    suspend fun insertSubNotas(subNotas: List<SubNota>)

    /** Inserta una sub-nota y devuelve su ID generado. */
    suspend fun insertSubNota(subNota: SubNota): Long

    suspend fun updateSubNotaValor(subNotaId: Long, valor: Float?)

    suspend fun deleteSubNotasByComponente(componenteId: Long)

    /** Elimina una sub-nota por su ID. */
    suspend fun deleteSubNota(subNotaId: Long)

    // ── Sub-Nota Detalles ────────────────────────────────────────

    /** Inserta un detalle y devuelve su ID generado. */
    suspend fun insertSubNotaDetalle(detalle: SubNotaDetalle): Long

    suspend fun updateSubNotaDetalleValor(detalleId: Long, valor: Float?)

    suspend fun deleteSubNotaDetalle(detalleId: Long)

    // ── Sheets ID ────────────────────────────────────────────────

    suspend fun updateSheetsId(materiaId: Long, sheetsId: String?)

    // ── Metas y notas ────────────────────────────────────────────

    /** Actualiza la nota meta (objetivo académico) de una materia. */
    suspend fun updateNotaMeta(materiaId: Long, notaMeta: Float?)

    /** Actualiza las notas personales de una materia. */
    suspend fun updateNotas(materiaId: Long, notas: String?)
}
