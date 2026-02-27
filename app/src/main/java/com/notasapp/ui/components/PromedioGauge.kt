package com.notasapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Indicador circular animado que muestra el promedio de una materia.
 *
 * Utiliza Canvas para dibujar un arco que se llena proporcionalmente al
 * promedio entre [escalaMin] y [escalaMax], con animación de entrada.
 *
 * @param promedio      Nota actual (null si no hay notas ingresadas).
 * @param escalaMin     Valor mínimo de la escala.
 * @param escalaMax     Valor máximo de la escala.
 * @param aprobacion    Nota mínima para aprobar (se cambia color cuando se supera).
 * @param size          Tamaño del componente.
 * @param strokeWidth   Grosor del arco.
 */
@Composable
fun PromedioGauge(
    promedio: Float?,
    escalaMin: Float,
    escalaMax: Float,
    aprobacion: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp
) {
    val progreso = if (promedio != null && escalaMax > escalaMin) {
        ((promedio - escalaMin) / (escalaMax - escalaMin)).coerceIn(0f, 1f)
    } else 0f

    val aprobado = (promedio ?: 0f) >= aprobacion

    // Color dinámico: verde si aprobado, ámbar si cerca, rojo si lejos
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = when {
        aprobado -> MaterialTheme.colorScheme.secondary
        progreso > 0.45f -> Color(0xFFF57F17) // ámbar oscuro
        else -> MaterialTheme.colorScheme.error
    }

    // Animación de entrada al cargar la pantalla
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(progreso) {
        animatedProgress.animateTo(
            targetValue = progreso,
            animationSpec = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.toPx() - strokePx
            val sweepAngle = 270f * animatedProgress.value

            // Arco de fondo (track)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2)
            )

            // Arco de progreso animado
            if (sweepAngle > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2)
                )
            }
        }

        // Texto en el centro: promedio o "--"
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = promedio?.let { "%.1f".format(it) } ?: "--",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (size >= 100.dp) 26.sp else 18.sp
                ),
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
            Text(
                text = "/ ${escalaMax.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Barra de progreso lineal animada para mostrar el aporte de un componente.
 *
 * @param progreso  Valor de 0.0 a 1.0.
 * @param color     Color de la barra.
 */
@Composable
fun GradeLinearIndicator(
    progreso: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(progreso) {
        animatedProgress.animateTo(
            targetValue = progreso.coerceIn(0f, 1f),
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }

    val barColor = if (color == Color.Unspecified)
        MaterialTheme.colorScheme.primary
    else color
    val bgColor = if (trackColor == Color.Unspecified)
        MaterialTheme.colorScheme.surfaceVariant
    else trackColor

    Canvas(
        modifier = modifier
            .size(height = 8.dp, width = Dp.Unspecified)
    ) {
        drawRoundRect(
            color = bgColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
        )
        drawRoundRect(
            color = barColor,
            size = androidx.compose.ui.geometry.Size(
                width = size.width * animatedProgress.value,
                height = size.height
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
        )
    }
}
