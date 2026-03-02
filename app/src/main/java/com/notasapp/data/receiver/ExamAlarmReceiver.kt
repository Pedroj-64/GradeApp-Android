package com.notasapp.data.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.notasapp.data.local.AppDatabase
import com.notasapp.data.mapper.toDomain
import com.notasapp.domain.model.TipoEvento
import com.notasapp.utils.NotificationHelper
import com.notasapp.utils.SmartNotificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver que se dispara cuando una alarma de examen
 * programada por [ExamAlarmScheduler] se activa.
 *
 * Genera notificaciones personalizadas basadas en el rendimiento
 * del estudiante en la materia asociada (usando [SmartNotificationBuilder]).
 */
class ExamAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENT_ID = "exam_event_id"
        const val EXTRA_TITLE = "exam_title"
        const val EXTRA_DESCRIPTION = "exam_description"
        const val EXTRA_TYPE = "exam_type"
        const val EXTRA_MATERIA_ID = "exam_materia_id"
        const val EXTRA_REMINDER_MINUTES = "exam_reminder_minutes"

        private const val REQUEST_CODE_BASE = 10_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Examen próximo"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val type = intent.getStringExtra(EXTRA_TYPE) ?: ""
        val materiaId = intent.getLongExtra(EXTRA_MATERIA_ID, -1)
        val reminderMinutes = intent.getIntExtra(EXTRA_REMINDER_MINUTES, 60)

        if (eventId == -1L) {
            Timber.w("ExamAlarmReceiver: evento sin ID, ignorando")
            return
        }

        Timber.i("Alarm disparada para evento $eventId: $title (materia=$materiaId)")

        val tipoEvento = TipoEvento.fromString(type)

        // Consultar datos de la materia para notificación personalizada
        scope.launch {
            try {
                val materia = if (materiaId > 0) {
                    val db = AppDatabase.getInstance(context)
                    val materiaDao = db.materiaDao()
                    val materiaConComponentes = materiaDao.getMateriaConComponentes(materiaId).first()
                    materiaConComponentes?.toDomain()
                } else null

                val smartContent = SmartNotificationBuilder.buildContent(
                    eventTitle = title,
                    eventType = tipoEvento,
                    materia = materia,
                    minutesBefore = reminderMinutes
                )

                val notificationHelper = NotificationHelper(context)
                notificationHelper.sendExamReminder(
                    title = smartContent.title,
                    message = if (description.isNotBlank()) {
                        "$description\n\n${smartContent.message}"
                    } else {
                        smartContent.message
                    },
                    notificationId = (REQUEST_CODE_BASE + eventId).toInt()
                )
            } catch (e: Exception) {
                Timber.e(e, "Error al generar notificación inteligente, usando genérica")
                // Fallback a notificación genérica
                val emoji = tipoEvento.emoji
                val notificationHelper = NotificationHelper(context)
                notificationHelper.sendExamReminder(
                    title = "$emoji $title",
                    message = if (description.isNotBlank()) description
                    else "Tienes un evento académico próximamente",
                    notificationId = (REQUEST_CODE_BASE + eventId).toInt()
                )
            }
        }
    }
}

/**
 * Programador de alarmas para eventos académicos.
 *
 * Utiliza [AlarmManager.setExactAndAllowWhileIdle] para garantizar
 * que la notificación se envíe puntualmente incluso en Doze mode.
 */
class ExamAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Programa una alarma para un evento.
     *
     * @param eventId ID del evento en Room
     * @param title Título del evento
     * @param description Descripción del evento
     * @param tipoEvento Tipo de evento
     * @param triggerAtMs Timestamp epoch cuando debe dispararse la alarma
     * @param materiaId ID de la materia asociada
     * @param reminderMinutes Minutos de antelación del recordatorio
     */
    fun scheduleAlarm(
        eventId: Long,
        title: String,
        description: String,
        tipoEvento: String,
        triggerAtMs: Long,
        materiaId: Long = -1,
        reminderMinutes: Int = 60
    ) {
        if (triggerAtMs <= System.currentTimeMillis()) {
            Timber.d("No se programa alarma para evento $eventId (fecha ya pasó)")
            return
        }

        val intent = Intent(context, ExamAlarmReceiver::class.java).apply {
            putExtra(ExamAlarmReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ExamAlarmReceiver.EXTRA_TITLE, title)
            putExtra(ExamAlarmReceiver.EXTRA_DESCRIPTION, description)
            putExtra(ExamAlarmReceiver.EXTRA_TYPE, tipoEvento)
            putExtra(ExamAlarmReceiver.EXTRA_MATERIA_ID, materiaId)
            putExtra(ExamAlarmReceiver.EXTRA_REMINDER_MINUTES, reminderMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (10_000 + eventId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
                    )
                } else {
                    // Fallback a alarma inexacta si no tiene permiso
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
                )
            }
            Timber.i("Alarma programada para evento $eventId en $triggerAtMs")
        } catch (e: SecurityException) {
            Timber.w(e, "Sin permiso para programar alarma exacta, usando inexacta")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
            )
        }
    }

    /**
     * Cancela la alarma de un evento.
     */
    fun cancelAlarm(eventId: Long) {
        val intent = Intent(context, ExamAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (10_000 + eventId).toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            Timber.i("Alarma cancelada para evento $eventId")
        }
    }
}
