package com.notasapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Efecto shimmer para estados de carga.
 *
 * Muestra un gradiente animado que simula contenido cargando,
 * evitando spinners que interrumpen el flujo visual.
 */

// ── Brush de shimmer animado ────────────────────────────────────

@Composable
fun rememberShimmerBrush(
    widthOfShadowBrush: Int = 500,
    angleOfAxisY: Float = 270f,
    durationMillis: Int = 1000
): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = (durationMillis + widthOfShadowBrush).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation - widthOfShadowBrush, y = 0.0f),
        end = Offset(x = translateAnimation, y = angleOfAxisY),
    )
}

// ── Bloques de shimmer reutilizables ─────────────────────────────

/**
 * Bloque rectangular con efecto shimmer.
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 4.dp
) {
    val shimmerBrush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush)
    )
}

/**
 * Placeholder de carga para la Card de una materia en el Home.
 */
@Composable
fun MateriaCardShimmer(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBlock(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    height = 18.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBlock(
                    modifier = Modifier.fillMaxWidth(0.4f),
                    height = 13.dp
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                ShimmerBlock(modifier = Modifier.width(36.dp), height = 22.dp)
                Spacer(modifier = Modifier.height(4.dp))
                ShimmerBlock(modifier = Modifier.width(24.dp), height = 12.dp)
            }
        }
    }
}

/**
 * Placeholder de carga para la pantalla de detalle de materia.
 * Muestra 3 cards de componentes simulados.
 */
@Composable
fun MateriaDetailShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card resumen de promedio
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    ShimmerBlock(modifier = Modifier.width(100.dp), height = 13.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBlock(modifier = Modifier.width(80.dp), height = 28.dp)
                }
                ShimmerBlock(modifier = Modifier.width(60.dp), height = 18.dp)
            }
        }

        // 3 componentes simulados
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 18.dp)
                        ShimmerBlock(modifier = Modifier.width(40.dp), height = 22.dp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    repeat(2) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.55f), height = 14.dp)
                            ShimmerBlock(modifier = Modifier.width(50.dp), height = 36.dp)
                        }
                    }
                }
            }
        }
    }
}
