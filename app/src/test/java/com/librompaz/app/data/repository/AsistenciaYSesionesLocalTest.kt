package com.librompaz.app.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.librompaz.app.data.MockData
import com.librompaz.app.data.local.AcademicoDao
import com.librompaz.app.data.local.AnotacionDao
import com.librompaz.app.data.local.AppDatabase
import com.librompaz.app.data.local.AsistenciaDao
import com.librompaz.app.data.local.AuditoriaDao
import com.librompaz.app.data.local.CalificacionDao
import com.librompaz.app.data.local.SesionDao
import com.librompaz.app.data.local.UsuarioDao
import com.librompaz.app.domain.MENSAJE_LISTA_ASISTENCIA
import com.librompaz.app.domain.MENSAJE_OA_AJENOS
import com.librompaz.app.domain.MENSAJE_SESION_DUPLICADA
import com.librompaz.app.domain.errorListaAsistencia
import com.librompaz.app.domain.erroresDeSesionNueva
import com.librompaz.app.domain.primerMensaje
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.SesionClase
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de Asistencia y Sesiones de clase en el Repositorio Local (Matías).
 * Cubre guardar y consultar asistencia, crear sesiones de clase y la auditoría automática en Room.
 */
class AsistenciaYSesionesLocalTest {

    private val db = mockk<AppDatabase>(relaxed = true)
    private val usuarioDao = mockk<UsuarioDao>(relaxed = true)
    private val academicoDao = mockk<AcademicoDao>(relaxed = true)
    private val asistenciaDao = mockk<AsistenciaDao>(relaxed = true)
    private val sesionDao = mockk<SesionDao>(relaxed = true)
    private val calificacionDao = mockk<CalificacionDao>(relaxed = true)
    private val anotacionDao = mockk<AnotacionDao>(relaxed = true)
    private val auditoriaDao = mockk<AuditoriaDao>(relaxed = true)

    private val docente = MockData.usuarios.first { it.usuario == "docente.mat@libromp.cl" }
    private lateinit var repository: LibroMpazRepository

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction<Any?>(any()) } answers {
            val block = secondArg<suspend () -> Any?>()
            runBlocking { block() }
        }

        every { db.usuarioDao() } returns usuarioDao
        every { db.academicoDao() } returns academicoDao
        every { db.asistenciaDao() } returns asistenciaDao
        every { db.sesionDao() } returns sesionDao
        every { db.calificacionDao() } returns calificacionDao
        every { db.anotacionDao() } returns anotacionDao
        every { db.auditoriaDao() } returns auditoriaDao

        repository = LibroMpazRepository(db)
    }

    // ---------- Pruebas de Asistencia ----------

    @Test
    fun guardarAsistenciaNueva_insertaRegistrosYAuditaComoCrear() = runBlocking {
        val cursoId = "C3A"
        val fecha = "2026-11-20"
        val estados = mapOf("E3A-1" to EstadoAsistencia.PRESENTE, "E3A-2" to EstadoAsistencia.AUSENTE)
        val detalle = "Registro de asistencia clase 1"

        coEvery { asistenciaDao.delCursoEnFecha(cursoId, fecha) } returns emptyList()

        val resultado = repository.guardarAsistencia(docente, cursoId, fecha, estados, detalle)

        assertTrue(resultado is com.librompaz.app.data.remote.ApiResult.Exito)

        val slotRegistros = slot<List<RegistroAsistencia>>()
        coVerify { asistenciaDao.guardar(capture(slotRegistros)) }
        assertEquals(2, slotRegistros.captured.size)
        assertEquals("E3A-1", slotRegistros.captured[0].estudianteId)
        assertEquals(EstadoAsistencia.PRESENTE, slotRegistros.captured[0].estado)

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals(docente.nombre, slotAuditoria.captured.usuario)
        assertEquals("CREAR", slotAuditoria.captured.accion)
        assertEquals(detalle, slotAuditoria.captured.detalle)
    }

    @Test
    fun guardarAsistenciaExistente_auditaComoModificar() = runBlocking {
        val cursoId = "C3A"
        val fecha = "2026-11-20"
        val previas = listOf(
            RegistroAsistencia("E3A-1_2026-11-20", "E3A-1", cursoId, fecha, EstadoAsistencia.PRESENTE, docente.nombre, "2026-11-20 08:00:00")
        )
        val nuevosEstados = mapOf("E3A-1" to EstadoAsistencia.ATRASADO)
        val detalle = "Corrección de asistencia"

        coEvery { asistenciaDao.delCursoEnFecha(cursoId, fecha) } returns previas

        repository.guardarAsistencia(docente, cursoId, fecha, nuevosEstados, detalle)

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("MODIFICAR", slotAuditoria.captured.accion)
        assertEquals(detalle, slotAuditoria.captured.detalle)
    }

    @Test
    fun asistenciaDe_retornaDatosDelDao() = runBlocking {
        val cursoId = "C3A"
        val fecha = "2026-11-20"
        val esperadas = listOf(
            RegistroAsistencia("E3A-1_2026-11-20", "E3A-1", cursoId, fecha, EstadoAsistencia.PRESENTE, docente.nombre, "2026-11-20 08:00:00")
        )
        coEvery { asistenciaDao.delCursoEnFecha(cursoId, fecha) } returns esperadas

        val resultado = repository.asistenciaDe(cursoId, fecha)
        assertEquals(esperadas, resultado)
    }

    // ---------- Pruebas de Sesiones de Clase ----------

    @Test
    fun guardarSesionNueva_insertaSesionYAuditaComoCrear() = runBlocking {
        val cursoId = "C3A"
        val asignaturaId = "MAT"
        val fecha = "2026-11-20"
        val bloque = "Bloque 1"
        val oaIds = listOf("OA-MAT-1")
        val contenido = "Operaciones básicas"
        val detalle = "Clase de suma y resta"

        val resultado = repository.guardarSesion(docente, cursoId, asignaturaId, fecha, bloque, oaIds, contenido, detalle)

        assertTrue(resultado is com.librompaz.app.data.remote.ApiResult.Exito)

        val slotSesion = slot<SesionClase>()
        coVerify { sesionDao.insertar(capture(slotSesion)) }
        assertEquals(cursoId, slotSesion.captured.cursoId)
        assertEquals(asignaturaId, slotSesion.captured.asignaturaId)
        assertEquals(bloque, slotSesion.captured.bloque)
        assertEquals(oaIds, slotSesion.captured.oaIds)
        assertEquals(contenido, slotSesion.captured.contenido)
        assertEquals(docente.nombre, slotSesion.captured.registradoPor)

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("CREAR", slotAuditoria.captured.accion)
        assertEquals(detalle, slotAuditoria.captured.detalle)
    }

    // ---------- Integración de Reglas de Negocio de Asistencia y Sesiones ----------

    @Test
    fun validacionSesion_detectaBloqueDuplicado() {
        val existentes = listOf(
            SesionClase("S1", "C3A", "MAT", "2026-11-20", "Bloque 1", listOf("OA-MAT-1"), "Sumas", "Docente", "2026-11-20 08:00:00")
        )
        val oasDocente = listOf(ObjetivoAprendizaje("OA-MAT-1", "MAT", "Código 1", "Descripción 1"))

        val errores = erroresDeSesionNueva(
            fecha = "2026-11-20",
            bloque = "Bloque 1",
            oaIds = listOf("OA-MAT-1"),
            contenido = "Repaso sumas",
            hoy = "2026-11-20",
            objetivosDeLaAsignatura = oasDocente,
            existentes = existentes,
            cursoId = "C3A",
            asignaturaId = "MAT"
        )

        assertEquals(MENSAJE_SESION_DUPLICADA, errores.bloque)
        assertEquals(MENSAJE_SESION_DUPLICADA, errores.primerMensaje())
    }

    @Test
    fun validacionSesion_detectaOAAjenos() {
        val existentes = emptyList<SesionClase>()
        val oasDocente = listOf(ObjetivoAprendizaje("OA-MAT-1", "MAT", "Código 1", "Descripción 1"))

        val errores = erroresDeSesionNueva(
            fecha = "2026-11-20",
            bloque = "Bloque 2",
            oaIds = listOf("OA-LEN-1"), // OA de otra asignatura
            contenido = "Lectura",
            hoy = "2026-11-20",
            objetivosDeLaAsignatura = oasDocente,
            existentes = existentes,
            cursoId = "C3A",
            asignaturaId = "MAT"
        )

        assertEquals(MENSAJE_OA_AJENOS, errores.objetivos)
        assertEquals(MENSAJE_OA_AJENOS, errores.primerMensaje())
    }

    @Test
    fun errorListaAsistencia_detectaListaVaciaOEstudianteAjeno() {
        val estudiantesDelCurso = setOf("E3A-1", "E3A-2")
        
        // Lista vacía
        assertEquals(MENSAJE_LISTA_ASISTENCIA, errorListaAsistencia(estudiantesDelCurso, emptyMap()))

        // Estudiante de otro curso
        val estadosConAjeno = mapOf("E3A-1" to EstadoAsistencia.PRESENTE, "E4A-1" to EstadoAsistencia.PRESENTE)
        assertEquals(MENSAJE_LISTA_ASISTENCIA, errorListaAsistencia(estudiantesDelCurso, estadosConAjeno))

        // Lista correcta
        val estadosOk = mapOf("E3A-1" to EstadoAsistencia.PRESENTE, "E3A-2" to EstadoAsistencia.AUSENTE)
        assertNull(errorListaAsistencia(estudiantesDelCurso, estadosOk))
    }
}
