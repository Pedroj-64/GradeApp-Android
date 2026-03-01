package com.notasapp.ui.recomendaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.data.remote.ai.GeminiService
import com.notasapp.data.remote.ai.Recomendacion
import com.notasapp.domain.model.Materia
import com.notasapp.domain.repository.MateriaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Estado de la pantalla de recomendaciones.
 */
data class RecomendacionesUiState(
    val isLoading: Boolean = false,
    val materiaSeleccionada: Materia? = null,
    val recomendaciones: List<Recomendacion> = emptyList(),
    val error: String? = null,
    val apiKeyFaltante: Boolean = false
)

/**
 * ViewModel de la pantalla de recomendaciones.
 *
 * Carga las materias del usuario, permite seleccionar una, y genera
 * recomendaciones usando Gemini AI basándose en el nombre de la materia.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecomendacionesViewModel @Inject constructor(
    private val materiaRepository: MateriaRepository,
    private val usuarioDao: UsuarioDao,
    private val geminiService: GeminiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecomendacionesUiState())
    val uiState: StateFlow<RecomendacionesUiState> = _uiState.asStateFlow()

    /** Todas las materias del usuario para el selector. */
    val materias: StateFlow<List<Materia>> = usuarioDao
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

    // Cache de recomendaciones por materiaId para no repetir llamadas
    private val cache = mutableMapOf<Long, List<Recomendacion>>()

    /**
     * Selecciona una materia y genera recomendaciones para ella.
     */
    fun seleccionarMateria(materia: Materia) {
        _uiState.update {
            it.copy(
                materiaSeleccionada = materia,
                recomendaciones = emptyList(),
                error = null,
                apiKeyFaltante = false
            )
        }

        // Verificar si ya tenemos recomendaciones en cache
        cache[materia.id]?.let { cached ->
            _uiState.update { it.copy(recomendaciones = cached) }
            return
        }

        generarRecomendaciones(materia)
    }

    /**
     * Reintenta la generación de recomendaciones.
     */
    fun reintentar() {
        _uiState.value.materiaSeleccionada?.let { materia ->
            generarRecomendaciones(materia)
        }
    }

    private fun generarRecomendaciones(materia: Materia) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, apiKeyFaltante = false) }

            geminiService.generarRecomendaciones(
                nombreMateria = materia.nombre,
                periodo = materia.periodo
            ).fold(
                onSuccess = { recomendaciones ->
                    cache[materia.id] = recomendaciones
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recomendaciones = recomendaciones
                        )
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Error generando recomendaciones")
                    val isApiKeyMissing = error is IllegalStateException &&
                            error.message?.contains("GEMINI_API_KEY") == true
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = if (isApiKeyMissing) null else
                                "No se pudieron generar recomendaciones: ${error.localizedMessage ?: error.javaClass.simpleName}",
                            apiKeyFaltante = isApiKeyMissing
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
