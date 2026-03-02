package com.notasapp.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.notasapp.domain.model.Materia
import com.notasapp.domain.util.GradeCalculator
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genera un PDF resumen de una o múltiples [Materia]s usando la API nativa
 * [PdfDocument] de Android (no requiere dependencias externas).
 *
 * ## Estructura del PDF
 * - Encabezado: título "Gradify – Reporte Académico", fecha y hora
 * - Por cada materia: nombre, escala, promedio, estado de aprobación
 * - Tabla de componentes con pesos, notas parciales, aportes
 * - Pie: promedio general y créditos totales
 */
@Singleton
class PdfExporter @Inject constructor() {

    companion object {
        private const val PAGE_WIDTH = 595   // A4 en pts (≈ 210 mm)
        private const val PAGE_HEIGHT = 842  // A4 en pts (≈ 297 mm)
        private const val MARGIN = 40f
        private const val LINE_HEIGHT = 16f
        private const val HEADER_HEIGHT = 22f
    }

    // ── Paints reutilizables ────────────────────────────────────

    private val paintTitle = Paint().apply {
        color = Color.rgb(33, 50, 91)
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintSubtitle = Paint().apply {
        color = Color.rgb(80, 80, 80)
        textSize = 12f
        isAntiAlias = true
    }

    private val paintHeader = Paint().apply {
        color = Color.rgb(33, 50, 91)
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintBody = Paint().apply {
        color = Color.rgb(40, 40, 40)
        textSize = 11f
        isAntiAlias = true
    }

    private val paintBodyBold = Paint().apply {
        color = Color.rgb(40, 40, 40)
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintAprobado = Paint().apply {
        color = Color.rgb(46, 125, 50)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintReprobado = Paint().apply {
        color = Color.rgb(198, 40, 40)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintLine = Paint().apply {
        color = Color.rgb(200, 200, 200)
        strokeWidth = 0.5f
    }

    private val paintHeaderBg = Paint().apply {
        color = Color.rgb(230, 237, 250)
        style = Paint.Style.FILL
    }

    // ── API pública ────────────────────────────────────────────

    fun sugerirNombreArchivo(materia: Materia): String {
        val safe = materia.nombre.replace(Regex("[^\\w\\s-]"), "").replace(" ", "_")
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "${safe}_${ts}.pdf"
    }

    fun sugerirNombreResumen(): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "Gradify_Resumen_${ts}.pdf"
    }

    /**
     * Genera un PDF con todas las materias y lo escribe en el [OutputStream].
     */
    fun exportarToOutputStream(materias: List<Materia>, out: OutputStream) {
        val bytes = buildPdfBytes(materias)
        out.write(bytes)
        out.flush()
    }

    /**
     * Genera un PDF de una sola materia y lo escribe en el [OutputStream].
     */
    fun exportarMateriaToOutputStream(materia: Materia, out: OutputStream) {
        exportarToOutputStream(listOf(materia), out)
    }

    /**
     * Retorna los bytes del PDF completo.
     */
    fun buildPdfBytes(materias: List<Materia>): ByteArray {
        val pdf = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        // ── Encabezado del documento ────────────────────────────
        y = drawDocHeader(canvas, y)

        // ── Resumen general ─────────────────────────────────────
        if (materias.size > 1) {
            y = drawResumenGeneral(canvas, y, materias)
        }

        // ── Cada materia ────────────────────────────────────────
        for ((index, materia) in materias.withIndex()) {
            // Verificar si necesitamos nueva página (al menos 120 pts para una materia)
            if (y > PAGE_HEIGHT - 120f) {
                pdf.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }

            y = drawMateria(canvas, y, materia)

            if (index < materias.size - 1) {
                y += 8f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paintLine)
                y += 12f
            }

            // Verificar overflow durante el dibujo de la materia
            if (y > PAGE_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
        }

        // ── Pie de página ───────────────────────────────────────
        drawFooter(canvas)

        pdf.finishPage(page)

        val bos = ByteArrayOutputStream()
        pdf.writeTo(bos)
        pdf.close()
        return bos.toByteArray()
    }

    // ── Dibujo del encabezado ──────────────────────────────────

    private fun drawDocHeader(canvas: Canvas, startY: Float): Float {
        var y = startY

        canvas.drawText("Gradify – Reporte Académico", MARGIN, y + 20f, paintTitle)
        y += 28f

        val fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        canvas.drawText("Generado el $fecha", MARGIN, y + 12f, paintSubtitle)
        y += 24f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paintLine)
        y += 16f

        return y
    }

    // ── Resumen general (cuando hay múltiples materias) ────────

    private fun drawResumenGeneral(canvas: Canvas, startY: Float, materias: List<Materia>): Float {
        var y = startY

        canvas.drawText("Resumen General", MARGIN, y + 14f, paintHeader)
        y += 24f

        val conNotas = materias.filter { it.promedio != null }
        val promedioGeneral = if (conNotas.isNotEmpty()) {
            conNotas.sumOf { it.promedio!!.toDouble() } / conNotas.size
        } else 0.0

        val aprobadas = materias.count { it.aprobado }
        val reprobadas = materias.count { it.promedio != null && !it.aprobado }
        val totalCreditos = materias.sumOf { it.creditos }
        val creditosAprobados = materias.filter { it.aprobado }.sumOf { it.creditos }

        canvas.drawText("Materias: ${materias.size}", MARGIN, y + LINE_HEIGHT, paintBody)
        canvas.drawText(
            "Promedio general: ${GradeCalculator.display(promedioGeneral.toFloat())}",
            MARGIN + 200f, y + LINE_HEIGHT, paintBodyBold
        )
        y += LINE_HEIGHT + 4f

        canvas.drawText("Aprobadas: $aprobadas", MARGIN, y + LINE_HEIGHT, paintAprobado)
        canvas.drawText("Reprobadas: $reprobadas", MARGIN + 120f, y + LINE_HEIGHT, paintReprobado)
        canvas.drawText(
            "Créditos: $creditosAprobados / $totalCreditos",
            MARGIN + 270f, y + LINE_HEIGHT, paintBody
        )
        y += LINE_HEIGHT + 12f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paintLine)
        y += 16f

        return y
    }

    // ── Dibujo de una materia ──────────────────────────────────

    private fun drawMateria(canvas: Canvas, startY: Float, materia: Materia): Float {
        var y = startY
        val maxX = PAGE_WIDTH - MARGIN

        // Nombre + escala
        canvas.drawText(materia.nombre, MARGIN, y + HEADER_HEIGHT, paintHeader)
        canvas.drawText(
            "Escala: ${materia.escalaMin.toInt()}–${materia.escalaMax.toInt()} | " +
                    "Aprobación: ${GradeCalculator.display(materia.notaAprobacion)} | " +
                    "Créditos: ${materia.creditos}",
            MARGIN, y + HEADER_HEIGHT + 16f, paintSubtitle
        )
        y += HEADER_HEIGHT + 28f

        // Promedio y estado
        val promedioText = materia.promedioDisplay
        val estadoText = if (materia.aprobado) "APROBADO" else if (materia.promedio != null) "EN RIESGO" else "SIN NOTAS"
        val estadoPaint = if (materia.aprobado) paintAprobado else paintReprobado

        canvas.drawText("Promedio: $promedioText", MARGIN, y + LINE_HEIGHT, paintBodyBold)
        canvas.drawText(estadoText, MARGIN + 200f, y + LINE_HEIGHT, estadoPaint)

        val acumText = "Acumulado: ${materia.acumuladoDisplay} | Evaluado: ${(materia.porcentajeEvaluado * 100).toInt()}%"
        canvas.drawText(acumText, MARGIN, y + LINE_HEIGHT * 2 + 4f, paintBody)
        y += LINE_HEIGHT * 2 + 16f

        // Tabla de componentes
        if (materia.componentes.isNotEmpty()) {
            // Header de tabla
            val colX = floatArrayOf(MARGIN, MARGIN + 180f, MARGIN + 250f, MARGIN + 380f, MARGIN + 440f)
            canvas.drawRect(MARGIN - 4f, y, maxX, y + LINE_HEIGHT + 4f, paintHeaderBg)
            canvas.drawText("Componente", colX[0], y + LINE_HEIGHT, paintBodyBold)
            canvas.drawText("Peso", colX[1], y + LINE_HEIGHT, paintBodyBold)
            canvas.drawText("Actividad", colX[2], y + LINE_HEIGHT, paintBodyBold)
            canvas.drawText("Nota", colX[3], y + LINE_HEIGHT, paintBodyBold)
            canvas.drawText("Aporte", colX[4], y + LINE_HEIGHT, paintBodyBold)
            y += LINE_HEIGHT + 8f

            for (comp in materia.componentes) {
                // Fila componente
                canvas.drawText(comp.nombre, colX[0], y + LINE_HEIGHT, paintBodyBold)
                canvas.drawText("${kotlin.math.round(comp.porcentaje * 100).toInt()}%", colX[1], y + LINE_HEIGHT, paintBody)
                y += LINE_HEIGHT + 2f

                for (sub in comp.subNotas) {
                    val valorText = sub.valorEfectivo?.let { GradeCalculator.display(it) } ?: "–"
                    val aporteText = sub.valorEfectivo?.let {
                        GradeCalculator.display(it * sub.porcentajeDelComponente * comp.porcentaje)
                    } ?: "–"

                    canvas.drawText(sub.descripcion, colX[2], y + LINE_HEIGHT, paintBody)
                    canvas.drawText(
                        "${(sub.porcentajeDelComponente * 100).toInt()}%",
                        colX[1], y + LINE_HEIGHT, paintSubtitle
                    )
                    canvas.drawText(valorText, colX[3], y + LINE_HEIGHT, paintBody)
                    canvas.drawText(aporteText, colX[4], y + LINE_HEIGHT, paintBody)
                    y += LINE_HEIGHT + 1f

                    // Detalles (notas compuestas)
                    if (sub.esCompuesta) {
                        for (det in sub.detalles) {
                            val detVal = det.valor?.let { GradeCalculator.display(it) } ?: "–"
                            canvas.drawText(
                                "  · ${det.descripcion}",
                                colX[2], y + LINE_HEIGHT, paintSubtitle
                            )
                            canvas.drawText(
                                "${kotlin.math.round(det.porcentaje * 100).toInt()}%",
                                colX[1], y + LINE_HEIGHT, paintSubtitle
                            )
                            canvas.drawText(detVal, colX[3], y + LINE_HEIGHT, paintSubtitle)
                            y += LINE_HEIGHT
                        }
                    }

                    // Check page overflow
                    if (y > PAGE_HEIGHT - MARGIN - 40f) break
                }

                // Subtotal del componente
                val compPromedio = comp.promedio?.let { GradeCalculator.display(it) } ?: "–"
                val compAporte = comp.aporteAlFinal?.let { GradeCalculator.display(it) } ?: "–"
                canvas.drawText("Subtotal", colX[2], y + LINE_HEIGHT, paintBodyBold)
                canvas.drawText(compPromedio, colX[3], y + LINE_HEIGHT, paintBodyBold)
                canvas.drawText(compAporte, colX[4], y + LINE_HEIGHT, paintBodyBold)
                y += LINE_HEIGHT + 6f

                canvas.drawLine(MARGIN, y, maxX, y, paintLine)
                y += 4f

                if (y > PAGE_HEIGHT - MARGIN - 40f) break
            }
        }

        return y
    }

    // ── Pie de página ──────────────────────────────────────────

    private fun drawFooter(canvas: Canvas) {
        val y = PAGE_HEIGHT - MARGIN + 10f
        canvas.drawLine(MARGIN, y - 8f, PAGE_WIDTH - MARGIN, y - 8f, paintLine)
        canvas.drawText(
            "Generado por Gradify · ${LocalDateTime.now().year}",
            MARGIN, y + 4f, paintSubtitle
        )
    }
}
