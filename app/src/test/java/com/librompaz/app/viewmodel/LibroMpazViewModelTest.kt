package com.librompaz.app.viewmodel

import android.app.Application
import com.librompaz.app.data.MockData
import com.librompaz.app.data.remote.ApiResult
import com.librompaz.app.data.repository.LibroMpazDatos
import com.librompaz.app.data.repository.PreferenciaModoPersistente
import com.librompaz.app.data.repository.ResultadoLogin
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.testing.AlmacenModoFalso
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias completas para LibroMpazViewModel (Katerine & Matías).
 * Cubre autenticación, perfiles, selección de curso/asignatura, formularios de asistencia,
 * sesiones, evaluaciones, calificaciones, anotaciones y cambios de modo local/servidor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibroMpazViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val repoLocal = mockk<LibroMpazDatos>(relaxed = true)
    private val repoServidor = mockk<LibroMpazDatos>(relaxed = true)

    private val almacenModo = AlmacenModoFalso(true)
    private val preferenciaModo = PreferenciaModoPersistente(almacenModo, valorInicial = true)

    private val docenteMat = MockData.usuarios.first { it.usuario == "docente.mat@libromp.cl" }

    private lateinit var viewModel: LibroMpazViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Simula la emisión de flujos del repositorio local
        coEvery { repoLocal.cursos } returns flowOf(MockData.cursos)
        coEvery { repoLocal.asignaturas } returns flowOf(MockData.asignaturas)
        coEvery { repoLocal.estudiantes } returns flowOf(MockData.estudiantes)
        coEvery { repoLocal.objetivos } returns flowOf(MockData.objetivos)
        coEvery { repoLocal.cargas } returns flowOf(MockData.cargas)
        coEvery { repoLocal.cargasDe(any()) } returns flowOf(MockData.cargas)
        coEvery { repoLocal.asistencias } returns flowOf(MockData.asistencias)
        coEvery { repoLocal.sesiones } returns flowOf(MockData.sesiones)
        coEvery { repoLocal.evaluaciones } returns flowOf(MockData.evaluaciones)
        coEvery { repoLocal.calificaciones } returns flowOf(MockData.calificaciones)
        coEvery { repoLocal.anotaciones } returns flowOf(MockData.anotaciones)
        coEvery { repoLocal.auditoria } returns flowOf(MockData.auditoria)

        viewModel = LibroMpazViewModel(
            application = application,
            modoInyectado = preferenciaModo,
            repositorioLocalInyectado = repoLocal,
            repositorioServidorInyectado = repoServidor
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- Autenticación y Sesión ----------

    @Test
    fun ingresar_conCamposVacios_muestraErroresDeValidacion() {
        viewModel.onUsuarioChange("")
        viewModel.onClaveChange("")

        var llamado = false
        viewModel.ingresar { llamado = true }

        assertFalse(llamado)
        assertTrue(viewModel.login.value.errores.hayErrores)
        assertEquals("El correo es obligatorio", viewModel.login.value.errores.usuario)
        assertEquals("La clave es obligatoria", viewModel.login.value.errores.clave)
    }

    @Test
    fun ingresar_conCredencialesValidas_autenticaYNotificaPerfil() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)

        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")

        var perfilLogueado: Perfil? = null
        viewModel.ingresar { perfil -> perfilLogueado = perfil }

        assertEquals(Perfil.DOCENTE, perfilLogueado)
        assertEquals(docenteMat, viewModel.usuarioActivo.value)
        assertFalse(viewModel.login.value.errores.hayErrores)
    }

    @Test
    fun ingresar_conCredencialesIncorrectas_muestraErrorGeneral() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "erronea") } returns ResultadoLogin.Rechazado("Correo o clave incorrectos")

        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("erronea")

        var llamado = false
        viewModel.ingresar { llamado = true }

        assertFalse(llamado)
        assertNull(viewModel.usuarioActivo.value)
        assertEquals("Correo o clave incorrectos", viewModel.login.value.errores.general)
    }

    @Test
    fun logout_limpiaUsuarioYRestableceEstado() {
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.ingresar {}

        assertEquals(docenteMat, viewModel.usuarioActivo.value)

        viewModel.logout()

        assertNull(viewModel.usuarioActivo.value)
        assertNull(viewModel.cursoSeleccionadoId.value)
    }

    // ---------- Selección y Consultas ----------

    @Test
    fun seleccionarCurso_actualizaIdentificadoresYLimpiaAviso() {
        viewModel.seleccionarCurso("C3A", "MAT")
        assertEquals("C3A", viewModel.cursoSeleccionadoId.value)
        assertEquals("MAT", viewModel.asignaturaSeleccionadaId.value)
        assertNull(viewModel.aviso.value)

        assertNotNull(viewModel.cursoPorId("C3A"))
        assertNotNull(viewModel.asignaturaPorId("MAT"))
        assertNotNull(viewModel.estudiantePorId("E3A-1"))
        assertTrue(viewModel.estudiantesDelCurso("C3A").isNotEmpty())
        assertTrue(viewModel.objetivosDe("MAT").isNotEmpty())
    }

    // ---------- Formulario y Guardado de Asistencia ----------

    @Test
    fun guardarAsistencia_sinSesion_avisaError() {
        viewModel.guardarAsistencia("C3A", "2026-11-20", mapOf("E3A-1" to EstadoAsistencia.PRESENTE)) {}
        assertNotNull(viewModel.aviso.value)
        assertTrue(viewModel.aviso.value!!.esError)
        assertTrue(viewModel.aviso.value!!.texto.contains("Inicia sesión"))
    }

    @Test
    fun guardarAsistencia_conUsuarioAutenticado_guardaYEjecutaCallback() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        coEvery { repoLocal.guardarAsistencia(docenteMat, "C3A", "2026-11-20", any(), any()) } returns ApiResult.Exito(Unit)

        var terminado = false
        val estados = mapOf("E3A-1" to EstadoAsistencia.PRESENTE)
        viewModel.guardarAsistencia("C3A", "2026-11-20", estados) { terminado = true }

        assertTrue(terminado)
        assertNotNull(viewModel.aviso.value)
        assertFalse(viewModel.aviso.value!!.esError)
        coVerify { repoLocal.guardarAsistencia(docenteMat, "C3A", "2026-11-20", estados, any()) }
    }

    // ---------- Formulario y Guardado de Sesiones ----------

    @Test
    fun guardarSesion_conErroresDeValidacion_retornaErroresSinGuardar() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        val errores = viewModel.guardarSesion("C3A", "MAT", "2026-11-20", "", emptyList(), "") {}
        assertTrue(errores.hayErrores)
        assertNotNull(errores.bloque)
        assertNotNull(errores.objetivos)
        assertNotNull(errores.contenido)
    }

    @Test
    fun guardarSesion_conDatosValidos_guardaClase() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        coEvery { repoLocal.guardarSesion(docenteMat, "C3A", "MAT", "2026-11-20", "Bloque 1", listOf("OA-MAT-1"), "Contenido ok", any()) } returns ApiResult.Exito(Unit)

        var terminado = false
        val errores = viewModel.guardarSesion("C3A", "MAT", "2026-11-20", "Bloque 1", listOf("OA-MAT-1"), "Contenido ok") {
            terminado = true
        }

        assertFalse(errores.hayErrores)
        assertTrue(terminado)
        coVerify { repoLocal.guardarSesion(docenteMat, "C3A", "MAT", "2026-11-20", "Bloque 1", listOf("OA-MAT-1"), "Contenido ok", any()) }
    }

    // ---------- Formulario y Guardado de Evaluaciones / Notas ----------

    @Test
    fun crearEvaluacion_conCamposCorrectos_persisteInstrumento() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        coEvery { repoLocal.crearEvaluacion(docenteMat, "C3A", "MAT", "Prueba 1", "2026-11-20", any()) } returns ApiResult.Exito(Unit)

        var terminado = false
        val errores = viewModel.crearEvaluacion("C3A", "MAT", "Prueba 1", "2026-11-20") { terminado = true }

        assertFalse(errores.hayErrores)
        assertTrue(terminado)
        coVerify { repoLocal.crearEvaluacion(docenteMat, "C3A", "MAT", "Prueba 1", "2026-11-20", any()) }
    }

    @Test
    fun guardarCalificaciones_conNotasInvalidas_muestraAvisoDeError() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        var terminado = false
        viewModel.guardarCalificaciones("EV1", mapOf("E3A-1" to "9.5")) { terminado = true }

        assertFalse(terminado)
        assertNotNull(viewModel.aviso.value)
        assertTrue(viewModel.aviso.value!!.esError)
    }

    @Test
    fun eliminarEvaluacion_solicitaEliminacionYAuditoria() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        val eval = MockData.evaluaciones.first()
        coEvery { repoLocal.eliminarEvaluacion(docenteMat, eval, any()) } returns ApiResult.Exito(Unit)

        var terminado = false
        viewModel.eliminarEvaluacion(eval.id) { terminado = true }

        assertTrue(terminado)
        coVerify { repoLocal.eliminarEvaluacion(docenteMat, eval, any()) }
    }

    // ---------- Formulario y Guardado de Anotaciones ----------

    @Test
    fun registrarAnotacion_conDescripcionCorta_avisaError() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        var terminado = false
        viewModel.registrarAnotacion("E3A-1", TipoAnotacion.POSITIVA, "Corta") { terminado = true }

        assertFalse(terminado)
        assertNotNull(viewModel.aviso.value)
        assertTrue(viewModel.aviso.value!!.esError)
    }

    @Test
    fun registrarAnotacion_conDatosValidos_guardaYAvisa() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        val desc = "Excelente desempeño y participación durante la clase."
        coEvery { repoLocal.registrarAnotacion(docenteMat, "E3A-1", TipoAnotacion.POSITIVA, desc, any()) } returns ApiResult.Exito(Unit)

        var terminado = false
        viewModel.registrarAnotacion("E3A-1", TipoAnotacion.POSITIVA, desc) { terminado = true }

        assertTrue(terminado)
        assertNotNull(viewModel.aviso.value)
        assertFalse(viewModel.aviso.value!!.esError)
        coVerify { repoLocal.registrarAnotacion(docenteMat, "E3A-1", TipoAnotacion.POSITIVA, desc, any()) }
    }

    @Test
    fun guardarCalificaciones_conNotasValidas_persisteCalificaciones() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        val eval = MockData.evaluaciones.first()
        coEvery { repoLocal.guardarCalificaciones(docenteMat, eval.id, any(), any(), any()) } returns ApiResult.Exito(Unit)

        var terminado = false
        viewModel.guardarCalificaciones(eval.id, mapOf("E3A-1" to "6,5")) { terminado = true }

        assertTrue(terminado)
        coVerify { repoLocal.guardarCalificaciones(docenteMat, eval.id, any(), any(), any()) }
    }

    @Test
    fun eliminarEvaluacion_conEvaluacionInexistente_avisaError() {
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        var terminado = false
        viewModel.eliminarEvaluacion("EV_INEXISTENTE") { terminado = true }

        assertFalse(terminado)
        assertNotNull(viewModel.aviso.value)
        assertTrue(viewModel.aviso.value!!.esError)
    }

    // ---------- Cobertura Curricular, Anotaciones y Consultas ----------

    @Test
    fun consultasDeCoberturaYAnotaciones_retornanFiltrosCorrectos() {
        val oas = viewModel.oasTrabajados("C3A", "MAT")
        assertNotNull(oas)

        val ultimasFechas = viewModel.ultimaFechaOa("C3A", "MAT")
        assertNotNull(ultimasFechas)

        val anotacionesNegativas = viewModel.anotacionesPorTipo(TipoAnotacion.NEGATIVA)
        assertNotNull(anotacionesNegativas)
        assertTrue(anotacionesNegativas.all { it.tipo == TipoAnotacion.NEGATIVA })

        val todasAnotaciones = viewModel.anotacionesPorTipo(null)
        assertNotNull(todasAnotaciones)

        val deEstudiante = viewModel.anotacionesDe("E3A-1")
        assertNotNull(deEstudiante)

        assertEquals("La nota debe estar entre 1,0 y 7,0", viewModel.validarNota("8.0"))
    }

    @Test
    fun asistenciaGuardada_sinPermisoDevuelveVacio_conPermisoDevuelveMapa() {
        // Sin usuario en sesión -> permiso denegado -> emptyMap
        runBlocking {
            val sinPermiso = viewModel.asistenciaGuardada("C3A", "2026-11-20")
            assertTrue(sinPermiso.isEmpty())
        }

        // Con docente con carga -> retorna mapa de asistencia
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        coEvery { repoLocal.asistenciaDe("C3A", "2026-11-20") } returns listOf(
            com.librompaz.app.model.RegistroAsistencia("E3A-1_2026-11-20", "E3A-1", "C3A", "2026-11-20", EstadoAsistencia.PRESENTE, docenteMat.nombre, "2026-11-20 08:00:00")
        )

        runBlocking {
            val conPermiso = viewModel.asistenciaGuardada("C3A", "2026-11-20")
            assertEquals(1, conPermiso.size)
            assertEquals(EstadoAsistencia.PRESENTE, conPermiso["E3A-1"])
        }
    }

    // ---------- Cambios de Modo y Servidor ----------

    @Test
    fun cambiosDeModoLocalYServidor_actualizanEstados() {
        viewModel.cambiarModoLocal(false)
        assertFalse(viewModel.modoLocal.value)

        viewModel.cambiarModoLocal(true)
        assertTrue(viewModel.modoLocal.value)

        viewModel.usarModoLocal()
        assertTrue(viewModel.modoLocal.value)

        viewModel.abrirServidor()
        assertNotNull(viewModel.servidorUi.value)

        viewModel.onUrlServidorChange("http://192.168.1.50:3000/")
        assertEquals("http://192.168.1.50:3000/", viewModel.servidorUi.value.url)

        try {
            viewModel.restablecerServidor()
        } catch (_: NotImplementedError) {
            // Manejo seguro del TODO E3-16 de ServidorPreferencias
        }

        coEvery { repoLocal.restablecerDatosLocales() } returns Unit
        viewModel.restablecerDatosLocales()
        coVerify { repoLocal.restablecerDatosLocales() }
    }

    @Test
    fun flujosDeEstadoViewModel_emitenPropiedades() {
        assertNotNull(viewModel.misCargas.value)
        assertNotNull(viewModel.porcentajesAsistencia.value)
        assertNotNull(viewModel.alertasInasistencia.value)
        assertNotNull(viewModel.sesiones.value)
        assertNotNull(viewModel.evaluaciones.value)
        assertNotNull(viewModel.calificaciones.value)
        assertNotNull(viewModel.desfases.value)
        assertNotNull(viewModel.anotaciones.value)
        assertNotNull(viewModel.alertasAnotaciones.value)
        assertNotNull(viewModel.auditoria.value)
        assertNotNull(viewModel.resumenCursos.value)
        assertNotNull(viewModel.servidorVigente.value)

        coEvery { repoLocal.sincronizar(docenteMat) } returns ApiResult.Exito(Unit)
        coEvery { repoLocal.ingresar("docente.mat@libromp.cl", "docente123") } returns ResultadoLogin.Aceptado(docenteMat)
        viewModel.onUsuarioChange("docente.mat@libromp.cl")
        viewModel.onClaveChange("docente123")
        viewModel.ingresar {}

        viewModel.sincronizar()
        coVerify { repoLocal.sincronizar(docenteMat) }
    }

    // ---------- Manejo de Avisos y Modo ----------

    @Test
    fun descartarAviso_limpiaElEstadoDeAviso() {
        viewModel.validarFechaAsistencia("2099-01-01")
        viewModel.guardarAsistencia("C3A", "2099-01-01", emptyMap()) {}

        assertNotNull(viewModel.aviso.value)
        viewModel.descartarAviso()
        assertNull(viewModel.aviso.value)
    }
}
