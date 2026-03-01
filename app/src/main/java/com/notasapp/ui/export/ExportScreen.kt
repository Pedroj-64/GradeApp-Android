package com.notasapp.ui.export

import android.app.Activity
import android.content.Intent
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

/**
 * Pantalla de exportación y sincronización de una materia.
 *
 * Soporta dos canales:
 * - **Excel (.xlsx)**: guarda el archivo en el almacenamiento local.
 * - **Google Sheets**: sincroniza vía API v4 con manejo de permisos OAuth2.
 *
 * Cuando la cuenta no ha concedido acceso a Sheets, se lanza el Intent de
 * recuperación OAuth2 y, al volver, se reintenta la sincronización.
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
    val uriHandler = LocalUriHandler.current
    val context    = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDriveDialog by remember { mutableStateOf(false) }

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

    // Launcher para el Intent de recuperación OAuth2 de Google
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onPermissionGranted()
        }
    }

    // Lanzar el Intent de recuperación automáticamente cuando aparece
    LaunchedEffect(uiState.userRecoverableIntent) {
        uiState.userRecoverableIntent?.let { intent ->
            permissionLauncher.launch(intent)
        }
    }

    // Mensajes de resultado
    LaunchedEffect(uiState.exportSuccess, uiState.exportError, uiState.syncError) {
        when {
            uiState.exportSuccess ->
                snackbarHostState.showSnackbar("✓ ¡Archivo guardado correctamente!")

            uiState.exportError != null ->
                snackbarHostState.showSnackbar("⚠ ${uiState.exportError}")

            uiState.syncError != null && uiState.userRecoverableIntent == null ->
                snackbarHostState.showSnackbar("⚠ ${uiState.syncError}")
        }
        viewModel.clearMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar / Sincronizar") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "¿Cómo deseas exportar\ntus notas?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // ── Tarjeta Excel ────────────────────────────────────────────────
            ExportCard(
                title = "Archivo Excel (.xlsx)",
                description = "Elige la carpeta donde guardar el archivo. Incluye fórmulas para recalcular el promedio automáticamente al modificar notas.",
                actionLabel = if (uiState.isExporting) null else if (uiState.exportSuccess) "Guardar de nuevo" else "Guardar como Excel",
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
                            text  = "¡Archivo guardado exitosamente en la ubicación elegida!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Tarjeta PDF ─────────────────────────────────────────────────
            ExportCard(
                title = "Resumen PDF",
                description = "Genera un PDF con el resumen completo de la materia: promedio, componentes, notas parciales y aportes.",
                actionLabel = if (uiState.isExporting) null else "Guardar como PDF",
                actionIcon = Icons.Default.PictureAsPdf,
                isLoading = uiState.isExporting,
                isDone = false,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onAction = {
                    createPdfLauncher.launch(viewModel.sugerirNombrePdf())
                }
            )

            HorizontalDivider()

            // ── Tarjeta Google Sheets ────────────────────────────────────────
            val syncedId = uiState.syncedSheetId
            if (uiState.syncSuccess && syncedId != null) {
                // Estado: sincronización exitosa — mostrar link al spreadsheet
                SyncSuccessCard(
                    spreadsheetId = syncedId,
                    onOpen = { id ->
                        uriHandler.openUri("https://docs.google.com/spreadsheets/d/$id")
                    },
                    onShare = { id ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT,
                                "https://docs.google.com/spreadsheets/d/$id")
                            putExtra(Intent.EXTRA_SUBJECT, "Mis notas en Google Sheets")
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Compartir link de Sheets")
                        )
                    },
                    onCopyLink = { id ->
                        clipboardManager.setText(AnnotatedString("https://docs.google.com/spreadsheets/d/$id"))
                    }
                )
            } else {
                ExportCard(
                    title = "Google Sheets",
                    description = "Guarda tus notas en un spreadsheet de tu Google Drive. Puedes abrirlo, compartirlo o moverlo a la carpeta que prefieras.",
                    actionLabel = when {
                        uiState.isSyncing -> null
                        uiState.syncError?.contains("permiso") == true ||
                                uiState.userRecoverableIntent != null -> "Conceder acceso a Sheets"
                        else -> "Guardar en Google Drive"
                    },
                    actionIcon = Icons.Default.Save,
                    isLoading = uiState.isSyncing,
                    isDone = false,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onAction = {
                        if (uiState.userRecoverableIntent != null ||
                            uiState.syncError?.contains("permiso") == true) {
                            viewModel.sincronizarSheets()
                        } else {
                            showDriveDialog = true
                        }
                    }
                )
            }
        }
    }

    // ── Diálogo de confirmación “Guardar en Google Drive” ──────────────────
    if (showDriveDialog) {
        AlertDialog(
            onDismissRequest = { showDriveDialog = false },
            icon = {
                Icon(
                    imageVector        = Icons.Default.Save,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Guardar en Google Drive") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Se creará (o actualizará) un spreadsheet en tu Google Drive con todas las notas de esta materia."
                    )
                    Text(
                        text  = "• El archivo quedará en la raíz de tu Drive.\n• Puedes moverlo a cualquier carpeta desde Google Drive.\n• Se actualizará cada vez que sincronices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDriveDialog = false
                    viewModel.sincronizarSheets()
                }) { Text("Guardar en Drive") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDriveDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ── Componentes internos ──────────────────────────────────────────────────────

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
                    Text("Completado")
                } else {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel ?: "Procesando…")
                }
            }
        }
    }
}

@Composable
private fun SyncSuccessCard(
    spreadsheetId: String,
    onOpen:  (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "¡Guardado en Google Drive!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tu spreadsheet fue creado/actualizado en Google Drive. Puedes abrirlo, compartirlo o moverlo a la carpeta que prefieras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = { onOpen(spreadsheetId) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abrir en Google Sheets")
            }
            OutlinedButton(onClick = { onCopyLink(spreadsheetId) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copiar enlace")
            }
            OutlinedButton(onClick = { onShare(spreadsheetId) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir enlace")
            }
        }
    }
}