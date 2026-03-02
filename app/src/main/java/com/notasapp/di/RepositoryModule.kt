package com.notasapp.di

import com.notasapp.data.repository.CalendarRepositoryImpl
import com.notasapp.data.repository.MateriaRepositoryImpl
import com.notasapp.domain.repository.CalendarRepository
import com.notasapp.domain.repository.MateriaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt que vincula las interfaces de repositorio con sus implementaciones.
 *
 * Usar @Binds (en lugar de @Provides) es más eficiente porque no genera
 * código adicional en tiempo de compilación.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Vincula [MateriaRepository] → [MateriaRepositoryImpl].
     * Cuando se inyecte [MateriaRepository], Hilt proveerá [MateriaRepositoryImpl].
     */
    @Binds
    @Singleton
    abstract fun bindMateriaRepository(
        impl: MateriaRepositoryImpl
    ): MateriaRepository

    /**
     * Vincula [CalendarRepository] → [CalendarRepositoryImpl].
     */
    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl
    ): CalendarRepository
}
