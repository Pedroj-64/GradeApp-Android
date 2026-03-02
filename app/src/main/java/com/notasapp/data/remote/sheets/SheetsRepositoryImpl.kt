package com.notasapp.data.remote.sheets

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.data.remote.NetworkResult
import com.notasapp.data.remote.drive.DriveFileNotFoundException
import com.notasapp.data.remote.drive.DriveService
import com.notasapp.domain.model.Materia
import com.notasapp.domain.repository.MateriaRepository
import com.notasapp.domain.repository.SheetsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [SheetsRepository] basada en **Google Drive**.
 *
 * En lugar de usar la API de Google Sheets v4 (que requería múltiples llamadas
 * y era propensa a errores de cuota), ahora:
 * 1. Genera un .xlsx completo con [com.notasapp.utils.ExcelExporter].
 * 2. Lo sube a Google Drive con [DriveService] (una sola llamada multipart).
 *
 * El ID del archivo en Drive se guarda en [Materia.googleSheetsId] (se reutiliza
 * el campo existente en Room para no requerir migración).
 */
@Singleton
class SheetsRepositoryImpl @Inject constructor(
    private val driveService: DriveService,
    private val materiaRepository: MateriaRepository,
    private val usuarioDao: UsuarioDao
) : SheetsRepository {

    override suspend fun syncMateria(
        materia: Materia,
        userEmail: String
    ): NetworkResult<String> {
        // Validar que la materia tenga nombre antes de intentar sincronizar
        if (materia.nombre.isBlank()) {
            return NetworkResult.Error(
                message = "La materia no tiene nombre. Edítala antes de sincronizar."
            )
        }

        return try {

        val result = driveService.uploadMateria(
            materia = materia,
            userEmail = userEmail,
            existingFileId = materia.googleSheetsId
        )

        // Guardar/actualizar el file ID en Room para futuras sincronizaciones
        if (materia.googleSheetsId != result.fileId) {
            materiaRepository.updateSheetsId(materia.id, result.fileId)
        }

        Timber.i("Sync OK: materia=${materia.nombre} driveFileId=${result.fileId}")
        NetworkResult.Success(result.webViewLink)

    } catch (e: DriveFileNotFoundException) {
        // El archivo fue eliminado externamente — limpiar referencia
        Timber.w("Archivo Drive eliminado, limpiando referencia…")
        materiaRepository.updateSheetsId(materia.id, null)
        NetworkResult.Error(
            message = "[DriveFileNotFound] ${e.message} | cause: ${e.cause}",
            cause = e
        )
    } catch (e: UserRecoverableAuthIOException) {
        NetworkResult.Error(
            message = "[UserRecoverableAuth] ${e.message}",
            cause = e,
            needsUserRecovery = true
        )
    } catch (e: Exception) {
        Timber.e(e, "Error al sincronizar '${materia.nombre}'")
        val fullTrace = buildString {
            append("[${e.javaClass.simpleName}] ${e.message}")
            e.cause?.let { append(" | cause: [${it.javaClass.simpleName}] ${it.message}") }
        }
        NetworkResult.Error(
            message = fullTrace,
            cause = e
        )
    }
    }

    override suspend fun syncAllMaterias(userEmail: String): NetworkResult<Int> {
        val usuario = usuarioDao.getUsuarioActivo().first()
            ?: return NetworkResult.Error("No hay usuario activo")

        val materias = materiaRepository.getMateriasByUsuario(usuario.googleId).first()
        if (materias.isEmpty()) return NetworkResult.Success(0)

        var synced = 0
        var lastError: NetworkResult.Error? = null

        for (materia in materias) {
            when (val result = syncMateria(materia, userEmail)) {
                is NetworkResult.Success -> synced++
                is NetworkResult.Error   -> {
                    lastError = result
                    // Si necesita recuperación del usuario, abortar el ciclo
                    if (result.needsUserRecovery) return result
                }
                else -> Unit
            }
        }

        return if (lastError != null && synced == 0) lastError
        else NetworkResult.Success(synced)
    }
}
