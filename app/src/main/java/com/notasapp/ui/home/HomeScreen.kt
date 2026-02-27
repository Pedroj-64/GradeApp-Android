package com.notasapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.domain.model.Materia
import com.notasapp.ui.components.AnimatedText
import com.notasapp.ui.components.EstadoBadge
import com.notasapp.ui.components.MateriaCardShimmer
import com.notasapp.ui.components.SwipeToDeleteWrapper
import com.notasapp.ui.theme.NotasAppTheme
import kotlinx.coroutines.delay

/**
 * Pantalla principal: lista de materias del usuario.
 *
 * - Shimmer skeleton mientras llega el primer dato del Flow.
 * - Animación de entrada escalonada (stagger) por cada card.
 * - Swipe-to-delete integrado con [SwipeToDeleteWrapper].
 * - Acceso rápido a la pantalla de estadísticas desde el TopAppBar.
 *
 * @param onNavigateToCreateMateria  Abre el Wizard de nueva materia.
 * @param onNavigateToMateria        Abre el detalle de la materia dada.
 * @param onNavigateToEstadisticas   Abre la pantalla de estadísticas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCreateMateria: () -> Unit,
    onNavigateToMateria: (Long) -> Unit,
    onNavigateToEstadisticas: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val materias by viewModel.materias.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Controlamos si la primera carga ya resolvió (null = todavía no sabemos)
    var firstLoadDone by remember { mutableStateOf(false) }
    LaunchedEffect(materias) {
        if (!firstLoadDone) {
            delay(300) // espera mínima para evitar flash
            firstLoadDone = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis Materias",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToEstadisticas) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Estadísticas",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreateMateria,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva materia") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        when {
            // --- Shimmer de carga inicial ---
            !firstLoadDone -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) { MateriaCardShimmer() }
                }
            }

            // --- Lista vacía ---
            materias.isEmpty() -> {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onAddMateria = onNavigateToCreateMateria
                )
            }

            // --- Lista con materias ---
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = materias,
                        key = { _, m -> m.id }
                    ) { index, materia ->
                        // Entrada escalonada: cada card entra 60 ms después del anterior
                        var showed by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 60L)
                            showed = true
                        }

                        AnimatedVisibility(
                            visible = showed,
                            enter = slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300))
                        ) {
                            SwipeToDeleteWrapper(
                                onDelete = { viewModel.deleteMateria(materia.id) }
                            ) {
                                MateriaCard(
                                    materia = materia,
                                    onClick = { onNavigateToMateria(materia.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Componentes internos ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MateriaCard(
    materia: Materia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = materia.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${materia.periodo}${materia.profesor?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (materia.promedio != null) {
                        EstadoBadge(
                            aprobado = materia.aprobado,
                            texto = if (materia.aprobado) "APROBADO" else "EN RIESGO"
                        )
                    }
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedText(
                        text = materia.promedioDisplay,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            materia.aprobado -> MaterialTheme.colorScheme.secondary
                            materia.promedio != null -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "/ ${materia.escalaMax.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun EmptyState(
    onAddMateria: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Aún no tienes materias",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Toca el botón + para agregar tu primera materia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NotasAppTheme {
        // Preview estática
    }
}
