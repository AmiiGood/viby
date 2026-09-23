package com.sweetcode.viby.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNamesTest {

    private fun separar(tag: String, vararg biblioteca: String) =
        ArtistNames.separar(tag, ArtistNames.conocidos(biblioteca.toList() + tag))

    // --- clave: el mismo artista escrito de varias formas ---

    @Test
    fun `ignora mayusculas acentos y puntuacion`() {
        val esperada = ArtistNames.clave("Rosalía")
        assertEquals(esperada, ArtistNames.clave("ROSALIA"))
        assertEquals(esperada, ArtistNames.clave("rosalia"))
        assertEquals(esperada, ArtistNames.clave("  Rosalía  "))
    }

    @Test
    fun `ignora el articulo inicial`() {
        assertEquals(ArtistNames.clave("The Strokes"), ArtistNames.clave("Strokes"))
    }

    @Test
    fun `distingue artistas diferentes`() {
        assert(ArtistNames.clave("Drake") != ArtistNames.clave("Drake Bell"))
    }

    // --- colaboraciones explicitas: se separan siempre ---

    @Test
    fun `separa feat aunque el otro no este en la biblioteca`() {
        assertEquals(listOf("Bad Bunny", "Drake"), separar("Bad Bunny feat. Drake"))
    }

    @Test
    fun `separa las variantes de feat`() {
        for (tag in listOf("A ft. B", "A ft B", "A featuring B", "A vs. B", "A versus B")) {
            assertEquals(tag, listOf("A", "B"), separar(tag))
        }
    }

    @Test
    fun `desenvuelve el parentesis`() {
        assertEquals(listOf("Bad Bunny", "Drake"), separar("Bad Bunny (feat. Drake)"))
        assertEquals(listOf("Bad Bunny", "Drake"), separar("Bad Bunny [ft. Drake]"))
    }

    // --- separadores ambiguos: solo si las partes existen solas ---

    @Test
    fun `separa la coma cuando los dos son artistas de la biblioteca`() {
        assertEquals(
            listOf("Bad Bunny", "Drake"),
            separar("Bad Bunny, Drake", "Bad Bunny", "Drake"),
        )
    }

    @Test
    fun `no separa la coma si el resto no son artistas`() {
        assertEquals(listOf("Earth, Wind & Fire"), separar("Earth, Wind & Fire"))
    }

    @Test
    fun `no parte los nombres de grupo que llevan separadores`() {
        for (nombre in listOf("AC/DC", "Simon & Garfunkel", "Florence + The Machine")) {
            assertEquals(nombre, listOf(nombre), separar(nombre))
        }
    }

    @Test
    fun `no parte un grupo aunque una parte suya sea artista`() {
        // "Fire" existe solo, pero "Earth" y "Wind" no: el grupo se queda entero.
        assertEquals(listOf("Earth, Wind & Fire"), separar("Earth, Wind & Fire", "Fire"))
    }

    @Test
    fun `el ampersand separa a dos artistas conocidos`() {
        assertEquals(listOf("Calle 13", "Rubén Blades"),
            separar("Calle 13 & Rubén Blades", "Calle 13", "Rubén Blades"))
    }

    // --- combinaciones ---

    @Test
    fun `combina feat con separador ambiguo`() {
        assertEquals(
            listOf("Karol G", "Nicki Minaj", "Shakira"),
            separar("Karol G feat. Nicki Minaj, Shakira", "Nicki Minaj", "Shakira"),
        )
    }

    @Test
    fun `deja en paz un nombre normal`() {
        assertEquals(listOf("Radiohead"), separar("Radiohead"))
    }
}
