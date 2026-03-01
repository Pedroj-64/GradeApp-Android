package com.notasapp.ui.recomendaciones

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.data.remote.ai.Recomendacion
import com.notasapp.data.remote.ai.TipoRecomendacion
import kotlinx.coroutines.delay

/**
 * Pantalla de recomendaciones de estudio basadas en IA.
 *
 * Permite al usuario seleccionar una materia y recibir recomendaciones
 * personalizadas de videos de YouTube, libros y recursos web generadas
 * por Gemini AI.
 *
 * @param onBack Callback para volver atrás.
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
                    Text(
                        text = "Recomendaciones",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Encabezado ──────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Recomendaciones con IA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Selecciona una materia y obtén videos, libros y recursos de estudio personalizados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── Selector de materias ────────────────────────────
            if (materias.isEmpty()) {
                item {
                    EmptyMaterias()
                }
            } else {
                item {
                    Text(
                        text = "Elige una materia:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(materias) { materia ->
                            FilterChip(
                                selected = uiState.materiaSeleccionada?.id == materia.id,
                                onClick = { viewModel.seleccionarMateria(materia) },
                                label = { Text(materia.nombre) },
                                leadingIcon = if (uiState.materiaSeleccionada?.id == materia.id) {
                                    {
                                        Icon(
                                            Icons.Default.School,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // ── Estado: API Key faltante ────────────────────────
            if (uiState.apiKeyFaltante) {
                item {
                    ApiKeyMissingCard()
                }
            }

            // ── Estado: Loading ─────────────────────────────────
            if (uiState.isLoading) {
                item {
                    LoadingRecomendaciones()
                }
            }

            // ── Estado: Error con retry ─────────────────────────
            if (uiState.error != null && !uiState.isLoading) {
                item {
                    ErrorCard(
                        message = uiState.error!!,
                        onRetry = { viewModel.reintentar() }
                    )
                }
            }

            // ── Recomendaciones ─────────────────────────────────
            if (uiState.recomendaciones.isNotEmpty()) {
                // Videos de YouTube
                val videos = uiState.recomendaciones.filter { it.tipo == TipoRecomendacion.YOUTUBE }
                if (videos.isNotEmpty()) {
                    item {
                        SectionHeader(
                            icon = Icons.Default.PlayCircle,
                            title = "Videos de YouTube"
                        )
                    }
                    itemsIndexed(videos, key = { i, r -> "yt_$i" }) { index, rec ->
                        AnimatedRecomendacionCard(
                            recomendacion = rec,
                            index = index,
                            onOpenUrl = { url ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            }
                        )
                    }
                }

                // Libros
                val libros = uiState.recomendaciones.filter { it.tipo == TipoRecomendacion.LIBRO }
                if (libros.isNotEmpty()) {
                    item {
                        SectionHeader(
                            icon = Icons.Default.Book,
                            title = "Libros recomendados"
                        )
                    }
                    itemsIndexed(libros, key = { i, r -> "lib_$i" }) { index, rec ->
                        AnimatedRecomendacionCard(
                            recomendacion = rec,
                            index = index + videos.size,
                            onOpenUrl = { url ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            }
                        )
                    }
                }

                // Recursos web
                val recursos = uiState.recomendaciones.filter { it.tipo == TipoRecomendacion.RECURSO }
                if (recursos.isNotEmpty()) {
                    item {
                        SectionHeader(
                            icon = Icons.Default.Language,
                            title = "Recursos web"
                        )
                    }
                    itemsIndexed(recursos, key = { i, r -> "res_$i" }) { index, rec ->
                        AnimatedRecomendacionCard(
                            recomendacion = rec,
                            index = index + videos.size + libros.size,
                            onOpenUrl = { url ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            }
                        )
                    }
                }

                // Botón de refrescar
                item {
                    OutlinedButton(
                        onClick = { viewModel.reintentar() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generar nuevas recomendaciones")
                    }
                }
            }

            // ── Espaciado final ─────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Componentes internos ──────────────────────────────────────

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AnimatedRecomendacionCard(
    recomendacion: Recomendacion,
    index: Int,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        showed = true
    }

    AnimatedVisibility(
        visible = showed,
        enter = slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300))
    ) {
        RecomendacionCard(
            recomendacion = recomendacion,
            onOpenUrl = onOpenUrl,
            modifier = modifier
        )
    }
}

@Composable
private fun RecomendacionCard(
    recomendacion: Recomendacion,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, icon) = when (recomendacion.tipo) {
        TipoRecomendacion.YOUTUBE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) to Icons.Default.PlayCircle
        TipoRecomendacion.LIBRO -> MaterialTheme.colorScheme.tertiaryContainer to Icons.Default.AutoStories
        TipoRecomendacion.RECURSO -> MaterialTheme.colorScheme.secondaryContainer to Icons.Default.Language
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = { onOpenUrl(recomendacion.url) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(36.dp)
                    .padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recomendacion.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                recomendacion.autor?.let { autor ->
                    Text(
                        text = autor,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = recomendacion.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = "Abrir",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun LoadingRecomendaciones(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            Text(
                text = "Analizando tu materia con IA...",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Buscando los mejores videos, libros y recursos para ti",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚠️ Error al generar recomendaciones",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun ApiKeyMissingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Configuración necesaria",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = "Para usar las recomendaciones con IA necesitas una clave de Gemini (gratuita):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "1. Ve a aistudio.google.com/app/apikey\n2. Crea una API Key gratuita\n3. Agrégala en local.properties:\n   GEMINI_API_KEY=tu_clave_aquí\n4. Recompila la app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun EmptyMaterias(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = "Agrega materias primero",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Una vez que tengas materias registradas, podrás obtener recomendaciones personalizadas de estudio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
