package com.librompaz.app.data.repository

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
import com.librompaz.app.domain.MENSAJE_ESTUDIANTE_AJENO
import com.librompaz.app.domain.MENSAJE_EVALUACION_DUPLICADA
import com.librompaz.app.domain.MENSAJE_EVALUACION_INEXISTENTE
import com.librompaz.app.domain.MENSAJE_NOTA_DECIMALES
import com.librompaz.app.domain.MENSAJE_NOTA_FUERA_DE_RANGO
import com.librompaz.app.domain.MENSAJE_NOTA_INVALIDA
import com.librompaz.app.domain.calcularAnotacionesIntervencion
import com.librompaz.app.domain.erroresDeEvaluacionNueva
import com.librompaz.app.domain.parsearNota
import com.librompaz.app.domain.primerMensaje
import com.librompaz.app.domain.validarAnotacion
import com.librompaz.app.domain.validarNota
import com.librompaz.app.domain.validarNotaNumerica
import com.librompaz.app.domain.validarNotasDeEvaluacion
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.TipoAnotacion
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de Calificaciones y Hoja de Vida en el Repositorio Local (Matías).
 * Cubre la creación y eliminación de evaluaciones, guardado de notas, registro de anotaciones
 * inalterables y las reglas de negocio/validaciones del dominio.
 */
class CalificacionesYHojaDeVidaLocalTest {

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

    // ---------- Pruebas de Calificaciones ----------

    @Test
    fun crearEvaluacion_insertaEvaluacionYAuditaComoCrear() = runBlocking {
        val cursoId = "C3A"
        val asignaturaId = "MAT"
        val nombre = "Prueba Coeficiente 1"
        val fecha = "2026-11-20"
        val detalle = "Creación de evaluación sumativa"

        val resultado = repository.crearEvaluacion(docente, cursoId, asignaturaId, nombre, fecha, detalle)

        assertTrue(resultado is com.librompaz.app.data.remote.ApiResult.Exito)

        val slotEvaluacion = slot<Evaluacion>()
        coVerify { calificacionDao.insertarEvaluacion(capture(slotEvaluacion)) }
        assertEquals(cursoId, slotEvaluacion.captured.cursoId)
        assertEquals(asignaturaId, slotEvaluacion.captured.asignaturaId)
        assertEquals(nombre, slotEvaluacion.captured.nombreInstrumento)
        assertEquals(fecha, slotEvaluacion.captured.fecha)

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("CREAR", slotAuditoria.captured.accion)
        assertEquals(detalle, slotAuditoria.captured.detalle)
    }

    @Test
    fun eliminarEvaluacion_eliminaDeDaoYAuditaComoEliminar() = runBlocking {
        val evaluacion = Evaluacion("EV100", "C3A", "MAT", "Control 1", "2026-11-20")
        val detalle = "Eliminar control duplicado"

        val resultado = repository.eliminarEvaluacion(docente, evaluacion, detalle)

        assertTrue(resultado is com.librompaz.app.data.remote.ApiResult.Exito)

        coVerify { calificacionDao.eliminarEvaluacion(evaluacion) }

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("ELIMINAR", slotAuditoria.captured.accion)
        assertEquals(detalle, slotAuditoria.captured.detalle)
    }

    @Test
    fun guardarCalificaciones_sinNotasPrevias_auditaComoCrear() = runBlocking {
        val evaluacionId = "EV1"
        val notas = mapOf("E3A-1" to 6.5, "E3A-2" to 5.0)
        val detalle = "Ingreso inicial de notas"

        coEvery { calificacionDao.notasDe(evaluacionId) } returns emptyList()

        repository.guardarCalificaciones(docente, evaluacionId, notas, emptyList(), detalle)

        val slotNotas = slot<List<Calificacion>>()
        coVerify { calificacionDao.guardarNotas(capture(slotNotas)) }
        assertEquals(2, slotNotas.captured.size)
        assertEquals(6.5, slotNotas.captured.first { it.estudianteId == "E3A-1" }.nota, 0.01)

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("CREAR", slotAuditoria.captured.accion)
    }

    @Test
    fun guardarCalificaciones_conNotasPreviasYSinNota_eliminaBorradasYAuditaComoModificar() = runBlocking {
        val evaluacionId = "EV1"
        val previas = listOf(Calificacion("EV1_E3A-1", evaluacionId, "E3A-1", 6.0, docente.nombre))
        val notasNuevas = mapOf("E3A-1" to 6.8)
        val sinNota = listOf("E3A-2")
        val detalle = "Corrección de nota y eliminación"

        coEvery { calificacionDao.notasDe(evaluacionId) } returns previas

        repository.guardarCalificaciones(docente, evaluacionId, notasNuevas, sinNota, detalle)

        coVerify { calificacionDao.guardarNotas(any()) }
        coVerify { calificacionDao.eliminarNotas(evaluacionId, sinNota) }

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("MODIFICAR", slotAuditoria.captured.accion)
    }

    // ---------- Pruebas de Hoja de Vida (Anotaciones) ----------

    @Test
    fun registrarAnotacion_insertaConFirmaYAuditaComoCrear() = runBlocking {
        val estudianteId = "E3A-1"
        val tipo = TipoAnotacion.POSITIVA
        val descripcion = "Excelente participación en la feria de ciencias."
        val detalle = "Registro de felicitación"

        val resultado = repository.registrarAnotacion(docente, estudianteId, tipo, descripcion, detalle)

        assertTrue(resultado is com.librompaz.app.data.remote.ApiResult.Exito)

        val slotAnotacion = slot<Anotacion>()
        coVerify { anotacionDao.insertar(capture(slotAnotacion)) }
        assertEquals(estudianteId, slotAnotacion.captured.estudianteId)
        assertEquals(tipo, slotAnotacion.captured.tipo)
        assertEquals(descripcion, slotAnotacion.captured.descripcion)
        assertEquals(docente.nombre, slotAnotacion.captured.firmadoPor)

        val slotAuditoria = slot<RegistroAuditoria>()
        coVerify { auditoriaDao.insertar(capture(slotAuditoria)) }
        assertEquals("CREAR", slotAuditoria.captured.accion)
    }

    // ---------- Reglas de Dominio y Validaciones ----------

    @Test
    fun evaluacionDuplicada_detectaMismoNombreYFecha() {
        val existentes = listOf(Evaluacion("EV1", "C3A", "MAT", "Prueba 1", "2026-11-20"))

        val errores = erroresDeEvaluacionNueva(
            nombre = "prueba 1", // insensible a mayúsculas
            fecha = "2026-11-20",
            hoy = "2026-11-20",
            existentes = existentes,
            cursoId = "C3A",
            asignaturaId = "MAT"
        )

        assertEquals(MENSAJE_EVALUACION_DUPLICADA, errores.nombre)
        assertEquals(MENSAJE_EVALUACION_DUPLICADA, errores.primerMensaje())
    }

    @Test
    fun validacionDeNotas_detectaFormatosYRangosCorrectos() {
        assertNull(validarNota("5,5"))
        assertNull(validarNota("6.0"))
        assertNull(validarNota("")) // en blanco es válido (sin nota)

        assertEquals(MENSAJE_NOTA_INVALIDA, validarNota("abc"))
        assertEquals(MENSAJE_NOTA_FUERA_DE_RANGO, validarNota("0.5"))
        assertEquals(MENSAJE_NOTA_FUERA_DE_RANGO, validarNota("7.5"))
        assertEquals(MENSAJE_NOTA_DECIMALES, validarNota("5.55"))

        assertEquals(5.5, parsearNota("5,5")!!, 0.01)
        assertEquals(6.0, parsearNota("6.0")!!, 0.01)
        assertNull(parsearNota("inválido"))

        assertNull(validarNotaNumerica(6.5))
        assertEquals(MENSAJE_NOTA_FUERA_DE_RANGO, validarNotaNumerica(8.0))
        assertEquals(MENSAJE_NOTA_DECIMALES, validarNotaNumerica(5.555))
    }

    @Test
    fun validarNotasDeEvaluacion_detectaEvaluacionInexistenteYEstudianteAjeno() {
        // Evaluación inexistente
        val errInexistente = validarNotasDeEvaluacion(null, setOf("E3A-1"), mapOf("E3A-1" to "6.0"))
        assertEquals(MENSAJE_EVALUACION_INEXISTENTE, errInexistente.general)

        // Estudiante de otro curso
        val eval = Evaluacion("EV1", "C3A", "MAT", "Test", "2026-11-20")
        val errAjeno = validarNotasDeEvaluacion(eval, setOf("E3A-1"), mapOf("E4A-1" to "6.0"))
        assertEquals(MENSAJE_ESTUDIANTE_AJENO, errAjeno.porEstudiante["E4A-1"])
    }

    @Test
    fun validacionAnotaciones_exigeCamposYLongitudValida() {
        // Sin estudiante o tipo
        val sinEstudiante = validarAnotacion(hayEstudiante = false, hayTipo = true, descripcion = "Descripción extensa...")
        assertEquals("Elige un estudiante", sinEstudiante.estudiante)

        val sinTipo = validarAnotacion(hayEstudiante = true, hayTipo = false, descripcion = "Descripción extensa...")
        assertEquals("Elige el tipo de anotación", sinTipo.tipo)

        // Descripción corta
        val corta = validarAnotacion(hayEstudiante = true, hayTipo = true, descripcion = "Corta")
        assertTrue(corta.descripcion!!.contains("al menos 10 caracteres"))

        // Correcta
        val ok = validarAnotacion(hayEstudiante = true, hayTipo = true, descripcion = "Comportamiento muy destacado durante la clase.")
        assertFalse(ok.hayErrores)
    }

    @Test
    fun intervencionAnotaciones_filtraEstudiantesConDosOMasNegativas() {
        val anotaciones = listOf(
            Anotacion("A1", "E3A-1", TipoAnotacion.NEGATIVA, "Distracción en clase", "2026-11-18 10:00:00", "Docente"),
            Anotacion("A2", "E3A-1", TipoAnotacion.NEGATIVA, "Sin cumplir tarea", "2026-11-19 10:00:00", "Docente"),
            Anotacion("A3", "E3A-2", TipoAnotacion.NEGATIVA, "Llegada tarde", "2026-11-19 10:00:00", "Docente"),
            Anotacion("A4", "E3A-2", TipoAnotacion.POSITIVA, "Gran colaboración", "2026-11-20 10:00:00", "Docente")
        )

        val enRiesgo = calcularAnotacionesIntervencion(anotaciones)
        assertEquals(2, enRiesgo.size)
        assertTrue(enRiesgo.all { it.estudianteId == "E3A-1" })
    }
}
