package com.sweetcode.viby.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El titulo ICY es texto libre y es la pieza mas fragil de la busqueda de
 * caratulas: si cuela basura, se gastan peticiones buscando portadas de anuncios.
 */
class NowPlayingArtworkTest {

    private fun query(raw: String) = NowPlayingArtwork.toQuery(raw)

    @Test
    fun `separa artista y titulo quitando el guion`() {
        assertEquals("Post Malone Circles", query("Post Malone - Circles"))
    }

    @Test
    fun `tolera espacios de sobra`() {
        assertEquals("a-ha Take on Me", query("   a-ha  -  Take on Me   "))
    }

    @Test
    fun `mantiene los guiones que forman parte del nombre`() {
        assertEquals("Jay-Z 99 Problems", query("Jay-Z - 99 Problems"))
    }

    @Test
    fun `acepta titulos sin guion`() {
        assertEquals("Bohemian Rhapsody", query("Bohemian Rhapsody"))
    }

    @Test
    fun `descarta lo que no es una cancion`() {
        assertNull(query(""))
        assertNull(query("   "))
        assertNull(query("-"))
        assertNull(query("Unknown"))
        assertNull(query("unknown artist"))
        assertNull(query("No Title"))
        assertNull(query("advert break"))
        assertNull(query("publicidad"))
    }

    @Test
    fun `descarta la emisora anunciando su web`() {
        assertNull(query("https://www.radio.example"))
        assertNull(query("Escuchanos en www.miradio.com"))
    }

    @Test
    fun `descarta cadenas demasiado cortas para buscar`() {
        assertNull(query("ab"))
        assertNull(query("abc"))
    }
}
