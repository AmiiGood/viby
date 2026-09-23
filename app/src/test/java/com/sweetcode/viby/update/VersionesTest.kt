package com.sweetcode.viby.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionesTest {

    @Test
    fun `detecta una version posterior`() {
        assertTrue(esMasNueva("1.7", "1.6"))
        assertTrue(esMasNueva("2.0", "1.9"))
    }

    @Test
    fun `no avisa con la misma version`() {
        assertFalse(esMasNueva("1.6", "1.6"))
    }

    @Test
    fun `no avisa con una version anterior`() {
        assertFalse(esMasNueva("1.5", "1.6"))
    }

    @Test
    fun `no compara como texto`() {
        // El caso que rompe el orden alfabético: "1.10" va antes que "1.9".
        assertTrue(esMasNueva("1.10", "1.9"))
        assertFalse(esMasNueva("1.9", "1.10"))
    }

    @Test
    fun `acepta la v del tag`() {
        assertTrue(esMasNueva("v1.7", "1.6"))
        assertFalse(esMasNueva("v1.6", "1.6"))
    }

    @Test
    fun `maneja distinto numero de partes`() {
        assertTrue(esMasNueva("1.6.1", "1.6"))
        assertFalse(esMasNueva("1.6", "1.6.1"))
        assertFalse(esMasNueva("1.6.0", "1.6"))
    }

    @Test
    fun `no avisa con basura`() {
        assertFalse(esMasNueva("", "1.6"))
        assertFalse(esMasNueva("nightly", "1.6"))
    }

    @Test
    fun `sobrevive a un sufijo`() {
        assertTrue(esMasNueva("1.7-beta", "1.6"))
    }
}

class NotasTest {

    @Test
    fun `quita las marcas de markdown`() {
        val md = """
            ### Ya no suenan dos cosas a la vez
            El reproductor **no pedía** el foco de audio.

            - Primer arreglo
            * Segundo arreglo
        """.trimIndent()
        val esperado = """
            Ya no suenan dos cosas a la vez
            El reproductor no pedía el foco de audio.

            • Primer arreglo
            • Segundo arreglo
        """.trimIndent()
        assertEquals(esperado, notasLegibles(md))
    }

    @Test
    fun `deja solo el texto de los enlaces`() {
        assertEquals("Vía Radio Browser", notasLegibles("Vía [Radio Browser](https://x.com)"))
    }

    @Test
    fun `no deja huecos grandes`() {
        assertEquals("a\n\nb", notasLegibles("a\n\n\n\n b \n"))
    }
}
