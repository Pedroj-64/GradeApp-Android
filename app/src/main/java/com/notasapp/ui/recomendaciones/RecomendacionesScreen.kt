package com.notasapp.ui.recomendaciones

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.R
import com.notasapp.data.remote.ai.Recomendacion
import com.notasapp.data.remote.ai.TipoRecomendacion
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

// ── Metadata por tipo ──────────────────────────────────────────

private data class TipoMeta(
    val icon: ImageVector,
    val accentColor: Color,
    val label: String
)

@Composable
private fun tipoMeta(tipo: TipoRecomendacion): TipoMeta = when (tipo) {
    TipoRecomendacion.YOUTUBE -> TipoMeta(
        icon = Icons.Default.PlayCircle,
        accentColor = Color(0xFFEF5350),
        label = stringResource(R.string.recs_video)
    )
    TipoRecomendacion.LIBRO -> TipoMeta(
        icon = Icons.Default.AutoStories,
        accentColor = Color(0xFF7B1FA2),
        label = stringResource(R.string.recs_book)
    )
    TipoRecomendacion.RECURSO -> TipoMeta(
        icon = Icons.Default.Language,
        accentColor = Color(0xFF00897B),
        label = stringResource(R.string.recs_web)
    )
}

/**
 * Pantalla de recomendaciones de estudio generadas por Gemini AI.
 *
 * Diseño minimalista: chips de materia, tarjetas con acento lateral de
 * color segun el tipo, skeleton shimmer durante la carga y banners
 * de error/apikey compactos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecomendacionesScreen(
    onBack: () -> Unit,
    viewModel: RecomendacionesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val materias by viewModel.materias.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.recs_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.recs_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val sectionVideos = stringResource(R.string.recs_videos)
        val sectionBooks = stringResource(R.string.recs_books)
        val sectionWebResources = stringResource(R.string.recs_web_resources)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Selector de materias
            if (materias.isEmpty()) {
                item { EmptyMaterias() }
            } else {
                item {
                    MateriaSelector(
                        materias = materias.map { it.nombre to it.id },
                        selectedId = uiState.materiaSeleccionada?.id,
                        onSelect = { id ->
                            materias.find { it.id == id }?.let { viewModel.seleccionarMateria(it) }
                        }
                    )
                }
            }

            // API Key faltante
            if (uiState.apiKeyFaltante) {
                item { ApiKeyMissingBanner() }
            }

            // Cargando
            if (uiState.isLoading) {
                item {
                    LoadingBanner(materia = uiState.materiaSeleccionada?.nombre ?: "")
                }
                items(3) { i -> RecomendacionCardSkeleton(index = i) }
            }

            // Error
            if (!uiState.isLoading && uiState.error != null) {
                item {
                    ErrorBanner(
                        message = uiState.error!!,
                        onRetry = { viewModel.reintentar() }
                    )
                }
            }

            // Recomendaciones por tipo
            if (uiState.recomendaciones.isNotEmpty()) {
                val grupos = listOf(
                    TipoRecomendacion.YOUTUBE to sectionVideos,
                    TipoRecomendacion.LIBRO   to sectionBooks,
                    TipoRecomendacion.RECURSO to sectionWebResources
                )
                var globalIdx = 0
                grupos.forEach { (tipo, titulo) ->
                    val lista = uiState.recomendaciones.filter { it.tipo == tipo }
                    if (lista.isNotEmpty()) {
                        item(key = "header_$tipo") {
                            SectionDivider(tipo = tipo, title = titulo)
                        }
                        itemsIndexed(lista, key = { _, rec -> "${tipo}_${rec.url}" }) { i, rec ->
                            AnimatedRecomendacionCard(
                                recomendacion = rec,
                                index = globalIdx + i,
                                onOpenUrl = { url ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    }
                                }
                            )
                        }
                        globalIdx += lista.size
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.reintentar() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.recs_regenerate))
                    }
                }
            }

            // Placeholder: materia no seleccionada
            if (!uiState.isLoading
                && uiState.recomendaciones.isEmpty()
                && uiState.materiaSeleccionada == null
                && materias.isNotEmpty()
                && !uiState.apiKeyFaltante
            ) {
                item { SelectMateriaHint() }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Selector de materias ──────────────────────────────────────

@Composable
private fun MateriaSelector(
    materias: List<Pair<String, Long>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.recs_subject_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(materias) { (nombre, id) ->
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(id) },
                    label = {
                        Text(
                            text = nombre,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = if (selectedId == id) {
                        { Icon(Icons.Default.School, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

// ── Divisor de seccion ────────────────────────────────────────

@Composable
private fun SectionDivider(tipo: TipoRecomendacion, title: String) {
    val meta = tipoMeta(tipo)
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(meta.accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(meta.icon, null, tint = meta.accentColor, modifier = Modifier.size(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ── Card de recomendacion ─────────────────────────────────────

@Composable
private fun AnimatedRecomendacionCard(
    recomendacion: Recomendacion,
    index: Int,
    onOpenUrl: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) { it / 3 } + fadeIn(tween(280))
    ) {
        RecomendacionCard(recomendacion = recomendacion, onOpenUrl = onOpenUrl)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecomendacionCard(
    recomendacion: Recomendacion,
    onOpenUrl: (String) -> Unit
) {
    val meta = tipoMeta(recomendacion.tipo)

    Card(
        onClick = { onOpenUrl(recomendacion.url) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Acento lateral de color
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(meta.accentColor)
            )

            Spacer(Modifier.width(12.dp))

            // Icono tipo en caja redonda
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(meta.accentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(meta.icon, null, tint = meta.accentColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            // Texto
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    text = recomendacion.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                recomendacion.autor?.takeIf { it.isNotBlank() }?.let { autor ->
                    Text(
                        text = autor,
                        style = MaterialTheme.typography.labelSmall,
                        color = meta.accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Text(
                    text = recomendacion.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Flecha "abrir"
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.recs_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Skeleton de carga ─────────────────────────────────────────

@Composable
private fun RecomendacionCardSkeleton(index: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_$index")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, delayMillis = index * 160, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset_$index"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 300f, 200f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(shimmerBrush))
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(shimmerBrush)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.75f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
                Box(Modifier.fillMaxWidth(0.90f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            }
            Spacer(Modifier.width(16.dp))
        }
    }
}

// ── Banners de estado ─────────────────────────────────────────

@Composable
private fun LoadingBanner(materia: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = if (materia.isNotBlank()) "Analizando $materia…" else "Generando recomendaciones…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recs_error),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.recommendations_retry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ApiKeyMissingBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.VpnKey, null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.recs_key_needed),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.recs_key_setup_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ── Estados vacíos ────────────────────────────────────────────

@Composable
private fun SelectMateriaHint() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Lightbulb, null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Text(
                text = stringResource(R.string.recommendations_select_materia),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.recs_will_generate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyMaterias() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.School, null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Text(
                text = stringResource(R.string.recs_no_subjects),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.recs_no_subjects_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}