package com.notasapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notasapp.data.local.dao.ComponenteDao
import com.notasapp.data.local.dao.ExamenEventDao
import com.notasapp.data.local.dao.MateriaDao
import com.notasapp.data.local.dao.SubNotaDao
import com.notasapp.data.local.dao.SubNotaDetailDao
import com.notasapp.data.local.dao.UsuarioDao
import com.notasapp.data.local.entities.ComponenteEntity
import com.notasapp.data.local.entities.ExamenEventEntity
import com.notasapp.data.local.entities.MateriaEntity
import com.notasapp.data.local.entities.SubNotaDetailEntity
import com.notasapp.data.local.entities.SubNotaEntity
import com.notasapp.data.local.entities.UsuarioEntity

/**
 * Base de datos local de NotasApp (Room / SQLite).
 *
 * Centraliza el acceso a todas las tablas.
 * La instancia es singleton y se provee mediante Hilt (ver [DatabaseModule]).
 *
 * **Versioning:** al agregar columnas o tablas, incrementar [version]
 * y agregar una [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        UsuarioEntity::class,
        MateriaEntity::class,
        ComponenteEntity::class,
        SubNotaEntity::class,
        SubNotaDetailEntity::class,
        ExamenEventEntity::class
    ],
    version = 5,
    exportSchema = true     // genera JSON en /schemas para historial de migraciones
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun materiaDao(): MateriaDao
    abstract fun componenteDao(): ComponenteDao
    abstract fun subNotaDao(): SubNotaDao
    abstract fun subNotaDetailDao(): SubNotaDetailDao
    abstract fun examenEventDao(): ExamenEventDao

    companion object {

        const val DATABASE_NAME = "notas_app.db"

        /**
         * Migración v1 → v2: agrega la columna `fechaLimite` a la tabla `componentes`.
         * Permite guardar fechas de evaluación para el sistema de recordatorios.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE componentes ADD COLUMN fechaLimite INTEGER DEFAULT NULL"
                )
            }
        }

        /**
         * Migración v2 → v3: crea la tabla `sub_nota_details` para sub-notas compuestas.
         * Permite que una sub-nota contenga múltiples detalles con pesos propios.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sub_nota_details` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subNotaId` INTEGER NOT NULL,
                        `descripcion` TEXT NOT NULL,
                        `porcentaje` REAL NOT NULL,
                        `valor` REAL,
                        FOREIGN KEY(`subNotaId`) REFERENCES `sub_notas`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sub_nota_details_subNotaId` ON `sub_nota_details` (`subNotaId`)"
                )
            }
        }

        /**
         * Migración v3 → v4: agrega la columna `creditos` a la tabla `materias`.
         * Permite calcular promedios ponderados por créditos académicos.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE materias ADD COLUMN creditos INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migración v4 → v5: crea la tabla `examen_events` para el calendario académico.
         * Permite registrar exámenes, entregas y quizzes con recordatorios.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `examen_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `materiaId` INTEGER NOT NULL,
                        `titulo` TEXT NOT NULL,
                        `descripcion` TEXT NOT NULL DEFAULT '',
                        `tipoEvento` TEXT NOT NULL DEFAULT 'PARCIAL',
                        `fechaEpochMs` INTEGER NOT NULL,
                        `recordatorioMinutos` INTEGER NOT NULL DEFAULT 60,
                        `recordatorioProgramado` INTEGER NOT NULL DEFAULT 0,
                        `colorInt` INTEGER DEFAULT NULL,
                        `creadoEnMs` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`materiaId`) REFERENCES `materias`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_examen_events_materiaId` ON `examen_events` (`materiaId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_examen_events_fechaEpochMs` ON `examen_events` (`fechaEpochMs`)"
                )
            }
        }

        /**
         * Crea la instancia de Room.
         * Llamado únicamente desde [DatabaseModule] (Hilt).
         * No llamar directamente desde código de producto.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration() // fallback de seguridad para dev
                .build()

        /**
         * Singleton para acceso fuera de Hilt (e.g., [ExamAlarmReceiver]).
         * Thread-safe con double-checked locking.
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context).also { INSTANCE = it }
            }
    }
}
