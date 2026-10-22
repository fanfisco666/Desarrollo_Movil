package com.librompaz.app.data.repository

import com.librompaz.app.data.local.LibroMpazDatabase
import com.librompaz.app.data.local.SesionConOas
import com.librompaz.app.data.local.SesionEntity
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.SesionClase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repositorio del módulo "Cobertura curricular" (sesiones de clase).
 *
 * Patrón de repositorio clásico de la guía de arquitectura de Android: es la ÚNICA fuente de
 * verdad de las sesiones para la capa de presentación (ViewModel). Encapsula Room, corre las
 * operaciones de escritura fuera del hilo principal y expone [Flow] reactivos que las pantallas
 * observan vía `stateIn(...)`.
 *
 * Cumple las reglas de negocio del caso:
 *  - RN-02 (auditoría): cada guardado deja un [RegistroAuditoria] en la MISMA transacción; si
 *    la auditoría falla, revierte también la sesión (todo o nada).
 *  - Unicidad curso+asignatura+fecha+bloque: se valida aquí y además lo garantiza el índice
 *    único de la tabla `sesiones`.
 *  - El id y la marca temporal los genera el sistema, nunca el usuario.
 */
class SesionRepository(
    private val db: LibroMpazDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val dao = db.sesionDao()

    /** Todas las sesiones con sus OA, reactivas (cualquier cambio en Room re-emite la lista). */
    fun observarSesiones(): Flow<List<SesionClase>> =
        dao.observarTodas().map { filas -> filas.map { it.aModelo() } }

    /** Sesiones de un curso y asignatura concretos (pantalla del docente). */
    fun observarSesionesDe(cursoId: String, asignaturaId: String): Flow<List<SesionClase>> =
        dao.observarPorCursoYAsignatura(cursoId, asignaturaId).map { filas -> filas.map { it.aModelo() } }

    /** OA ya trabajados en un curso+asignatura: alimenta la cobertura curricular de UTP. */
    suspend fun oaIdsTrabajados(cursoId: String, asignaturaId: String): Set<String> =
        withContext(ioDispatcher) { dao.oaIdsTrabajados(cursoId, asignaturaId).toSet() }

    /** Contador de sesiones por curso: métrica del panel de Dirección. */
    suspend fun contarSesionesPorCurso(cursoId: String): Int =
        withContext(ioDispatcher) { dao.contarPorCurso(cursoId) }

    /**
     * Guarda una sesión con sus OA y deja auditoría en la misma transacción (RN-02).
     *
     * @param registradoPor nombre del usuario activo (lo pasa el ViewModel desde la sesión).
     * @param perfil perfil del usuario activo, para la traza de auditoría.
     * @return null si se guardó correctamente, o el mensaje de error que la pantalla debe mostrar.
     */
    suspend fun guardar(
        cursoId: String,
        asignaturaId: String,
        fecha: String,
        bloque: String,
        oaIds: List<String>,
        contenido: String,
        registradoPor: String,
        perfil: Perfil,
        nombreCurso: String,
        nombreAsignatura: String,
    ): String? = withContext(ioDispatcher) {
        if (oaIds.isEmpty()) return@withContext "Marca al menos un OA."

        // Doble validación de unicidad (la barrera real es el índice único de la tabla).
        if (dao.hayDuplicada(cursoId, asignaturaId, fecha, bloque) > 0) {
            return@withContext "Ya existe una clase registrada para esa fecha y bloque."
        }

        val ahora = ahora()
        val entity = SesionEntity(
            id = "S${System.currentTimeMillis()}",
            cursoId = cursoId,
            asignaturaId = asignaturaId,
            fecha = fecha,
            bloque = bloque,
            contenido = contenido,
            registradoPor = registradoPor,
            fechaHoraRegistro = ahora,
        )
        val detalle = "Sesión de clase $nombreCurso, $nombreAsignatura, $fecha $bloque"

        try {
            db.guardarSesionConAuditoria(
                sesion = entity,
                oaIds = oaIds.distinct(),
                usuario = registradoPor,
                perfil = perfil,
                detalle = detalle,
                fechaHora = ahora,
            )
        } catch (_: Exception) {
            // Incluye UNIQUE constraint failed del índice de duplicados (carrera entre
            // la validación y el insert): la BD siempre tiene la última palabra.
            return@withContext "No se pudo guardar la sesión (posible duplicado o error de base de datos)."
        }
        null
    }

    /** Borrado explícito (solo tras el diálogo de confirmación de la pantalla). */
    suspend fun eliminar(sesionId: String) = withContext(ioDispatcher) { dao.eliminar(sesionId) }

    // ---------- Mapeo entidad ↔ modelo de dominio ----------

    private fun SesionConOas.aModelo(): SesionClase = SesionClase(
        id = sesion.id,
        cursoId = sesion.cursoId,
        asignaturaId = sesion.asignaturaId,
        fecha = sesion.fecha,
        bloque = sesion.bloque,
        oaIds = oaIds,
        contenido = sesion.contenido,
        registradoPor = sesion.registradoPor,
        fechaHoraRegistro = sesion.fechaHoraRegistro,
    )

    /** Formato "yyyy-MM-dd HH:mm", idéntico al que usa el ViewModel hoy (sin java.time: minSdk 24). */
    private fun ahora(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
