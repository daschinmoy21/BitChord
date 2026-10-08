package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyImporterTest {
    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    @Test fun acceptsSharedPlaylistLinksAndUris() {
        for (link in listOf("https://open.spotify.com/playlist/$id?si=abc", "spotify:playlist:$id",
            "https://open.spotify.com/intl-de/playlist/$id")) {
            assertEquals(id, SpotifyImporter.extractPlaylistId(link))
        }
    }

    @Test fun rejectsOtherHostsAndNonPlaylists() {
        for (link in listOf("https://open.spotify.com.evil.example/playlist/$id", "https://evilspotify.com/playlist/$id",
            "https://open.spotify.com/album/$id", "https://open.spotify.com/playlist/short", "file:///playlist/$id")) {
            assertNull(SpotifyImporter.extractPlaylistId(link))
        }
    }
}
