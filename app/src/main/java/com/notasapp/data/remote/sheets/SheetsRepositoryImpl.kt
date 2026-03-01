package com.notasapp.data.remote.sheets

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.notasapp.data.local.dao.MateriaDao
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.data.mapper.toDomain
import com.notasapp.data.remote.NetworkResult
import com.notasapp.domain.model.Materia
import com.notasapp.domain.repository.MateriaRepository
import com.notasapp.domain.repository.SheetsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [SheetsRepository].
 *
 * Orquesta las llamadas a [SheetsService] y actualiza la tabla [MateriaDao]
 * con el [Materia.googleSheetsId] devuelto por la API de Google Sheets.
 *
 * Los errores de red se convierten en [NetworkResult.Error] con mensajes
 * legibles para la UI. El caso de permisos faltantes se etiqueta con
 * [NetworkResult.Error.needsUserRecovery] = true.
 */
@Singleton
class SheetsRepositoryImpl @Inject constructor(
    private val sheetsService: SheetsService,
    private val materiaRepository: MateriaRepository,
    private val usuarioDao: UsuarioDao
) : SheetsRepository {

    override suspend fun syncMateria(
        materia: Materia,
        userEmail: String
    ): NetworkResult<String> = runCatching {
        // Obtener o crear el spreadsheet ID
        val spreadsheetId = if (materia.googleSheetsId.isNullOrBlank()) {
            val id = sheetsService.createSpreadsheet(materia, userEmail)
            // Persistir el ID en Room para futuros syncs
            materiaRepository.updateSheetsId(materia.id, id)
            Timber.d("Nuevo spreadsheet creado: $id")
            id
        } else {
            materia.googleSheetsId
        }

        // Escribir datos en la hoja
        sheetsService.writeMateria(spreadsheetId, materia, userEmail)

        Timber.i("Sync OK: materia=${materia.nombre} sheetsId=$spreadsheetId")
        NetworkResult.Success(spreadsheetId)
    }.getOrElse { e ->
        Timber.e(e, "Error al sincronizar materia '${materia.nombre}'")

        // Si el spreadsheet fue eliminado externamente, limpiar la referencia
        // y reintentar con un nuevo spreadsheet en la siguiente sincronización
        if (e is GoogleJsonResponseException) {
            val statusCode = e.statusCode
            if (statusCode == 404 && !materia.googleSheetsId.isNullOrBlank()) {
                Timber.w("Spreadsheet ${materia.googleSheetsId} no encontrado (404), limpiando referencia…")
                materiaRepository.updateSheetsId(materia.id, null)
                return NetworkResult.Error(
                    message = "La hoja fue eliminada. Intenta sincronizar de nuevo para crear una nueva.",
                    cause = e
                )
            }
        }

        when (e) {
            is UserRecoverableAuthIOException -> NetworkResult.Error(
                message = "Necesitas conceder permiso para Google Sheets",
                cause = e,
                needsUserRecovery = true
            )
            is SocketTimeoutException,
            is UnknownHostException -> NetworkResult.Error(
                message = "Sin conexión a internet. Verifica tu red e intenta de nuevo.",
                cause = e
            )
            is IOException -> NetworkResult.Error(
                message = "Error de conexión: ${e.localizedMessage ?: "Error de red"}",
                cause = e
            )
            is NullPointerException -> NetworkResult.Error(
                message = "Error interno: cuenta de Google no configurada correctamente. Cierra sesión e inicia de nuevo.",
                cause = e
            )
            else -> NetworkResult.Error(
                message = "Error inesperado: ${e.localizedMessage ?: e.javaClass.simpleName}",
                cause = e
            )
        }
    }.let { it }

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
