package com.sweetcode.viby.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Uniones manuales: Panda y PXNDX son el mismo y ningún nombre lo delata. */
class ArtistAliasTest {

    private var n = 0
    private fun cancion(artista: String) = "id${n++}" to artista

    private val biblioteca = listOf(
        cancion("Panda"), cancion("Panda"), cancion("Panda"),
        cancion("PXNDX"), cancion("PXNDX"),
        cancion("Zoé"),
    )

    private fun clave(s: String) = ArtistNames.clave(s)

    @Test
    fun `sin unir son dos artistas`() {
        val r = ArtistNames.agruparEtiquetas(biblioteca)
        assertEquals(3, r.size)
        assertEquals(3, r.first { it.clave == clave("Panda") }.ids.size)
    }

    @Test
    fun `al unir se juntan las canciones bajo el destino`() {
        val r = ArtistNames.agruparEtiquetas(biblioteca, mapOf(clave("PXNDX") to clave("Panda")))
        assertEquals(2, r.size)
        val panda = r.first { it.clave == clave("Panda") }
        assertEquals(5, panda.ids.size)
        assertEquals("Panda", panda.nombre)
        assertEquals(listOf("PXNDX"), panda.unidos)
    }

    @Test
    fun `el destino elegido es el que da el nombre aunque tenga menos canciones`() {
        val r = ArtistNames.agruparEtiquetas(biblioteca, mapOf(clave("Panda") to clave("PXNDX")))
        val grupo = r.first { it.clave == clave("PXNDX") }
        assertEquals(5, grupo.ids.size)
        assertEquals("PXNDX", grupo.nombre)
    }

    @Test
    fun `las uniones encadenadas llegan hasta el final`() {
        val alias = mapOf(clave("a") to clave("b"), clave("b") to clave("c"))
        assertEquals(clave("c"), ArtistNames.destino(clave("a"), alias))
    }

    @Test
    fun `un ciclo guardado no cuelga la app`() {
        val alias = mapOf(clave("a") to clave("b"), clave("b") to clave("a"))
        // Lo que importa es que termine y dé algo estable, no cuál de los dos.
        val d = ArtistNames.destino(clave("a"), alias)
        assertEquals(d, ArtistNames.destino(clave("a"), alias))
    }

    @Test
    fun `unir no afecta a los demas`() {
        val r = ArtistNames.agruparEtiquetas(biblioteca, mapOf(clave("PXNDX") to clave("Panda")))
        assertEquals(1, r.first { it.clave == clave("Zoé") }.ids.size)
    }
}
