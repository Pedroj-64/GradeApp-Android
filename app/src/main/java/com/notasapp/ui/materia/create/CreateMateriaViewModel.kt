package com.notasapp.ui.materia.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.domain.model.Componente
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.domain.model.TipoEscala
import com.notasapp.domain.repository.MateriaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel del Wizard de 3 pasos para crear una nueva materia.
 *
 * Mantiene el estado acumulado entre pasos sin navegar a nuevas pantallas:
 * el Wizard se implementa con un único Composable y un estado de paso.
 */
@HiltViewModel
class CreateMateriaViewModel @Inject constructor(
    private val usuarioDao: UsuarioDao,
    private val materiaRepository: MateriaRepository
) : ViewModel() {

    // ── Estado del Wizard ──────────────────────────────────────

    private val _wizardState = MutableStateFlow(WizardState())
    val wizardState: StateFlow<WizardState> = _wizardState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // ── Paso 1: Información básica ─────────────────────────────

    fun updateNombre(nombre: String) = _wizardState.update { it.copy(nombre = nombre) }
    fun updatePeriodo(periodo: String) = _wizardState.update { it.copy(periodo = periodo) }
    fun updateProfesor(profesor: String) = _wizardState.update { it.copy(profesor = profesor) }
    fun updateCreditos(creditos: Int) = _wizardState.update { it.copy(creditos = creditos) }
    fun goToStep2() = _wizardState.update { it.copy(currentStep = 2) }

    // ── Paso 2: Escala de calificación ─────────────────────────

    fun updateTipoEscala(tipo: TipoEscala) = _wizardState.update {
        it.copy(
            tipoEscala = tipo,
            escalaMin = tipo.escalaMin,
            escalaMax = tipo.escalaMax,
            notaAprobacion = tipo.notaAprobacion
        )
    }
    fun updateEscalaMin(min: Float) = _wizardState.update { it.copy(escalaMin = min) }
    fun updateEscalaMax(max: Float) = _wizardState.update { it.copy(escalaMax = max) }
    fun updateNotaAprobacion(nota: Float) = _wizardState.update { it.copy(notaAprobacion = nota) }
    fun goToStep3() = _wizardState.update { it.copy(currentStep = 3) }

    // ── Paso 3: Componentes ────────────────────────────────────

    fun addComponente() = _wizardState.update { state ->
        val newIndex = state.componentes.size + 1
        state.copy(
            componentes = state.componentes + ComponenteInput(
                nombre = "Componente $newIndex",
                porcentaje = 0f
            )
        )
    }

    fun updateComponenteNombre(index: Int, nombre: String) = _wizardState.update { state ->
        state.copy(
            componentes = state.componentes.toMutableList().apply {
                this[index] = this[index].copy(nombre = nombre)
            }
        )
    }

    fun updateComponentePorcentaje(index: Int, porcentaje: Float) = _wizardState.update { state ->
        // Redondear a múltiplos de 1% para evitar imprecisión de punto flotante
        val snapped = kotlin.math.round(porcentaje * 100f) / 100f
        state.copy(
            componentes = state.componentes.toMutableList().apply {
                this[index] = this[index].copy(porcentaje = snapped)
            }
        )
    }

    fun removeComponente(index: Int) = _wizardState.update { state ->
        state.copy(componentes = state.componentes.toMutableList().apply { removeAt(index) })
    }

    // ── Guardar Materia ────────────────────────────────────────

    fun guardarMateria() {
        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            val state = _wizardState.value

            // Validaciones
            if (state.nombre.isBlank()) {
                _saveState.value = SaveState.Error("El nombre de la materia es obligatorio")
                return@launch
            }
            val sumaPorcentajes = state.componentes.sumOf { it.porcentaje.toDouble() }.toFloat()
            if (state.componentes.isNotEmpty() && kotlin.math.abs(sumaPorcentajes - 1f) > 0.01f) {
                _saveState.value = SaveState.Error("Los porcentajes deben sumar 100%")
                return@launch
            }

            try {
                val usuario = usuarioDao.getUsuarioActivoOnce()
                    ?: throw IllegalStateException("No hay usuario activo")

                // Crear materia
                val materia = Materia(
                    usuarioId = usuario.googleId,
                    nombre = state.nombre.trim(),
                    periodo = state.periodo.trim(),
                    profesor = state.profesor.takeIf { it.isNotBlank() },
                    escalaMin = state.escalaMin,
                    escalaMax = state.escalaMax,
                    notaAprobacion = state.notaAprobacion,
                    creditos = state.creditos,
                    tipoEscala = state.tipoEscala
                )
                val materiaId = materiaRepository.insertMateria(materia)

                // Crear componentes, cada uno con una sub-nota por defecto (100% del corte)
                state.componentes.forEachIndexed { index, input ->
                    val componenteId = materiaRepository.insertComponente(
                        Componente(
                            materiaId = materiaId,
                            nombre = input.nombre,
                            porcentaje = input.porcentaje,
                            orden = index
                        )
                    )
                    // Sub-nota por defecto: representa el 100% del componente
                    materiaRepository.insertSubNota(
                        SubNota(
                            componenteId = componenteId,
                            descripcion = "Nota ${input.nombre}",
                            porcentajeDelComponente = 1.0f
                        )
                    )
                }

                Timber.i("Materia creada: ${state.nombre} (id=$materiaId)")
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                Timber.e(e, "Error al guardar materia")
                _saveState.value = SaveState.Error(e.localizedMessage ?: "Error desconocido")
            }
        }
    }

    fun goBack() = _wizardState.update { state ->
        if (state.currentStep > 1) state.copy(currentStep = state.currentStep - 1)
        else state
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }
}

// ── Modelos de estado del Wizard ───────────────────────────────

data class WizardState(
    val currentStep: Int = 1,
    // Paso 1
    val nombre: String = "",
    val periodo: String = "",
    val profesor: String = "",
    val creditos: Int = 3,
    // Paso 2
    val tipoEscala: TipoEscala = TipoEscala.NUMERICO_5,
    val escalaMin: Float = 0f,
    val escalaMax: Float = 5f,
    val notaAprobacion: Float = 3f,
    // Paso 3
    val componentes: List<ComponenteInput> = listOf(
        ComponenteInput("Primer corte", 0.30f),
        ComponenteInput("Segundo corte", 0.30f),
        ComponenteInput("Examen final", 0.40f)
    )
) {
    val sumaPorcentajes: Float get() = componentes.sumOf { it.porcentaje.toDouble() }.toFloat()
    val porcentajesValidos: Boolean get() = kotlin.math.abs(sumaPorcentajes - 1f) <= 0.01f
}

data class ComponenteInput(
    val nombre: String = "",
    val porcentaje: Float = 0f
) {
    val porcentajeDisplay: Int get() = kotlin.math.round(porcentaje * 100).toInt()
}

sealed class SaveState {
    data object Idle : SaveState()
    data object Loading : SaveState()
    data object Success : SaveState()
    data class Error(val message: String) : SaveState()
}
