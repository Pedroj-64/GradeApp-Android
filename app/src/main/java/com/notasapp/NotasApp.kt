package com.notasapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notasapp.data.worker.ReminderWorker
import com.notasapp.data.worker.AutoBackupWorker
import com.notasapp.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Clase principal de la aplicación NotasApp.
 *
 * Inicializa Hilt (inyección de dependencias), Timber (logging),
 * y configura WorkManager para usar HiltWorkerFactory.
 */
@HiltAndroidApp
class NotasApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        initTimber()
        notificationHelper.createNotificationChannels()
        scheduleReminderWorker()
        scheduleAutoBackupWorker()
    }

    /**
     * Configura WorkManager con HiltWorkerFactory para que los Workers
     * puedan recibir dependencias inyectadas por Hilt.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleAutoBackupWorker() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AutoBackupWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Inicializa Timber únicamente en builds de debug.
     */
    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
