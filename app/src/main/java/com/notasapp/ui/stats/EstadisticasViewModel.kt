package com.notasapp.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.domain.model.Materia
import com.notasapp.domain.repository.MateriaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Resumen estadístico calculado a partir del listado de materias.
 *
 * @param totalMaterias          Total de materias registradas.
 * @param aprobadas              Materias con promedio ≥ notaAprobacion.
 * @param enRiesgo               Materias con nota entre 50-74% del rango de aprobación.
 * @param reprobadas             Materias con promedio < notaAprobacion.
 * @param sinNotas               Materias sin ninguna nota ingresada todavía.
 * @param promedioGeneral        Promedio global entre las materias con promedio calculado.
 * @param materiasMejorNota      Top-3 materias con mejor promedio.
 * @param materiasPeorNota       Top-3 materias con peor promedio (con notas).
 */
data class EstadisticasSemestre(
    val totalMaterias: Int = 0,
    val aprobadas: Int = 0,
    val enRiesgo: Int = 0,
    val reprobadas: Int = 0,
    val sinNotas: Int = 0,
    val promedioGeneral: Float? = null,
    val materiasMejorNota: List<Materia> = emptyList(),
    val materiasPeorNota: List<Materia> = emptyList(),
)

/**
 * ViewModel para la pantalla de estadísticas del semestre.
 *
 * Todo el cómputo se realiza sobre el [StateFlow] reactivo de materias;
 * no hay llamadas manuales: la UI siempre está al día.
 */
@HiltViewModel
class EstadisticasViewModel @Inject constructor(
    usuarioDao: UsuarioDao,
    materiaRepository: MateriaRepository
) : ViewModel() {

    private val materias: StateFlow<List<Materia>> = usuarioDao
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

    val estadisticas: StateFlow<EstadisticasSemestre> = materias
        .map { lista -> calcular(lista) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EstadisticasSemestre()
        )

    // ── Cómputo puro ──────────────────────────────────────────

    private fun calcular(materias: List<Materia>): EstadisticasSemestre {
        if (materias.isEmpty()) return EstadisticasSemestre()

        val conNotas   = materias.filter { it.promedio != null }
        val sinNotas   = materias.count { it.promedio == null }
        val aprobadas  = conNotas.count { it.aprobado }
        val reprobadas = conNotas.count { materia ->
            !materia.aprobado && !esEnRiesgo(materia)
        }
        val enRiesgo   = conNotas.count { materia ->
            !materia.aprobado && esEnRiesgo(materia)
        }

        val promedioGeneral = if (conNotas.isEmpty()) null
        else conNotas.mapNotNull { it.promedio }.average().toFloat()

        val sorted         = conNotas.sortedByDescending { it.promedio }
        val mejoresNota    = sorted.take(3)
        val peoresNota     = sorted.takeLast(3).reversed()

        return EstadisticasSemestre(
            totalMaterias     = materias.size,
            aprobadas         = aprobadas,
            enRiesgo          = enRiesgo,
            reprobadas        = reprobadas,
            sinNotas          = sinNotas,
            promedioGeneral   = promedioGeneral,
            materiasMejorNota = mejoresNota,
            materiasPeorNota  = if (peoresNota == mejoresNota) emptyList() else peoresNota
        )
    }

    /**
     * Considera materia "en riesgo" si su promedio está dentro del 20% inferior
     * del rango de aprobación (es decir, muy cerca de reprobar pero no reprobada).
     */
    private fun esEnRiesgo(materia: Materia): Boolean {
        val promedio = materia.promedio ?: return false
        val rango    = materia.escalaMax - materia.escalaMin
        val margen   = rango * 0.20f
        return promedio < materia.notaAprobacion && promedio >= (materia.notaAprobacion - margen)
    }
}
