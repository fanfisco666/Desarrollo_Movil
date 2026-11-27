package com.librompaz.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vacíos de cobertura: el TypeConverter que guarda los OA de una sesión como texto en Room. */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun listaATexto_unaVariosYVacia_separaPorComas() {
        assertEquals("OA-MAT-1", converters.listaATexto(listOf("OA-MAT-1")))
        assertEquals("OA-MAT-1,OA-LEN-1", converters.listaATexto(listOf("OA-MAT-1", "OA-LEN-1")))
        assertEquals("", converters.listaATexto(emptyList()))
    }

    @Test
    fun textoALista_textoVacioOSoloComas_devuelveListaVacia() {
        assertEquals(emptyList<String>(), converters.textoALista(""))
        assertEquals(emptyList<String>(), converters.textoALista(","))
        assertEquals(emptyList<String>(), converters.textoALista(" , , "))
    }

    @Test
    fun textoALista_ignoraLosSegmentosEnBlanco() {
        assertEquals(listOf("OA-MAT-1", "OA-LEN-1"), converters.textoALista("OA-MAT-1,,OA-LEN-1"))
        assertEquals(listOf("OA-MAT-1"), converters.textoALista("OA-MAT-1,"))
    }

    @Test
    fun idaYVuelta_unaListaConOaSeRecuperaIgual() {
        val original = listOf("OA-MAT-1", "OA-MAT-2", "OA-LEN-1")
        assertEquals(original, converters.textoALista(converters.listaATexto(original)))
    }
}
