package com.librompaz.app.data.local

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Vacíos de cobertura: el [AlmacenModo] real sobre SharedPreferences. */
class AlmacenModoPreferenciasTest {

    private lateinit var contexto: Context
    private lateinit var preferencias: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun preparar() {
        contexto = mockk(relaxed = true)
        preferencias = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { contexto.applicationContext } returns contexto
        every { contexto.getSharedPreferences("modo_datos", Context.MODE_PRIVATE) } returns preferencias
        every { preferencias.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
    }

    @Test
    fun construir_usaElContextoDeLaAplicacionYElArchivoModoDatos() {
        AlmacenModoPreferencias(contexto)

        verify { contexto.applicationContext }
        verify { contexto.getSharedPreferences("modo_datos", Context.MODE_PRIVATE) }
    }

    @Test
    fun leer_sinClaveGuardada_devuelveNullParaQueMandElValorInicial() {
        every { preferencias.contains("modo_local") } returns false

        assertNull(AlmacenModoPreferencias(contexto).leer())
    }

    @Test
    fun leer_conClaveGuardada_devuelveElBooleanoGuardado() {
        every { preferencias.contains("modo_local") } returns true
        every { preferencias.getBoolean("modo_local", false) } returns true

        assertTrue(AlmacenModoPreferencias(contexto).leer() == true)

        every { preferencias.getBoolean("modo_local", false) } returns false
        assertFalse(AlmacenModoPreferencias(contexto).leer() == true)
    }

    @Test
    fun guardar_escribeModoLocalYAplicaElCambio() {
        val almacen = AlmacenModoPreferencias(contexto)

        almacen.guardar(true)
        verify { editor.putBoolean("modo_local", true) }
        verify(exactly = 1) { editor.apply() }

        almacen.guardar(false)
        verify { editor.putBoolean("modo_local", false) }
        verify(exactly = 2) { editor.apply() }
    }
}
