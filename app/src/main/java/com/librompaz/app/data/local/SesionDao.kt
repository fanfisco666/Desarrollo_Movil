package com.librompaz.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// =====================================================================================
// DAO de sesiones de clase (PersistenciaPendiente.kt punto 2).
// Cada consulta responde a una necesidad concreta de las pantallas:
//   - RegistroClaseScreen: validar duplicados y guardar la sesión con sus OA.
//   - CoberturaCurricularScreen (UTP): sesiones por curso/asignatura y OA trabajados.
//   - PanelDireccionScreen: métricas consolidadas (contador de sesiones por curso).
// =====================================================================================

@Dao
interface SesionDao {

    /** Todas las sesiones con sus OA, más recientes primero (fuente reactiva de la lista). */
    @Transaction
    @Query("SELECT * FROM sesiones ORDER BY fecha DESC, fechaHoraRegistro DESC")
    fun observarTodas(): Flow<List<SesionConOas>>

    /** Sesiones de un curso y asignatura (lista del docente al abrir un curso). */
    @Transaction
    @Query(
        """
        SELECT * FROM sesiones
        WHERE cursoId = :cursoId AND asignaturaId = :asignaturaId
        ORDER BY fecha DESC, bloque ASC
        """
    )
    fun observarPorCursoYAsignatura(cursoId: String, asignaturaId: String): Flow<List<SesionConOas>>

    /** IDs de los OA ya trabajados en un curso+asignatura (cobertura curricular de UTP, RF pendiente `oasTrabajados()`). */
    @Query(
        """
        SELECT DISTINCT so.oaId
        FROM sesiones s
        INNER JOIN sesion_oa so ON so.sesionId = s.id
        WHERE s.cursoId = :cursoId AND s.asignaturaId = :asignaturaId
        """
    )
    suspend fun oaIdsTrabajados(cursoId: String, asignaturaId: String): List<String>

    /** Total de sesiones (para cargar los datos de ejemplo solo la primera vez). */
    @Query("SELECT COUNT(*) FROM sesiones")
    suspend fun contarTodas(): Int

    /** Métrica de Dirección: cuántas sesiones tiene registradas un curso. */
    @Query("SELECT COUNT(*) FROM sesiones WHERE cursoId = :cursoId")
    suspend fun contarPorCurso(cursoId: String): Int

    /** Chequeo de unicidad RN (curso+asignatura+fecha+bloque) antes de insertar. */
    @Query(
        """
        SELECT COUNT(*) FROM sesiones
        WHERE cursoId = :cursoId AND asignaturaId = :asignaturaId
          AND fecha = :fecha AND LOWER(bloque) = LOWER(:bloque)
        """
    )
    suspend fun hayDuplicada(cursoId: String, asignaturaId: String, fecha: String, bloque: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(sesion: SesionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarVinculos(vinculos: List<SesionOaEntity>)

    @Query("DELETE FROM sesiones WHERE id = :sesionId")
    suspend fun eliminar(sesionId: String)

    /**
     * Inserción atómica: la sesión y sus vínculos con los OA entran en la misma transacción.
     * Si algo falla, nada queda a medio guardar (integridad referencial del registro).
     */
    @Transaction
    suspend fun insertarConOas(sesion: SesionEntity, oaIds: List<String>) {
        insertar(sesion)
        insertarVinculos(oaIds.distinct().map { SesionOaEntity(sesion.id, it) })
    }
}
