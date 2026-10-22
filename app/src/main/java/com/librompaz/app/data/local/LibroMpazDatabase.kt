package com.librompaz.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import com.librompaz.app.model.Perfil

// =====================================================================================
// Base de datos Room (PersistenciaPendiente.kt punto 3): instancia única hilo-seguro.
// Por ahora solo incluye el módulo de sesiones; al crear las demás entidades (usuarios,
// asistencias, evaluaciones, calificaciones, anotaciones) se agregan aquí a
// `entities` y `version`/migraciones correspondientes.
// =====================================================================================

@Database(
    entities = [SesionEntity::class, SesionOaEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ConvertidoresRoom::class)
abstract class LibroMpazDatabase : RoomDatabase() {

    abstract fun sesionDao(): SesionDao

    /**
     * RN-02: deja auditoría automática e inalterable en la MISMA transacción de Room que
     * guarda la sesión (el DAO ya corre dentro de @Transaction, así que ambas escrituras
     * comparten transacción y revierten juntas si algo falla). La tabla `auditoria` se
     * escribe con SQL directo mientras el equipo crea su propia @Entity: así se cumple la
     * regla sin duplicar todavía el esquema de auditoría.
     */
    suspend fun guardarSesionConAuditoria(
        sesion: SesionEntity,
        oaIds: List<String>,
        usuario: String,
        perfil: Perfil,
        detalle: String,
        fechaHora: String,
    ) {
        withTransaction {
            sesionDao().insertarConOas(sesion, oaIds)
            openHelper.writableDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS auditoria (
                    id TEXT PRIMARY KEY NOT NULL,
                    usuario TEXT NOT NULL,
                    perfil TEXT NOT NULL,
                    accion TEXT NOT NULL,
                    detalle TEXT NOT NULL,
                    fechaHora TEXT NOT NULL
                );
                """.trimIndent()
            )
            openHelper.writableDatabase.execSQL(
                """
                INSERT INTO auditoria (id, usuario, perfil, accion, detalle, fechaHora)
                VALUES (?, ?, ?, ?, ?, ?);
                """.trimIndent(),
                arrayOf(
                    "AU${System.currentTimeMillis()}",
                    usuario,
                    perfil.name,
                    "CREAR",
                    detalle,
                    fechaHora,
                )
            )
        }
    }

    companion object {
        private const val NOMBRE_BD = "librompaz.db"

        @Volatile
        private var instancia: LibroMpazDatabase? = null

        /** Singleton con doble chequeo bajo lock (evita abrir la BD más de una vez). */
        fun obtener(contexto: Context): LibroMpazDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    LibroMpazDatabase::class.java,
                    NOMBRE_BD
                )
                    // Con `exportSchema = false` no hay JSON de esquema que versionar;
                    // durante el desarrollo, reconstruir la BD antes que fallar el arranque.
                    .fallbackToDestructiveMigration()
                    .build().also { instancia = it }
            }
    }
}
