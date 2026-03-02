package com.notasapp.ui.materia.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.domain.model.TipoEscala

/**
 * Wizard de 3 pasos para crear una nueva materia.
 *
 * Paso 1: Información básica (nombre, período, profesor)
 * Paso 2: Escala de calificación
 * Paso 3: Componentes / Cortes con porcentajes
 *
 * @param onWizardComplete Callback al guardar exitosamente.
 * @param onBack           Callback para cancelar y regresar al Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMateriaWizard(
    onWizardComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateMateriaViewModel = hiltViewModel()
) {
    val state by viewModel.wizardState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> onWizardComplete()
            is SaveState.Error -> {
                snackbarHostState.showSnackbar((saveState as SaveState.Error).message)
                viewModel.resetSaveState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Materia · Paso ${state.currentStep}/3") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.currentStep == 1) onBack()
                        else viewModel.goBack()
                    }) {
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
        ) {
            // ── Barra de progreso ────────────────────────────
            LinearProgressIndicator(
                progress = { state.currentStep / 3f },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Contenido del paso actual ────────────────────
            AnimatedContent(
                targetState = state.currentStep,
                label = "wizard_step"
            ) { step ->
                when (step) {
                    1 -> Step1BasicInfo(
                        state = state,
                        onNombreChange = viewModel::updateNombre,
                        onPeriodoChange = viewModel::updatePeriodo,
                        onProfesorChange = viewModel::updateProfesor,
                        onCreditosChange = viewModel::updateCreditos,
                        onNext = viewModel::goToStep2
                    )
                    2 -> Step2Escala(
                        state = state,
                        onTipoEscalaChange = viewModel::updateTipoEscala,
                        onNotaAprobacionChange = viewModel::updateNotaAprobacion,
                        onNext = viewModel::goToStep3
                    )
                    3 -> Step3Componentes(
                        state = state,
                        isSaving = saveState is SaveState.Loading,
                        onAddComponente = viewModel::addComponente,
                        onRemoveComponente = viewModel::removeComponente,
                        onNombreChange = viewModel::updateComponenteNombre,
                        onPorcentajeChange = viewModel::updateComponentePorcentaje,
                        onGuardar = viewModel::guardarMateria
                    )
                }
            }
        }
    }
}

// ── Paso 1: Información básica ─────────────────────────────────

@Composable
private fun Step1BasicInfo(
    state: WizardState,
    onNombreChange: (String) -> Unit,
    onPeriodoChange: (String) -> Unit,
    onProfesorChange: (String) -> Unit,
    onCreditosChange: (Int) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Información básica",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = state.nombre,
            onValueChange = onNombreChange,
            label = { Text("Nombre de la materia *") },
            placeholder = { Text("Ej: Matemáticas") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = state.periodo,
            onValueChange = onPeriodoChange,
            label = { Text("Semestre / Período *") },
            placeholder = { Text("Ej: 2026-1") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true
        )

        OutlinedTextField(
            value = state.profesor,
            onValueChange = onProfesorChange,
            label = { Text("Profesor (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )

        // ── Créditos ──
        Text(
            text = "Créditos: ${state.creditos}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1, 2, 3, 4, 5, 6).forEach { c ->
                FilterChip(
                    selected = state.creditos == c,
                    onClick = { onCreditosChange(c) },
                    label = { Text("$c") }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = state.nombre.isNotBlank() && state.periodo.isNotBlank()
        ) {
            Text("Siguiente")
        }
    }
}

// ── Paso 2: Escala ─────────────────────────────────────────────

@Composable
private fun Step2Escala(
    state: WizardState,
    onTipoEscalaChange: (TipoEscala) -> Unit,
    onNotaAprobacionChange: (Float) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Escala de calificación",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "¿Qué escala de notas usa esta materia?",
            style = MaterialTheme.typography.bodyLarge
        )

        // Chips de selección de tipo de escala
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TipoEscala.entries.forEach { tipo ->
                FilterChip(
                    selected = state.tipoEscala == tipo,
                    onClick = { onTipoEscalaChange(tipo) },
                    label = { Text(tipo.displayName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Nota mínima de aprobación
        Text(
            text = "Nota mínima para aprobar: ${"%.1f".format(state.notaAprobacion)}",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = state.notaAprobacion,
            onValueChange = onNotaAprobacionChange,
            valueRange = state.escalaMin..state.escalaMax,
            steps = ((state.escalaMax - state.escalaMin) * 10 - 1).toInt().coerceAtLeast(0)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Siguiente")
        }
    }
}

// ── Paso 3: Componentes ────────────────────────────────────────

@Composable
private fun Step3Componentes(
    state: WizardState,
    isSaving: Boolean,
    onAddComponente: () -> Unit,
    onRemoveComponente: (Int) -> Unit,
    onNombreChange: (Int, String) -> Unit,
    onPorcentajeChange: (Int, Float) -> Unit,
    onGuardar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Componentes de evaluación",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Define los cortes o parciales con su peso en la nota final.",
            style = MaterialTheme.typography.bodyLarge
        )

        // Lista de componentes
        state.componentes.forEachIndexed { index, componente ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = componente.nombre,
                            onValueChange = { onNombreChange(index, it) },
                            label = { Text("Nombre") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (state.componentes.size > 1) {
                            IconButton(onClick = { onRemoveComponente(index) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Eliminar componente",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = componente.porcentaje,
                            onValueChange = { onPorcentajeChange(index, it) },
                            valueRange = 0f..1f,
                            steps = 99,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${componente.porcentajeDisplay}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        // Total de porcentajes
        val totalColor = if (state.porcentajesValidos)
            MaterialTheme.colorScheme.secondary
        else
            MaterialTheme.colorScheme.error

        Text(
            text = "Total: ${kotlin.math.round(state.sumaPorcentajes * 100).toInt()}% ${if (state.porcentajesValidos) "✓" else "(debe ser 100%)"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = totalColor
        )

        // Botón agregar componente
        TextButton(
            onClick = onAddComponente,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Agregar componente")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Botón guardar
        Button(
            onClick = onGuardar,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isSaving && state.porcentajesValidos
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Guardar materia")
            }
        }
    }
}
