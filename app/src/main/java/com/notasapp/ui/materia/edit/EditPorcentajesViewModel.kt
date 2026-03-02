package com.notasapp.ui.materia.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.domain.model.Componente
import com.notasapp.domain.repository.MateriaRepository
import com.notasapp.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel de la pantalla de edición de porcentajes.
 *
 * Carga los componentes de la materia, permite modificar sus porcentajes
 * y orden, y persiste los cambios en Room.
 */
@HiltViewModel
class EditPorcentajesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val materiaRepository: MateriaRepository
) : ViewModel() {

    private val materiaId: Long = checkNotNull(
        savedStateHandle[Screen.EditPorcentajes.ARG_MATERIA_ID]
    )

    private val _uiState = MutableStateFlow(EditPorcentajesUiState())
    val uiState: StateFlow<EditPorcentajesUiState> = _uiState.asStateFlow()

    init {
        cargarComponentes()
    }

    private fun cargarComponentes() {
        viewModelScope.launch {
            try {
                val materia = materiaRepository.getMateriaConComponentes(materiaId).first()
                _uiState.update { it.copy(componentes = materia?.componentes ?: emptyList()) }
            } catch (e: Exception) {
                Timber.e(e, "Error al cargar componentes")
                _uiState.update { it.copy(error = "No se pudieron cargar los componentes") }
            }
        }
    }

    /**
     * Actualiza el porcentaje de un componente específico.
     * La validación de suma al 100% se hace en la UI.
     */
    fun onPorcentajeChange(componenteId: Long, nuevoPorcentaje: Float) {
        // Redondear a múltiplos de 1% para evitar imprecisión de punto flotante
        val snapped = kotlin.math.round(nuevoPorcentaje * 100f) / 100f
        _uiState.update { state ->
            state.copy(
                componentes = state.componentes.map { componente ->
                    if (componente.id == componenteId)
                        componente.copy(porcentaje = snapped)
                    else
                        componente
                }
            )
        }
    }

    /**
     * Actualiza el orden de los componentes luego de un drag & drop.
     */
    fun onReorder(nuevoOrden: List<Componente>) {
        _uiState.update { state ->
            state.copy(
                componentes = nuevoOrden.mapIndexed { index, c -> c.copy(orden = index) }
            )
        }
    }

    /**
     * Persiste los cambios de porcentajes y orden en Room.
     */
    fun guardar() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                materiaRepository.updateComponentes(_uiState.value.componentes)
                Timber.i("Porcentajes actualizados para materia $materiaId")
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                Timber.e(e, "Error al guardar porcentajes")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "No se pudieron guardar los cambios"
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class EditPorcentajesUiState(
    val componentes: List<Componente> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)
