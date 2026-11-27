package com.librompaz.app.viewmodel

import android.app.Application
import com.librompaz.app.data.remote.ApiResult
import com.librompaz.app.data.repository.LibroMpazDatos
import com.librompaz.app.data.repository.MENSAJE_CREDENCIALES
import com.librompaz.app.data.repository.PreferenciaModoPersistente
import com.librompaz.app.data.repository.ResultadoLogin
import com.librompaz.app.testing.AlmacenModoFalso
import com.librompaz.app.domain.MENSAJE_EVALUACION_INEXISTENTE
import com.librompaz.app.domain.MENSAJE_NOTA_DECIMALES
import com.librompaz.app.domain.MENSAJE_NOTA_FUERA_DE_RANGO
import com.librompaz.app.domain.MENSAJE_NOTA_INVALIDA
import com.librompaz.app.domain.MENSAJE_SIN_NOTAS
import com.librompaz.app.domain.PoliticaAcceso
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Asignatura
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.Curso
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Estudiante
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario
import com.librompaz.app.util.hayRedActiva
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas JVM del ViewModel con el constructor inyectable (RF-01, RF-02, RF-08).
 *
 * Se fuerza el modo local con [AlmacenModoFalso] y se inyecta un repositorio falso. La fuente
 * "servidor" solo permite poblar la base, como hace SelectorFuente al arrancar: cualquier otra
 * operación falla, así que los tests demuestran que el flujo evaluado no usa red ni backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibroMpazViewModelLocalTest {

    private companion object {
        const val FECHA = "2026-01-15"
        const val CONECTIVIDAD = "com.librompaz.app.util.ConectividadKt"
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var local: RepositorioViewModelFalso
    private lateinit var servidor: RepositorioViewModelFalso
    private lateinit var vm: LibroMpazViewModel

    private val docente: Usuario get() = usuarioDe("U1")
    private val utp: Usuario get() = usuarioDe("U3")
    private val inspectoria: Usuario get() = usuarioDe("U4")
    private val direccion: Usuario get() = usuarioDe("U5")

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
        local = RepositorioViewModelFalso()
        servidor = RepositorioViewModelFalso(prohibido = true)
        val aplicacion = mockk<Application>(relaxed = true)
        val modo = PreferenciaModoPersistente(AlmacenModoFalso(true), true)
        vm = LibroMpazViewModel(aplicacion, modo, local, servidor)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
        runCatching { unmockkStatic(CONECTIVIDAD) }
    }

    private fun usuarioDe(id: String): Usuario =
        com.librompaz.app.data.MockData.usuarios.first { it.id == id }

    /** Solo los casos que pasan por avisoDeRed necesitan simular ConnectivityManager. */
    private fun simularSinRed() {
        mockkStatic(CONECTIVIDAD)
        every { hayRedActiva(any()) } returns false
    }

    /** Ingresa con credenciales válidas contra la fuente falsa para dejar la sesión lista. */
    private fun iniciarSesion(usuario: Usuario) {
        local.resultadoIngreso = ResultadoLogin.Aceptado(usuario)
        vm.onUsuarioChange(usuario.usuario)
        vm.onClaveChange("clave-de-prueba")
        vm.ingresar {}
        dispatcher.scheduler.advanceUntilIdle()
        assertSame(usuario, vm.usuarioActivo.value)
    }

    // ---------------------------------------------------------------------
    // Login (RF-01)
    // ---------------------------------------------------------------------

    @Test
    fun login_camposVacios_yCorreoInvalido_noLlamanAlRepositorio() {
        vm.onUsuarioChange("")
        vm.onClaveChange("")
        vm.ingresar {}

        assertEquals("El correo es obligatorio", vm.login.value.errores.usuario)
        assertEquals("La clave es obligatoria", vm.login.value.errores.clave)
        assertFalse(vm.login.value.ingresando)
        assertFalse(local.llamadas.contains("ingresar"))

        vm.onUsuarioChange("correo-invalido")
        vm.onClaveChange("clave")
        vm.ingresar {}

        assertEquals("El correo no tiene un formato válido", vm.login.value.errores.usuario)
        assertNull(vm.login.value.errores.clave)
        assertFalse(local.llamadas.contains("ingresar"))
    }

    @Test
    fun escribirCorreoOClave_limpiaErroresDelCampoYElGeneral() {
        local.resultadoIngreso = ResultadoLogin.Rechazado(MENSAJE_CREDENCIALES)
        vm.onUsuarioChange("correo@ejemplo.cl")
        vm.onClaveChange("mala")
        vm.ingresar {}
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MENSAJE_CREDENCIALES, vm.login.value.errores.general)

        vm.onUsuarioChange("otro@ejemplo.cl")
        assertNull(vm.login.value.errores.usuario)
        assertNull(vm.login.value.errores.general)

        vm.onClaveChange("otra")
        assertNull(vm.login.value.errores.clave)
    }

    @Test
    fun loginAceptado_guardaUsuarioLimpiaClaveYAvisaAlNavegador() {
        var perfil: Perfil? = null
        local.resultadoIngreso = ResultadoLogin.Aceptado(docente)
        vm.onUsuarioChange("  ${docente.usuario}  ")
        vm.onClaveChange("docente123")

        vm.ingresar { perfil = it }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(docente.usuario to "docente123", local.ultimoIngreso)
        assertSame(docente, vm.usuarioActivo.value)
        assertEquals(Perfil.DOCENTE, perfil)
        assertEquals(LoginUiState(), vm.login.value)
        assertTrue(local.llamadas.contains("ingresar"))
        assertTrue(servidor.llamadas.all { it == "poblarSiVacia" })
    }

    @Test
    fun loginRechazado_muestraMensajeGenericoYConservaElFormulario() {
        local.resultadoIngreso = ResultadoLogin.Rechazado(MENSAJE_CREDENCIALES)
        vm.onUsuarioChange(docente.usuario)
        vm.onClaveChange("clave-mala")

        vm.ingresar {}
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MENSAJE_CREDENCIALES, vm.login.value.errores.general)
        assertFalse(vm.login.value.ingresando)
        assertNull(vm.usuarioActivo.value)
        assertEquals("clave-mala", vm.login.value.clave)
    }

    @Test
    fun loginSinConexion_aceptaUsuarioYaValidadoYAvisaQueNoHayRed() {
        simularSinRed()
        var perfil: Perfil? = null
        local.resultadoIngreso = ResultadoLogin.SinConexion(docente, "El servidor no responde.")
        vm.onUsuarioChange(docente.usuario)
        vm.onClaveChange("docente123")

        vm.ingresar { perfil = it }
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(docente, vm.usuarioActivo.value)
        assertEquals(Perfil.DOCENTE, perfil)
        val estado = vm.estadoSync.value as EstadoSincronizacion.SinConexion
        assertTrue(estado.mensaje.contains("El servidor no responde."))
        assertTrue(estado.mensaje.contains("El equipo no tiene una red activa"))
    }

    @Test
    fun loginNoDisponible_muestraElFalloYRecomiendaElModoLocal() {
        simularSinRed()
        local.resultadoIngreso = ResultadoLogin.NoDisponible("No se pudo validar el ingreso.")
        vm.onUsuarioChange(docente.usuario)
        vm.onClaveChange("docente123")

        vm.ingresar {}
        dispatcher.scheduler.advanceUntilIdle()

        val general = vm.login.value.errores.general.orEmpty()
        assertTrue(general.contains("No se pudo validar el ingreso."))
        assertTrue(general.contains("El equipo no tiene una red activa"))
        assertTrue(general.contains("activa el modo local"))
        assertFalse(vm.login.value.ingresando)
        assertNull(vm.usuarioActivo.value)
    }

    @Test
    fun loginConIntegracionPendiente_avisaSinCerrarLaApp() {
        local.excepcionIngreso = NotImplementedError("E3-12")
        vm.onUsuarioChange(docente.usuario)
        vm.onClaveChange("docente123")

        vm.ingresar {}
        dispatcher.scheduler.advanceUntilIdle()

        val general = vm.login.value.errores.general.orEmpty()
        assertTrue(general.contains("Falta completar la integración con el servidor"))
        assertTrue(general.contains("E3-12"))
        assertTrue(general.contains("Usa el modo local"))
        assertFalse(vm.login.value.ingresando)
        assertNull(vm.usuarioActivo.value)
    }

    @Test
    fun logout_limpiaSesionAvisoYSeleccionYCierraSesionEnLaFuenteLocal() {
        iniciarSesion(docente)
        vm.seleccionarCurso("C3A", "MAT")
        vm.guardarCalificaciones("EV1", mapOf("E3A-1" to "")) {}
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNullAviso("Escribe al menos una nota.")

        vm.logout()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.usuarioActivo.value)
        assertNull(vm.aviso.value)
        assertNull(vm.cursoSeleccionadoId.value)
        assertNull(vm.asignaturaSeleccionadaId.value)
        assertEquals("token-local", local.tokenCerrado)
        assertTrue(servidor.llamadas.all { it == "poblarSiVacia" })
    }

    // ---------------------------------------------------------------------
    // Permisos y reglas de escritura (RF-02, RF-03, RF-06, RF-08, RF-10)
    // ---------------------------------------------------------------------

    @Test
    fun guardarAsistencia_sinSesion_avisaYNingunaFuenteEscribe() {
        vm.guardarAsistencia("C3A", FECHA, mapOf("E3A-1" to EstadoAsistencia.PRESENTE)) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso(PoliticaAcceso.MENSAJE_SIN_SESION)
        assertTrue(vm.aviso.value!!.esError)
        assertFalse(local.llamadas.contains("guardarAsistencia"))
        assertTrue(servidor.llamadas.all { it == "poblarSiVacia" })
    }

    @Test
    fun guardarAsistencia_docenteConCarga_guardaYAvisaExito() {
        iniciarSesion(docente)
        var termino = false

        vm.guardarAsistencia("C3A", FECHA, mapOf("E3A-1" to EstadoAsistencia.PRESENTE)) { termino = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(termino)
        assertTrue(local.llamadas.contains("guardarAsistencia"))
        assertEquals("Asistencia del $FECHA guardada.", vm.aviso.value?.texto)
        assertEquals("Asistencia 3° Básico A, $FECHA", local.detalleGuardado)
    }

    @Test
    fun guardarAsistencia_inspectoriaPuedeRegistrarCualquierCurso() {
        iniciarSesion(inspectoria)

        vm.guardarAsistencia("C4A", FECHA, mapOf("E4A-1" to EstadoAsistencia.AUSENTE)) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(local.llamadas.contains("guardarAsistencia"))
        assertEquals("Asistencia del $FECHA guardada.", vm.aviso.value?.texto)
    }

    @Test
    fun guardarAsistencia_utpYDireccion_sonDenegadosSinEscribir() {
        for (usuario in listOf(utp, direccion)) {
            iniciarSesion(usuario)
            vm.descartarAviso()

            vm.guardarAsistencia("C3A", FECHA, mapOf("E3A-1" to EstadoAsistencia.PRESENTE)) {}
            dispatcher.scheduler.advanceUntilIdle()

            assertNotNullAviso(PoliticaAcceso.MENSAJE_SIN_PERMISO)
        }
        assertFalse(local.llamadas.contains("guardarAsistencia"))
    }

    @Test
    fun guardarSesion_docenteConCarga_registraLaClaseConSusOa() {
        iniciarSesion(docente)
        var termino = false

        val errores = vm.guardarSesion(
            cursoId = "C3A",
            asignaturaId = "MAT",
            fecha = FECHA,
            bloque = "Bloque 1",
            oaIds = listOf("OA-MAT-1"),
            contenido = "Números hasta 100"
        ) { termino = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(errores.hayErrores)
        assertTrue(termino)
        assertTrue(local.llamadas.contains("guardarSesion"))
        assertEquals("Clase del $FECHA registrada.", vm.aviso.value?.texto)
    }

    @Test
    fun guardarSesion_docenteSinCargaEnEseCursoYAsignatura_esDenegado() {
        iniciarSesion(docente)

        val errores = vm.guardarSesion(
            cursoId = "C4A",
            asignaturaId = "LEN",
            fecha = FECHA,
            bloque = "Bloque 1",
            oaIds = listOf("OA-LEN-1"),
            contenido = "Lectura de textos"
        ) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(errores.hayErrores)
        assertNotNullAviso(PoliticaAcceso.MENSAJE_SIN_CARGA)
        assertFalse(local.llamadas.contains("guardarSesion"))
    }

    @Test
    fun crearEvaluacion_utp_esDenegadaSinEscribir() {
        iniciarSesion(utp)

        vm.crearEvaluacion("C3A", "MAT", "Prueba de prueba", FECHA) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso(PoliticaAcceso.MENSAJE_SIN_PERMISO)
        assertFalse(local.llamadas.contains("crearEvaluacion"))
    }

    @Test
    fun registrarAnotacion_docenteSobreEstudianteAjeno_esDenegada() {
        iniciarSesion(docente)

        vm.registrarAnotacion("E4A-1", TipoAnotacion.NEGATIVA, "Llegó tarde") {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso(PoliticaAcceso.MENSAJE_SIN_CARGA)
        assertFalse(local.llamadas.contains("registrarAnotacion"))
    }

    @Test
    fun registrarAnotacion_docenteConCarga_guardaFirmadaYRecortaLaDescripcion() {
        iniciarSesion(docente)

        vm.registrarAnotacion("E3A-1", TipoAnotacion.POSITIVA, "  Trabajo ordenado  ") {}
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(local.llamadas.contains("registrarAnotacion"))
        assertEquals("Trabajo ordenado", local.ultimaDescripcion)
        assertTrue(local.detalleGuardado.orEmpty().contains("Estudiante 3A-01"))
        assertEquals("Anotación registrada y firmada.", vm.aviso.value?.texto)
    }

    // ---------------------------------------------------------------------
    // Notas (RF-08, RN-04, RN-08)
    // ---------------------------------------------------------------------

    @Test
    fun validarNota_comaPuntoYLimitesIncluidos_sonAceptados() {
        assertNull(vm.validarNota("5,8"))
        assertNull(vm.validarNota("5.8"))
        assertNull(vm.validarNota("7,0"))
        assertNull(vm.validarNota("1,0"))
        assertNull(vm.validarNota(""))
    }

    @Test
    fun validarNota_fueraDeRangoConExcesoDeDecimalesYTexto_muestraElErrorCorrespondiente() {
        assertEquals(MENSAJE_NOTA_FUERA_DE_RANGO, vm.validarNota("0,9"))
        assertEquals(MENSAJE_NOTA_FUERA_DE_RANGO, vm.validarNota("7,1"))
        assertEquals(MENSAJE_NOTA_DECIMALES, vm.validarNota("5,88"))
        assertEquals(MENSAJE_NOTA_INVALIDA, vm.validarNota("abc"))
    }

    @Test
    fun guardarCalificaciones_notasConComaYPunto_sonNormalizadasYGuardadas() {
        iniciarSesion(docente)
        var termino = false

        vm.guardarCalificaciones(
            "EV1",
            mapOf("E3A-1" to "5,8", "E3A-2" to "7.0")
        ) { termino = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(termino)
        assertEquals(mapOf("E3A-1" to 5.8, "E3A-2" to 7.0), local.notasGuardadas)
        assertTrue(local.sinNotaGuardado.orEmpty().isEmpty())
        assertEquals("Notas guardadas correctamente.", vm.aviso.value?.texto)
    }

    @Test
    fun guardarCalificaciones_unaNotaInvalida_noGuardaParteDelLote() {
        iniciarSesion(docente)

        vm.guardarCalificaciones("EV1", mapOf("E3A-1" to "5,8", "E3A-2" to "7,1")) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso("Revisa las notas marcadas: no se guardó nada.")
        assertTrue(vm.aviso.value!!.esError)
        assertNull(local.notasGuardadas)
        assertFalse(local.llamadas.contains("guardarCalificaciones"))
    }

    @Test
    fun guardarCalificaciones_estudianteAjenoAlCurso_noGuardaNada() {
        iniciarSesion(docente)

        vm.guardarCalificaciones("EV1", mapOf("E4A-1" to "6,0")) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso("Revisa las notas marcadas: no se guardó nada.")
        assertFalse(local.llamadas.contains("guardarCalificaciones"))
    }

    @Test
    fun guardarCalificaciones_evaluacionInexistente_avisaSinEscribir() {
        iniciarSesion(docente)

        vm.guardarCalificaciones("EV-NO-EXISTE", mapOf("E3A-1" to "6,0")) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso(MENSAJE_EVALUACION_INEXISTENTE)
        assertFalse(local.llamadas.contains("guardarCalificaciones"))
    }

    @Test
    fun guardarCalificaciones_calificacionPreviasSinNota_seEliminaSinExigirOtraNota() {
        local.calificacionesState.value = listOf(
            Calificacion("EV1_E3A-1", "EV1", "E3A-1", 6.0, docente.id)
        )
        iniciarSesion(docente)

        vm.guardarCalificaciones("EV1", mapOf("E3A-1" to "  ")) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyMap<String, Double>(), local.notasGuardadas)
        assertEquals(listOf("E3A-1"), local.sinNotaGuardado)
        assertTrue(local.llamadas.contains("guardarCalificaciones"))
        assertEquals("Notas guardadas correctamente.", vm.aviso.value?.texto)
    }

    @Test
    fun guardarCalificaciones_todoEnBlancoSinCalificacionesPrevias_avisaSinEscribir() {
        iniciarSesion(docente)

        vm.guardarCalificaciones("EV1", mapOf("E3A-1" to "   ")) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso(MENSAJE_SIN_NOTAS)
        assertFalse(local.llamadas.contains("guardarCalificaciones"))
    }

    @Test
    fun guardarCalificaciones_mientrasGuarda_ignoraUnSegundoGuardado() {
        iniciarSesion(docente)
        val bloqueo = CompletableDeferred<Unit>()
        local.bloquearEscritura = bloqueo
        var terminoPrimero = false
        var terminoSegundo = false

        vm.guardarCalificaciones("EV1", mapOf("E3A-1" to "5,8")) { terminoPrimero = true }
        assertTrue(vm.guardando.value)

        vm.guardarCalificaciones("EV1", mapOf("E3A-2" to "6,0")) { terminoSegundo = true }
        assertEquals(1, local.llamadas.count { it == "guardarCalificaciones" })

        bloqueo.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.guardando.value)
        assertTrue(terminoPrimero)
        assertFalse(terminoSegundo)
    }

    @Test
    fun guardarCalificaciones_falloDelRepositorio_muestraErrorYLiberaElBoton() {
        iniciarSesion(docente)
        local.resultadoEscritura = ApiResult.Error("No tienes permiso para ingresar notas.", 403)
        var termino = false

        vm.guardarCalificaciones("EV1", mapOf("E3A-1" to "5,8")) { termino = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNullAviso("No tienes permiso para ingresar notas.")
        assertTrue(vm.aviso.value!!.esError)
        assertFalse(termino)
        assertFalse(vm.guardando.value)
    }

    private fun assertNotNullAviso(texto: String) {
        assertEquals(texto, vm.aviso.value?.texto)
    }
}

/**
 * [LibroMpazDatos] en memoria con datos de referencia y captura de escrituras. Con [prohibido]
 * falla en cualquier operación distinta de poblarSiVacia, que es lo único que SelectorFuente
 * llama en las dos fuentes al crear el ViewModel.
 */
private class RepositorioViewModelFalso(private val prohibido: Boolean = false) : LibroMpazDatos {

    val llamadas = mutableListOf<String>()

    val cursosState = MutableStateFlow(
        listOf(Curso("C3A", "3° Básico A"), Curso("C4A", "4° Básico A"))
    )
    val asignaturasState = MutableStateFlow(
        listOf(Asignatura("MAT", "Matemática"), Asignatura("LEN", "Lenguaje y Comunicación"))
    )
    val estudiantesState = MutableStateFlow(
        listOf(
            Estudiante("E3A-1", "Estudiante 3A-01", "C3A"),
            Estudiante("E3A-2", "Estudiante 3A-02", "C3A"),
            Estudiante("E4A-1", "Estudiante 4A-01", "C4A")
        )
    )
    val objetivosState = MutableStateFlow(
        listOf(
            ObjetivoAprendizaje("OA-MAT-1", "MAT", "OA-MAT-1", "Números naturales"),
            ObjetivoAprendizaje("OA-LEN-1", "LEN", "OA-LEN-1", "Lectura comprensiva")
        )
    )
    val cargasState = MutableStateFlow(listOf(CargaDocente("CD1", "U1", "C3A", "MAT")))
    val asistenciasState = MutableStateFlow(emptyList<RegistroAsistencia>())
    val sesionesState = MutableStateFlow(emptyList<SesionClase>())
    val evaluacionesState = MutableStateFlow(
        listOf(Evaluacion("EV1", "C3A", "MAT", "Prueba unidad 1", "2026-01-15"))
    )
    val calificacionesState = MutableStateFlow(emptyList<Calificacion>())
    val anotacionesState = MutableStateFlow(emptyList<Anotacion>())
    val auditoriaState = MutableStateFlow(emptyList<RegistroAuditoria>())

    var resultadoIngreso: ResultadoLogin = ResultadoLogin.Rechazado("sin resultado configurado")
    var excepcionIngreso: Throwable? = null
    var resultadoEscritura: ApiResult<Unit> = ApiResult.Exito(Unit)
    var bloquearEscritura: CompletableDeferred<Unit>? = null

    var ultimoIngreso: Pair<String, String>? = null
    var ultimosEstadosAsistencia: Map<String, EstadoAsistencia>? = null
    var notasGuardadas: Map<String, Double>? = null
    var sinNotaGuardado: List<String>? = null
    var ultimaDescripcion: String? = null
    var detalleGuardado: String? = null
    var tokenCerrado: String? = null

    override val cursos: Flow<List<Curso>> get() = flujo(cursosState)
    override val asignaturas: Flow<List<Asignatura>> get() = flujo(asignaturasState)
    override val estudiantes: Flow<List<Estudiante>> get() = flujo(estudiantesState)
    override val objetivos: Flow<List<ObjetivoAprendizaje>> get() = flujo(objetivosState)
    override val cargas: Flow<List<CargaDocente>> get() = flujo(cargasState)
    override val asistencias: Flow<List<RegistroAsistencia>> get() = flujo(asistenciasState)
    override val sesiones: Flow<List<SesionClase>> get() = flujo(sesionesState)
    override val evaluaciones: Flow<List<Evaluacion>> get() = flujo(evaluacionesState)
    override val calificaciones: Flow<List<Calificacion>> get() = flujo(calificacionesState)
    override val anotaciones: Flow<List<Anotacion>> get() = flujo(anotacionesState)
    override val auditoria: Flow<List<RegistroAuditoria>> get() = flujo(auditoriaState)

    override fun cargasDe(docenteId: String): Flow<List<CargaDocente>> {
        if (prohibido) prohibida("cargasDe")
        return cargasState.map { lista -> lista.filter { it.docenteId == docenteId } }
    }

    override suspend fun poblarSiVacia() = anotar("poblarSiVacia")

    override suspend fun ingresar(correo: String, clave: String): ResultadoLogin {
        anotar("ingresar")
        ultimoIngreso = correo to clave
        excepcionIngreso?.let { throw it }
        return resultadoIngreso
    }

    override fun retirarToken(): String? {
        anotar("retirarToken")
        return if (prohibido) "token-servidor" else "token-local"
    }

    override suspend fun cerrarSesionRemota(token: String?) {
        anotar("cerrarSesionRemota")
        tokenCerrado = token
    }

    override suspend fun sincronizar(usuario: Usuario): ApiResult<Unit> {
        anotar("sincronizar")
        return ApiResult.Exito(Unit)
    }

    override suspend fun asistenciaDe(cursoId: String, fecha: String): List<RegistroAsistencia> {
        anotar("asistenciaDe")
        return emptyList()
    }

    override suspend fun guardarAsistencia(
        usuario: Usuario,
        cursoId: String,
        fecha: String,
        estados: Map<String, EstadoAsistencia>,
        detalle: String
    ): ApiResult<Unit> {
        anotar("guardarAsistencia")
        esperar()
        ultimosEstadosAsistencia = estados
        detalleGuardado = detalle
        return resultadoEscritura
    }

    override suspend fun guardarSesion(
        usuario: Usuario,
        cursoId: String,
        asignaturaId: String,
        fecha: String,
        bloque: String,
        oaIds: List<String>,
        contenido: String,
        detalle: String
    ): ApiResult<Unit> {
        anotar("guardarSesion")
        esperar()
        detalleGuardado = detalle
        return resultadoEscritura
    }

    override suspend fun crearEvaluacion(
        usuario: Usuario,
        cursoId: String,
        asignaturaId: String,
        nombreInstrumento: String,
        fecha: String,
        detalle: String
    ): ApiResult<Unit> {
        anotar("crearEvaluacion")
        esperar()
        detalleGuardado = detalle
        return resultadoEscritura
    }

    override suspend fun eliminarEvaluacion(
        usuario: Usuario,
        evaluacion: Evaluacion,
        detalle: String
    ): ApiResult<Unit> {
        anotar("eliminarEvaluacion")
        esperar()
        detalleGuardado = detalle
        return resultadoEscritura
    }

    override suspend fun guardarCalificaciones(
        usuario: Usuario,
        evaluacionId: String,
        notas: Map<String, Double>,
        sinNota: List<String>,
        detalle: String
    ): ApiResult<Unit> {
        anotar("guardarCalificaciones")
        esperar()
        notasGuardadas = notas
        sinNotaGuardado = sinNota
        detalleGuardado = detalle
        return resultadoEscritura
    }

    override suspend fun registrarAnotacion(
        usuario: Usuario,
        estudianteId: String,
        tipo: TipoAnotacion,
        descripcion: String,
        detalle: String
    ): ApiResult<Unit> {
        anotar("registrarAnotacion")
        esperar()
        ultimaDescripcion = descripcion
        detalleGuardado = detalle
        return resultadoEscritura
    }

    override suspend fun restablecerDatosLocales() = anotar("restablecerDatosLocales")

    private suspend fun esperar() {
        bloquearEscritura?.await()
    }

    private fun <T> flujo(estado: MutableStateFlow<T>): Flow<T> {
        if (prohibido) prohibida("observar un Flow")
        return estado
    }

    private fun anotar(operacion: String) {
        if (prohibido && operacion != "poblarSiVacia") prohibida(operacion)
        llamadas += if (prohibido && operacion != "poblarSiVacia") "servidor:$operacion" else operacion
    }

    private fun prohibida(operacion: String): Nothing {
        throw AssertionError("La fuente servidor no debía usarse (operación: $operacion)")
    }
}
