package com.notasapp.ui.materia.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.domain.model.SubNotaDetalle
import com.notasapp.domain.usecase.CalcularNotaNecesariaUseCase
import com.notasapp.domain.usecase.GetMateriaConPromedioUseCase
import com.notasapp.domain.usecase.SimularNotaUseCase
import com.notasapp.domain.repository.MateriaRepository
import com.notasapp.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel de la pantalla de detalle de una materia.
 *
 * Observa la materia completa (con componentes y sub-notas) como Flow reactivo.
 * Gestiona la actualización de sub-notas directamente desde la pantalla.
 */
@HiltViewModel
class MateriaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getMateriaConPromedio: GetMateriaConPromedioUseCase,
    private val calcularNotaNecesaria: CalcularNotaNecesariaUseCase,
    private val simularNota: SimularNotaUseCase,
    private val materiaRepository: MateriaRepository
) : ViewModel() {

    private val materiaId: Long = checkNotNull(
        savedStateHandle[Screen.MateriaDetail.ARG_MATERIA_ID]
    )

    // ── Estado de la materia ────────────────────────────────────

    /** La materia completa con componentes y sub-notas, actualizada en tiempo real. */
    val materia: StateFlow<Materia?> = getMateriaConPromedio(materiaId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    // ── Calculadora "¿qué nota necesito?" ──────────────────────

    private val _notaNecesariaResult =
        MutableStateFlow<CalcularNotaNecesariaUseCase.Resultado?>(null)
    val notaNecesariaResult: StateFlow<CalcularNotaNecesariaUseCase.Resultado?> =
        _notaNecesariaResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Simulador "¿Qué pasa si saco X?" ──────────────────────

    private val _simuladorResult =
        MutableStateFlow<SimularNotaUseCase.Resultado?>(null)
    val simuladorResult: StateFlow<SimularNotaUseCase.Resultado?> =
        _simuladorResult.asStateFlow()

    // ── Acciones ───────────────────────────────────────────────

    /**
     * Actualiza el valor de una sub-nota específica.
     *
     * @param subNotaId  ID de la [SubNotaEntity] a actualizar.
     * @param valor      Nueva nota. Null para borrar la nota.
     */
    fun actualizarSubNota(subNotaId: Long, valor: Float?) {
        viewModelScope.launch {
            try {
                materiaRepository.updateSubNotaValor(subNotaId, valor)
            } catch (e: Exception) {
                Timber.e(e, "Error al actualizar sub-nota $subNotaId")
                _error.value = "No se pudo guardar la nota"
            }
        }
    }

    /**
     * Agrega una nueva sub-nota a un componente.
     *
     * @param componenteId             ID del componente padre.
     * @param descripcion              Nombre de la actividad, ej: "Parcial 1".
     * @param porcentajeDelComponente  Peso dentro del componente (0.0 a 1.0).
     */
    fun agregarSubNota(componenteId: Long, descripcion: String, porcentajeDelComponente: Float) {
        viewModelScope.launch {
            try {
                materiaRepository.insertSubNota(
                    SubNota(
                        componenteId = componenteId,
                        descripcion = descripcion,
                        porcentajeDelComponente = porcentajeDelComponente
                    )
                )
            } catch (e: IllegalArgumentException) {
                _error.value = e.message ?: "No se pudo agregar la nota"
            } catch (e: Exception) {
                Timber.e(e, "Error al agregar sub-nota al componente $componenteId")
                _error.value = "No se pudo agregar la nota"
            }
        }
    }

    /**
     * Elimina una sub-nota por su ID.
     */
    fun eliminarSubNota(subNotaId: Long) {
        viewModelScope.launch {
            try {
                materiaRepository.deleteSubNota(subNotaId)
            } catch (e: Exception) {
                Timber.e(e, "Error al eliminar sub-nota $subNotaId")
                _error.value = "No se pudo eliminar la nota"
            }
        }
    }

    // ── Sub-nota Detalles (notas compuestas) ───────────────────

    /**
     * Agrega un detalle a una sub-nota compuesta.
     *
     * @param subNotaId    ID de la sub-nota padre.
     * @param descripcion  Nombre del detalle, ej: "Primer intento".
     * @param porcentaje   Peso dentro de la sub-nota (0.0 a 1.0).
     */
    fun agregarDetalle(subNotaId: Long, descripcion: String, porcentaje: Float) {
        viewModelScope.launch {
            try {
                materiaRepository.insertSubNotaDetalle(
                    SubNotaDetalle(
                        subNotaId = subNotaId,
                        descripcion = descripcion,
                        porcentaje = porcentaje
                    )
                )
            } catch (e: IllegalArgumentException) {
                _error.value = e.message ?: "No se pudo agregar el detalle"
            } catch (e: Exception) {
                Timber.e(e, "Error al agregar detalle a sub-nota $subNotaId")
                _error.value = "No se pudo agregar el detalle"
            }
        }
    }

    /**
     * Actualiza el valor de un detalle.
     */
    fun actualizarDetalle(detalleId: Long, valor: Float?) {
        viewModelScope.launch {
            try {
                materiaRepository.updateSubNotaDetalleValor(detalleId, valor)
            } catch (e: Exception) {
                Timber.e(e, "Error al actualizar detalle $detalleId")
                _error.value = "No se pudo guardar el detalle"
            }
        }
    }

    /**
     * Elimina un detalle por su ID.
     */
    fun eliminarDetalle(detalleId: Long) {
        viewModelScope.launch {
            try {
                materiaRepository.deleteSubNotaDetalle(detalleId)
            } catch (e: Exception) {
                Timber.e(e, "Error al eliminar detalle $detalleId")
                _error.value = "No se pudo eliminar el detalle"
            }
        }
    }

    /**
     * Calcula la nota necesaria en los componentes faltantes
     * para alcanzar [meta] en esta materia.
     */
    fun calcularNotaNecesaria(meta: Float) {
        val materiaActual = materia.value ?: return
        _notaNecesariaResult.value = calcularNotaNecesaria(
            componentes = materiaActual.componentes,
            metaFinal = meta,
            escalaMax = materiaActual.escalaMax
        )
    }

    fun clearError() { _error.value = null }
    fun clearCalculadora() { _notaNecesariaResult.value = null }
    fun clearSimulador() { _simuladorResult.value = null }

    /**
     * Simula la nota final con notas hipotéticas.
     *
     * @param notasHipoteticas Mapa de componenteId → nota hipotética.
     */
    fun simularNota(notasHipoteticas: Map<Long, Float>) {
        val materiaActual = materia.value ?: return
        _simuladorResult.value = simularNota(
            componentes = materiaActual.componentes,
            notasHipoteticas = notasHipoteticas
        )
    }

    /**
     * Duplica la materia actual con un nuevo nombre.
     */
    fun duplicarMateria(nuevoNombre: String? = null) {
        viewModelScope.launch {
            try {
                val original = materia.value ?: return@launch
                val nombre = nuevoNombre ?: "${original.nombre} (copia)"
                val nueva = original.copy(
                    id = 0,
                    nombre = nombre,
                    googleSheetsId = null,
                    componentes = original.componentes.map { comp ->
                        comp.copy(
                            id = 0,
                            subNotas = comp.subNotas.map { sn ->
                                sn.copy(id = 0, valor = null, detalles = sn.detalles.map { d ->
                                    d.copy(id = 0, valor = null)
                                })
                            }
                        )
                    }
                )
                val newMateriaId = materiaRepository.insertMateria(nueva)
                nueva.componentes.forEachIndexed { _, comp ->
                    val newCompId = materiaRepository.insertComponente(
                        comp.copy(materiaId = newMateriaId)
                    )
                    comp.subNotas.forEach { sn ->
                        val newSnId = materiaRepository.insertSubNota(
                            sn.copy(componenteId = newCompId)
                        )
                        sn.detalles.forEach { det ->
                            materiaRepository.insertSubNotaDetalle(
                                det.copy(subNotaId = newSnId)
                            )
                        }
                    }
                }
                Timber.i("Materia duplicada: $nombre (id=$newMateriaId)")
            } catch (e: Exception) {
                Timber.e(e, "Error al duplicar materia")
                _error.value = "No se pudo duplicar la materia"
            }
        }
    }

    // ── Metas y notas ─────────────────────────────────────────────

    /**
     * Actualiza la nota meta (objetivo académico) de la materia.
     * @param notaMeta Nota objetivo, o null para eliminar la meta.
     */
    fun actualizarNotaMeta(notaMeta: Float?) {
        viewModelScope.launch {
            try {
                materiaRepository.updateNotaMeta(materiaId, notaMeta)
                Timber.i("Nota meta actualizada: $notaMeta")
            } catch (e: Exception) {
                Timber.e(e, "Error al actualizar nota meta")
                _error.value = "No se pudo guardar la meta"
            }
        }
    }

    /**
     * Actualiza las notas personales de la materia.
     * @param notas Texto de notas, o null para borrar.
     */
    fun actualizarNotas(notas: String?) {
        viewModelScope.launch {
            try {
                materiaRepository.updateNotas(materiaId, notas?.takeIf { it.isNotBlank() })
                Timber.i("Notas actualizadas")
            } catch (e: Exception) {
                Timber.e(e, "Error al actualizar notas")
                _error.value = "No se pudo guardar las notas"
            }
        }
    }
}
