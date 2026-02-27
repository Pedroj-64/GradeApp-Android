package com.notasapp.ui.stats

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.domain.model.Materia
import com.notasapp.ui.components.PromedioGauge
import kotlinx.coroutines.delay

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
                title = { Text("Estadísticas del semestre") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
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
                        titulo = "Mejor rendimiento",
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
                        titulo = "Necesita atención",
                        materias = stats.materiasPeorNota,
                        esPositivo = false
                    )
                }
            }
        }
    }
}

// ── Gauge promedio general ──────────────────────────────────────────────────

@Composable
private fun PromedioGeneralCard(
    promedioGeneral: Float?,
    totalMaterias: Int,
    modifier: Modifier = Modifier
) {
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
                text = "Promedio general",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            PromedioGauge(
                promedio = promedioGeneral ?: 0f,
                escalaMax = 10f,
                notaAprobacion = 6f,
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (promedioGeneral != null) "${"%.2f".format(promedioGeneral)}" else "Sin notas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$totalMaterias materia(s) registrada(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            "Estado de materias",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstadoTile(
                label = "Aprobadas",
                count = aprobadas,
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            EstadoTile(
                label = "En riesgo",
                count = enRiesgo,
                icon = Icons.Default.Warning,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstadoTile(
                label = "Reprobadas",
                count = reprobadas,
                icon = Icons.Default.Error,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                onContainerColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            EstadoTile(
                label = "Sin notas",
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
                text = "Sin estadísticas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Agrega materias y empieza a ingresar notas para ver tu resumen del semestre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
