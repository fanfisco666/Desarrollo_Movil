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
    entities = [SesionEntity::class, SesionOaEntity::class, AuditoriaEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(ConvertidoresRoom::class)
abstract class LibroMpazDatabase : RoomDatabase() {

    abstract fun sesionDao(): SesionDao
    abstract fun auditoriaDao(): AuditoriaDao

    /**
     * RN-02: deja auditoría automática e inalterable en la MISMA transacción de Room que
     * guarda la sesión (el DAO ya corre dentro de @Transaction, así que ambas escrituras
     * comparten transacción y revierten juntas si algo falla). La tabla `auditoria` es una
     * @Entity propia (AuditoriaEntity) y su DAO solo inserta y consulta.
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
            auditoriaDao().insertar(
                AuditoriaEntity(
                    id = "AU${System.currentTimeMillis()}",
                    usuario = usuario,
                    perfil = perfil.name,
                    accion = "CREAR",
                    detalle = detalle,
                    fechaHora = fechaHora,
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
