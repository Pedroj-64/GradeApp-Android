package com.notasapp.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notasapp.data.local.AppDatabase
import com.notasapp.data.local.UserPreferencesRepository
import com.notasapp.data.local.dao.UsuarioDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Worker de respaldo automático periódico.
 *
 * Crea un archivo JSON de backup cada vez que se ejecuta (típicamente cada 24h).
 * Mantiene un máximo de 5 backups recientes para no acumular archivos innecesarios.
 *
 * Los backups se almacenan en `files/auto_backups/` dentro del directorio
 * interno de la app, accesible sin permisos de almacenamiento externo.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: AppDatabase,
    private val usuarioDao: UsuarioDao,
    private val userPrefsRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "AutoBackupWorker"
        private const val BACKUP_DIR = "auto_backups"
        private const val MAX_BACKUPS = 5
    }

    override suspend fun doWork(): Result {
        Timber.d("$TAG: iniciando respaldo automático")

        val usuario = usuarioDao.getUsuarioActivo().first()
        if (usuario == null) {
            Timber.w("$TAG: sin usuario activo, saltando backup")
            return Result.success()
        }

        return try {
            val json = buildBackupJson(usuario.googleId)
            saveBackupFile(json)
            pruneOldBackups()
            userPrefsRepository.touchLastBackup()
            Timber.i("$TAG: respaldo automático completado exitosamente")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al crear respaldo automático")
            Result.retry()
        }
    }

    /**
     * Builds a JSON backup string mirroring BackupManager v2 format.
     */
    private suspend fun buildBackupJson(usuarioId: String): String {
        val materiaDao = db.materiaDao()
        val componenteDao = db.componenteDao()
        val subNotaDao = db.subNotaDao()
        val subNotaDetailDao = db.subNotaDetailDao()

        val materias = materiaDao.getMateriasByUsuario(usuarioId).first()

        val sb = StringBuilder()
        sb.append("{\n  \"version\": 2,\n  \"timestamp\": \"${Date()}\",\n  \"materias\": [\n")

        materias.forEachIndexed { mIndex, materia ->
            sb.append("    {\n")
            sb.append("      \"nombre\": \"${materia.nombre}\",\n")
            sb.append("      \"periodo\": \"${materia.periodo}\",\n")
            sb.append("      \"creditos\": ${materia.creditos},\n")
            sb.append("      \"escalaMin\": ${materia.escalaMin},\n")
            sb.append("      \"escalaMax\": ${materia.escalaMax},\n")
            sb.append("      \"notaAprobacion\": ${materia.notaAprobacion},\n")

            val componentes = componenteDao.getComponentesByMateriaOnce(materia.id)
            sb.append("      \"componentes\": [\n")
            componentes.forEachIndexed { cIndex, comp ->
                sb.append("        {\n")
                sb.append("          \"nombre\": \"${comp.nombre}\",\n")
                sb.append("          \"porcentaje\": ${comp.porcentaje},\n")

                val subNotas = subNotaDao.getSubNotasByComponenteOnce(comp.id)
                sb.append("          \"subNotas\": [\n")
                subNotas.forEachIndexed { sIndex, subNota ->
                    sb.append("            {\n")
                    sb.append("              \"descripcion\": \"${subNota.descripcion}\",\n")
                    sb.append("              \"porcentajeDelComponente\": ${subNota.porcentajeDelComponente},\n")
                    sb.append("              \"valor\": ${subNota.valor},\n")

                    val detalles = subNotaDetailDao.getBySubNotaOnce(subNota.id)
                    sb.append("              \"detalles\": [\n")
                    detalles.forEachIndexed { dIndex, detalle ->
                        sb.append("                {\n")
                        sb.append("                  \"descripcion\": \"${detalle.descripcion}\",\n")
                        sb.append("                  \"porcentaje\": ${detalle.porcentaje},\n")
                        sb.append("                  \"valor\": ${detalle.valor}\n")
                        sb.append("                }${if (dIndex < detalles.size - 1) "," else ""}\n")
                    }
                    sb.append("              ]\n")
                    sb.append("            }${if (sIndex < subNotas.size - 1) "," else ""}\n")
                }
                sb.append("          ]\n")
                sb.append("        }${if (cIndex < componentes.size - 1) "," else ""}\n")
            }
            sb.append("      ]\n")
            sb.append("    }${if (mIndex < materias.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n}")

        return sb.toString()
    }

    private fun saveBackupFile(json: String) {
        val backupDir = File(applicationContext.filesDir, BACKUP_DIR).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "backup_auto_$timestamp.json"
        File(backupDir, fileName).writeText(json)
        Timber.d("$TAG: archivo guardado → $fileName")
    }

    private fun pruneOldBackups() {
        val backupDir = File(applicationContext.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) return
        val files = backupDir.listFiles { _, name -> name.startsWith("backup_auto_") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (files.size > MAX_BACKUPS) {
            files.drop(MAX_BACKUPS).forEach { old ->
                old.delete()
                Timber.d("$TAG: backup antiguo eliminado → ${old.name}")
            }
        }
    }
}
