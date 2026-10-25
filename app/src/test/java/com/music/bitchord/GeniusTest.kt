package com.music.bitchord

import com.music.bitchord.data.lyrics.Genius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody

class GeniusTest {

    private val sampleGeniusHtml = """
        <!DOCTYPE html>
        <html>
        <head><title>Queen - Bohemian Rhapsody Lyrics | Genius Lyrics</title></head>
        <body>
          <div data-lyrics-container="true" class="Lyrics__Container">
            <div data-exclude-from-selection="true" class="LyricsHeader__Container">
              <button>523 Contributors</button>
              <div class="SongBioPreview__Container">Song Bio</div>
            </div>
            [Intro]<br>
            Is this the real life?<br>
            Is this just fantasy?<br>
            <br>
            [Verse 1]<br>
            Mama, just killed a man<br>
            Put a gun against his head, pulled my trigger, now he's dead<br>
            15You might also like<br>
          </div>
          <div data-lyrics-container="true" class="Lyrics__Container">
            [Chorus]<br>
            Mama, life had just begun<br>
            But now I've gone and thrown it all away<br>
            42Embed
          </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `detects section headers correctly`() {
        assertTrue(Genius.isSectionHeader("[Verse 1]"))
        assertTrue(Genius.isSectionHeader("[Chorus]"))
        assertTrue(Genius.isSectionHeader("[Guitar Solo]"))
        assertTrue(Genius.isSectionHeader("[Bridge: Freddie Mercury]"))
        assertFalse(Genius.isSectionHeader("Is this the real life?"))
        assertFalse(Genius.isSectionHeader("[short"))
        assertFalse(Genius.isSectionHeader("]short["))
    }

    @Test
    fun `parses genius html and extracts clean lyrics and sections`() {
        val lines = Genius.parseHtml(sampleGeniusHtml)
        assertNotNull(lines)
        val texts = lines!!.map { it.text }

        // Headers excluded
        assertFalse(texts.any { it.contains("Contributors") })
        assertFalse(texts.any { it.contains("Song Bio") })

        // Artifacts stripped
        assertFalse(texts.any { it.contains("You might also like") })
        assertFalse(texts.any { it.contains("Embed") })

        // Sections and lyrics present
        assertTrue(texts.contains("[Intro]"))
        assertTrue(texts.contains("Is this the real life?"))
        assertTrue(texts.contains("Is this just fantasy?"))
        assertTrue(texts.contains("[Verse 1]"))
        assertTrue(texts.contains("Mama, just killed a man"))
        assertTrue(texts.contains("[Chorus]"))
        assertTrue(texts.contains("Mama, life had just begun"))
        assertTrue(texts.contains("But now I've gone and thrown it all away"))
    }

    @Test
    fun `strips artifacts like embed counters and unicode spaces`() {
        val raw = "Some lyric line\u00A0with non-breaking spaces\n12You might also like\nAnother line\n345Embed"
        val cleaned = Genius.stripArtifacts(raw)

        assertFalse(cleaned.contains("You might also like"))
        assertFalse(cleaned.contains("Embed"))
        assertFalse(cleaned.contains('\u00A0'))
        assertTrue(cleaned.contains("Some lyric line with non-breaking spaces"))
        assertTrue(cleaned.contains("Another line"))
    }

    @Test
    fun `converts multi-line text into lyric lines with proper stanza separation`() {
        val input = """
            [Verse 1]
            Line 1
            Line 2

            [Chorus]
            Line 3
        """.trimIndent()

        val lines = Genius.textToLyricLines(input)
        assertEquals(6, lines.size)
        assertEquals("[Verse 1]", lines[0].text)
        assertEquals("Line 1", lines[1].text)
        assertEquals("Line 2", lines[2].text)
        assertTrue(lines[3].isGap)
        assertEquals("[Chorus]", lines[4].text)
        assertEquals("Line 3", lines[5].text)
    }

    @Test
    fun `searches noisy titles and scrapes lyrics without live network access`() = kotlinx.coroutines.runBlocking {
        val queries = mutableListOf<String>()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("BitChord", request.header("User-Agent"))
            val body = if (request.url.encodedPath == "/api/search/multi") {
                queries += request.url.queryParameter("q")!!
                """{"response":{"sections":[{"type":"song","hits":[{"result":{
                    "title":"Bohemian Rhapsody","artist_names":"Queen",
                    "url":"https://genius.com/Queen-bohemian-rhapsody-lyrics"
                }}]}]}}"""
            } else {
                assertEquals("/Queen-bohemian-rhapsody-lyrics", request.url.encodedPath)
                sampleGeniusHtml
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val expected = Genius.parseHtml(sampleGeniusHtml)
        for (title in listOf("Bohemian Rhapsody (Official Video)", "♪ Queen - Bohemian Rhapsody [OFFICIAL MUSIC VIDEO] Prod. Someone ♪")) {
            assertEquals(expected, Genius.lyrics(title, "Queen", client))
        }
        assertEquals(listOf("Queen Bohemian Rhapsody", "Queen Bohemian Rhapsody"), queries)
    }

    @Test
    fun `an unavailable provider returns no lyrics`() = kotlinx.coroutines.runBlocking {
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(403).message("Forbidden").body("challenge".toResponseBody()).build()
        }.build()
        org.junit.Assert.assertNull(Genius.lyrics("Song", "Artist", client))
    }
}
