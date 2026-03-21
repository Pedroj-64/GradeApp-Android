package com.notasapp.ui.materia.detail

import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.R
import com.notasapp.domain.model.Componente
import com.notasapp.domain.model.EstadoMeta
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.domain.model.SubNotaDetalle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.notasapp.ui.components.AnimatedText
import com.notasapp.ui.components.EstadoBadge
import com.notasapp.ui.components.GradeLinearIndicator
import com.notasapp.ui.components.MateriaDetailShimmer
import com.notasapp.ui.components.PromedioGauge
import kotlinx.coroutines.launch

/**
 * Formatea un Float con punto decimal (invariant) para que sea parseable.
 * Acepta tanto punto como coma al parsear.
 */
private fun Float.toInputString(): String = String.format(Locale.US, "%.1f", this)
private fun String.parseGrade(): Float? = this.replace(',', '.').toFloatOrNull()

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
    onNavigateToRecomendaciones: () -> Unit = {},
    viewModel: MateriaDetailViewModel = hiltViewModel()
) {
    val materia by viewModel.materia.collectAsState()
    val error by viewModel.error.collectAsState()
    val notaNecesariaResult by viewModel.notaNecesariaResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Bottom sheet de calculadora ───────────────────────────
    var showCalculadora by rememberSaveable { mutableStateOf(false) }
    var showQuickEntry by rememberSaveable { mutableStateOf(false) }
    var showMetaDialog by rememberSaveable { mutableStateOf(false) }
    var showNotasDialog by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickEntrySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    // Quick entry: only show if there are pending grades
                    if (materia?.componentes?.any { c ->
                            c.subNotas.any { s -> !s.esCompuesta && s.valor == null }
                        } == true
                    ) {
                        IconButton(onClick = { showQuickEntry = true }) {
                            Icon(Icons.Default.FlashOn, contentDescription = stringResource(R.string.detail_quick_entry))
                        }
                    }
                    IconButton(onClick = onNavigateToRecomendaciones) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = stringResource(R.string.detail_study_resources),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEditPorcentajes) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.detail_edit_percentages))
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.detail_export))
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
                        contentDescription = stringResource(R.string.detail_what_grade_needed),
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        PromedioResumen(materia = mat)
                    }

                    // ── Sección: Meta y Notas ──────────────────────
                    item {
                        MetaYNotasSection(
                            materia = mat,
                            onEditMeta = { showMetaDialog = true },
                            onEditNotas = { showNotasDialog = true }
                        )
                    }

                    // ── Sección: Evaluaciones ──────────────────────
                    item {
                        Text(
                            text = stringResource(R.string.detail_evaluations),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(mat.componentes, key = { it.id }) { componente ->
                        ComponenteCard(
                            componente = componente,
                            escalaMax = mat.escalaMax,
                            onSubNotaValueChange = { subNotaId, valor ->
                                viewModel.actualizarSubNota(subNotaId, valor)
                            },
                            onAgregarSubNota = { desc, pct ->
                                viewModel.agregarSubNota(componente.id, desc, pct)
                            },
                            onEliminarSubNota = { subNotaId ->
                                viewModel.eliminarSubNota(subNotaId)
                            },
                            onAgregarDetalle = { subNotaId, desc, pct ->
                                viewModel.agregarDetalle(subNotaId, desc, pct)
                            },
                            onActualizarDetalle = { detalleId, valor ->
                                viewModel.actualizarDetalle(detalleId, valor)
                            },
                            onEliminarDetalle = { detalleId ->
                                viewModel.eliminarDetalle(detalleId)
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

    // ── Entrada rápida bottom sheet ──────────────────────────
    if (showQuickEntry) {
        materia?.let { mat ->
            QuickEntryBottomSheet(
                materia = mat,
                onValueChange = { subNotaId, valor ->
                    viewModel.actualizarSubNota(subNotaId, valor)
                },
                onDismiss = {
                    scope.launch { quickEntrySheetState.hide() }.invokeOnCompletion {
                        showQuickEntry = false
                    }
                },
                sheetState = quickEntrySheetState
            )
        }
    }

    // ── Diálogo de meta académica ──────────────────────────
    if (showMetaDialog) {
        materia?.let { mat ->
            MetaDialog(
                currentMeta = mat.notaMeta,
                escalaMax = mat.escalaMax,
                onDismiss = { showMetaDialog = false },
                onSave = { newMeta ->
                    viewModel.actualizarNotaMeta(newMeta)
                    showMetaDialog = false
                }
            )
        }
    }

    // ── Diálogo de notas personales ──────────────────────────
    if (showNotasDialog) {
        materia?.let { mat ->
            NotasDialog(
                currentNotas = mat.notas,
                onDismiss = { showNotasDialog = false },
                onSave = { newNotas ->
                    viewModel.actualizarNotas(newNotas)
                    showNotasDialog = false
                }
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
    Column(modifier = modifier.fillMaxWidth()) {
        // ── Card principal de promedio ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gauge animado
                PromedioGauge(
                    promedio = materia.promedio,
                    escalaMin = materia.escalaMin,
                    escalaMax = materia.escalaMax,
                    aprobacion = materia.notaAprobacion,
                    modifier = Modifier.size(100.dp)
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.promedio_actual),
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
                        texto = if (materia.aprobado) stringResource(R.string.estado_aprobado) else stringResource(R.string.estado_en_riesgo)
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

        Spacer(Modifier.height(12.dp))

        // ── Card de progreso acumulado ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.detail_accumulated),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    AnimatedText(
                        text = "${materia.acumuladoDisplay} / ${materia.escalaMax.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Barra de progreso general
                val progresoAcum = (materia.acumulado / materia.escalaMax).coerceIn(0f, 1f)
                GradeLinearIndicator(progreso = progresoAcum)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Evaluado: ${kotlin.math.round(materia.porcentajeEvaluado * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    materia.notaNecesariaParaAprobar?.let { necesita ->
                        Text(
                            text = "Necesitas ≈ ${"%.2f".format(necesita)} en lo restante",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // ── Mensaje motivacional según progreso ──────────────────
        val mensajeMotivacional = getMensajeMotivacional(materia)
        AnimatedVisibility(
            visible = mensajeMotivacional != null,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn()
        ) {
            if (mensajeMotivacional != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mensajeMotivacional.first,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = mensajeMotivacional.second,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ── Banner de felicitación ──────────────────────────────
        if (materia.yaAprobo && !materia.completa) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎉",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "¡Felicidades!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Ya superaste el mínimo de ${materia.notaAprobacion} para aprobar. ¡Llevas ${materia.acumuladoDisplay} acumulado!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Devuelve un par (emoji, mensaje) motivacional según el progreso del estudiante.
 * Null si no hay notas ingresadas aún.
 */
private fun getMensajeMotivacional(materia: Materia): Pair<String, String>? {
    val pct = materia.porcentajeEvaluado

    // Sin notas todavía
    if (pct == 0f || materia.promedio == null) return null

    // Ya completó todo
    if (materia.completa) {
        return if (materia.aprobado) {
            "🏆" to "¡Completaste todas las evaluaciones y aprobaste! Excelente semestre."
        } else {
            "📋" to "Completaste todas las evaluaciones. Revisa las áreas donde puedes mejorar."
        }
    }

    // Ya aprobó antes de terminar
    if (materia.yaAprobo) return null // El banner de felicitación ya maneja esto

    // Mensajes según porcentaje evaluado y rendimiento
    val rendimiento = materia.promedio!! / materia.escalaMax // 0.0 – 1.0
    val aprobacionRatio = materia.notaAprobacion / materia.escalaMax

    return when {
        // Primer corte (0-25%)
        pct <= 0.25f -> when {
            rendimiento >= aprobacionRatio * 1.2f ->
                "🚀" to "¡Gran inicio! Arrancaste con todo. Mantén ese ritmo y el semestre será tuyo."
            rendimiento >= aprobacionRatio ->
                "💪" to "Buen comienzo, vas por buen camino. ¡Sigue así y cada vez será más fácil!"
            else ->
                "📚" to "Es solo el inicio, hay mucho camino por recorrer. ¡Cada nota cuenta, tú puedes!"
        }
        // Segundo corte (25-50%)
        pct <= 0.50f -> when {
            rendimiento >= aprobacionRatio * 1.2f ->
                "⭐" to "¡Llevas un rendimiento excelente! Ya vas por la mitad, sigue destacándote."
            rendimiento >= aprobacionRatio ->
                "📈" to "Vas bien, ya pasaste la primera mitad. ¡Mantén el enfoque y lo lograrás!"
            else ->
                "🔥" to "Aún puedes remontar, queda bastante por evaluar. ¡Enfócate en lo que viene!"
        }
        // Tercer corte (50-75%)
        pct <= 0.75f -> when {
            rendimiento >= aprobacionRatio * 1.2f ->
                "🌟" to "¡Vas volando! Más de la mitad evaluada y tu rendimiento es sobresaliente."
            rendimiento >= aprobacionRatio ->
                "✨" to "¡Ya se ve la meta! Mantén la concentración en la recta final."
            else ->
                "💡" to "Queda poco, pero cada punto cuenta. ¡Da lo mejor de ti en lo que resta!"
        }
        // Recta final (75-99%)
        else -> when {
            rendimiento >= aprobacionRatio * 1.2f ->
                "🎯" to "¡Casi terminas y vas increíble! Un último empujón para cerrar con broche de oro."
            rendimiento >= aprobacionRatio ->
                "🏁" to "¡Ya casi llegas! Falta poco para terminar el semestre con éxito."
            else ->
                "⚡" to "El último tramo es clave. ¡Concéntrate y da todo, aún es posible!"
        }
    }
}

@Composable
private fun ComponenteCard(
    componente: Componente,
    escalaMax: Float,
    onSubNotaValueChange: (Long, Float?) -> Unit,
    onAgregarSubNota: (descripcion: String, porcentaje: Float) -> Unit,
    onEliminarSubNota: (Long) -> Unit,
    onAgregarDetalle: (subNotaId: Long, descripcion: String, porcentaje: Float) -> Unit,
    onActualizarDetalle: (detalleId: Long, valor: Float?) -> Unit,
    onEliminarDetalle: (detalleId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var mostrarDialogAgregar by remember { mutableStateOf(false) }
    val porcentajeSubNotasUsado = componente.subNotas
        .sumOf { it.porcentajeDelComponente.toDouble() }.toFloat()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(250)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                    Text(
                        text = componente.progresoDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Barra de progreso del componente
            val progress = componente.promedio?.let { it / escalaMax } ?: 0f
            Spacer(Modifier.height(8.dp))
            GradeLinearIndicator(progreso = progress)

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            componente.subNotas.forEach { subNota ->
                SubNotaRow(
                    subNota = subNota,
                    escalaMax = escalaMax,
                    onValueChange = { valor -> onSubNotaValueChange(subNota.id, valor) },
                    onEliminar = { onEliminarSubNota(subNota.id) },
                    onAgregarDetalle = { desc, pct -> onAgregarDetalle(subNota.id, desc, pct) },
                    onActualizarDetalle = onActualizarDetalle,
                    onEliminarDetalle = onEliminarDetalle
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Botón agregar nota al corte
            TextButton(
                onClick = { mostrarDialogAgregar = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.detail_add_grade), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (mostrarDialogAgregar) {
        AgregarSubNotaDialog(
            componenteNombre = componente.nombre,
            porcentajeYaUsado = porcentajeSubNotasUsado,
            onDismiss = { mostrarDialogAgregar = false },
            onAgregar = { desc, pct ->
                onAgregarSubNota(desc, pct)
                mostrarDialogAgregar = false
            }
        )
    }
}

@Composable
private fun SubNotaRow(
    subNota: SubNota,
    escalaMax: Float,
    onValueChange: (Float?) -> Unit,
    onEliminar: () -> Unit,
    onAgregarDetalle: (descripcion: String, porcentaje: Float) -> Unit,
    onActualizarDetalle: (detalleId: Long, valor: Float?) -> Unit,
    onEliminarDetalle: (detalleId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(subNota.esCompuesta) { mutableStateOf(subNota.esCompuesta) }
    var mostrarDialogDetalle by remember { mutableStateOf(false) }
    var textValue by remember(subNota.valor) {
        mutableStateOf(subNota.valor?.toInputString() ?: "")
    }
    val porcentajeDetallesUsado = subNota.detalles
        .sumOf { it.porcentaje.toDouble() }.toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Fila principal de la sub-nota ─────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                if (subNota.esCompuesta) {
                    Text(
                        text = "Compuesta · ${subNota.detalles.size} elemento(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (subNota.esCompuesta) {
                // Sub-nota compuesta: mostrar valor calculado (solo lectura)
                Text(
                    text = subNota.valorEfectivo?.let { "%.2f".format(it) } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // Botón expandir/contraer
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) stringResource(R.string.detail_collapse) else stringResource(R.string.detail_expand)
                    )
                }
            } else {
                // Sub-nota simple: campo editable
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input
                        val parsed = input.parseGrade()
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

            IconButton(
                onClick = onEliminar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.detail_delete_grade),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── Sección de detalles (sub-notas internas) ──────────
        if (expanded && subNota.esCompuesta) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                subNota.detalles.forEach { detalle ->
                    DetalleRow(
                        detalle = detalle,
                        escalaMax = escalaMax,
                        onValueChange = { valor -> onActualizarDetalle(detalle.id, valor) },
                        onEliminar = { onEliminarDetalle(detalle.id) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(
                    onClick = { mostrarDialogDetalle = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.detail_add_element), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Botón para "convertir a compuesta" (agregar primer detalle)
        if (!subNota.esCompuesta) {
            TextButton(
                onClick = { mostrarDialogDetalle = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Dividir en sub-notas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (mostrarDialogDetalle) {
        AgregarDetalleDialog(
            subNotaNombre = subNota.descripcion,
            porcentajeYaUsado = porcentajeDetallesUsado,
            onDismiss = { mostrarDialogDetalle = false },
            onAgregar = { desc, pct ->
                onAgregarDetalle(desc, pct)
                mostrarDialogDetalle = false
                expanded = true
            }
        )
    }
}

@Composable
private fun DetalleRow(
    detalle: SubNotaDetalle,
    escalaMax: Float,
    onValueChange: (Float?) -> Unit,
    onEliminar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(detalle.valor) {
        mutableStateOf(detalle.valor?.toInputString() ?: "")
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "· ${detalle.descripcion}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${kotlin.math.round(detalle.porcentaje * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = { input ->
                textValue = input
                val parsed = input.parseGrade()
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

        IconButton(
            onClick = onEliminar,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.btn_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AgregarDetalleDialog(
    subNotaNombre: String,
    porcentajeYaUsado: Float = 0f,
    onDismiss: () -> Unit,
    onAgregar: (descripcion: String, porcentaje: Float) -> Unit
) {
    var descripcion by remember { mutableStateOf("") }
    val disponible = (100f - porcentajeYaUsado * 100f).coerceIn(0f, 100f)
    var porcentajeText by remember { mutableStateOf(disponible.toInt().toString()) }
    val pct = porcentajeText.parseGrade()
    val valido = descripcion.isNotBlank() && pct != null && pct in 1f..disponible.coerceAtLeast(1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elem. de «$subNotaNombre»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text(stringResource(R.string.detail_description)) },
                    placeholder = { Text("Ej: Primer intento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = porcentajeText,
                    onValueChange = { porcentajeText = it },
                    label = { Text("Peso (% dentro de la actividad)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "Disponible: ${disponible.toInt()}%",
                            color = if (pct != null && pct > disponible)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = pct != null && pct > disponible
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = pct ?: return@Button
                    onAgregar(descripcion.trim(), p / 100f)
                },
                enabled = valido
            ) { Text(stringResource(R.string.detail_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

// ── Quick Entry Bottom Sheet ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntryBottomSheet(
    materia: Materia,
    onValueChange: (subNotaId: Long, valor: Float?) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState
) {
    // Collect all simple (non-compound) sub-notas without a value
    data class PendingEntry(
        val componenteNombre: String,
        val subNota: SubNota,
        val escalaMax: Float
    )

    val pending = materia.componentes.flatMap { comp ->
        comp.subNotas
            .filter { !it.esCompuesta && it.valor == null }
            .map { PendingEntry(comp.nombre, it, materia.escalaMax) }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_quick_entry),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${pending.size} nota(s) pendiente(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (pending.isEmpty()) {
                Text(
                    text = "¡Todas las notas están ingresadas!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(pending, key = { it.subNota.id }) { entry ->
                        QuickEntryRow(
                            componenteNombre = entry.componenteNombre,
                            subNota = entry.subNota,
                            escalaMax = entry.escalaMax,
                            onValueChange = { valor -> onValueChange(entry.subNota.id, valor) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickEntryRow(
    componenteNombre: String,
    subNota: SubNota,
    escalaMax: Float,
    onValueChange: (Float?) -> Unit
) {
    var textValue by remember(subNota.valor) {
        mutableStateOf(subNota.valor?.toInputString() ?: "")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subNota.descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = componenteNombre,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    textValue = input
                    val parsed = input.parseGrade()
                    if (parsed != null && parsed in 0f..escalaMax) {
                        onValueChange(parsed)
                    } else if (input.isEmpty()) {
                        onValueChange(null)
                    }
                },
                modifier = Modifier.width(90.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                placeholder = {
                    Text("0-${escalaMax.toInt()}", style = MaterialTheme.typography.bodySmall)
                }
            )
        }
    }
}

// ── Diálogos de creación ──────────────────────────────────────

@Composable
private fun AgregarSubNotaDialog(
    componenteNombre: String,
    porcentajeYaUsado: Float = 0f,
    onDismiss: () -> Unit,
    onAgregar: (descripcion: String, porcentaje: Float) -> Unit
) {
    var descripcion by remember { mutableStateOf("") }
    val disponible = (100f - porcentajeYaUsado * 100f).coerceIn(0f, 100f)
    var porcentajeText by remember { mutableStateOf(disponible.toInt().toString()) }
    val pct = porcentajeText.parseGrade()
    val valido = descripcion.isNotBlank() && pct != null && pct in 1f..disponible.coerceAtLeast(1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar nota · $componenteNombre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text(stringResource(R.string.detail_description)) },
                    placeholder = { Text("Ej: Parcial 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = porcentajeText,
                    onValueChange = { porcentajeText = it },
                    label = { Text("Peso (% del corte)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "Disponible: ${disponible.toInt()}%",
                            color = if (pct != null && pct > disponible)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = pct != null && pct > disponible
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = pct ?: return@Button
                    onAgregar(descripcion.trim(), p / 100f)
                },
                enabled = valido
            ) { Text(stringResource(R.string.detail_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

// ── Sección Meta y Notas ──────────────────────────────────────

@Composable
private fun MetaYNotasSection(
    materia: Materia,
    onEditMeta: () -> Unit,
    onEditNotas: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Metas y notas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card de Meta Académica
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = when (materia.estadoMeta) {
                        EstadoMeta.ALCANZADA -> MaterialTheme.colorScheme.secondaryContainer
                        EstadoMeta.EN_CAMINO -> MaterialTheme.colorScheme.primaryContainer
                        EstadoMeta.REQUIERE_ESFUERZO -> MaterialTheme.colorScheme.tertiaryContainer
                        EstadoMeta.INALCANZABLE -> MaterialTheme.colorScheme.errorContainer
                        EstadoMeta.SIN_META -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                onClick = onEditMeta
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (materia.estadoMeta) {
                            EstadoMeta.ALCANZADA -> Icons.Default.Star
                            EstadoMeta.SIN_META -> Icons.Default.StarOutline
                            else -> Icons.AutoMirrored.Filled.TrendingUp
                        },
                        contentDescription = null,
                        tint = when (materia.estadoMeta) {
                            EstadoMeta.ALCANZADA -> MaterialTheme.colorScheme.onSecondaryContainer
                            EstadoMeta.EN_CAMINO -> MaterialTheme.colorScheme.onPrimaryContainer
                            EstadoMeta.REQUIERE_ESFUERZO -> MaterialTheme.colorScheme.onTertiaryContainer
                            EstadoMeta.INALCANZABLE -> MaterialTheme.colorScheme.onErrorContainer
                            EstadoMeta.SIN_META -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (materia.tieneMeta) "Meta: ${materia.notaMeta}" else stringResource(R.string.goal_no_goal),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (materia.tieneMeta) {
                        val estadoTexto = when (materia.estadoMeta) {
                            EstadoMeta.ALCANZADA -> stringResource(R.string.goal_achieved)
                            EstadoMeta.EN_CAMINO -> stringResource(R.string.goal_on_track)
                            EstadoMeta.REQUIERE_ESFUERZO -> stringResource(R.string.goal_effort_needed)
                            EstadoMeta.INALCANZABLE -> stringResource(R.string.goal_unreachable)
                            else -> ""
                        }
                        Text(
                            text = estadoTexto,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card de Notas Personales
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (materia.notas?.isNotBlank() == true)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = onEditNotas
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = if (materia.notas?.isNotBlank() == true)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (materia.notas?.isNotBlank() == true)
                            "Tienes notas"
                        else
                            "Sin notas",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (materia.notas?.isNotBlank() == true) {
                        Text(
                            text = materia.notas!!.take(30).let {
                                if (materia.notas!!.length > 30) "$it..." else it
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

// ── Diálogos ──────────────────────────────────────────────────

@Composable
private fun MetaDialog(
    currentMeta: Float?,
    escalaMax: Float,
    onDismiss: () -> Unit,
    onSave: (Float?) -> Unit
) {
    var metaText by remember { mutableStateOf(currentMeta?.toInputString() ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val parsedMeta = metaText.parseGrade()
    val isValid = metaText.isEmpty() || (parsedMeta != null && parsedMeta > 0f && parsedMeta <= escalaMax)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_set)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.goal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = metaText,
                    onValueChange = { metaText = it },
                    label = { Text("Nota meta") },
                    placeholder = { Text("0.0 - ${escalaMax.toInt()}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (parsedMeta != null && parsedMeta > escalaMax) {
                            Text(
                                text = "Máximo: ${escalaMax.toInt()}",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Deja vacío para quitar meta")
                        }
                    },
                    isError = !isValid
                )

                if (currentMeta != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.goal_remove))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val meta = if (metaText.isEmpty()) null else parsedMeta
                    onSave(meta)
                },
                enabled = isValid
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.goal_remove)) },
            text = { Text("¿Quitar la meta académica?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSave(null)
                        showDeleteConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun NotasDialog(
    currentNotas: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var notasText by remember { mutableStateOf(currentNotas ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.notes_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = notasText,
                    onValueChange = { notasText = it },
                    label = { Text("Notas") },
                    placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Deja vacío para quitar notas")
                    }
                )

                if (!currentNotas.isNullOrBlank()) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Eliminar notas")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val notes = notasText.takeIf { it.isNotBlank() }
                    onSave(notes)
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar notas") },
            text = { Text("¿Eliminar todas las notas personales?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSave(null)
                        showDeleteConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}