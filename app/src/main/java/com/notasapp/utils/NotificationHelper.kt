package com.notasapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.notasapp.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ayudante singleton para gestionar canales de notificación y enviar
 * notificaciones locales.
 *
 * Canales disponibles:
 * - [CHANNEL_REMINDERS]: recordatorios de fechas de evaluación próximas.
 * - [CHANNEL_SYNC]: estado de la sincronización automática con Sheets.
 *
 * Llamar [createNotificationChannels] una única vez al inicio de la app
 * (en [NotasApp.onCreate]). Las llamadas repetidas son seguras (idempotentes).
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_REMINDERS = "notasapp_reminders"
        const val CHANNEL_SYNC      = "notasapp_sync"
        const val CHANNEL_EXAMS     = "notasapp_exams"
    }

    // ── Channel creation ─────────────────────────────────────────────────────

    /**
     * Registra todos los canales de notificación del sistema.
     * Es seguro llamarlo múltiples veces; Android ignora el registro
     * de canales que ya existen.
     */
    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        NotificationChannel(
            CHANNEL_REMINDERS,
            "Recordatorios de Evaluaciones",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alertas cuando un componente de evaluación está próximo"
            enableLights(true)
            manager.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_SYNC,
            "Sincronización con Google Sheets",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Estado del worker de sincronización automática"
            manager.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_EXAMS,
            "Recordatorios de Exámenes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de exámenes, entregas y evaluaciones programadas"
            enableLights(true)
            enableVibration(true)
            manager.createNotificationChannel(this)
        }
    }

    // ── Notification senders ─────────────────────────────────────────────────

    /**
     * Envía una notificación de recordatorio de evaluación.
     *
     * @param title          Título visible en la notificación.
     * @param message        Cuerpo detallado.
     * @param notificationId ID único; usar el mismo valor para actualizar
     *                       una notificación ya emitida.
     */
    fun sendReminder(title: String, message: String, notificationId: Int) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // runCatching absorbe SecurityException si el permiso POST_NOTIFICATIONS fue revocado
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /**
     * Envía una notificación de recordatorio de examen/evento académico.
     * Usa el canal de alta prioridad [CHANNEL_EXAMS].
     */
    fun sendExamReminder(title: String, message: String, notificationId: Int) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "calendar")
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EXAMS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
