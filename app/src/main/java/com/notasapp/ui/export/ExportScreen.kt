package com.notasapp.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import com.notasapp.R

/**
 * Pantalla de exportación de una materia.
 *
 * Soporta tres canales de guardado (todos vía SAF):
 * - **Excel (.xlsx)**: guarda en el almacenamiento local o Drive.
 * - **PDF (.pdf)**: genera un PDF con el resumen de notas.
 * - **Google Drive**: reutiliza el Excel export con SAF — el usuario
 *   puede elegir Google Drive como destino en el selector del sistema.
 *
 * @param materiaId ID de la materia a exportar.
 * @param onBack    Vuelve al detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    materiaId: Long,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context    = LocalContext.current

    // Launcher SAF: el usuario elige dónde guardar el .xlsx
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        uri?.let { viewModel.exportarExcelSAF(it) }
    }

    // Launcher SAF: el usuario elige dónde guardar el .pdf
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.exportarPdfSAF(it) }
    }

    // Mensajes de resultado
    LaunchedEffect(uiState.exportSuccess, uiState.exportError) {
        when {
            uiState.exportSuccess ->
                snackbarHostState.showSnackbar(context.getString(R.string.export_file_saved))

            uiState.exportError != null ->
                snackbarHostState.showSnackbar("⚠ ${uiState.exportError}")
        }
        viewModel.clearMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.export_how),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // ── Tarjeta Excel ────────────────────────────────────────────────
            ExportCard(
                title = stringResource(R.string.export_excel_title),
                description = stringResource(R.string.export_excel_desc),
                actionLabel = if (uiState.isExporting) null else if (uiState.exportSuccess) stringResource(R.string.export_resave) else stringResource(R.string.export_save_excel),
                actionIcon = Icons.Default.FileDownload,
                isLoading = uiState.isExporting,
                isDone = false,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onAction = {
                    createDocumentLauncher.launch(viewModel.sugerirNombreArchivo())
                }
            )

            // Mensaje de éxito tras guardar
            if (uiState.exportSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.secondary,
                            modifier           = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text  = stringResource(R.string.export_saved_ok),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Tarjeta PDF ─────────────────────────────────────────────────
            ExportCard(
                title = stringResource(R.string.export_pdf_title),
                description = stringResource(R.string.export_pdf_desc),
                actionLabel = if (uiState.isExporting) null else stringResource(R.string.export_save_pdf),
                actionIcon = Icons.Default.PictureAsPdf,
                isLoading = uiState.isExporting,
                isDone = false,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onAction = {
                    createPdfLauncher.launch(viewModel.sugerirNombrePdf())
                }
            )

            HorizontalDivider()

            // ── Tarjeta Google Drive (vía SAF) ──────────────────────────────
            ExportCard(
                title = stringResource(R.string.export_drive_title),
                description = stringResource(R.string.export_drive_desc),
                actionLabel = if (uiState.isExporting) null else stringResource(R.string.export_save_drive),
                actionIcon = Icons.Default.CloudUpload,
                isLoading = uiState.isExporting,
                isDone = false,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onAction = {
                    // Abre el selector SAF: Google Drive aparece como opcion
                    createDocumentLauncher.launch(viewModel.sugerirNombreArchivo())
                }
            )
        }
    }
}


@Composable
private fun ExportCard(
    title: String,
    description: String,
    actionLabel: String?,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    isDone: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else if (isDone) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_completed))
                } else {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel ?: "Procesando…")
                }
            }
        }
    }
}