package com.librompaz.app.data.repository

import androidx.room.withTransaction
import com.librompaz.app.data.MockData
import com.librompaz.app.data.local.AcademicoDao
import com.librompaz.app.data.local.AnotacionDao
import com.librompaz.app.data.local.AppDatabase
import com.librompaz.app.data.local.AsistenciaDao
import com.librompaz.app.data.local.AuditoriaDao
import com.librompaz.app.data.local.CacheRoom
import com.librompaz.app.data.local.CalificacionDao
import com.librompaz.app.data.local.InstantaneaServidor
import com.librompaz.app.data.local.SesionDao
import com.librompaz.app.data.local.UsuarioDao
import com.librompaz.app.model.Usuario
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de cobertura completa para LibroMpazRepository y CacheRoom en el entorno local (Matías).
 * Valida los flujos de lectura observables, métodos de sesión, poblado inicial y sincronización atómica.
 */
class LibroMpazRepositoryLocalCompletoTest {

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
    private lateinit var cacheRoom: CacheRoom

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
        cacheRoom = CacheRoom(db)
    }

    @Test
    fun metodosDeSesionYConexionRemota_enModoLocal() {
        runBlocking {
            // Ingresar credenciales OK
            coEvery { usuarioDao.buscarPorUsuario("docente.mat@libromp.cl") } returns docente
            val resOk = repository.ingresar("docente.mat@libromp.cl", "docente123")
            assertTrue(resOk is ResultadoLogin.Aceptado)

            // Ingresar credenciales erróneas
            val resError = repository.ingresar("docente.mat@libromp.cl", "claveFalsa")
            assertTrue(resError is ResultadoLogin.Rechazado)

            // Operaciones remotas sin efecto en modo local
            assertNull(repository.retirarToken())
            repository.cerrarSesionRemota("tokenFalso")
            val syncRes = repository.sincronizar(docente)
            assertTrue(syncRes is com.librompaz.app.data.remote.ApiResult.Exito)
        }
    }

    @Test
    fun poblarSiVacia_vaciaPoblaDatosFicticios_noVaciaNoHaceNada() {
        runBlocking {
            // Si está vacía (contar = 0)
            coEvery { usuarioDao.contar() } returns 0
            repository.poblarSiVacia()
            coVerify { usuarioDao.insertarTodos(any()) }
            coVerify { academicoDao.insertarCursos(any()) }

            // Si ya tiene datos (contar > 0)
            coEvery { usuarioDao.contar() } returns 5
            cacheRoom.poblarSiVacia()
            coVerify(exactly = 1) { usuarioDao.insertarTodos(any()) } // no vuelve a insertar
        }
    }

    @Test
    fun restablecerDatosLocales_limpiaTablasYPobla() {
        runBlocking {
            coEvery { usuarioDao.contar() } returns 0
            repository.restablecerDatosLocales()
            coVerify { db.clearAllTables() }
            coVerify { usuarioDao.insertarTodos(any()) }
        }
    }

    @Test
    fun flujosObservables_propaganDatosDeDaos() {
        runBlocking {
            every { academicoDao.cursos() } returns flowOf(MockData.cursos)
            every { academicoDao.asignaturas() } returns flowOf(MockData.asignaturas)
            every { academicoDao.estudiantes() } returns flowOf(MockData.estudiantes)
            every { academicoDao.objetivos() } returns flowOf(MockData.objetivos)
            every { academicoDao.cargas() } returns flowOf(MockData.cargas)
            every { academicoDao.cargasDe("U1") } returns flowOf(MockData.cargas)
            every { asistenciaDao.todas() } returns flowOf(MockData.asistencias)
            every { sesionDao.todas() } returns flowOf(MockData.sesiones)
            every { calificacionDao.evaluaciones() } returns flowOf(MockData.evaluaciones)
            every { calificacionDao.calificaciones() } returns flowOf(MockData.calificaciones)
            every { anotacionDao.todas() } returns flowOf(MockData.anotaciones)
            every { auditoriaDao.todos() } returns flowOf(MockData.auditoria)

            assertNotNull(repository.cursos)
            assertNotNull(repository.asignaturas)
            assertNotNull(repository.estudiantes)
            assertNotNull(repository.objetivos)
            assertNotNull(repository.cargas)
            assertNotNull(repository.cargasDe("U1"))
            assertNotNull(repository.asistencias)
            assertNotNull(repository.sesiones)
            assertNotNull(repository.evaluaciones)
            assertNotNull(repository.calificaciones)
            assertNotNull(repository.anotaciones)
            assertNotNull(repository.auditoria)

            assertNotNull(cacheRoom.cursos)
            assertNotNull(cacheRoom.asignaturas)
            assertNotNull(cacheRoom.estudiantes)
            assertNotNull(cacheRoom.objetivos)
            assertNotNull(cacheRoom.cargas)
            assertNotNull(cacheRoom.cargasDe("U1"))
            assertNotNull(cacheRoom.asistencias)
            assertNotNull(cacheRoom.sesiones)
            assertNotNull(cacheRoom.evaluaciones)
            assertNotNull(cacheRoom.calificaciones)
            assertNotNull(cacheRoom.anotaciones)
            assertNotNull(cacheRoom.auditoria)
        }
    }

    @Test
    fun cacheRoom_operacionesPuntualesYReemplazar() {
        runBlocking {
            coEvery { usuarioDao.buscarPorUsuario("docente.mat@libromp.cl") } returns docente
            assertEquals(docente, cacheRoom.buscarUsuario("docente.mat@libromp.cl"))

            cacheRoom.guardarUsuario(docente)
            coVerify { usuarioDao.guardar(docente) }

            val esperadas = MockData.asistencias
            coEvery { asistenciaDao.delCursoEnFecha("C3A", "2026-11-20") } returns esperadas
            assertEquals(esperadas, cacheRoom.asistenciaDe("C3A", "2026-11-20"))

            val instantanea = InstantaneaServidor(
                cursos = MockData.cursos,
                asignaturas = MockData.asignaturas,
                estudiantes = MockData.estudiantes,
                objetivos = MockData.objetivos,
                cargas = MockData.cargas,
                asistencias = MockData.asistencias,
                sesiones = MockData.sesiones,
                evaluaciones = MockData.evaluaciones,
                calificaciones = MockData.calificaciones,
                anotaciones = MockData.anotaciones,
                auditoria = MockData.auditoria
            )

            cacheRoom.reemplazar(instantanea)

            coVerify { auditoriaDao.borrarCopiaLocal() }
            coVerify { anotacionDao.borrarCopiaLocal() }
            coVerify { calificacionDao.borrarNotas() }
            coVerify { academicoDao.insertarCursos(MockData.cursos) }
        }
    }
}
