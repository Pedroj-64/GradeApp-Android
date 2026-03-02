package com.notasapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.notasapp.MainActivity
import com.notasapp.R
import com.notasapp.data.mapper.toDomain
import com.notasapp.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Widget de resumen del semestre con estadísticas completas.
 *
 * Muestra: promedio general, total de materias, cuántas aprobando,
 * cuántas en riesgo, barra de progreso del semestre y un mensaje
 * motivacional.
 */
class ResumenSemestreWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    )
                    val db = entryPoint.database()
                    val usuario = db.usuarioDao().getUsuarioActivoOnce()

                    val views = RemoteViews(context.packageName, R.layout.widget_resumen_semestre)

                    if (usuario != null) {
                        val materias = db.materiaDao()
                            .getMateriasConComponentesOnce(usuario.googleId)
                            .map { it.toDomain() }

                        // Promedio general
                        val promedios = materias.mapNotNull { it.promedio }
                        val promedioStr = if (promedios.isNotEmpty()) {
                            "%.2f".format(promedios.average())
                        } else "– –"

                        // Conteos
                        val total = materias.size
                        val aprobando = materias.count { it.aprobado }
                        val enRiesgo = materias.count { !it.aprobado && it.promedio != null }

                        // Progreso del semestre (promedio de porcentajeEvaluado)
                        val progresoSemestre = if (materias.isNotEmpty()) {
                            kotlin.math.round(materias.sumOf { it.porcentajeEvaluado.toDouble() }.toFloat() / materias.size * 100).toInt()
                        } else 0

                        // Periodo
                        val periodo = materias.firstOrNull()?.periodo ?: ""

                        // Mensaje motivacional
                        val mensaje = when {
                            promedios.isEmpty() -> "✏️ Registra tus primeras notas"
                            promedios.average() >= 4.5 -> "🌟 ¡Rendimiento excepcional!"
                            promedios.average() >= 4.0 -> "🔥 ¡Vas muy bien, sigue así!"
                            promedios.average() >= 3.5 -> "💪 Buen trabajo, puedes mejorar"
                            promedios.average() >= 3.0 -> "📚 Mantén el esfuerzo"
                            else -> "⚡ ¡No te rindas, tú puedes!"
                        }

                        views.setTextViewText(R.id.tv_resumen_promedio, promedioStr)
                        views.setTextViewText(R.id.tv_resumen_materias, "$total")
                        views.setTextViewText(R.id.tv_resumen_aprobadas, "$aprobando")
                        views.setTextViewText(R.id.tv_resumen_riesgo, "$enRiesgo")
                        views.setProgressBar(R.id.pb_resumen_progreso, 100, progresoSemestre, false)
                        views.setTextViewText(R.id.tv_resumen_progreso_label, "$progresoSemestre% del semestre evaluado")
                        views.setTextViewText(R.id.tv_widget_periodo, periodo)
                        views.setTextViewText(R.id.tv_resumen_mensaje, mensaje)
                    } else {
                        views.setTextViewText(R.id.tv_resumen_promedio, "– –")
                        views.setTextViewText(R.id.tv_resumen_materias, "0")
                        views.setTextViewText(R.id.tv_resumen_aprobadas, "0")
                        views.setTextViewText(R.id.tv_resumen_riesgo, "0")
                        views.setProgressBar(R.id.pb_resumen_progreso, 100, 0, false)
                        views.setTextViewText(R.id.tv_resumen_progreso_label, "Inicia sesión para ver datos")
                        views.setTextViewText(R.id.tv_resumen_mensaje, "")
                    }

                    // Click en el widget abre la app
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, 2, launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_resumen_root, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Timber.d("Widget Resumen actualizado")

                } catch (e: Exception) {
                    Timber.e(e, "Error al actualizar widget de resumen")
                }
            }
        }
    }
}
