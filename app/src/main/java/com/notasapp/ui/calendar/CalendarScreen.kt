package com.notasapp.ui.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.notasapp.R
import com.notasapp.domain.model.ExamenEvent
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.TipoEvento
import com.notasapp.ui.components.NotificationPermissionFlow
import com.notasapp.utils.getDisplayName
import com.notasapp.utils.getRecordatorioDisplay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pantalla de calendario académico con vista mensual.
 *
 * Muestra un grid de días con indicadores de eventos (exámenes, tareas, etc.),
 * permite crear, editar y eliminar eventos, y programa recordatorios.
 */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val materias by viewModel.materias.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val calendarAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onGoogleCalendarAuthResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.googleCalendarRecoveryIntent) {
        val intent = uiState.googleCalendarRecoveryIntent ?: return@LaunchedEffect
        viewModel.onGoogleCalendarRecoveryIntentConsumed()
        calendarAuthLauncher.launch(intent)
    }

    LaunchedEffect(uiState.successMessage, uiState.error) {
        val msg = uiState.successMessage ?: uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessages()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón para importar de Google Calendar
                FloatingActionButton(
                    onClick = { viewModel.showGoogleCalendarDialog() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (uiState.isImportingGoogleCalendar) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.calendar_import_gcal),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                // Botón principal para crear evento
                FloatingActionButton(
                    onClick = { viewModel.showCreateDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.calendar_add_event))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Header: mes + navegación ────────────────────────────────
            MonthHeader(
                currentMonth = uiState.currentMonth,
                onPrevious = viewModel::goToPreviousMonth,
                onNext = viewModel::goToNextMonth,
                onToday = viewModel::goToToday
            )

            // ── Grid del calendario ─────────────────────────────────────
            CalendarGrid(
                currentMonth = uiState.currentMonth,
                selectedDate = uiState.selectedDate,
                events = uiState.events,
                onDateSelected = viewModel::selectDate
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Eventos del día seleccionado ────────────────────────────
            DayEventsList(
                selectedDate = uiState.selectedDate,
                events = uiState.eventsForSelectedDate,
                onEdit = viewModel::showEditDialog,
                onDelete = viewModel::deleteEvent,
                onAddEvent = { viewModel.showCreateDialog(uiState.selectedDate) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // ── Diálogos ────────────────────────────────────────────────────────
    if (uiState.showCreateDialog || uiState.showEditDialog) {
        EventFormDialog(
            isEdit = uiState.showEditDialog,
            formState = formState,
            materias = materias,
            onUpdateTitulo = { t -> viewModel.updateForm { copy(titulo = t) } },
            onUpdateDescripcion = { d -> viewModel.updateForm { copy(descripcion = d) } },
            onUpdateTipo = { t -> viewModel.updateForm { copy(tipoEvento = t) } },
            onUpdateMateria = { id -> viewModel.updateForm { copy(materiaId = id) } },
            onUpdateRecordatorio = { r -> viewModel.onReminderSelected(r) },
            onUpdateFecha = { f -> viewModel.updateForm { copy(fecha = f) } },
            onUpdateHora = { h -> viewModel.updateForm { copy(hora = h) } },
            onSave = viewModel::saveEvent,
            onDismiss = viewModel::dismissDialog
        )
    }

    // ── Diálogo de explicación de permiso de notificaciones ──────────
    if (uiState.showNotificationPermissionFlow) {
        NotificationPermissionFlow(
            onResult = viewModel::onNotificationPermissionResult,
            onDismiss = viewModel::dismissNotificationPermission
        )
    }

    // ── Diálogo de importación de Google Calendar ──────────────────
    if (uiState.showGoogleCalendarDialog) {
        GoogleCalendarImportDialog(
            materias = materias,
            onImport = { materiaId -> viewModel.importFromGoogleCalendar(materiaId) },
            onDismiss = viewModel::dismissGoogleCalendarDialog
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Componentes internos
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun MonthHeader(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, stringResource(R.string.calendar_prev_month))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currentMonth.year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            IconButton(onClick = onToday) {
                Icon(Icons.Default.Today, stringResource(R.string.calendar_go_today),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.calendar_next_month))
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<ExamenEvent>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysOfWeek = listOf(
        stringResource(R.string.calendar_day_mon),
        stringResource(R.string.calendar_day_tue),
        stringResource(R.string.calendar_day_wed),
        stringResource(R.string.calendar_day_thu),
        stringResource(R.string.calendar_day_fri),
        stringResource(R.string.calendar_day_sat),
        stringResource(R.string.calendar_day_sun)
    )
    val today = LocalDate.now()

    // Calcular celdas del grid
    val firstDay = currentMonth.atDay(1)
    val firstDayOfWeek = firstDay.dayOfWeek.value // 1=Monday ... 7=Sunday
    val daysInMonth = currentMonth.lengthOfMonth()
    val leadingEmptyDays = firstDayOfWeek - 1 // Monday = 0 leading days
    val totalCells = leadingEmptyDays + daysInMonth

    // Map de fechas con eventos
    val eventDates = events.groupBy { it.fecha }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Días de la semana header
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Grid de días — render week by week
        val weeks = (0 until totalCells).chunked(7)
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cellIndex ->
                    val dayNumber = cellIndex - leadingEmptyDays + 1
                    if (dayNumber < 1 || dayNumber > daysInMonth) {
                        // Empty cell
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        val date = currentMonth.atDay(dayNumber)
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val dayEvents = eventDates[date] ?: emptyList()

                        DayCell(
                            day = dayNumber,
                            isToday = isToday,
                            isSelected = isSelected,
                            eventCount = dayEvents.size,
                            eventTypes = dayEvents.map { it.tipoEvento }.distinct(),
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Pad remaining cells in the last week
                val remaining = 7 - week.size
                repeat(remaining) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    eventTypes: List<TipoEvento>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                fontSize = 14.sp
            )
            if (eventCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    val dots = eventTypes.take(3)
                    dots.forEach { tipo ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .padding(horizontal = 0.5.dp)
                                .clip(CircleShape)
                                .background(eventTypeColor(tipo))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEventsList(
    selectedDate: LocalDate,
    events: List<ExamenEvent>,
    onEdit: (ExamenEvent) -> Unit,
    onDelete: (ExamenEvent) -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedDate.format(
                    DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
                ).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (events.isEmpty()) {
                TextButton(onClick = onAddEvent) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.detail_add))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.calendar_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        onEdit = { onEdit(event) },
                        onDelete = { onDelete(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: ExamenEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de color por tipo
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(eventTypeColor(event.tipoEvento))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${event.tipoEvento.emoji} ${event.titulo}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = event.horaDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.descripcion.isNotBlank()) {
                    Text(
                        text = event.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = getRecordatorioDisplay(context, event.recordatorioMinutos),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, stringResource(R.string.calendar_edit), Modifier.size(18.dp))
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete, stringResource(R.string.btn_delete),
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.calendar_delete_title)) },
            text = { Text(stringResource(R.string.calendar_delete_confirm)) },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Diálogo de formulario
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventFormDialog(
    isEdit: Boolean,
    formState: EventFormState,
    materias: List<Materia>,
    onUpdateTitulo: (String) -> Unit,
    onUpdateDescripcion: (String) -> Unit,
    onUpdateTipo: (TipoEvento) -> Unit,
    onUpdateMateria: (Long) -> Unit,
    onUpdateRecordatorio: (Int) -> Unit,
    onUpdateFecha: (LocalDate) -> Unit,
    onUpdateHora: (java.time.LocalTime) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tipoExpanded by remember { mutableStateOf(false) }
    var materiaExpanded by remember { mutableStateOf(false) }
    var recordatorioExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) stringResource(R.string.calendar_edit_event) else stringResource(R.string.calendar_new_event)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Título
                OutlinedTextField(
                    value = formState.titulo,
                    onValueChange = onUpdateTitulo,
                    label = { Text(stringResource(R.string.calendar_event_title_hint)) },
                    placeholder = { Text(stringResource(R.string.calendar_event_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Tipo de evento
                ExposedDropdownMenuBox(
                    expanded = tipoExpanded,
                    onExpandedChange = { tipoExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${formState.tipoEvento.emoji} ${formState.tipoEvento.getDisplayName(context)}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.calendar_event_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tipoExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = tipoExpanded,
                        onDismissRequest = { tipoExpanded = false }
                    ) {
                        TipoEvento.entries.forEach { tipo ->
                            DropdownMenuItem(
                                text = { Text("${tipo.emoji} ${tipo.getDisplayName(context)}") },
                                onClick = {
                                    onUpdateTipo(tipo)
                                    tipoExpanded = false
                                }
                            )
                        }
                    }
                }

                // Materia
                ExposedDropdownMenuBox(
                    expanded = materiaExpanded,
                    onExpandedChange = { materiaExpanded = it }
                ) {
                    val selectedMateria = materias.find { it.id == formState.materiaId }
                    OutlinedTextField(
                        value = selectedMateria?.nombre ?: stringResource(R.string.calendar_select_materia),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.calendar_event_materia)) },
                        isError = formState.materiaId == null && formState.titulo.isNotBlank(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(materiaExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = materiaExpanded,
                        onDismissRequest = { materiaExpanded = false }
                    ) {
                        materias.forEach { materia ->
                            DropdownMenuItem(
                                text = { Text(materia.nombre) },
                                onClick = {
                                    onUpdateMateria(materia.id)
                                    materiaExpanded = false
                                }
                            )
                        }
                    }
                }

                // Fecha — botón que muestra el valor actual
                OutlinedButton(
                    onClick = {
                        // Simple: usar días +/- para navegar
                        // En una implementación completa usarías un DatePicker
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Event, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formState.fecha.let {
                            "${it.dayOfMonth}/${it.monthValue}/${it.year}"
                        }
                    )
                }

                // Navegación simple de fecha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { onUpdateFecha(formState.fecha.minusDays(1)) }
                    ) { Text(stringResource(R.string.calendar_prev_day)) }
                    OutlinedButton(
                        onClick = { onUpdateFecha(LocalDate.now()) }
                    ) { Text(stringResource(R.string.calendar_today)) }
                    OutlinedButton(
                        onClick = { onUpdateFecha(formState.fecha.plusDays(1)) }
                    ) { Text(stringResource(R.string.calendar_next_day)) }
                }

                // Hora
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.calendar_hour_label), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { onUpdateHora(formState.hora.minusHours(1)) }
                    ) { Text(stringResource(R.string.calendar_1h_minus)) }
                    Text(
                        text = formState.hora.let {
                            "%02d:%02d".format(it.hour, it.minute)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { onUpdateHora(formState.hora.plusHours(1)) }
                    ) { Text(stringResource(R.string.calendar_1h_plus)) }
                }

                // Recordatorio
                ExposedDropdownMenuBox(
                    expanded = recordatorioExpanded,
                    onExpandedChange = { recordatorioExpanded = it }
                ) {
                    val recordatorioLabels = mapOf(
                        0 to stringResource(R.string.calendar_no_reminder),
                        15 to stringResource(R.string.calendar_15min),
                        30 to stringResource(R.string.calendar_30min),
                        60 to stringResource(R.string.calendar_1h),
                        120 to stringResource(R.string.calendar_2h),
                        1440 to stringResource(R.string.calendar_1d),
                        2880 to stringResource(R.string.calendar_2d)
                    )
                    OutlinedTextField(
                        value = recordatorioLabels[formState.recordatorioMinutos]
                            ?: stringResource(R.string.calendar_n_min_before, formState.recordatorioMinutos),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.calendar_event_reminder)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(recordatorioExpanded)
                        },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = recordatorioExpanded,
                        onDismissRequest = { recordatorioExpanded = false }
                    ) {
                        recordatorioLabels.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onUpdateRecordatorio(mins)
                                    recordatorioExpanded = false
                                }
                            )
                        }
                    }
                }

                // Descripción
                OutlinedTextField(
                    value = formState.descripcion,
                    onValueChange = onUpdateDescripcion,
                    label = { Text(stringResource(R.string.calendar_description_label)) },
                    placeholder = { Text(stringResource(R.string.calendar_description_placeholder)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = formState.isValid
            ) {
                Text(if (isEdit) stringResource(R.string.calendar_update) else stringResource(R.string.calendar_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_cancel)) }
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  Google Calendar Import Dialog
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleCalendarImportDialog(
    materias: List<Materia>,
    onImport: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMateriaId by remember { mutableStateOf(materias.firstOrNull()?.id) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudDownload, null) },
        title = { Text(stringResource(R.string.gcal_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.gcal_import_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.gcal_import_select_materia),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    val selected = materias.find { it.id == selectedMateriaId }
                    OutlinedTextField(
                        value = selected?.nombre ?: stringResource(R.string.calendar_select_materia),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.calendar_event_materia)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        materias.forEach { materia ->
                            DropdownMenuItem(
                                text = { Text(materia.nombre) },
                                onClick = {
                                    selectedMateriaId = materia.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedMateriaId?.let { onImport(it) } },
                enabled = selectedMateriaId != null
            ) {
                Text(stringResource(R.string.gcal_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  Helpers
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun eventTypeColor(tipo: TipoEvento): Color = when (tipo) {
    TipoEvento.PARCIAL -> MaterialTheme.colorScheme.error
    TipoEvento.FINAL -> Color(0xFFD32F2F) // Rojo oscuro
    TipoEvento.QUIZ -> MaterialTheme.colorScheme.tertiary
    TipoEvento.TAREA -> MaterialTheme.colorScheme.primary
    TipoEvento.PROYECTO -> Color(0xFF7B1FA2) // Morado
    TipoEvento.EXPOSICION -> Color(0xFFF57C00) // Naranja
    TipoEvento.OTRO -> MaterialTheme.colorScheme.outline
}
