package com.notasapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.domain.model.Materia
import com.notasapp.domain.repository.MateriaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Orden de listado para materias en Home.
 */
enum class OrdenMateria(val label: String) {
    NOMBRE("Nombre"),
    PROMEDIO_ASC("Promedio ↑"),
    PROMEDIO_DESC("Promedio ↓"),
    PERIODO("Semestre")
}

/**
 * ViewModel de la pantalla Home (lista de materias).
 *
 * Observa al usuario activo y carga sus materias como Flow reactivo.
 * Soporta búsqueda, filtro por semestre, ordenamiento y métricas de riesgo.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usuarioDao: UsuarioDao,
    private val materiaRepository: MateriaRepository
) : ViewModel() {

    // ── Filtros y búsqueda ─────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filtroSemestre = MutableStateFlow<String?>(null)
    val filtroSemestre: StateFlow<String?> = _filtroSemestre.asStateFlow()

    private val _orden = MutableStateFlow(OrdenMateria.NOMBRE)
    val orden: StateFlow<OrdenMateria> = _orden.asStateFlow()

    // ── Datos base ─────────────────────────────────────────────

    /** Todas las materias del usuario (sin filtrar). */
    private val allMaterias: StateFlow<List<Materia>> = usuarioDao
        .getUsuarioActivo()
        .flatMapLatest { usuario ->
            if (usuario == null) flowOf(emptyList())
            else materiaRepository.getMateriasByUsuario(usuario.googleId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Materias filtradas, buscadas y ordenadas. */
    val materias: StateFlow<List<Materia>> = combine(
        allMaterias, _searchQuery, _filtroSemestre, _orden
    ) { all, query, semestre, sort ->
        var result = all

        // Filtro por semestre
        if (!semestre.isNullOrBlank()) {
            result = result.filter { it.periodo == semestre }
        }

        // Búsqueda por nombre/profesor
        if (query.isNotBlank()) {
            val q = query.lowercase()
            result = result.filter {
                it.nombre.lowercase().contains(q) ||
                        (it.profesor?.lowercase()?.contains(q) == true)
            }
        }

        // Ordenamiento
        when (sort) {
            OrdenMateria.NOMBRE -> result.sortedBy { it.nombre.lowercase() }
            OrdenMateria.PROMEDIO_ASC -> result.sortedBy { it.promedio ?: Float.MAX_VALUE }
            OrdenMateria.PROMEDIO_DESC -> result.sortedByDescending { it.promedio ?: -1f }
            OrdenMateria.PERIODO -> result.sortedByDescending { it.periodo }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Semestres disponibles para el filtro. */
    val semestresDisponibles: StateFlow<List<String>> = allMaterias
        .map { materias -> materias.map { it.periodo }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Materias en riesgo académico (nota proyectada < mínima). */
    val materiasEnRiesgo: StateFlow<List<Materia>> = allMaterias
        .map { materias -> materias.filter { m -> m.promedio != null && !m.aprobado } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Promedio general ponderado rápido para el dashboard. */
    val promedioGeneral: StateFlow<Float?> = allMaterias
        .map { materias ->
            val conNotas = materias.filter { it.promedio != null }
            if (conNotas.isEmpty()) null
            else {
                val totalCred = conNotas.sumOf { it.creditos }
                if (totalCred > 0)
                    conNotas.sumOf { (it.promedio!! * it.creditos).toDouble() }.toFloat() / totalCred
                else
                    conNotas.map { it.promedio!! }.average().toFloat()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** ID de materia pendiente de confirmación de borrado. */
    private val _pendingDeleteId = MutableStateFlow<Long?>(null)
    val pendingDeleteId: StateFlow<Long?> = _pendingDeleteId.asStateFlow()

    // ── Acciones de filtro ─────────────────────────────────────

    fun updateSearchQuery(query: String) = _searchQuery.update { query }
    fun updateFiltroSemestre(semestre: String?) = _filtroSemestre.update { semestre }
    fun updateOrden(orden: OrdenMateria) = _orden.update { orden }

    // ── Acciones de borrado con confirmación ───────────────────

    /** Solicita confirmación para eliminar una materia. */
    fun requestDelete(materiaId: Long) {
        _pendingDeleteId.value = materiaId
    }

    /** Cancela la solicitud de borrado pendiente. */
    fun cancelDelete() {
        _pendingDeleteId.value = null
    }

    /** Confirma y ejecuta el borrado. */
    fun confirmDelete() {
        val id = _pendingDeleteId.value ?: return
        _pendingDeleteId.value = null
        viewModelScope.launch {
            try {
                materiaRepository.deleteMateria(id)
                Timber.i("Materia $id eliminada")
            } catch (e: Exception) {
                Timber.e(e, "Error al eliminar materia")
                _error.value = "No se pudo eliminar la materia"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
