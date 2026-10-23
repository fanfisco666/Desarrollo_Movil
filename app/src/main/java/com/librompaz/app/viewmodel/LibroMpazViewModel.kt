package com.librompaz.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.librompaz.app.data.local.LibroMpazDatabase
import com.librompaz.app.data.repository.SesionRepository
import com.librompaz.app.data.MockData
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
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.ResumenCurso
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado de guardar una sesión. `id` único: cada intento se notifica aunque el texto se repita. */
data class ResultadoSesion(val mensaje: String, val exito: Boolean, val id: Long = System.nanoTime())

class LibroMpazViewModel(application: Application) : AndroidViewModel(application) {

    // Capa de datos (RF-06): ViewModel -> Repository -> DAO -> Room (SQLite).
    private val repositorioSesiones = SesionRepository(LibroMpazDatabase.obtener(application))

    init {
        // Primera ejecución: carga la clase de ejemplo S1 en Room (después los datos ya persisten).
        viewModelScope.launch { repositorioSesiones.sembrarSiVacia(MockData.sesiones) }
    }

    // =====================================================================
    // Módulo "Autenticación y alcance por rol"
    // =====================================================================

    private val _usuarioActivo = MutableStateFlow<Usuario?>(null)
    val usuarioActivo: StateFlow<Usuario?> = _usuarioActivo.asStateFlow()

    private val _errorLogin = MutableStateFlow<String?>(null)
    val errorLogin: StateFlow<String?> = _errorLogin.asStateFlow()

    /**
     * RF-01: autentica contra MockData.usuarios (datos ficticios).
     * - Correo o clave vacíos: mensaje de campo obligatorio.
     * - Credenciales incorrectas: "Correo o clave incorrectos" (no revela qué perfiles existen, RN-01).
     * Si son correctas deja el usuario en _usuarioActivo y devuelve true; AppNavigation lo lleva a su pantalla.
     */
    fun login(usuario: String, clave: String): Boolean {
        val correo = usuario.trim()
        if (correo.isBlank() || clave.isBlank()) {
            _errorLogin.value = "Ingresa tu correo y tu clave."
            return false
        }
        val encontrado = MockData.usuarios.find {
            it.usuario.equals(correo, ignoreCase = true) && it.clave == clave
        }
        if (encontrado == null) {
            _errorLogin.value = "Correo o clave incorrectos"
            return false
        }
        _usuarioActivo.value = encontrado
        _errorLogin.value = null
        return true
    }

    fun logout() {
        _usuarioActivo.value = null
        _errorLogin.value = null
        _cursoSeleccionadoId.value = null
        _asignaturaSeleccionadaId.value = null
    }

    // =====================================================================
    // Datos de referencia y selección (no es lógica de negocio)
    // =====================================================================

    val cursos: List<Curso> = MockData.cursos
    val asignaturas: List<Asignatura> = MockData.asignaturas
    private val estudiantes: List<Estudiante> = MockData.estudiantes
    private val objetivos: List<ObjetivoAprendizaje> = MockData.objetivos

    private val _cursoSeleccionadoId = MutableStateFlow<String?>(null)
    val cursoSeleccionadoId: StateFlow<String?> = _cursoSeleccionadoId.asStateFlow()

    private val _asignaturaSeleccionadaId = MutableStateFlow<String?>(null)
    val asignaturaSeleccionadaId: StateFlow<String?> = _asignaturaSeleccionadaId.asStateFlow()

    fun seleccionarCurso(cursoId: String?, asignaturaId: String? = null) {
        _cursoSeleccionadoId.value = cursoId
        _asignaturaSeleccionadaId.value = asignaturaId
    }

    fun cursoPorId(id: String?): Curso? = cursos.find { it.id == id }
    fun asignaturaPorId(id: String?): Asignatura? = asignaturas.find { it.id == id }
    fun estudiantePorId(id: String?): Estudiante? = estudiantes.find { it.id == id }
    fun estudiantesDelCurso(cursoId: String?): List<Estudiante> = estudiantes.filter { it.cursoId == cursoId }
    fun objetivosDe(asignaturaId: String?): List<ObjetivoAprendizaje> = objetivos.filter { it.asignaturaId == asignaturaId }

    /**
     * TODO(equipo): devolver SOLO las cargas del docente autenticado.
     * RF: "mostrar únicamente los cursos/asignaturas bajo su asignación formal" (RBAC).
     * Hoy devuelve todas las cargas, incluso las de otros docentes.
     */
    fun misCargas(usuario: Usuario?): List<CargaDocente> = MockData.cargas

    // =====================================================================
    // Módulo "Asistencia"
    // =====================================================================

    private val _asistencias = MutableStateFlow(MockData.asistencias)

    /**
     * TODO(equipo): devolver la asistencia ya guardada del curso en esa fecha
     * (idEstudiante -> estado), para mostrarla y poder corregirla. Hoy devuelve un mapa vacío.
     * Pendiente con el CITT: si se puede corregir la asistencia de días anteriores.
     */
    fun asistenciaGuardada(cursoId: String, fecha: String): Map<String, EstadoAsistencia> = emptyMap()

    /**
     * TODO(equipo): guardar (o sobrescribir) la asistencia del curso en esa fecha, con
     * registradoPor = usuario activo y la fecha y hora actuales, y dejar un RegistroAuditoria.
     * RF: "registrar la asistencia diaria con estados presente, ausente, atrasado, justificado".
     * Hoy no hace nada.
     */
    fun guardarAsistencia(cursoId: String, fecha: String, estados: Map<String, EstadoAsistencia>) {
        // Sin implementar todavía.
    }

    /**
     * TODO(equipo): porcentaje de asistencia del curso (0 a 100) para Inspectoría y Dirección.
     * Hoy devuelve null (sin calcular).
     */
    fun porcentajeAsistencia(cursoId: String): Int? = null

    /**
     * TODO(equipo): estudiantes con inasistencias reiteradas (por ejemplo, 3 o más ausencias),
     * con su número de ausencias. RF de Inspectoría: "identificar inasistencias reiteradas".
     * Hoy devuelve una lista vacía.
     */
    fun inasistenciasReiteradas(): List<Pair<Estudiante, Int>> = emptyList()

    // =====================================================================
    // Módulo "Cobertura curricular"
    // =====================================================================

    /** Fuente de verdad: Room. Cualquier cambio en la tabla re-emite la lista a las pantallas. */
    val sesiones: StateFlow<List<SesionClase>> = repositorioSesiones.observarSesiones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _resultadoSesion = MutableStateFlow<ResultadoSesion?>(null)
    val resultadoSesion: StateFlow<ResultadoSesion?> = _resultadoSesion.asStateFlow()

    /** Mensaje simple (texto) para pantallas que aún no usan [resultadoSesion]. */
    private val _errorSesion = MutableStateFlow<String?>(null)
    val errorSesion: StateFlow<String?> = _errorSesion.asStateFlow()

    fun limpiarResultadoSesion() {
        _resultadoSesion.value = null
        _errorSesion.value = null
    }

    fun sesionesDe(cursoId: String?, asignaturaId: String?): List<SesionClase> =
        sesiones.value.filter { it.cursoId == cursoId && it.asignaturaId == asignaturaId }

    /**
     * RF-06: guarda una sesión con sus OA y contenidos en Room.
     * 1) Valida en el ViewModel (perfil, carga propia, fecha, bloque, OA, contenido).
     * 2) El Repository revisa duplicados y guarda sesión + OA + auditoría en UNA transacción (RN-02).
     * El resultado (éxito o error) sale por [resultadoSesion].
     */
    fun guardarSesionEnRoom(
        cursoId: String,
        asignaturaId: String,
        fecha: String,
        bloque: String,
        oaIds: List<String>,
        contenido: String
    ) {
        val error = validarSesion(cursoId, asignaturaId, fecha, bloque, oaIds, contenido)
        if (error != null) {
            _resultadoSesion.value = ResultadoSesion(error, exito = false)
            _errorSesion.value = error
            return
        }
        val usuario = _usuarioActivo.value ?: return
        viewModelScope.launch {
            val errorRepo = repositorioSesiones.guardar(
                cursoId = cursoId,
                asignaturaId = asignaturaId,
                fecha = fecha,
                bloque = bloque,
                oaIds = oaIds,
                contenido = contenido,
                registradoPor = usuario.nombre,
                perfil = usuario.perfil,
                nombreCurso = cursoPorId(cursoId)?.nombre ?: cursoId,
                nombreAsignatura = asignaturaPorId(asignaturaId)?.nombre ?: asignaturaId
            )
            if (errorRepo == null) {
                _resultadoSesion.value = ResultadoSesion("Clase guardada correctamente.", exito = true)
                _errorSesion.value = "¡Guardado con éxito en la base de datos!"
            } else {
                _resultadoSesion.value = ResultadoSesion(errorRepo, exito = false)
                _errorSesion.value = errorRepo
            }
        }
    }

    /** Reglas de RF-06 en una clase pura (ValidadorSesion), para poder probarlas con JUnit. */
    private val validadorSesion = ValidadorSesion(MockData.cargas, MockData.objetivos)

    /** Validaciones de RF-06 (sin tocar la base de datos). Devuelve el error o null si es válida. */
    fun validarSesion(cursoId: String, asignaturaId: String, fecha: String, bloque: String, oaIds: List<String>, contenido: String): String? =
        validadorSesion.validar(_usuarioActivo.value, cursoId, asignaturaId, fecha, bloque, oaIds, contenido)

    /**
     * TODO(equipo): ids de los OA ya trabajados en el curso y asignatura (según las sesiones
     * registradas). RF de UTP: "vistas analíticas de cobertura curricular por curso".
     * Hoy devuelve un conjunto vacío.
     */
    fun oasTrabajados(cursoId: String?, asignaturaId: String?): Set<String> = emptySet()

    // =====================================================================
    // Módulo "Calificaciones"
    // =====================================================================

    private val _evaluaciones = MutableStateFlow(MockData.evaluaciones)
    val evaluaciones: StateFlow<List<Evaluacion>> = _evaluaciones.asStateFlow()

    private val _calificaciones = MutableStateFlow(MockData.calificaciones)

    fun evaluacionesDe(cursoId: String?, asignaturaId: String?): List<Evaluacion> =
        _evaluaciones.value.filter { it.cursoId == cursoId && it.asignaturaId == asignaturaId }

    fun calificacionesDe(evaluacionId: String?): List<Calificacion> =
        _calificaciones.value.filter { it.evaluacionId == evaluacionId }

    /**
     * TODO(equipo): crear la evaluación (instrumento + fecha) para el curso y la asignatura, con
     * auditoría. Validar nombre y fecha obligatorios. Hoy no hace nada.
     */
    fun crearEvaluacion(cursoId: String, asignaturaId: String, nombreInstrumento: String, fecha: String) {
        // Sin implementar todavía.
    }

    /**
     * TODO(equipo): validar una nota escrita por el docente y devolver el mensaje de error o
     * null si es válida (número con coma o punto; rango 1,0 a 7,0 si el CITT lo confirma).
     * Hoy siempre devuelve null (sin validar).
     */
    fun validarNota(texto: String): String? = null

    /**
     * TODO(equipo): guardar las notas de la evaluación (idEstudiante -> nota escrita), con
     * auditoría. RF: "registrar calificaciones asociadas a un instrumento y fecha". Hoy no hace nada.
     */
    fun guardarCalificaciones(evaluacionId: String, notas: Map<String, String>) {
        // Sin implementar todavía.
    }

    /**
     * TODO(equipo): detectar desfases u omisiones de registro de notas, por ejemplo
     * evaluaciones con estudiantes sin nota, o cursos/asignaturas sin evaluaciones.
     * RF de UTP: "identificar desfases u omisiones de registro". Hoy devuelve una lista vacía.
     */
    fun desfasesCalificaciones(): List<String> = emptyList()

    // =====================================================================
    // Módulo "Hoja de vida"
    // =====================================================================

    private val _anotaciones = MutableStateFlow(MockData.anotaciones)
    val anotaciones: StateFlow<List<Anotacion>> = _anotaciones.asStateFlow()

    fun anotacionesDe(estudianteId: String?): List<Anotacion> =
        _anotaciones.value.filter { it.estudianteId == estudianteId }

    /**
     * TODO(equipo): registrar la anotación con marca temporal y "firma digital" del docente
     * (usuario activo + fecha y hora), y dejar auditoría. Hoy no hace nada.
     */
    fun registrarAnotacion(estudianteId: String, tipo: TipoAnotacion, descripcion: String) {
        // Sin implementar todavía.
    }

    /**
     * TODO(equipo): anotaciones que requieren intervención de Inspectoría (por ejemplo,
     * negativas reiteradas de un mismo estudiante). Hoy devuelve una lista vacía.
     */
    fun anotacionesQueRequierenIntervencion(): List<Anotacion> = emptyList()

    // =====================================================================
    // Módulo "Panel administrativo" (Dirección) y auditoría
    // =====================================================================

    /** Auditoría real (Room, más reciente primero) seguida de los registros de ejemplo. */
    val auditoria: StateFlow<List<RegistroAuditoria>> = repositorioSesiones.observarAuditoria()
        .map { it + MockData.auditoria }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MockData.auditoria)

    /**
     * TODO(equipo): calcular las métricas consolidadas de cada curso (asistencia, sesiones,
     * notas registradas, anotaciones negativas). RF de Dirección: "estado global administrativo
     * del colegio". Hoy devuelve los cursos sin métricas (valores null).
     */
    fun resumenPorCurso(): List<ResumenCurso> = cursos.map { ResumenCurso(cursoId = it.id) }
}
