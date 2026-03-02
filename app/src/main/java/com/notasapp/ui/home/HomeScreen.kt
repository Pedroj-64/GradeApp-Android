package com.notasapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.R
import com.notasapp.domain.model.Materia
import com.notasapp.domain.util.GradeCalculator
import com.notasapp.ui.components.AnimatedText
import com.notasapp.ui.components.EstadoBadge
import com.notasapp.ui.components.GradeLinearIndicator
import com.notasapp.ui.components.MateriaCardShimmer
import com.notasapp.ui.components.SwipeToDeleteWrapper
import com.notasapp.ui.theme.rememberResponsiveDimens
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val materias by viewModel.materias.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filtroSemestre by viewModel.filtroSemestre.collectAsState()
    val orden by viewModel.orden.collectAsState()
    val semestres by viewModel.semestresDisponibles.collectAsState()
    val materiasEnRiesgo by viewModel.materiasEnRiesgo.collectAsState()
    val promedioGeneral by viewModel.promedioGeneral.collectAsState()
    val pendingDeleteId by viewModel.pendingDeleteId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Controlamos si la primera carga ya resolvió
    var firstLoadDone by remember { mutableStateOf(false) }
    LaunchedEffect(materias) {
        if (!firstLoadDone) {
            delay(300)
            firstLoadDone = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // ── Diálogo de confirmación de borrado ─────────────────────
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.home_delete_title)) },
            text = {
                Text(stringResource(R.string.home_delete_message))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.home_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            @Suppress("DEPRECATION")
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = stringResource(R.string.home_sort),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            OrdenMateria.entries.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(o.label) },
                                    onClick = {
                                        viewModel.updateOrden(o)
                                        showSortMenu = false
                                    },
                                    leadingIcon = if (orden == o) {
                                        { Text("✓") }
                                    } else null
                                )
                            }
                        }
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
                text = { Text(stringResource(R.string.home_fab_add)) },
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

            // --- Lista vacía (sin materias reales, no solo sin filtro) ---
            materias.isEmpty() && searchQuery.isBlank() && filtroSemestre == null -> {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onAddMateria = onNavigateToCreateMateria
                )
            }

            // --- Lista con materias (o resultado de búsqueda) ---
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── Barra de búsqueda ─────────────────────────
                    if (showSearch) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, stringResource(R.string.home_clear))
                                        }
                                    }
                                },
                                singleLine = true
                            )
                        }
                    }

                    // ── Filtro por semestre ────────────────────────
                    if (semestres.size > 1) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = filtroSemestre == null,
                                        onClick = { viewModel.updateFiltroSemestre(null) },
                                        label = { Text(stringResource(R.string.home_filter_all)) }
                                    )
                                }
                                items(semestres) { sem ->
                                    FilterChip(
                                        selected = filtroSemestre == sem,
                                        onClick = {
                                            viewModel.updateFiltroSemestre(
                                                if (filtroSemestre == sem) null else sem
                                            )
                                        },
                                        label = { Text(sem) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Dashboard rápido ──────────────────────────
                    if (promedioGeneral != null || materiasEnRiesgo.isNotEmpty()) {
                        item {
                            DashboardCard(
                                promedioGeneral = promedioGeneral,
                                materiasEnRiesgo = materiasEnRiesgo.size,
                                totalMaterias = materias.size
                            )
                        }
                    }

                    // ── Alerta de riesgo académico ────────────────
                    if (materiasEnRiesgo.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "⚠ ${materiasEnRiesgo.size} materia(s) en riesgo: " +
                                                materiasEnRiesgo.take(3).joinToString(", ") { it.nombre },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // ── Resultado de búsqueda vacío ───────────────
                    if (materias.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.home_no_results),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── Cards de materias ─────────────────────────
                    itemsIndexed(
                        items = materias,
                        key = { _, m -> m.id }
                    ) { index, materia ->
                        var showed by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 50L)
                            showed = true
                        }

                        AnimatedVisibility(
                            visible = showed,
                            enter = slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { it / 3 } + fadeIn(tween(400))
                        ) {
                            SwipeToDeleteWrapper(
                                onDelete = { viewModel.requestDelete(materia.id) }
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

/**
 * Mini-dashboard en la parte superior: promedio general y materias en riesgo.
 */
@Composable
private fun DashboardCard(
    promedioGeneral: Float?,
    materiasEnRiesgo: Int,
    totalMaterias: Int,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(dimens.iconSizeSmall)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_summary),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Promedio
                DashboardStat(
                    value = promedioGeneral?.let { GradeCalculator.display(it) } ?: "–",
                    label = stringResource(R.string.home_average),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Divider vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                )

                // Materias
                DashboardStat(
                    value = "$totalMaterias",
                    label = stringResource(R.string.home_subjects),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (materiasEnRiesgo > 0) {
                    // Divider vertical
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    )

                    DashboardStat(
                        value = "$materiasEnRiesgo",
                        label = stringResource(R.string.home_at_risk),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStat(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedText(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MateriaCard(
    materia: Materia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()
    val progreso = materia.porcentajeEvaluado.coerceIn(0f, 1f)
    val promedioColor = when {
        materia.aprobado -> MaterialTheme.colorScheme.secondary
        materia.promedio != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.cardElevation)
    ) {
        Column(modifier = Modifier.padding(dimens.cardPadding)) {
            // ── Header: nombre + nota ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = materia.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(materia.periodo)
                            materia.profesor?.let { append(" · $it") }
                            if (materia.creditos > 0) append(" · ${materia.creditos} cr")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Nota circular
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(promedioColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedText(
                            text = materia.promedioDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = promedioColor
                        )
                        Text(
                            text = "/${materia.escalaMax.toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Barra de progreso ──────────────────────────
            GradeLinearIndicator(
                progreso = progreso,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = promedioColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(6.dp))

            // ── Footer: estado + progreso % ────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (materia.promedio != null) {
                    EstadoBadge(
                        aprobado = materia.aprobado,
                        texto = if (materia.yaAprobo) stringResource(R.string.home_status_approved) else if (materia.aprobado) stringResource(R.string.home_status_passing) else stringResource(R.string.home_status_at_risk)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.home_not_evaluated),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(R.string.home_evaluated_pct, (progreso * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.home_welcome_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAddMateria,
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_add_first))
            }
        }
    }
}
