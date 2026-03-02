package com.notasapp.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Datos de cada página del onboarding.
 */
private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val highlight: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.School,
        title = "Bienvenido a Gradify",
        description = "Tu compañero académico para gestionar materias, notas y promedios de forma organizada.",
        highlight = "Registra tus materias, componentes y calificaciones en un solo lugar."
    ),
    OnboardingPage(
        icon = Icons.Default.BarChart,
        title = "Estadísticas inteligentes",
        description = "Visualiza tu rendimiento con gráficos y promedios en tiempo real.",
        highlight = "Obtén recomendaciones de estudio personalizadas con inteligencia artificial."
    ),
    OnboardingPage(
        icon = Icons.Default.CalendarMonth,
        title = "Calendario de exámenes",
        description = "Programa tus evaluaciones y recibe recordatorios antes de cada examen.",
        highlight = "Nunca más olvides una fecha importante."
    ),
    OnboardingPage(
        icon = Icons.Default.CloudSync,
        title = "Respaldo seguro",
        description = "Exporta tus notas a Excel o PDF. Sincroniza con Google Drive para no perder nada.",
        highlight = "Tus datos siempre protegidos y accesibles."
    )
)

/**
 * Pantalla de onboarding que se muestra la primera vez que
 * el usuario abre la app después de iniciar sesión.
 *
 * 4 páginas deslizables con animación, indicadores de progreso,
 * botón "Siguiente" / "Empezar" en la última.
 *
 * @param onFinish Se llama cuando el usuario completa el tutorial.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]
    val isLastPage = currentPage == pages.lastIndex

    // Función que marca onboarding como visto y navega
    val finish = {
        viewModel.markOnboardingComplete()
        onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Botón saltar ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isLastPage) {
                TextButton(onClick = finish) {
                    Text("Saltar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Contenido animado ───────────────────────────────────
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "OnboardingPage"
        ) { pageIndex ->
            val p = pages[pageIndex]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icono grande
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = p.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Título
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(16.dp))

                // Descripción
                Text(
                    text = p.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Highlight
                Text(
                    text = p.highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Indicadores de página ───────────────────────────────
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == currentPage) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        // ── Botones de navegación ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                TextButton(onClick = { currentPage-- }) {
                    Text("Anterior")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        finish()
                    } else {
                        currentPage++
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastPage)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isLastPage) "¡Empezar!" else "Siguiente",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
