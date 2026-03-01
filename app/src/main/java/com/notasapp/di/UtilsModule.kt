package com.notasapp.di

import com.notasapp.utils.ExcelExporter
import com.notasapp.utils.PdfExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para utilitarios de la aplicación.
 *
 * Al ser clases sin dependencias externas complejas, se proveen aquí
 * para que Hilt las gestione como singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {

    @Singleton
    @Provides
    fun provideExcelExporter(): ExcelExporter = ExcelExporter()

    @Singleton
    @Provides
    fun providePdfExporter(): PdfExporter = PdfExporter()
}
