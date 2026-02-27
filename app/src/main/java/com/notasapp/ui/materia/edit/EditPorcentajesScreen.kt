package com.notasapp.ui.materia.edit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pantalla para editar los porcentajes de los componentes.
 *
 * Soporta:
 * - Reordenamiento por drag & drop con handle visible y retroalimentación háptica.
 * - Elevación dinámica en el ítem que se está arrastrando.
 * - Ajuste de porcentaje en tiempo real con Sliders.
 * - Validación: el total siempre debe sumar 100%.
 *
 * @param materiaId ID de la materia cuyos componentes se editarán.
 * @param onBack    Callback para regresar al detalle de la materia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPorcentajesScreen(
    materiaId: Long,
    onBack: () -> Unit,
    viewModel: EditPorcentajesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onBack()
    }

    val componentes = remember(uiState.componentes) {
        mutableStateListOf(*uiState.componentes.toTypedArray())
    }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val moved = componentes.removeAt(from.index)
            componentes.add(to.index, moved)
            viewModel.onReorder(componentes.toList())
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar porcentajes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Indicador de total
            val sumaTotal = componentes.sumOf { it.porcentaje.toDouble() }.toFloat()
            val totalColor = if (kotlin.math.abs(sumaTotal - 1f) <= 0.01f)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.error

            Text(
                text = "Total: ${(sumaTotal * 100).toInt()}%  " +
                        if (kotlin.math.abs(sumaTotal - 1f) <= 0.01f) "✓" else "≠ 100%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = totalColor,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Text(
                text = "Mantén pulsado el ≡ para reordenar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                state = reorderState.lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = componentes,
                    key = { _, item -> item.id }
                ) { _, componente ->
                    ReorderableItem(
                        reorderableLazyListState = reorderState,
                        key = componente.id
                    ) { isDragging ->
                        // Elevación animada: sube mientras se arrastra
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 1.dp,
                            label = "drag_elevation"
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDragging)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Handle de arrastre con feedback háptico
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Reordenar",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                },
                                                onDragStopped = {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                }
                                            ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = componente.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )

                                    Text(
                                        text = "${(componente.porcentaje * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Slider(
                                    value = componente.porcentaje,
                                    onValueChange = { nuevoPct ->
                                        viewModel.onPorcentajeChange(componente.id, nuevoPct)
                                    },
                                    valueRange = 0f..1f,
                                    steps = 19,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::guardar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = kotlin.math.abs(sumaTotal - 1f) <= 0.01f
            ) {
                Text("Guardar cambios")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

