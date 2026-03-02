package com.notasapp.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.R
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.Semestre
import com.notasapp.ui.components.PromedioGauge
import kotlinx.coroutines.delay

/** CompositionLocal holder to pass stats down without threading params. */
private val LocalStatsHolder = androidx.compose.runtime.staticCompositionLocalOf<EstadisticasSemestre?> { null }

/**
 * Pantalla de estadísticas del semestre.
 *
 * Muestra un resumen visual del desempeño en todas las materias:
 * gauge de promedio general, contadores por estado (aprobado / en riesgo / reprobado),
 * y listas de mejores/peores materias.
 *
 * @param onNavigateBack  Callback para volver atrás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstadisticasScreen(
    onNavigateBack: () -> Unit,
    viewModel: EstadisticasViewModel = hiltViewModel()
) {
    val stats by viewModel.estadisticas.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (stats.totalMaterias == 0) {
            EmptyEstadisticas(Modifier.padding(paddingValues))
        } else {
            EstadisticasContent(
                stats = stats,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// ── Contenido principal ──────────────────────────────────────────────────────

@Composable
private fun EstadisticasContent(
    stats: EstadisticasSemestre,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        visible = true
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalStatsHolder provides stats) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gauge + promedio general
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 3 }
            ) {
                PromedioGeneralCard(
                    promedioGeneral = stats.promedioGeneral,
                    totalMaterias = stats.totalMaterias
                )
            }
        }

        // Contadores de estado
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 3 }
            ) {
                EstadoGrid(
                    aprobadas = stats.aprobadas,
                    enRiesgo = stats.enRiesgo,
                    reprobadas = stats.reprobadas,
                    sinNotas = stats.sinNotas
                )
            }
        }

        // Top materias
        if (stats.materiasMejorNota.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -it / 3 }
                ) {
                    MateriaRankingCard(
                        titulo = stringResource(R.string.stats_best),
                        materias = stats.materiasMejorNota,
                        esPositivo = true
                    )
                }
            }
        }

        if (stats.materiasPeorNota.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { -it / 3 }
                ) {
                    MateriaRankingCard(
                        titulo = stringResource(R.string.stats_needs_attention),
                        materias = stats.materiasPeorNota,
                        esPositivo = false
                    )
                }
            }
        }

        // Gráfico de barras de rendimiento
        if (stats.barrasRendimiento.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -it / 3 }
                ) {
                    RendimientoBarChart(
                        datos = stats.barrasRendimiento,
                        escalaMax = stats.materiasMejorNota.firstOrNull()?.escalaMax ?: 5f
                    )
                }
            }
        }

        // Gráfico de evolución por semestre (línea)
        if (stats.semestres.size > 1) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(850)) + slideInVertically(tween(850)) { -it / 3 }
                ) {
                    SemesterEvolutionChart(semestres = stats.semestres)
                }
            }
        }

        // Historial por semestre
        if (stats.semestres.size > 1) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(900)) + slideInVertically(tween(900)) { -it / 3 }
                ) {
                    SemestresHistorialCard(semestres = stats.semestres)
                }
            }
        }
    }
    } // CompositionLocalProvider
}

// ── Gauge promedio general ──────────────────────────────────────────────────

@Composable
private fun PromedioGeneralCard(
    promedioGeneral: Float?,
    totalMaterias: Int,
    modifier: Modifier = Modifier
) {
    val stats = (LocalStatsHolder.current)
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.stats_general_avg),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            PromedioGauge(
                promedio = promedioGeneral ?: 0f,
                escalaMin = 0f,
                escalaMax = 10f,
                aprobacion = 6f,
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (promedioGeneral != null) "${"%.2f".format(promedioGeneral)}" else stringResource(R.string.stats_no_grades),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Promedio ponderado por créditos
            if (stats?.promedioPonderado != null && stats.promedioPonderado != promedioGeneral) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ponderado por créditos: ${"%.2f".format(stats.promedioPonderado)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = "$totalMaterias materia(s) registrada(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (stats != null && stats.totalCreditos > 0) {
                Text(
                    text = "${stats.creditosAprobados}/${stats.totalCreditos} créditos aprobados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Grid de estados ──────────────────────────────────────────────────────────

@Composable
private fun EstadoGrid(
    aprobadas: Int,
    enRiesgo: Int,
    reprobadas: Int,
    sinNotas: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.stats_subject_status),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstadoTile(
                label = stringResource(R.string.stats_passing),
                count = aprobadas,
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            EstadoTile(
                label = stringResource(R.string.stats_at_risk),
                count = enRiesgo,
                icon = Icons.Default.Warning,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstadoTile(
                label = stringResource(R.string.stats_failing),
                count = reprobadas,
                icon = Icons.Default.Error,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                onContainerColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            @Suppress("DEPRECATION")
            EstadoTile(
                label = stringResource(R.string.stats_no_grades),
                count = sinNotas,
                icon = Icons.Default.HelpOutline,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onContainerColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EstadoTile(
    label: String,
    count: Int,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onContainerColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainerColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Ranking de materias ──────────────────────────────────────────────────────

@Composable
private fun MateriaRankingCard(
    titulo: String,
    materias: List<Materia>,
    esPositivo: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            materias.forEachIndexed { index, materia ->
                MateriaRankingRow(materia = materia, rank = index + 1, esPositivo = esPositivo)
            }
        }
    }
}

@Composable
private fun MateriaRankingRow(
    materia: Materia,
    rank: Int,
    esPositivo: Boolean
) {
    val promedio   = materia.promedio ?: return
    val progress   = (promedio / materia.escalaMax).coerceIn(0f, 1f)
    val trackColor = if (esPositivo)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.errorContainer

    val fillColor  = if (esPositivo)
        MaterialTheme.colorScheme.secondary
    else
        MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$rank.",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = materia.nombre,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${"%.2f".format(promedio)} / ${"%.0f".format(materia.escalaMax)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = fillColor,
                trackColor = trackColor
            )
        }
    }
}

// ── Gráfico de barras de rendimiento ─────────────────────────────────────────

@Composable
private fun RendimientoBarChart(
    datos: List<Pair<String, Float>>,
    escalaMax: Float,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_performance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((datos.size * 40 + 16).dp)
            ) {
                val barHeight = 24.dp.toPx()
                val spacing = 16.dp.toPx()
                val leftMargin = 0f
                val maxWidth = size.width - leftMargin

                datos.forEachIndexed { index, (nombre, valor) ->
                    val y = index * (barHeight + spacing)
                    val progress = (valor / escalaMax).coerceIn(0f, 1f)

                    // Track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(leftMargin, y),
                        size = Size(maxWidth, barHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )

                    // Bar
                    if (progress > 0f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(leftMargin, y),
                            size = Size(maxWidth * progress, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }

                    // Text
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = textColor.hashCode()
                            textSize = 11.sp.toPx()
                            isAntiAlias = true
                        }
                        val label = if (nombre.length > 15) "${nombre.take(13)}.." else nombre
                        drawText(
                            "$label: ${"%.2f".format(valor)}",
                            leftMargin + 8.dp.toPx(),
                            y + barHeight * 0.7f,
                            paint
                        )
                    }
                }
            }
        }
    }
}

// ── Gráfico de evolución por semestre (línea) ─────────────────────────────────

@Composable
private fun SemesterEvolutionChart(
    semestres: List<Semestre>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    // Filter semestres that have a promedio
    val dataSemestres = semestres.filter { it.promedioSimple != null }
    if (dataSemestres.size < 2) return

    val promedios = dataSemestres.map { it.promedioSimple!! }
    val maxVal = promedios.max().coerceAtLeast(5f)
    val minVal = 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_evolution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val leftPadding = 40.dp.toPx()
                val rightPadding = 16.dp.toPx()
                val topPadding = 12.dp.toPx()
                val bottomPadding = 32.dp.toPx()

                val chartWidth = size.width - leftPadding - rightPadding
                val chartHeight = size.height - topPadding - bottomPadding
                val range = (maxVal - minVal).coerceAtLeast(1f)

                val pointCount = dataSemestres.size
                val stepX = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth

                // Y axis grid lines (4 lines)
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val yVal = minVal + (range / gridSteps) * i
                    val y = topPadding + chartHeight - (chartHeight * ((yVal - minVal) / range))
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPadding, y),
                        end = Offset(size.width - rightPadding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Y axis label
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.1f".format(yVal),
                        4.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = textColor.hashCode()
                            textSize = 10.sp.toPx()
                            isAntiAlias = true
                        }
                    )
                }

                // Calculate points
                val points = dataSemestres.mapIndexed { index, semestre ->
                    val x = leftPadding + index * stepX
                    val normalized = ((semestre.promedioSimple!! - minVal) / range).coerceIn(0f, 1f)
                    val y = topPadding + chartHeight - (chartHeight * normalized)
                    Offset(x, y)
                }

                // Fill area under the line
                if (points.size >= 2) {
                    val fillPath = Path().apply {
                        moveTo(points.first().x, topPadding + chartHeight)
                        lineTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                        lineTo(points.last().x, topPadding + chartHeight)
                        close()
                    }
                    drawPath(fillPath, fillColor)
                }

                // Draw line
                if (points.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Draw dots and X axis labels
                points.forEachIndexed { index, point ->
                    // Dot
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = dotColor,
                        radius = 4.dp.toPx(),
                        center = point
                    )

                    // X label (semester name)
                    val label = dataSemestres[index].periodo
                    val shortLabel = if (label.length > 7) label.takeLast(7) else label
                    drawContext.canvas.nativeCanvas.drawText(
                        shortLabel,
                        point.x - 16.dp.toPx(),
                        size.height - 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = textColor.hashCode()
                            textSize = 9.sp.toPx()
                            isAntiAlias = true
                        }
                    )
                }
            }
        }
    }
}

// ── Historial de semestres ───────────────────────────────────────────────────

@Composable
private fun SemestresHistorialCard(
    semestres: List<Semestre>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            semestres.forEach { semestre ->
                SemestreRow(semestre = semestre)
            }
        }
    }
}

@Composable
private fun SemestreRow(semestre: Semestre) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = semestre.periodo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${semestre.totalMaterias} materias · ${semestre.totalCreditos} créditos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = semestre.promedioPonderadoDisplay,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${semestre.aprobadas}/${semestre.materiasConNotas.size} aprobadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Estado vacío ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyEstadisticas(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.stats_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
