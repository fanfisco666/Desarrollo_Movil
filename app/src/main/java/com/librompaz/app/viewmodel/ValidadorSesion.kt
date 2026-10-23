package com.librompaz.app.viewmodel

import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.Usuario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reglas de validación de RF-06 (registrar una sesión de clase).
 *
 * Es Kotlin puro (sin Android) para poder probarla con JUnit. Lo usa LibroMpazViewModel.
 * La unicidad curso + asignatura + fecha + bloque NO se valida aquí: la revisa el Repository
 * y la garantiza el índice único de la tabla `sesiones`.
 *
 * @param hoy fecha de hoy en AAAA-MM-DD (se puede reemplazar en las pruebas).
 */
class ValidadorSesion(
    private val cargas: List<CargaDocente>,
    private val objetivos: List<ObjetivoAprendizaje>,
    private val hoy: () -> String = { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
) {

    /** Devuelve el mensaje de error, o null si la sesión es válida. Las reglas se aplican en este orden. */
    fun validar(
        usuario: Usuario?,
        cursoId: String,
        asignaturaId: String,
        fecha: String,
        bloque: String,
        oaIds: List<String>,
        contenido: String
    ): String? {
        if (usuario == null) return "Debes iniciar sesión."
        if (usuario.perfil != Perfil.DOCENTE) return "Tu perfil no puede registrar clases."
        if (!tieneCarga(usuario, cursoId, asignaturaId)) return "No tienes asignado este curso o asignatura."
        validarFecha(fecha)?.let { return it }
        if (bloque.isBlank()) return "El bloque es obligatorio."
        if (oaIds.isEmpty()) return "Marca al menos un OA."
        if (contenido.isBlank()) return "Escribe los contenidos abordados."
        val oasValidos = objetivos.filter { it.asignaturaId == asignaturaId }.map { it.id }.toSet()
        if (!oasValidos.containsAll(oaIds)) return "Hay OA que no pertenecen a esta asignatura."
        return null
    }

    /** RN-03: el docente solo opera sobre sus cargas. */
    fun tieneCarga(usuario: Usuario, cursoId: String, asignaturaId: String? = null): Boolean =
        cargas.any {
            it.docenteId == usuario.id && it.cursoId == cursoId &&
                (asignaturaId == null || it.asignaturaId == asignaturaId)
        }

    /** Fecha AAAA-MM-DD válida y no futura. Devuelve el error o null. (Sin java.time: minSdk 24.) */
    fun validarFecha(fecha: String): String? {
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(fecha)) return "La fecha debe tener formato AAAA-MM-DD."
        val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        if (runCatching { formato.parse(fecha) }.getOrNull() == null) return "La fecha no es válida."
        if (fecha > hoy()) return "No se permiten fechas futuras."
        return null
    }
}
