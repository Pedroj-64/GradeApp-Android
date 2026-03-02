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
 * Widget compacto que muestra la materia con mejor/peor desempeño.
 *
 * Muestra: nombre de la materia, nota actual, barra de progreso y estado.
 * Si hay materias en riesgo, muestra la peor; si todas van bien, muestra la mejor.
 * Esto da un "vistazo rápido" al panorama académico.
 */
class MateriaWidgetProvider : AppWidgetProvider() {

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

                    val views = RemoteViews(context.packageName, R.layout.widget_materia)

                    if (usuario != null) {
                        val materias = db.materiaDao()
                            .getMateriasConComponentesOnce(usuario.googleId)
                            .map { it.toDomain() }
                            .filter { it.promedio != null }

                        if (materias.isNotEmpty()) {
                            // Si hay materias en riesgo, mostramos la peor para alertar.
                            // Si todas van bien, mostramos la mejor para motivar.
                            val enRiesgo = materias.filter { !it.aprobado }
                            val destacada = if (enRiesgo.isNotEmpty()) {
                                enRiesgo.minByOrNull { it.promedio ?: 0f }!!
                            } else {
                                materias.maxByOrNull { it.promedio ?: 0f }!!
                            }

                            val nota = destacada.promedioDisplay
                            val progreso = kotlin.math.round(destacada.porcentajeEvaluado * 100).toInt()

                            val estado = when {
                                destacada.yaAprobo -> "✅ Aprobada"
                                destacada.aprobado -> "📗 Aprobando"
                                destacada.notaNecesariaParaAprobar != null ->
                                    "⚠️ Necesita %.1f".format(destacada.notaNecesariaParaAprobar)
                                else -> "🔴 En riesgo"
                            }

                            views.setTextViewText(R.id.tv_materia_nombre, destacada.nombre)
                            views.setTextViewText(R.id.tv_materia_nota, nota)
                            views.setTextViewText(R.id.tv_materia_estado, estado)
                            views.setProgressBar(R.id.pb_materia_progreso, 100, progreso, false)
                            views.setTextViewText(R.id.tv_materia_progreso_label, "$progreso% evaluado")
                        } else {
                            views.setTextViewText(R.id.tv_materia_nombre, "Sin notas aún")
                            views.setTextViewText(R.id.tv_materia_nota, "–")
                            views.setTextViewText(R.id.tv_materia_estado, "Registra tus notas")
                            views.setProgressBar(R.id.pb_materia_progreso, 100, 0, false)
                            views.setTextViewText(R.id.tv_materia_progreso_label, "")
                        }
                    } else {
                        views.setTextViewText(R.id.tv_materia_nombre, "Gradify")
                        views.setTextViewText(R.id.tv_materia_nota, "– –")
                        views.setTextViewText(R.id.tv_materia_estado, "Inicia sesión")
                        views.setProgressBar(R.id.pb_materia_progreso, 100, 0, false)
                        views.setTextViewText(R.id.tv_materia_progreso_label, "")
                    }

                    // Click en el widget abre la app
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, 1, launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_materia_root, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Timber.d("Widget Materia actualizado")

                } catch (e: Exception) {
                    Timber.e(e, "Error al actualizar widget de materia")
                }
            }
        }
    }
}
