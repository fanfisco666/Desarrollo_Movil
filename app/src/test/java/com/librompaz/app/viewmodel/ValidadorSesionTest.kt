package com.librompaz.app.viewmodel

import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.Usuario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas unitarias de RF-06 (reglas de ValidadorSesion). Datos propios y fecha de hoy fija. */
class ValidadorSesionTest {

    private val docenteMat = Usuario("U1", "Docente Matemática", "docente.mat@libromp.cl", "x", Perfil.DOCENTE)
    private val docenteLen = Usuario("U2", "Docente Lenguaje", "docente.len@libromp.cl", "x", Perfil.DOCENTE)
    private val utp = Usuario("U3", "UTP", "utp@libromp.cl", "x", Perfil.UTP)

    private val cargas = listOf(
        CargaDocente("CD1", "U1", "C3A", "MAT"),
        CargaDocente("CD2", "U1", "C4A", "MAT"),
        CargaDocente("CD3", "U2", "C4A", "LEN")
    )
    private val objetivos = listOf(
        ObjetivoAprendizaje("OA-MAT-1", "MAT", "OA 1", "Contar"),
        ObjetivoAprendizaje("OA-MAT-2", "MAT", "OA 2", "Leer números"),
        ObjetivoAprendizaje("OA-LEN-1", "LEN", "OA 1", "Leer textos")
    )

    private val validador = ValidadorSesion(cargas, objetivos, hoy = { "2026-10-22" })

    /** Llama a validar() con una sesión correcta de 3° Básico A Matemática; cada prueba cambia solo lo que necesita. */
    private fun validar(
        usuario: Usuario? = docenteMat,
        cursoId: String = "C3A",
        asignaturaId: String = "MAT",
        fecha: String = "2026-10-22",
        bloque: String = "Bloque 1",
        oaIds: List<String> = listOf("OA-MAT-1"),
        contenido: String = "Conteo de 10 en 10"
    ) = validador.validar(usuario, cursoId, asignaturaId, fecha, bloque, oaIds, contenido)

    @Test
    fun sesionValida_noDevuelveError() {
        assertNull(validar())
    }

    // ---- RF-02 / RN-01: sesión y perfil ----

    @Test
    fun sinSesion_noGuarda() {
        assertEquals("Debes iniciar sesión.", validar(usuario = null))
    }

    @Test
    fun perfilUtp_noPuedeRegistrarClases() {
        assertEquals("Tu perfil no puede registrar clases.", validar(usuario = utp))
    }

    // ---- RN-03: el docente solo opera sobre sus cargas ----

    @Test
    fun rn03_docenteDeLenguaje_noPuedeGuardarEnMatematica3A() {
        assertEquals(
            "No tienes asignado este curso o asignatura.",
            validar(usuario = docenteLen, cursoId = "C3A", asignaturaId = "MAT")
        )
    }

    @Test
    fun rn03_docenteDeMatematica_noPuedeGuardarLenguaje4A() {
        assertEquals(
            "No tienes asignado este curso o asignatura.",
            validar(cursoId = "C4A", asignaturaId = "LEN", oaIds = listOf("OA-LEN-1"))
        )
    }

    @Test
    fun rn03_docenteConDosCargas_puedeGuardarEnAmbas() {
        assertNull(validar(cursoId = "C4A"))
    }

    @Test
    fun rn03_tieneCarga_conYSinAsignatura() {
        assertTrue(validador.tieneCarga(docenteMat, "C3A"))
        assertTrue(validador.tieneCarga(docenteMat, "C3A", "MAT"))
        assertFalse(validador.tieneCarga(docenteMat, "C3A", "LEN"))
        assertFalse(validador.tieneCarga(docenteLen, "C3A"))
    }

    // ---- Fecha ----

    @Test
    fun fecha_conFormatoIncorrecto_seRechaza() {
        assertEquals("La fecha debe tener formato AAAA-MM-DD.", validar(fecha = "2026-1-5"))
        assertEquals("La fecha debe tener formato AAAA-MM-DD.", validar(fecha = "abc"))
        assertEquals("La fecha debe tener formato AAAA-MM-DD.", validar(fecha = ""))
    }

    @Test
    fun fecha_inexistente_seRechaza() {
        assertEquals("La fecha no es válida.", validar(fecha = "2026-02-30"))
        assertEquals("La fecha no es válida.", validar(fecha = "2026-13-01"))
    }

    @Test
    fun fecha_futura_seRechaza() {
        assertEquals("No se permiten fechas futuras.", validar(fecha = "2026-10-23"))
    }

    @Test
    fun fecha_deHoyYPasada_seAcepta() {
        assertNull(validar(fecha = "2026-10-22"))
        assertNull(validar(fecha = "2026-10-01"))
    }

    // ---- Campos obligatorios y OA ----

    @Test
    fun bloqueVacio_seRechaza() {
        assertEquals("El bloque es obligatorio.", validar(bloque = "   "))
    }

    @Test
    fun sinOa_seRechaza() {
        assertEquals("Marca al menos un OA.", validar(oaIds = emptyList()))
    }

    @Test
    fun contenidoEnBlanco_seRechaza() {
        assertEquals("Escribe los contenidos abordados.", validar(contenido = "   "))
    }

    @Test
    fun oaDeOtraAsignatura_seRechaza() {
        assertEquals("Hay OA que no pertenecen a esta asignatura.", validar(oaIds = listOf("OA-LEN-1")))
    }

    @Test
    fun variosOaDeLaAsignatura_seAceptan() {
        assertNull(validar(oaIds = listOf("OA-MAT-1", "OA-MAT-2")))
    }
}
