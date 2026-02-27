package com.notasapp.ui.materia.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.domain.model.Componente
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.ui.components.AnimatedText
import com.notasapp.ui.components.EstadoBadge
import com.notasapp.ui.components.GradeLinearIndicator
import com.notasapp.ui.components.MateriaDetailShimmer
import com.notasapp.ui.components.PromedioGauge
import kotlinx.coroutines.launch

/**
 * Pantalla de detalle de una materia.
 *
 * Muestra el gauge animado del promedio, componentes con sub-notas editables,
 * shimmer skeleton durante la carga inicial, y un FAB para abrir la
 * calculadora "¿qué nota necesito?".
 *
 * @param materiaId           ID de la materia a mostrar.
 * @param onBack              Callback para volver atrás.
 * @param onEditPorcentajes   Abre la pantalla de edición de porcentajes.
 * @param onExport            Abre la pantalla de exportación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MateriaDetailScreen(
    materiaId: Long,
    onBack: () -> Unit,
    onEditPorcentajes: () -> Unit,
    onExport: () -> Unit,
    viewModel: MateriaDetailViewModel = hiltViewModel()
) {
    val materia by viewModel.materia.collectAsState()
    val error by viewModel.error.collectAsState()
    val notaNecesariaResult by viewModel.notaNecesariaResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Bottom sheet de calculadora ───────────────────────────
    var showCalculadora by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
                        text = materia?.nombre ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = onEditPorcentajes) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar porcentajes")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (materia != null) {
                FloatingActionButton(
                    onClick = { showCalculadora = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = "¿Qué nota necesito?",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        if (materia == null) {
            // --- Shimmer skeleton ---
            MateriaDetailShimmer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            materia?.let { mat ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PromedioResumen(materia = mat)
                    }
                    items(mat.componentes, key = { it.id }) { componente ->
                        ComponenteCard(
                            componente = componente,
                            escalaMax = mat.escalaMax,
                            onSubNotaValueChange = { subNotaId, valor ->
                                viewModel.actualizarSubNota(subNotaId, valor)
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Calculadora bottom sheet ──────────────────────────────
    if (showCalculadora) {
        materia?.let { mat ->
            CalculadoraBottomSheet(
                escalaMax = mat.escalaMax,
                resultado = notaNecesariaResult,
                onCalcular = { meta ->
                    viewModel.calcularNotaNecesaria(meta)
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showCalculadora = false
                        viewModel.clearCalculadora()
                    }
                },
                sheetState = sheetState
            )
        }
    }
}

// ── Componentes internos ──────────────────────────────────────

@Composable
private fun PromedioResumen(
    materia: Materia,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (materia.aprobado)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gauge animado
            PromedioGauge(
                promedio = materia.promedio ?: 0f,
                escalaMax = materia.escalaMax,
                notaAprobacion = materia.notaAprobacion,
                modifier = Modifier.size(100.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Promedio actual",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedText(
                    text = "${materia.promedioDisplay} / ${materia.escalaMax.toInt()}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                EstadoBadge(
                    aprobado = materia.aprobado,
                    texto = if (materia.aprobado) "APROBADO" else "EN RIESGO"
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Mínimo: ${materia.notaAprobacion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComponenteCard(
    componente: Componente,
    escalaMax: Float,
    onSubNotaValueChange: (Long, Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(250)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header del componente ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = componente.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${componente.porcentajeDisplay}% del total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedText(
                        text = componente.promedio?.let { "%.2f".format(it) } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    componente.aporteAlFinal?.let { aporte ->
                        Text(
                            text = "Aporte: %.2f".format(aporte),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Barra de progreso del componente
            val progress = componente.promedio?.let { it / escalaMax } ?: 0f
            Spacer(Modifier.height(8.dp))
            GradeLinearIndicator(progress = progress)

            if (componente.subNotas.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                componente.subNotas.forEach { subNota ->
                    SubNotaRow(
                        subNota = subNota,
                        escalaMax = escalaMax,
                        onValueChange = { valor -> onSubNotaValueChange(subNota.id, valor) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SubNotaRow(
    subNota: SubNota,
    escalaMax: Float,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(subNota.valor) {
        mutableStateOf(subNota.valor?.let { "%.1f".format(it) } ?: "")
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = subNota.descripcion, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${(subNota.porcentajeDelComponente * 100).toInt()}% del corte",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = { input ->
                textValue = input
                val parsed = input.toFloatOrNull()
                if (parsed != null && parsed in 0f..escalaMax) {
                    onValueChange(parsed)
                } else if (input.isEmpty()) {
                    onValueChange(null)
                }
            },
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(0.35f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            placeholder = {
                Text(text = "--", style = MaterialTheme.typography.bodySmall)
            }
        )
    }
}

