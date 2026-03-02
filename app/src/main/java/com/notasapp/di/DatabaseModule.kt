package com.notasapp.di

import android.content.Context
import androidx.room.Room
import com.notasapp.data.local.AppDatabase
import com.notasapp.data.local.dao.ComponenteDao
import com.notasapp.data.local.dao.ExamenEventDao
import com.notasapp.data.local.dao.MateriaDao
import com.notasapp.data.local.dao.SubNotaDao
import com.notasapp.data.local.dao.SubNotaDetailDao
import com.notasapp.data.local.dao.UsuarioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt que provee la base de datos Room y todos sus DAOs.
 *
 * Instalado en [SingletonComponent]: la base de datos vive todo el ciclo
 * de vida de la aplicación.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.create(context)

    @Provides
    fun provideUsuarioDao(db: AppDatabase): UsuarioDao = db.usuarioDao()

    @Provides
    fun provideMateriaDao(db: AppDatabase): MateriaDao = db.materiaDao()

    @Provides
    fun provideComponenteDao(db: AppDatabase): ComponenteDao = db.componenteDao()

    @Provides
    fun provideSubNotaDao(db: AppDatabase): SubNotaDao = db.subNotaDao()

    @Provides
    fun provideSubNotaDetailDao(db: AppDatabase): SubNotaDetailDao = db.subNotaDetailDao()

    @Provides
    fun provideExamenEventDao(db: AppDatabase): ExamenEventDao = db.examenEventDao()
}
