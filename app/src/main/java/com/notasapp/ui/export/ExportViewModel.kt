package com.notasapp.ui.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notasapp.domain.repository.MateriaRepository
import com.notasapp.navigation.Screen
import com.notasapp.utils.ExcelExporter
import com.notasapp.utils.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * ViewModel de la pantalla de exportación.
 *
 * Gestiona tres canales de salida de datos (todos vía SAF):
 *  1. **Excel** (.xlsx) — exporta vía Apache POI al almacenamiento elegido.
 *  2. **PDF** (.pdf) — genera resumen de notas.
 *  3. **Google Drive** — reutiliza el Excel export; el usuario elige Drive
 *     como destino en el selector de archivos del sistema (SAF).
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val materiaRepository: MateriaRepository,
    private val excelExporter: ExcelExporter,
    private val pdfExporter: PdfExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val materiaId: Long = checkNotNull(
        savedStateHandle[Screen.Export.ARG_MATERIA_ID]
    )

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    // ── Excel ────────────────────────────────────────────────────────────────

    /**
     * Nombre sugerido para el archivo .xlsx al abrir el SAF picker.
     * Puede llamarse desde la UI para prerellenar el nombre en el diálogo del sistema.
     */
    fun sugerirNombreArchivo(): String = "notas_materia_${materiaId}.xlsx"

    /**
     * Nombre sugerido para el archivo .pdf al abrir el SAF picker.
     */
    fun sugerirNombrePdf(): String = "notas_materia_${materiaId}.pdf"

    /**
     * Exporta la materia a .xlsx escribiendo en el URI elegido por el usuario (SAF).
     *
     * El usuario abre el selector de archivos del sistema con [ActivityResultContracts.CreateDocument]
     * y elige la carpeta/nombre. Este método escribe el contenido en ese URI.
     *
     * @param uri URI devuelto por [ActivityResultContracts.CreateDocument].
     */
    fun exportarExcelSAF(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null, exportSuccess = false) }
            try {
                val materia = materiaRepository.getMateriaConComponentes(materiaId).first()
                    ?: throw IllegalStateException("Materia no encontrada")

                withContext(Dispatchers.IO) {
                    // Build the bytes fully in memory first
                    val bytes = excelExporter.buildWorkbookBytes(materia)
                    Timber.d("Excel bytes generados: ${bytes.size} para ${materia.nombre}")

                    if (bytes.isEmpty()) {
                        throw IllegalStateException(
                            "El archivo Excel generado tiene 0 bytes — error interno de POI"
                        )
                    }

                    // Write bytes to SAF URI
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("No se pudo abrir el archivo destino")

                    outputStream.use { os ->
                        os.write(bytes)
                        os.flush()
                    }

                    Timber.i("Excel guardado en SAF: $uri (${bytes.size} bytes)")
                }

                _uiState.update {
                    it.copy(
                        isExporting      = false,
                        exportSuccess    = true,
                        exportedFilePath = uri.toString(),
                        exportedFileUri  = uri
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al exportar Excel SAF")
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportError = "[${e.javaClass.simpleName}] ${e.message}${e.cause?.let { c -> " | cause: [${c.javaClass.simpleName}] ${c.message}" } ?: ""}"
                    )
                }
            }
        }
    }

    /**
     * Exporta la materia a .xlsx en el almacenamiento privado de la app (fallback).
     *
     * @return Construye un [Intent] de compartir para que el usuario lo envíe.
     */
    fun exportarExcel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val materia = materiaRepository.getMateriaConComponentes(materiaId).first()
                    ?: throw IllegalStateException("Materia no encontrada")

                val (file, uri) = withContext(Dispatchers.IO) {
                    val f = excelExporter.exportar(context, materia)
                    val u = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        f
                    )
                    f to u
                }

                Timber.i("Excel generado: ${file.absolutePath}")
                _uiState.update {
                    it.copy(
                        isExporting      = false,
                        exportSuccess    = true,
                        exportedFilePath = file.absolutePath,
                        exportedFileUri  = uri
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al exportar Excel")
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportError = "[${e.javaClass.simpleName}] ${e.message}${e.cause?.let { c -> " | cause: [${c.javaClass.simpleName}] ${c.message}" } ?: ""}"
                    )
                }
            }
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    /**
     * Exporta la materia a .pdf escribiendo en el URI elegido por el usuario (SAF).
     */
    fun exportarPdfSAF(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null, exportSuccess = false) }
            try {
                val materia = materiaRepository.getMateriaConComponentes(materiaId).first()
                    ?: throw IllegalStateException("Materia no encontrada")

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        pdfExporter.exportarMateriaToOutputStream(materia, outputStream)
                    } ?: throw IOException("No se pudo abrir el archivo destino")
                }

                Timber.i("PDF guardado en SAF: $uri")
                _uiState.update {
                    it.copy(
                        isExporting      = false,
                        exportSuccess    = true,
                        exportedFilePath = uri.toString(),
                        exportedFileUri  = uri
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al exportar PDF SAF")
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportError = "[${e.javaClass.simpleName}] ${e.message}${e.cause?.let { c -> " | cause: [${c.javaClass.simpleName}] ${c.message}" } ?: ""}"
                    )
                }
            }
        }
    }

    fun clearMessages() = _uiState.update {
        it.copy(
            exportSuccess = false,
            exportError   = null
        )
    }
}

data class ExportUiState(
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val exportedFilePath: String? = null,
    /** URI FileProvider del .xlsx generado, listo para compartir. */
    val exportedFileUri: Uri? = null,
    val exportError: String? = null
)
