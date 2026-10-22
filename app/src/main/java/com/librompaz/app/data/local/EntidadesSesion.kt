package com.librompaz.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

// =====================================================================================
// Entidades Room del módulo "Cobertura curricular" (sesiones de clase).
// Ver data/local/PersistenciaPendiente.kt punto 1: sesiones + tabla intermedia sesion_oa.
// Las listas de String (oaIds) se resuelven con la tabla intermedia mediante @Junction,
// por lo que no se requiere un TypeConverter para ellas.
// =====================================================================================

/** Convertidor de apoyo: listas de String serializadas como JSON simple. */
class ConvertidoresRoom {

    @TypeConverter
    fun deListaString(valor: String?): List<String> =
        if (valor.isNullOrBlank()) emptyList()
        else valor.removePrefix("[").removeSuffix("]").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }

    @TypeConverter
    fun aListaString(lista: List<String>?): String =
        lista?.joinToString(prefix = "[", postfix = "]", separator = ", ") { "\"$it\"" } ?: "[]"
}

/**
 * Fila de la tabla `sesiones`. Corresponde al modelo de dominio [com.librompaz.app.model.SesionClase].
 * RN-02: registradoPor y fechaHoraRegistro los escribe el sistema, nunca el usuario.
 * Índice único en curso+asignatura+fecha+bloque: impide duplicar una misma clase (validación
 * que hoy hace el ViewModel en memoria; aquí la garantiza SQLite).
 */
@Entity(
    tableName = "sesiones",
    indices = [Index(value = ["cursoId", "asignaturaId", "fecha", "bloque"], unique = true)]
)
data class SesionEntity(
    @PrimaryKey val id: String,
    val cursoId: String,
    val asignaturaId: String,
    /** Fecha AAAA-MM-DD (formato del formulario, sin java.time por minSdk 24). */
    val fecha: String,
    val bloque: String,
    val contenido: String,
    val registradoPor: String,
    val fechaHoraRegistro: String,
)

/**
 * Tabla intermedia `sesion_oa` (relación N:N sesión ↔ objetivo de aprendizaje).
 * Clave primaria compuesta (sesionId, oaId): un OA no puede repetirse dentro de la misma sesión.
 */
@Entity(
    tableName = "sesion_oa",
    primaryKeys = ["sesionId", "oaId"],
    indices = [Index("oaId")]
)
data class SesionOaEntity(
    val sesionId: String,
    val oaId: String,
)

/**
 * Proyección relación-completa: una sesión con la lista de OA trabajados, obtenida desde la
 * tabla intermedia `sesion_oa` mediante @Junction. Es el tipo que consume el repositorio para
 * mapear a [com.librompaz.app.model.SesionClase].
 */
data class SesionConOas(
    @Embedded val sesion: SesionEntity,

    @Relation(
        entity = SesionOaEntity::class,
        parentColumn = "id",
        entityColumn = "sesionId",
        projection = ["oaId"]
    )
    val oaIds: List<String>,
)
