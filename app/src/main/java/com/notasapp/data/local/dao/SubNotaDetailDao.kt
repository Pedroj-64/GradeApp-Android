package com.notasapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.notasapp.data.local.entities.SubNotaDetailEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla [sub_nota_details].
 *
 * Los métodos suspend se usan en coroutines; el Flow de [getBySubNota]
 * emite automáticamente cuando los datos cambian en Room.
 */
@Dao
interface SubNotaDetailDao {

    @Insert
    suspend fun insert(detail: SubNotaDetailEntity): Long

    @Insert
    suspend fun insertAll(details: List<SubNotaDetailEntity>)

    @Update
    suspend fun update(detail: SubNotaDetailEntity)

    @Delete
    suspend fun delete(detail: SubNotaDetailEntity)

    @Query("SELECT * FROM sub_nota_details WHERE subNotaId = :subNotaId ORDER BY id ASC")
    fun getBySubNota(subNotaId: Long): Flow<List<SubNotaDetailEntity>>

    @Query("SELECT * FROM sub_nota_details WHERE id = :id")
    suspend fun getById(id: Long): SubNotaDetailEntity?

    @Query("UPDATE sub_nota_details SET valor = :valor WHERE id = :id")
    suspend fun updateValor(id: Long, valor: Float?)

    @Query("SELECT * FROM sub_nota_details WHERE subNotaId = :subNotaId ORDER BY id ASC")
    suspend fun getBySubNotaOnce(subNotaId: Long): List<SubNotaDetailEntity>
}
