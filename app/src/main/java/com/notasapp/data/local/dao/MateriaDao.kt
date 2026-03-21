package com.notasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.notasapp.data.local.entities.MateriaEntity
import com.notasapp.data.local.relations.MateriaConComponentes
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones CRUD de materias.
 *
 * Las queries que devuelven [Flow] se actualizan automáticamente cuando
 * la base de datos cambia, lo que permite que la UI reaccione en tiempo real.
 */
@Dao
interface MateriaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(materia: MateriaEntity): Long

    @Update
    suspend fun update(materia: MateriaEntity)

    @Delete
    suspend fun delete(materia: MateriaEntity)

    /** Lista de materias de un usuario, ordenadas por período y nombre. */
    @Query("""
        SELECT * FROM materias 
        WHERE usuarioId = :usuarioId 
        ORDER BY periodo DESC, nombre ASC
    """)
    fun getMateriasByUsuario(usuarioId: String): Flow<List<MateriaEntity>>

    @Query("SELECT * FROM materias WHERE id = :id")
    fun getMateriaById(id: Long): Flow<MateriaEntity?>

    @Query("SELECT * FROM materias WHERE id = :id")
    suspend fun getMateriaByIdOnce(id: Long): MateriaEntity?

    /**
     * Retorna la materia junto con todos sus componentes y sub-notas.
     * Se usa en la pantalla de detalle para mostrar la estructura completa.
     */
    @Transaction
    @Query("SELECT * FROM materias WHERE id = :id")
    fun getMateriaConComponentes(id: Long): Flow<MateriaConComponentes?>

    /**
     * Retorna todas las materias del usuario CON componentes y sub-notas (Flow).
     * Se usa en Home para calcular promedio correctamente.
     */
    @Transaction
    @Query("SELECT * FROM materias WHERE usuarioId = :usuarioId ORDER BY periodo DESC, nombre ASC")
    fun getMateriasConComponentesByUsuario(usuarioId: String): Flow<List<MateriaConComponentes>>

    /** Total de materias del usuario (para mostrar badge en Home). */
    @Query("SELECT COUNT(*) FROM materias WHERE usuarioId = :usuarioId")
    fun countByUsuario(usuarioId: String): Flow<Int>

    /** Actualiza el ID de la hoja de Google Sheets vinculada. */
    @Query("UPDATE materias SET googleSheetsId = :sheetsId WHERE id = :id")
    suspend fun updateSheetsId(id: Long, sheetsId: String?)

    /** Actualiza el timestamp de última modificación. */
    @Query("UPDATE materias SET ultimaModificacionMs = :ts WHERE id = :id")
    suspend fun touchUltimaModificacion(id: Long, ts: Long = System.currentTimeMillis())

    /** Actualiza la nota meta (objetivo académico) de una materia. */
    @Query("UPDATE materias SET notaMeta = :notaMeta, ultimaModificacionMs = :ts WHERE id = :id")
    suspend fun updateNotaMeta(id: Long, notaMeta: Float?, ts: Long = System.currentTimeMillis())

    /** Actualiza las notas personales de una materia. */
    @Query("UPDATE materias SET notas = :notas, ultimaModificacionMs = :ts WHERE id = :id")
    suspend fun updateNotas(id: Long, notas: String?, ts: Long = System.currentTimeMillis())

    /**
     * Retorna todas las materias del usuario con sus componentes y sub-notas.
     * Una sola lectura (suspend), sin Flow. Se usa en el Widget y en BackupManager.
     */
    @Transaction
    @Query("SELECT * FROM materias WHERE usuarioId = :usuarioId ORDER BY periodo DESC, nombre ASC")
    suspend fun getMateriasConComponentesOnce(usuarioId: String): List<MateriaConComponentes>
}
