package com.notasapp.utils

import android.content.Context
import android.os.Environment
import com.notasapp.domain.model.Materia
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genera un archivo .xlsx (OOXML) a partir de una [Materia] con sus componentes,
 * sub-notas y detalles.
 *
 * **Implementación 100 % nativa** — genera directamente el ZIP/XML que compone
 * un .xlsx, sin dependencia de Apache POI. Esto:
 * - Elimina los crash que POI causa en Android
 * - Reduce el tamaño del APK en ~15 MB
 * - Genera archivos compatibles con Excel, Google Sheets y LibreOffice
 *
 * ## Características del archivo generado
 * - **Colores profesionales**: azul oscuro (encabezados), celeste (componentes),
 *   crema (detalles), verde claro (subtotales), dorado (promedio final)
 * - **Fórmulas funcionales**: la persona puede editar notas y ver el resultado
 *   recalculado automáticamente
 * - **Paneles congelados**: la cabecera se mantiene visible al hacer scroll
 * - **Filas fusionadas**: título y datos de la materia abarcan todo el ancho
 *
 * ## Estructura de la hoja
 * ```
 * A: Componente  B: Peso(%)  C: Actividad  D: Peso(corte)%  E: Nota  F: Aporte
 * ```
 */
@Singleton
class ExcelExporter @Inject constructor() {

    // ── Tipos de celda ───────────────────────────────────────────────────

    private sealed class CellData {
        data class Str(val text: String) : CellData()
        data class Num(val value: Double) : CellData()
        data class Formula(val expr: String) : CellData()
        object Empty : CellData()
    }

    private data class XCell(val data: CellData = CellData.Empty, val style: Int = S_DEFAULT)

    // ── Índices de estilo (orden en <cellXfs> de styles.xml) ─────────────

    private companion object {
        const val S_DEFAULT      = 0   // Sin formato especial
        const val S_HEADER       = 1   // Azul oscuro, texto blanco, centrado
        const val S_COMP         = 2   // Celeste, negrita
        const val S_COMP_PCT     = 3   // Celeste, negrita, formato 0%
        const val S_SUB          = 4   // Normal
        const val S_SUB_PCT      = 5   // Formato 0%
        const val S_SUB_NUM      = 6   // Formato 0.00
        const val S_DETAIL       = 7   // Crema
        const val S_DETAIL_PCT   = 8   // Crema + 0%
        const val S_DETAIL_NUM   = 9   // Crema + 0.00
        const val S_TOTAL        = 10  // Verde claro, negrita
        const val S_TOTAL_NUM    = 11  // Verde claro, negrita, 0.00
        const val S_FINAL        = 12  // Dorado, negrita 12pt
        const val S_FINAL_NUM    = 13  // Dorado, negrita 12pt, 0.00
        const val S_TITLE        = 14  // Negrita 13pt
        const val S_ITALIC       = 15  // Cursiva
        const val S_COMPOSITE    = 16  // Cursiva + negrita
        const val S_GREEN        = 17  // Negrita verde
        const val S_RED          = 18  // Negrita rojo
        const val S_INFO         = 19  // Pequeño, cursiva, gris
    }

    /** Resultado intermedio de construir la hoja. */
    private data class BuildResult(
        val sheetName: String,
        val cells: Map<Int, Map<Int, XCell>>,
        val merges: List<String>,
        val colWidths: Map<Int, Double>
    )

    // ═════════════════════════════════════════════════════════════════════
    //  API pública (misma firma que antes)
    // ═════════════════════════════════════════════════════════════════════

    /** Nombre sugerido para el archivo .xlsx. */
    fun sugerirNombreArchivo(materia: Materia): String {
        val safe = materia.nombre.replace(Regex("[^\\w\\s-]"), "").replace(" ", "_")
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "${safe}_${ts}.xlsx"
    }

    /** Exporta al almacenamiento privado de la app (fallback sin SAF). */
    fun exportar(context: Context, materia: Materia): File {
        val bytes = buildWorkbookBytes(materia)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        dir.mkdirs()
        val file = File(dir, sugerirNombreArchivo(materia))
        BufferedOutputStream(FileOutputStream(file)).use { it.write(bytes); it.flush() }
        Timber.i("Excel generado: ${file.absolutePath} (${bytes.size} bytes)")
        return file
    }

    /** Escribe la materia en un OutputStream ya abierto (p. ej. SAF). */
    fun exportarToOutputStream(materia: Materia, outputStream: OutputStream) {
        val bytes = buildWorkbookBytes(materia)
        if (bytes.isEmpty()) throw IllegalStateException("Workbook generó 0 bytes")
        BufferedOutputStream(outputStream).use { it.write(bytes); it.flush() }
        Timber.i("Excel escrito en OutputStream (${bytes.size} bytes)")
    }

    /** Genera el .xlsx completo como ByteArray (listo para escribir a disco). */
    fun buildWorkbookBytes(materia: Materia): ByteArray {
        val result = buildSheetData(materia)
        val bytes = writeXlsx(result)
        Timber.d("XLSX generado: ${bytes.size} bytes para '${materia.nombre}'")
        return bytes
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Construcción de la hoja de datos
    // ═════════════════════════════════════════════════════════════════════

    private fun buildSheetData(materia: Materia): BuildResult {
        val cells = mutableMapOf<Int, MutableMap<Int, XCell>>()
        val merges = mutableListOf<String>()

        fun set(row: Int, col: Int, cell: XCell) {
            cells.getOrPut(row) { mutableMapOf() }[col] = cell
        }

        fun setStr(row: Int, col: Int, text: String, style: Int = S_DEFAULT) =
            set(row, col, XCell(CellData.Str(text), style))

        fun setNum(row: Int, col: Int, value: Double, style: Int = S_DEFAULT) =
            set(row, col, XCell(CellData.Num(value), style))

        fun setFormula(row: Int, col: Int, formula: String, style: Int = S_DEFAULT) =
            set(row, col, XCell(CellData.Formula(formula), style))

        fun setEmpty(row: Int, col: Int, style: Int = S_DEFAULT) =
            set(row, col, XCell(CellData.Empty, style))

        val sheetName = sanitizeSheetName("${materia.nombre} - ${materia.periodo ?: ""}")

        // ── Fila 0: Título ────────────────────────────────────────────
        val titulo = "${materia.nombre}  ·  Período: ${materia.periodo ?: "-"}  ·  Escala: ${materia.escalaMax.toInt()}"
        setStr(0, 0, titulo, S_TITLE)
        for (c in 1..5) setEmpty(0, c, S_TITLE)
        merges.add("A1:F1")

        // ── Fila 1: Profesor / nota mínima ────────────────────────────
        val prof = if (!materia.profesor.isNullOrBlank()) "Profesor: ${materia.profesor}  ·  " else ""
        setStr(1, 0, "${prof}Nota mínima: ${materia.notaAprobacion}", S_INFO)
        for (c in 1..5) setEmpty(1, c, S_INFO)
        merges.add("A2:F2")

        // ── Fila 2: separador vacío ───────────────────────────────────
        // (no se escribe nada)

        // ── Fila 3: Encabezados ───────────────────────────────────────
        val headerRow = 3
        listOf("Componente", "Peso %", "Actividad", "Peso (corte) %", "Nota", "Aporte")
            .forEachIndexed { col, text -> setStr(headerRow, col, text, S_HEADER) }

        var rowIndex = headerRow + 1
        val subtotalRowIndices = mutableListOf<Int>()

        // ── Datos: un bloque por componente ───────────────────────────
        materia.componentes.forEach { componente ->
            val compRowIdx = rowIndex
            val compExcel = compRowIdx + 1  // 1-based para fórmulas

            // Fila del componente
            setStr(rowIndex, 0, componente.nombre, S_COMP)
            setNum(rowIndex, 1, componente.porcentaje.toDouble(), S_COMP_PCT)
            for (c in 2..5) setEmpty(rowIndex, c, S_COMP)
            rowIndex++

            val subAporteRefs = mutableListOf<String>()

            if (componente.subNotas.isEmpty()) {
                setStr(rowIndex, 2, "(sin actividades registradas)", S_SUB)
                rowIndex++
            } else {
                componente.subNotas.forEach { subNota ->
                    val subExcel = rowIndex + 1

                    if (subNota.esCompuesta) {
                        // ── Sub-nota COMPUESTA ────────────────────────
                        val compuestaHeaderIdx = rowIndex
                        val compuestaHeaderExcel = compuestaHeaderIdx + 1
                        rowIndex++  // reservar fila para encabezado

                        val detStartExcel = rowIndex + 1
                        subNota.detalles.forEach { detalle ->
                            setStr(rowIndex, 2, "  · ${detalle.descripcion}", S_DETAIL)
                            setNum(rowIndex, 3, detalle.porcentaje.toDouble(), S_DETAIL_PCT)
                            if (detalle.valor != null) {
                                setNum(rowIndex, 4, detalle.valor!!.toDouble(), S_DETAIL_NUM)
                            } else {
                                setEmpty(rowIndex, 4, S_DETAIL_NUM)
                            }
                            setEmpty(rowIndex, 5, S_DETAIL_NUM)
                            rowIndex++
                        }
                        val detEndExcel = rowIndex  // exclusive (1-based)

                        // Fila reservada del encabezado compuesto
                        val totalPct = subNota.detalles.sumOf { it.porcentaje.toDouble() }
                        setStr(compuestaHeaderIdx, 2, "${subNota.descripcion} [compuesta]", S_COMPOSITE)
                        setNum(compuestaHeaderIdx, 3, subNota.porcentajeDelComponente.toDouble(), S_SUB_PCT)

                        if (subNota.detalles.isNotEmpty() && totalPct > 0) {
                            val eRange = "E$detStartExcel:E${detEndExcel - 1}"
                            val dRange = "D$detStartExcel:D${detEndExcel - 1}"
                            setFormula(compuestaHeaderIdx, 4,
                                "SUMPRODUCT($eRange,$dRange)/SUM($dRange)", S_SUB_NUM)
                        } else {
                            setEmpty(compuestaHeaderIdx, 4, S_SUB_NUM)
                        }
                        setFormula(compuestaHeaderIdx, 5,
                            "E$compuestaHeaderExcel*D$compuestaHeaderExcel", S_SUB_NUM)

                        subAporteRefs.add("F$compuestaHeaderExcel")
                    } else {
                        // ── Sub-nota SIMPLE ───────────────────────────
                        setStr(rowIndex, 2, subNota.descripcion, S_SUB)
                        setNum(rowIndex, 3, subNota.porcentajeDelComponente.toDouble(), S_SUB_PCT)
                        if (subNota.valor != null) {
                            setNum(rowIndex, 4, subNota.valor!!.toDouble(), S_SUB_NUM)
                        } else {
                            setEmpty(rowIndex, 4, S_SUB_NUM)
                        }
                        setFormula(rowIndex, 5, "E$subExcel*D$subExcel", S_SUB_NUM)
                        subAporteRefs.add("F$subExcel")
                        rowIndex++
                    }
                }
            }

            // ── Fila SUBTOTAL del componente ──────────────────────────
            val subtotalExcel = rowIndex + 1
            subtotalRowIndices.add(rowIndex)

            setEmpty(rowIndex, 0, S_TOTAL)
            setEmpty(rowIndex, 1, S_TOTAL)
            setStr(rowIndex, 2, "SUBTOTAL", S_TOTAL)
            setEmpty(rowIndex, 3, S_TOTAL)

            if (subAporteRefs.isNotEmpty()) {
                setFormula(rowIndex, 4, subAporteRefs.joinToString("+"), S_TOTAL_NUM)
            } else {
                setNum(rowIndex, 4, 0.0, S_TOTAL_NUM)
            }
            setFormula(rowIndex, 5, "E$subtotalExcel*B$compExcel", S_TOTAL_NUM)
            rowIndex++

            // Separador vacío entre componentes
            rowIndex++
        }

        // ── Fila PROMEDIO FINAL ───────────────────────────────────────
        setStr(rowIndex, 0, "PROMEDIO FINAL", S_FINAL)
        for (c in 1..3) setEmpty(rowIndex, c, S_FINAL)
        merges.add("A${rowIndex + 1}:D${rowIndex + 1}")

        val finalFormula = subtotalRowIndices
            .joinToString("+") { "F${it + 1}" }
            .ifEmpty { "0" }
        setFormula(rowIndex, 4, finalFormula, S_FINAL_NUM)
        setEmpty(rowIndex, 5, S_FINAL_NUM)
        rowIndex++

        // ── Info: acumulado y estado ──────────────────────────────────
        setStr(rowIndex, 0, "Acumulado: ${materia.acumuladoDisplay}", S_ITALIC)
        val evaluado = kotlin.math.round(materia.porcentajeEvaluado * 100).toInt()
        setStr(rowIndex, 2, "Evaluado: $evaluado%", S_ITALIC)
        val estado = if (materia.yaAprobo) "¡APROBADO!" else "EN PROGRESO"
        setStr(rowIndex, 4, estado, if (materia.yaAprobo) S_GREEN else S_RED)
        rowIndex++

        if (materia.yaAprobo) {
            setStr(rowIndex, 0,
                "¡Felicidades! Ya superaste el mínimo de ${materia.notaAprobacion} para aprobar.",
                S_GREEN)
            rowIndex++
        } else {
            materia.notaNecesariaParaAprobar?.let { necesita ->
                setStr(rowIndex, 0,
                    "Necesitas promediar ≈ ${String.format(Locale.US, "%.2f", necesita)} en lo restante para aprobar.",
                    S_RED)
                rowIndex++
            }
        }

        val colWidths = mapOf(
            0 to 28.0,   // Componente
            1 to 12.0,   // Peso %
            2 to 35.0,   // Actividad
            3 to 16.0,   // Peso (corte) %
            4 to 12.0,   // Nota
            5 to 12.0    // Aporte
        )

        return BuildResult(sheetName, cells, merges, colWidths)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Generación del ZIP/OOXML
    // ═════════════════════════════════════════════════════════════════════

    private fun writeXlsx(result: BuildResult): ByteArray {
        // Recopilar strings únicos
        val stringSet = LinkedHashSet<String>()
        result.cells.values.flatMap { it.values }.forEach { cell ->
            if (cell.data is CellData.Str) stringSet.add(cell.data.text)
        }
        val stringList = stringSet.toList()
        val stringIndex = stringList.withIndex().associate { (i, s) -> s to i }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zip ->
            zip.entry("[Content_Types].xml", xmlContentTypes())
            zip.entry("_rels/.rels", xmlRels())
            zip.entry("xl/workbook.xml", xmlWorkbook(result.sheetName))
            zip.entry("xl/_rels/workbook.xml.rels", xmlWorkbookRels())
            zip.entry("xl/styles.xml", xmlStyles())
            zip.entry("xl/sharedStrings.xml", xmlSharedStrings(stringList))
            zip.entry("xl/worksheets/sheet1.xml", xmlWorksheet(result, stringIndex))
        }
        return baos.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  XML — Content Types, Relationships, Workbook
    // ═════════════════════════════════════════════════════════════════════

    private fun xmlContentTypes() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        append("</Types>")
    }

    private fun xmlRels() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""")
        append("</Relationships>")
    }

    private fun xmlWorkbook(sheetName: String) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheets><sheet name="${esc(sheetName)}" sheetId="1" r:id="rId1"/></sheets>""")
        append("</workbook>")
    }

    private fun xmlWorkbookRels() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""")
        append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("</Relationships>")
    }

    // ═════════════════════════════════════════════════════════════════════
    //  XML — Styles (colores, fuentes, bordes, formatos de número)
    // ═════════════════════════════════════════════════════════════════════

    private fun xmlStyles() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // ── Fuentes ──────────────────────────────────────────────────
        append("""<fonts count="10">""")
        // 0: Default
        append("""<font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""")
        // 1: Bold White (header)
        append("""<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>""")
        // 2: Bold
        append("""<font><b/><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""")
        // 3: Bold 12pt (final)
        append("""<font><b/><sz val="12"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""")
        // 4: Bold 13pt (title)
        append("""<font><b/><sz val="13"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""")
        // 5: Italic
        append("""<font><i/><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""")
        // 6: Italic Bold (composite)
        append("""<font><b/><i/><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""")
        // 7: Bold Green
        append("""<font><b/><sz val="11"/><color rgb="FF00B050"/><name val="Calibri"/><family val="2"/></font>""")
        // 8: Bold Red
        append("""<font><b/><sz val="11"/><color rgb="FFFF0000"/><name val="Calibri"/><family val="2"/></font>""")
        // 9: Small Italic Gray (info)
        append("""<font><i/><sz val="10"/><color rgb="FF666666"/><name val="Calibri"/><family val="2"/></font>""")
        append("</fonts>")

        // ── Rellenos ─────────────────────────────────────────────────
        append("""<fills count="7">""")
        // 0, 1: obligatorios por spec
        append("""<fill><patternFill patternType="none"/></fill>""")
        append("""<fill><patternFill patternType="gray125"/></fill>""")
        // 2: Azul oscuro — encabezados (#2F5496)
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FF2F5496"/><bgColor indexed="64"/></patternFill></fill>""")
        // 3: Celeste — componentes (#D6E4F0)
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFD6E4F0"/><bgColor indexed="64"/></patternFill></fill>""")
        // 4: Crema — detalles (#FFF2CC)
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/><bgColor indexed="64"/></patternFill></fill>""")
        // 5: Verde claro — subtotales (#E2EFDA)
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFE2EFDA"/><bgColor indexed="64"/></patternFill></fill>""")
        // 6: Dorado — promedio final (#FFE699)
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFFFE699"/><bgColor indexed="64"/></patternFill></fill>""")
        append("</fills>")

        // ── Bordes ───────────────────────────────────────────────────
        append("""<borders count="2">""")
        // 0: Sin bordes
        append("""<border><left/><right/><top/><bottom/><diagonal/></border>""")
        // 1: Borde inferior fino (para encabezados)
        append("""<border><left/><right/><top/><bottom style="thin"><color indexed="64"/></bottom><diagonal/></border>""")
        append("</borders>")

        // ── Formato base ─────────────────────────────────────────────
        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")

        // ── Formatos de celda (cellXfs) ──────────────────────────────
        // Cada índice aquí corresponde a S_DEFAULT, S_HEADER, etc.
        append("""<cellXfs count="20">""")
        // 0: S_DEFAULT
        xf(0, 0, 0, 0)
        // 1: S_HEADER — azul oscuro, blanco, centrado, borde inferior
        xf(0, 1, 2, 1, align = "center", applyAll = true)
        // 2: S_COMP — celeste, negrita
        xf(0, 2, 3, 0, font = true, fill = true)
        // 3: S_COMP_PCT — celeste, negrita, 0%
        xf(9, 2, 3, 0, numFmt = true, font = true, fill = true)
        // 4: S_SUB — normal (igual que default)
        xf(0, 0, 0, 0)
        // 5: S_SUB_PCT — 0%
        xf(9, 0, 0, 0, numFmt = true)
        // 6: S_SUB_NUM — 0.00
        xf(2, 0, 0, 0, numFmt = true)
        // 7: S_DETAIL — crema
        xf(0, 0, 4, 0, fill = true)
        // 8: S_DETAIL_PCT — crema + 0%
        xf(9, 0, 4, 0, numFmt = true, fill = true)
        // 9: S_DETAIL_NUM — crema + 0.00
        xf(2, 0, 4, 0, numFmt = true, fill = true)
        // 10: S_TOTAL — verde, negrita
        xf(0, 2, 5, 0, font = true, fill = true)
        // 11: S_TOTAL_NUM — verde, negrita, 0.00
        xf(2, 2, 5, 0, numFmt = true, font = true, fill = true)
        // 12: S_FINAL — dorado, negrita 12pt
        xf(0, 3, 6, 0, font = true, fill = true)
        // 13: S_FINAL_NUM — dorado, negrita 12pt, 0.00
        xf(2, 3, 6, 0, numFmt = true, font = true, fill = true)
        // 14: S_TITLE — negrita 13pt
        xf(0, 4, 0, 0, font = true)
        // 15: S_ITALIC — cursiva
        xf(0, 5, 0, 0, font = true)
        // 16: S_COMPOSITE — cursiva + negrita
        xf(0, 6, 0, 0, font = true)
        // 17: S_GREEN — negrita verde
        xf(0, 7, 0, 0, font = true)
        // 18: S_RED — negrita rojo
        xf(0, 8, 0, 0, font = true)
        // 19: S_INFO — pequeño cursiva gris
        xf(0, 9, 0, 0, font = true)
        append("</cellXfs>")

        append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
        append("</styleSheet>")
    }

    /** Helper para generar un <xf> de cellXfs dentro de un StringBuilder. */
    private fun StringBuilder.xf(
        numFmtId: Int, fontId: Int, fillId: Int, borderId: Int,
        numFmt: Boolean = false, font: Boolean = false,
        fill: Boolean = false, border: Boolean = false,
        align: String? = null, applyAll: Boolean = false
    ) {
        append("""<xf numFmtId="$numFmtId" fontId="$fontId" fillId="$fillId" borderId="$borderId" xfId="0"""")
        if (numFmt || applyAll) append(""" applyNumberFormat="1"""")
        if (font || applyAll) append(""" applyFont="1"""")
        if (fill || applyAll) append(""" applyFill="1"""")
        if (border || applyAll) append(""" applyBorder="1"""")
        if (align != null) {
            append(""" applyAlignment="1"><alignment horizontal="$align"/></xf>""")
        } else {
            append("/>")
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  XML — Shared Strings
    // ═════════════════════════════════════════════════════════════════════

    private fun xmlSharedStrings(strings: List<String>) = buildString {
        val n = strings.size
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="$n" uniqueCount="$n">""")
        strings.forEach { s -> append("<si><t>").append(esc(s)).append("</t></si>") }
        append("</sst>")
    }

    // ═════════════════════════════════════════════════════════════════════
    //  XML — Worksheet (datos, fórmulas, merge, freeze)
    // ═════════════════════════════════════════════════════════════════════

    private fun xmlWorksheet(result: BuildResult, stringIndex: Map<String, Int>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Panel congelado debajo del encabezado (fila 4)
        append("<sheetViews>")
        append("""<sheetView tabSelected="1" workbookViewId="0">""")
        append("""<pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/>""")
        append("</sheetView></sheetViews>")

        // Anchos de columna
        append("<cols>")
        result.colWidths.toSortedMap().forEach { (col, width) ->
            val c = col + 1
            append("""<col min="$c" max="$c" width="$width" customWidth="1"/>""")
        }
        append("</cols>")

        // Datos de la hoja
        append("<sheetData>")
        result.cells.keys.sorted().forEach { rowIdx ->
            val rowCells = result.cells[rowIdx] ?: return@forEach
            val rowExcel = rowIdx + 1
            append("""<row r="$rowExcel">""")
            rowCells.keys.sorted().forEach { colIdx ->
                val cell = rowCells[colIdx] ?: return@forEach
                val ref = cellRef(rowIdx, colIdx)
                when (cell.data) {
                    is CellData.Str -> {
                        val idx = stringIndex[cell.data.text] ?: 0
                        append("""<c r="$ref" t="s" s="${cell.style}"><v>$idx</v></c>""")
                    }
                    is CellData.Num -> {
                        // Usar formato invariante (punto decimal) para XML
                        val v = String.format(Locale.US, "%g", cell.data.value)
                        append("""<c r="$ref" s="${cell.style}"><v>$v</v></c>""")
                    }
                    is CellData.Formula -> {
                        append("""<c r="$ref" s="${cell.style}"><f>${esc(cell.data.expr)}</f></c>""")
                    }
                    is CellData.Empty -> {
                        append("""<c r="$ref" s="${cell.style}"/>""")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData>")

        // Celdas fusionadas
        if (result.merges.isNotEmpty()) {
            append("""<mergeCells count="${result.merges.size}">""")
            result.merges.forEach { ref -> append("""<mergeCell ref="$ref"/>""") }
            append("</mergeCells>")
        }

        append("</worksheet>")
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════

    /** Convierte índice de columna (0-based) a letra de Excel (A, B, ..., Z, AA, ...). */
    private fun colRef(col: Int): String {
        var c = col
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + c % 26))
            c = c / 26 - 1
        } while (c >= 0)
        return sb.toString()
    }

    /** Referencia de celda: "A1", "B3", etc. */
    private fun cellRef(row: Int, col: Int) = "${colRef(col)}${row + 1}"

    /** Escapa caracteres XML especiales. */
    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Limita y sanitiza el nombre de hoja (máx. 31 chars, sin caracteres prohibidos). */
    private fun sanitizeSheetName(name: String): String =
        name.take(31).replace(Regex("[\\[\\]\\*\\?/\\\\:]"), "")
}
