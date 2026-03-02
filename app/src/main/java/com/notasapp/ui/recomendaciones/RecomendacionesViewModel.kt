package com.notasapp.ui.recomendaciones

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.cache.RecomendacionesCache
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
import kotlinx.coroutines.flow.first
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
    savedStateHandle: SavedStateHandle,
    private val materiaRepository: MateriaRepository,
    private val usuarioDao: UsuarioDao,
    private val geminiService: GeminiService,
    private val recomendacionesCache: RecomendacionesCache
) : ViewModel() {

    /** materiaId opcional inyectado desde la ruta de navegación. */
    private val preSelectId: Long? =
        savedStateHandle.get<Long>("materiaId")?.takeIf { it > 0 }

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

    // Cache en memoria complementario al cache persistente
    private val memoryCache = mutableMapOf<Long, List<Recomendacion>>()

    init {
        // Si llegamos desde MateriaDetailScreen con un ID concreto, auto-seleccionar
        if (preSelectId != null) {
            viewModelScope.launch {
                val materia = materias.first { it.isNotEmpty() }.find { it.id == preSelectId }
                materia?.let { seleccionarMateria(it) }
            }
        }
    }

    /**
     * Selecciona una materia y genera recomendaciones para ella.
     * Usa cache persistente: solo llama a la IA si las notas cambiaron.
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

        // 1. Cache en memoria (más rápido)
        memoryCache[materia.id]?.takeIf { it.isNotEmpty() }?.let { cached ->
            _uiState.update { it.copy(recomendaciones = cached) }
            return
        }

        // 2. Cache persistente con fingerprint de notas
        val componentesInfo = materia.componentes.map { it.nombre to it.promedio }
        val fingerprint = recomendacionesCache.buildFingerprint(
            materiaId = materia.id,
            promedio = materia.promedio,
            porcentajeEvaluado = materia.porcentajeEvaluado,
            componentesInfo = componentesInfo.ifEmpty { null }
        )

        val cachedJson = recomendacionesCache.get(materia.id, fingerprint)
        if (cachedJson != null) {
            try {
                val recs = recomendacionesCache.deserializeRecomendaciones(cachedJson)
                if (recs.isNotEmpty()) {
                    memoryCache[materia.id] = recs
                    _uiState.update { it.copy(recomendaciones = recs) }
                    Timber.i("Recomendaciones cargadas de cache persistente (materia ${materia.id})")
                    return
                }
            } catch (e: Exception) {
                Timber.w(e, "Error deserializando cache persistente")
            }
        }

        // 3. Llamar a la IA
        generarRecomendaciones(materia, fingerprint)
    }

    /**
     * Reintenta la generación de recomendaciones, forzando una nueva llamada a la API.
     */
    fun reintentar() {
        _uiState.value.materiaSeleccionada?.let { materia ->
            memoryCache.remove(materia.id)
            recomendacionesCache.invalidate(materia.id)
            _uiState.update { it.copy(recomendaciones = emptyList(), error = null) }
            val componentesInfo = materia.componentes.map { it.nombre to it.promedio }
            val fingerprint = recomendacionesCache.buildFingerprint(
                materia.id, materia.promedio, materia.porcentajeEvaluado,
                componentesInfo.ifEmpty { null }
            )
            generarRecomendaciones(materia, fingerprint)
        }
    }

    private fun generarRecomendaciones(materia: Materia, fingerprint: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, apiKeyFaltante = false) }

            val componentesInfo = materia.componentes.map { comp ->
                comp.nombre to comp.promedio
            }

            geminiService.generarRecomendaciones(
                nombreMateria = materia.nombre,
                periodo = materia.periodo,
                componentesInfo = componentesInfo.ifEmpty { null },
                promedio = materia.promedio,
                porcentajeEvaluado = materia.porcentajeEvaluado,
                notaAprobacion = materia.notaAprobacion,
                aprobado = materia.aprobado
            ).fold(
                onSuccess = { recomendaciones ->
                    if (recomendaciones.isNotEmpty()) {
                        memoryCache[materia.id] = recomendaciones
                        // Persistir con fingerprint para futuros lanzamientos
                        val json = recomendacionesCache.serializeRecomendaciones(recomendaciones)
                        recomendacionesCache.put(materia.id, fingerprint, json)
                    }
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
                    val rawMsg = buildString {
                        append("[${error.javaClass.simpleName}] ${error.message}")
                        error.cause?.let { append(" | cause: [${it.javaClass.simpleName}] ${it.message}") }
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = if (isApiKeyMissing) null else rawMsg,
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
