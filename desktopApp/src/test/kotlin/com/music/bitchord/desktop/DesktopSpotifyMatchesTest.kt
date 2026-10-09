package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSpotifyMatchesTest {
    private val song = Song(videoId = "yt1", title = "Song", artist = "Artist", thumbnailUrl = "https://img/1")

    @Test
    fun `matches survive a new cache over the same store`() = withStore { store ->
        runBlocking {
            DesktopSpotifyMatches(store).saveAll(mapOf("sp1" to song))
            val reopened = DesktopSpotifyMatches(DesktopPersistence(store.preferences))
            assertEquals(song, reopened.get("sp1"))
            assertNull(reopened.get("sp2"))
        }
    }

    @Test
    fun `a rematch replaces the old song and moves it to the newest end`() = withStore { store ->
        runBlocking {
            val cache = DesktopSpotifyMatches(store)
            cache.saveAll(mapOf("sp1" to song, "sp2" to song.copy(videoId = "yt2")))
            cache.saveAll(mapOf("sp1" to song.copy(videoId = "yt3")))
            assertEquals(listOf("sp2", "sp1"), store.spotifyMatches().keys.toList())
            assertEquals("yt3", store.spotifyMatches().getValue("sp1").videoId)
        }
    }

    @Test
    fun `damaged rows are skipped`() = withStore { store ->
        store.saveSpotifyMatches(mapOf("sp1" to song))
        store.saveString("spotify_matches", store.string("spotify_matches") + "\ngarbage\n|")
        assertEquals(setOf("sp1"), store.spotifyMatches().keys)
    }

    @Test
    fun `oldest matches are evicted from memory and disk`() = withStore { store ->
        runBlocking {
            val cache = DesktopSpotifyMatches(store)
            cache.saveAll((0..MAX_SPOTIFY_MATCHES).associate { "sp$it" to song })
            assertNull(cache.get("sp0"))
            assertEquals(song, cache.get("sp1"))
            assertEquals(MAX_SPOTIFY_MATCHES, store.spotifyMatches().size)
            assertNull(DesktopSpotifyMatches(store).get("sp0"))
        }
    }

    @Test
    fun `overlapping saves retain every match after reopening`() = withStore { store ->
        runBlocking {
            val cache = DesktopSpotifyMatches(store)
            (1..50).map { n -> async { cache.saveAll(mapOf("sp$n" to song)) } }.awaitAll()
            val reopened = DesktopSpotifyMatches(store)
            (1..50).forEach { n -> assertEquals(song, reopened.get("sp$n")) }
        }
    }

    private fun withStore(block: (DesktopPersistence) -> Unit) {
        val node = Preferences.userRoot().node("bitchord-test-spotify-${System.nanoTime()}")
        try { block(DesktopPersistence(node)) } finally { node.removeNode() }
    }
}
