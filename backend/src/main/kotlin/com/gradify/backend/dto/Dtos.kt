 package com.gradify.backend.dto

import jakarta.validation.constraints.NotBlank

/**
 * Request que recibe el proxy desde la app Android.
 */
data class RecomendacionesRequest(
    @field:NotBlank(message = "El nombre de la materia es obligatorio")
    val nombreMateria: String,
    val periodo: String? = null,
    val componentes: List<ComponenteInfo>? = null,
    val promedio: Float? = null,
    val porcentajeEvaluado: Float? = null,
    val notaAprobacion: Float? = null,
    val aprobado: Boolean? = null
)

data class ComponenteInfo(
    val nombre: String,
    val nota: Float?
)

/**
 * Respuesta del proxy hacia la app Android.
 */
data class RecomendacionesResponse(
    val recomendaciones: List<RecomendacionDto>,
    val modelo: String,
    val cached: Boolean = false
)

data class RecomendacionDto(
    val tipo: String,      // YOUTUBE, LIBRO, RECURSO
    val titulo: String,
    val descripcion: String,
    val url: String,
    val autor: String? = null
)

/**
 * Error estandarizado.
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val retryable: Boolean = false
)
