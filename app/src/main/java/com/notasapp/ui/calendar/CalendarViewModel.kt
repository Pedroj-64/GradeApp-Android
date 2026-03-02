package com.notasapp.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.UserPreferencesRepository
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.data.receiver.ExamAlarmScheduler
import com.notasapp.data.remote.calendar.GoogleCalendarService
import com.notasapp.domain.model.ExamenEvent
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.TipoEvento
import com.notasapp.domain.repository.CalendarRepository
import com.notasapp.domain.repository.MateriaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Estado de la pantalla de calendario.
 */
data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<ExamenEvent> = emptyList(),
    val eventsForSelectedDate: List<ExamenEvent> = emptyList(),
    val upcomingEvents: List<ExamenEvent> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingEvent: ExamenEvent? = null,
    val showNotificationPermissionFlow: Boolean = false,
    val isImportingGoogleCalendar: Boolean = false,
    val showGoogleCalendarDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * Estado del formulario de creación/edición de evento.
 */
data class EventFormState(
    val titulo: String = "",
    val descripcion: String = "",
    val tipoEvento: TipoEvento = TipoEvento.PARCIAL,
    val materiaId: Long? = null,
    val fecha: LocalDate = LocalDate.now().plusDays(1),
    val hora: LocalTime = LocalTime.of(8, 0),
    val recordatorioMinutos: Int = 60
) {
    val isValid: Boolean
        get() = titulo.isNotBlank() && materiaId != null
}

/**
 * ViewModel para la pantalla de calendario académico.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val materiaRepository: MateriaRepository,
    private val usuarioDao: UsuarioDao,
    private val userPrefsRepository: UserPreferencesRepository,
    private val googleCalendarService: GoogleCalendarService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val alarmScheduler = ExamAlarmScheduler(appContext)

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(EventFormState())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()

    /** Materias disponibles para asociar eventos. */
    val materias: StateFlow<List<Materia>> = usuarioDao
        .getUsuarioActivo()
        .flatMapLatest { usuario ->
            if (usuario == null) flowOf(emptyList())
            else materiaRepository.getMateriasByUsuario(usuario.googleId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            val usuario = usuarioDao.getUsuarioActivo().first() ?: return@launch

            // Observar eventos del mes actual
            launch {
                combine(
                    _uiState,
                    calendarRepository.getEventsByUsuario(usuario.googleId)
                ) { state, allEvents ->
                    val monthStart = state.currentMonth.atDay(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val monthEnd = state.currentMonth.plusMonths(1).atDay(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val monthEvents = allEvents.filter {
                        it.fechaEpochMs in monthStart until monthEnd
                    }
                    val selectedDateEvents = allEvents.filter {
                        it.fecha == state.selectedDate
                    }

                    state.copy(
                        events = monthEvents,
                        eventsForSelectedDate = selectedDateEvents,
                        upcomingEvents = allEvents.filter { !it.yaPaso }.take(5)
                    )
                }.collect { newState ->
                    _uiState.value = newState
                }
            }
        }
    }

    // ── Navegación del calendario ───────────────────────────────────

    fun goToPreviousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun goToNextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun goToToday() {
        _uiState.update {
            it.copy(
                currentMonth = YearMonth.now(),
                selectedDate = LocalDate.now()
            )
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                currentMonth = YearMonth.from(date)
            )
        }
    }

    // ── Crear / Editar evento ───────────────────────────────────────

    fun showCreateDialog(date: LocalDate? = null) {
        _formState.value = EventFormState(
            fecha = date ?: _uiState.value.selectedDate.let {
                if (it.isBefore(LocalDate.now())) LocalDate.now().plusDays(1) else it
            }
        )
        _uiState.update { it.copy(showCreateDialog = true, showEditDialog = false) }
    }

    fun showEditDialog(event: ExamenEvent) {
        _formState.value = EventFormState(
            titulo = event.titulo,
            descripcion = event.descripcion,
            tipoEvento = event.tipoEvento,
            materiaId = event.materiaId,
            fecha = event.fecha,
            hora = event.hora,
            recordatorioMinutos = event.recordatorioMinutos
        )
        _uiState.update {
            it.copy(showEditDialog = true, showCreateDialog = false, editingEvent = event)
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(showCreateDialog = false, showEditDialog = false, editingEvent = null)
        }
    }

    fun updateForm(transform: EventFormState.() -> EventFormState) {
        _formState.update { it.transform() }
    }

    fun saveEvent() {
        val form = _formState.value
        if (!form.isValid) return

        viewModelScope.launch {
            doSaveEvent(form)
        }
    }

    /**
     * Llamado cuando el usuario selecciona un valor de recordatorio.
     * Si selecciona un recordatorio > 0 y no hemos explicado notificaciones, pedir permiso.
     */
    fun onReminderSelected(minutes: Int) {
        _formState.update { it.copy(recordatorioMinutos = minutes) }
        if (minutes > 0) {
            viewModelScope.launch {
                val hasExplained = userPrefsRepository.hasExplainedNotifications.first()
                if (!hasExplained) {
                    _uiState.update { it.copy(showNotificationPermissionFlow = true) }
                }
            }
        }
    }

    /** Llamado después de que el usuario respondió al diálogo de permiso de notificaciones. */
    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            userPrefsRepository.setHasExplainedNotifications()
            _uiState.update { it.copy(showNotificationPermissionFlow = false) }
            if (granted) {
                Timber.i("Permiso de notificaciones concedido")
            } else {
                Timber.i("Permiso de notificaciones denegado")
                // Si denegó, quitar el recordatorio
                _formState.update { it.copy(recordatorioMinutos = 0) }
            }
        }
    }

    fun dismissNotificationPermission() {
        viewModelScope.launch {
            userPrefsRepository.setHasExplainedNotifications()
            _uiState.update { it.copy(showNotificationPermissionFlow = false) }
        }
    }

    private suspend fun doSaveEvent(form: EventFormState) {
        _uiState.update { it.copy(isLoading = true) }
        try {
            val fechaEpochMs = LocalDateTime.of(form.fecha, form.hora)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val event = ExamenEvent(
                id = _uiState.value.editingEvent?.id ?: 0,
                materiaId = form.materiaId!!,
                titulo = form.titulo.trim(),
                descripcion = form.descripcion.trim(),
                tipoEvento = form.tipoEvento,
                fechaEpochMs = fechaEpochMs,
                recordatorioMinutos = form.recordatorioMinutos
            )

            val savedId = calendarRepository.saveEvent(event)
            val isEdit = _uiState.value.editingEvent != null

            // Programar alarma con ID real del evento
            if (form.recordatorioMinutos > 0) {
                val triggerMs = fechaEpochMs - (form.recordatorioMinutos * 60_000L)
                alarmScheduler.scheduleAlarm(
                    eventId = savedId,
                    title = event.titulo,
                    description = event.descripcion,
                    tipoEvento = event.tipoEvento.name,
                    triggerAtMs = triggerMs,
                    materiaId = event.materiaId,
                    reminderMinutes = form.recordatorioMinutos
                )
            } else {
                // Si cambió de con-recordatorio a sin-recordatorio, cancelar alarma
                alarmScheduler.cancelAlarm(savedId)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    showCreateDialog = false,
                    showEditDialog = false,
                    editingEvent = null,
                    successMessage = if (isEdit) "Evento actualizado" else "Evento creado"
                )
            }
            Timber.i("Evento guardado: ${event.titulo}")
        } catch (e: Exception) {
            Timber.e(e, "Error al guardar evento")
            _uiState.update {
                it.copy(isLoading = false, error = "[${e.javaClass.simpleName}] ${e.message}")
            }
        }
    }

    fun deleteEvent(event: ExamenEvent) {
        viewModelScope.launch {
            try {
                calendarRepository.deleteEvent(event.id)
                alarmScheduler.cancelAlarm(event.id)
                _uiState.update {
                    it.copy(successMessage = "Evento eliminado")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al eliminar evento")
                _uiState.update { it.copy(error = "[${e.javaClass.simpleName}] ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    // ── Google Calendar import ─────────────────────────────────────

    fun showGoogleCalendarDialog() {
        _uiState.update { it.copy(showGoogleCalendarDialog = true) }
    }

    fun dismissGoogleCalendarDialog() {
        _uiState.update { it.copy(showGoogleCalendarDialog = false) }
    }

    /**
     * Importa eventos desde Google Calendar y los guarda como eventos locales.
     * @param materiaId Materia a la cual asociar los eventos importados.
     */
    fun importFromGoogleCalendar(materiaId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImportingGoogleCalendar = true,
                    showGoogleCalendarDialog = false,
                    error = null
                )
            }

            try {
                val userEmail = userPrefsRepository.userEmail.first()
                if (userEmail.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isImportingGoogleCalendar = false,
                            error = "Debes iniciar sesión para importar de Google Calendar"
                        )
                    }
                    return@launch
                }

                val events = googleCalendarService.fetchUpcomingEvents(
                    userEmail = userEmail,
                    materiaId = materiaId
                )

                var imported = 0
                for (event in events) {
                    calendarRepository.saveEvent(event)
                    imported++
                }

                _uiState.update {
                    it.copy(
                        isImportingGoogleCalendar = false,
                        successMessage = "$imported eventos importados de Google Calendar"
                    )
                }
                Timber.i("Google Calendar: $imported eventos importados")
            } catch (e: Exception) {
                Timber.e(e, "Error al importar de Google Calendar")
                _uiState.update {
                    it.copy(
                        isImportingGoogleCalendar = false,
                        error = "[${e.javaClass.simpleName}] ${e.message}"
                    )
                }
            }
        }
    }
}
