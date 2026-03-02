package com.notasapp.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.R
import com.notasapp.domain.model.ConfiguracionNota
import com.notasapp.domain.model.ModoRedondeo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de configuración y gestión de datos de la app.
 *
 * Secciones disponibles:
 * - **Datos y Backup**: exportar a JSON + restaurar desde archivo.
 * - **Acerca de**: versión e información del proyecto.
 *
 * @param onBack Callback para regresar al Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState          = viewModel.uiState.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }
    val context          = LocalContext.current

    // Navegar al Login cuando el logout se completa
    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLogout()
    }

    // SAF file picker para restaurar backup
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importarBackup(it) } }

    // Lanzar el share intent en cuanto esté disponible
    LaunchedEffect(uiState.shareIntent) {
        uiState.shareIntent?.let { intent ->
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_share_backup)))
            viewModel.clearMessages()
        }
    }

    // Mostrar mensajes de resultado
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        val msg = uiState.successMessage ?: uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Sección: Datos y Backup ───────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_data_backup)) {

                SettingsActionCard(
                    icon      = Icons.Default.Backup,
                    title     = stringResource(R.string.settings_export_backup),
                    subtitle  = uiState.lastSyncMs?.let {
                        "Última exportación: ${formatDate(it)}"
                    } ?: stringResource(R.string.settings_export_subtitle),
                    ctaLabel  = stringResource(R.string.settings_export_cta),
                    isLoading = uiState.isLoading,
                    onClick   = { viewModel.exportarBackup() }
                )

                SettingsActionCard(
                    icon        = Icons.Default.CloudDownload,
                    title       = stringResource(R.string.settings_restore_backup),
                    subtitle    = stringResource(R.string.settings_restore_subtitle),
                    ctaLabel    = stringResource(R.string.settings_select_file),
                    isLoading   = false,
                    onClick     = {
                        openFileLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }

            HorizontalDivider()

            // ── Sección: Notas y Redondeo ──────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_notes_rounding)) {
                RedondeoConfigCard(
                    config = uiState.configuracionNota,
                    onConfigChange = { viewModel.updateConfiguracionNota(it) }
                )
            }

            HorizontalDivider()

            // ── Sección: Idioma ───────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_language)) {
                LanguageSelectorCard()
            }

            HorizontalDivider()
            // ── Sección: Cuenta ───────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_account)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Logout,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = stringResource(R.string.settings_logout),
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text  = stringResource(R.string.settings_back_to_login),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.showLogoutDialog() },
                                colors  = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.settings_exit))
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            // ── Sección: Acerca de ────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_about)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector        = Icons.Default.Info,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text       = stringResource(R.string.app_name),
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text  = stringResource(R.string.settings_version, "2.0.0"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text  = stringResource(R.string.settings_app_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text       = "Desarrollado por Pedro Jose Soto Rivera,MargaDev-Society y Asociados",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = "Agradecimientos especiales a los testers y colaboradores que hicieron posible esta app. \n\n¡Gracias por usar Gradify! \n\n Dedicado a miripili <3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Diálogo de confirmación de logout ──────────────────────────────────────────
    if (uiState.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutDialog() },
            icon = {
                Icon(
                    imageVector        = Icons.Default.Logout,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.settings_logout)) },
            text  = {
                Text(
                    stringResource(R.string.settings_logout_confirm)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.settings_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogoutDialog() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

// ── Componentes internos ──────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title:   String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.labelLarge,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(bottom = 2.dp)
        )
        content()
    }
}

@Composable
private fun SettingsActionCard(
    icon:      ImageVector,
    title:     String,
    subtitle:  String,
    ctaLabel:  String,
    isLoading: Boolean,
    onClick:   () -> Unit,
    modifier:  Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                OutlinedButton(onClick = onClick) {
                    Text(ctaLabel)
                }
            }
        }
    }
}

// ── Selector de idioma ─────────────────────────────────────────────────────

private data class LanguageOption(val tag: String, val label: String)

@Composable
private fun LanguageSelectorCard(modifier: Modifier = Modifier) {
    val systemLabel = stringResource(R.string.settings_language_system)
    val options = remember(systemLabel) {
        listOf(
            LanguageOption("",   systemLabel),
            LanguageOption("es", "Español"),
            LanguageOption("en", "English")
        )
    }

    // Resolve current selection
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.language ?: ""

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))

            options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val locales = if (option.tag.isEmpty()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(option.tag)
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = currentTag == option.tag,
                        onClick = {
                            val locales = if (option.tag.isEmpty()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(option.tag)
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}private fun formatDate(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

// ── Configuración de redondeo ──────────────────────────────────────────────

@Composable
private fun RedondeoConfigCard(
    config: ConfiguracionNota,
    onConfigChange: (ConfiguracionNota) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_rounding_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))

            // Decimales
            Text(
                text = stringResource(R.string.settings_decimals),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { dec ->
                    OutlinedButton(
                        onClick = { onConfigChange(config.copy(decimales = dec)) },
                        colors = if (config.decimales == dec)
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("$dec")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Modo de redondeo
            Text(
                text = stringResource(R.string.settings_rounding_mode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            ModoRedondeo.entries.forEach { modo ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfigChange(config.copy(modoRedondeo = modo)) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = config.modoRedondeo == modo,
                        onClick = { onConfigChange(config.copy(modoRedondeo = modo)) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = modo.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
