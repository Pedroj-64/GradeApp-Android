package com.gradify.backend.controller

import com.gradify.backend.dto.ErrorResponse
import com.gradify.backend.dto.RecomendacionesRequest
import com.gradify.backend.dto.RecomendacionesResponse
import com.gradify.backend.service.GeminiProxyService
import com.gradify.backend.service.PromptBuilder
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * Controlador REST que actúa como proxy entre la app Android y la API de Gemini.
 *
 * Beneficios:
 * - La API key de Gemini NUNCA se incluye en el APK
 * - Rate limiting centralizado
 * - Mejor manejo de errores
 * - Reduce tamaño del APK (sin SDK de Gemini en el cliente)
 */
@RestController
@RequestMapping("/api/v1")
class RecomendacionesController(
    private val geminiService: GeminiProxyService,
    private val promptBuilder: PromptBuilder,
    @Value("\${rate-limit.requests-per-minute:30}") private val rpm: Long,
    @Value("\${rate-limit.burst-capacity:5}") private val burst: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Rate limiter global. */
    private val bucket: Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(burst)
                .refillGreedy(rpm, Duration.ofMinutes(1))
                .build()
        )
        .build()

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "ok", "service" to "gradify-backend"))

    /**
     * POST /api/v1/recomendaciones
     *
     * Recibe los datos de la materia, construye el prompt y
     * llama a Gemini para generar recomendaciones.
     */
    @PostMapping("/recomendaciones")
    fun generarRecomendaciones(
        @Valid @RequestBody request: RecomendacionesRequest
    ): ResponseEntity<Any> {

        // Rate limiting
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit excedido")
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse(
                    error = "RATE_LIMIT_EXCEEDED",
                    message = "Demasiadas solicitudes. Intenta de nuevo en unos segundos.",
                    retryable = true
                ))
        }

        return try {
            log.info("Generando recomendaciones para: {}", request.nombreMateria)

            val componentes = request.componentes?.map { it.nombre to it.nota }

            val prompt = promptBuilder.build(
                nombreMateria = request.nombreMateria,
                periodo = request.periodo,
                componentes = componentes,
                promedio = request.promedio,
                porcentajeEvaluado = request.porcentajeEvaluado,
                notaAprobacion = request.notaAprobacion,
                aprobado = request.aprobado
            )

            val (recomendaciones, modelo) = geminiService.generarRecomendaciones(prompt)

            log.info("Generadas {} recomendaciones con modelo {}", recomendaciones.size, modelo)

            ResponseEntity.ok(RecomendacionesResponse(
                recomendaciones = recomendaciones,
                modelo = modelo
            ))

        } catch (e: IllegalArgumentException) {
            log.error("Error de configuración: {}", e.message)
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse(
                    error = "CONFIG_ERROR",
                    message = "Error de configuración del servidor. Contacta al administrador.",
                    retryable = false
                ))

        } catch (e: Exception) {
            val retryable = e.message?.contains("API_KEY_INVALID", ignoreCase = true) != true

            log.error("Error generando recomendaciones: {}", e.message, e)
            ResponseEntity
                .status(if (retryable) HttpStatus.SERVICE_UNAVAILABLE else HttpStatus.FORBIDDEN)
                .body(ErrorResponse(
                    error = if (retryable) "AI_ERROR" else "API_KEY_INVALID",
                    message = e.message ?: "Error desconocido al generar recomendaciones",
                    retryable = retryable
                ))
        }
    }
}
