package com.notasapp.data.mapper

import com.notasapp.data.local.entities.ComponenteEntity
import com.notasapp.data.local.entities.MateriaEntity
import com.notasapp.data.local.entities.SubNotaDetailEntity
import com.notasapp.data.local.entities.SubNotaEntity
import com.notasapp.data.local.relations.ComponenteConSubNotas
import com.notasapp.data.local.relations.MateriaConComponentes
import com.notasapp.data.local.relations.SubNotaConDetalles
import com.notasapp.domain.model.Componente
import com.notasapp.domain.model.Materia
import com.notasapp.domain.model.SubNota
import com.notasapp.domain.model.SubNotaDetalle
import com.notasapp.domain.model.TipoEscala

/**
 * Funciones de conversión entre entidades Room y modelos de dominio.
 *
 * Mantener la transformación aquí (capa de datos) permite que el dominio
 * permanezca limpio y sin dependencias de Room.
 */

// ── SubNotaDetalle ───────────────────────────────────────────────

fun SubNotaDetailEntity.toDomain(): SubNotaDetalle = SubNotaDetalle(
    id = id,
    subNotaId = subNotaId,
    descripcion = descripcion,
    porcentaje = porcentaje,
    valor = valor
)

fun SubNotaDetalle.toEntity(): SubNotaDetailEntity = SubNotaDetailEntity(
    id = id,
    subNotaId = subNotaId,
    descripcion = descripcion,
    porcentaje = porcentaje,
    valor = valor
)

// ── SubNota ──────────────────────────────────────────────────────

fun SubNotaConDetalles.toDomain(): SubNota = SubNota(
    id = subNota.id,
    componenteId = subNota.componenteId,
    descripcion = subNota.descripcion,
    porcentajeDelComponente = subNota.porcentajeDelComponente,
    valor = subNota.valor,
    detalles = detalles.map { it.toDomain() }
)

fun SubNota.toEntity(): SubNotaEntity = SubNotaEntity(
    id = id,
    componenteId = componenteId,
    descripcion = descripcion,
    porcentajeDelComponente = porcentajeDelComponente,
    valor = valor
)

// ── Componente ───────────────────────────────────────────────────

fun ComponenteConSubNotas.toDomain(): Componente = Componente(
    id = componente.id,
    materiaId = componente.materiaId,
    nombre = componente.nombre,
    porcentaje = componente.porcentaje,
    orden = componente.orden,
    subNotas = subNotas.map { it.toDomain() },
    fechaLimite = componente.fechaLimite
)

fun Componente.toEntity(): ComponenteEntity = ComponenteEntity(
    id = id,
    materiaId = materiaId,
    nombre = nombre,
    porcentaje = porcentaje,
    orden = orden,
    fechaLimite = fechaLimite
)

// ── Materia ──────────────────────────────────────────────────────

fun MateriaConComponentes.toDomain(): Materia = Materia(
    id = materia.id,
    usuarioId = materia.usuarioId,
    nombre = materia.nombre,
    periodo = materia.periodo,
    profesor = materia.profesor,
    escalaMin = materia.escalaMin,
    escalaMax = materia.escalaMax,
    notaAprobacion = materia.notaAprobacion,
    creditos = materia.creditos,
    tipoEscala = tipoEscalaFromString(materia.tipoEscala),
    googleSheetsId = materia.googleSheetsId,
    notaMeta = materia.notaMeta,
    notas = materia.notas,
    componentes = componentesConSubNotas.sortedBy { it.componente.orden }.map { it.toDomain() }
)

fun Materia.toEntity(): MateriaEntity = MateriaEntity(
    id = id,
    usuarioId = usuarioId,
    nombre = nombre,
    periodo = periodo,
    profesor = profesor,
    escalaMin = escalaMin,
    escalaMax = escalaMax,
    notaAprobacion = notaAprobacion,
    creditos = creditos,
    tipoEscala = tipoEscala.name,
    googleSheetsId = googleSheetsId,
    notaMeta = notaMeta,
    notas = notas
)

// ── Helpers ──────────────────────────────────────────────────────

private fun tipoEscalaFromString(value: String): TipoEscala =
    try {
        TipoEscala.valueOf(value)
    } catch (e: IllegalArgumentException) {
        TipoEscala.NUMERICO_5
    }
