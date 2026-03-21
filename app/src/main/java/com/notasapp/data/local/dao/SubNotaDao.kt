package com.notasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.notasapp.data.local.entities.SubNotaEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones CRUD de sub-notas individuales.
 */
@Dao
interface SubNotaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subNota: SubNotaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subNotas: List<SubNotaEntity>)

    @Update
    suspend fun update(subNota: SubNotaEntity)

    @Delete
    suspend fun delete(subNota: SubNotaEntity)

    @Query("SELECT * FROM sub_notas WHERE componenteId = :componenteId ORDER BY id ASC")
    fun getSubNotasByComponente(componenteId: Long): Flow<List<SubNotaEntity>>

    @Query("SELECT * FROM sub_notas WHERE componenteId = :componenteId ORDER BY id ASC")
    suspend fun getSubNotasByComponenteOnce(componenteId: Long): List<SubNotaEntity>

    @Query("SELECT * FROM sub_notas WHERE id = :id")
    suspend fun getSubNotaById(id: Long): SubNotaEntity?

    @Query("SELECT COALESCE(SUM(porcentajeDelComponente), 0) FROM sub_notas WHERE componenteId = :componenteId")
    suspend fun getSumaPorcentajeByComponente(componenteId: Long): Float

    /**
     * Actualiza el valor numérico de una sub-nota.
     * Llamado cada vez que el usuario ingresa o edita una nota.
     */
    @Query("""
        UPDATE sub_notas 
        SET valor = :valor, ultimaModificacionMs = :ts 
        WHERE id = :id
    """)
    suspend fun updateValor(id: Long, valor: Float?, ts: Long = System.currentTimeMillis())

    /** Cuenta cuántas sub-notas de un componente aún no tienen valor. */
    @Query("SELECT COUNT(*) FROM sub_notas WHERE componenteId = :componenteId AND valor IS NULL")
    fun countPendientesByComponente(componenteId: Long): Flow<Int>

    @Query("DELETE FROM sub_notas WHERE componenteId = :componenteId")
    suspend fun deleteByComponente(componenteId: Long)
}
