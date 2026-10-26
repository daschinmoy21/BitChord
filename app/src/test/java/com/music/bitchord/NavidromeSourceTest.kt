package com.music.bitchord

import com.music.bitchord.data.navidrome.NavidromeCredentials
import com.music.bitchord.data.navidrome.NavidromeSong
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.permits
import com.music.bitchord.data.sources.NavidromeSource
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.StreamRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NavidromeSourceTest {
    private val credentials = NavidromeCredentials.fromPassword("user", "secret", "salt")
    private val config = SourceConfig(
        id = "personal", kind = SourceKind.NAVIDROME, label = "My music", baseUrl = "https://music.example.com",
        username = credentials.username, salt = credentials.salt, token = credentials.token,
    )

    @Test fun aServerRequiresCredentialsAndItsSwitchControlsEligibility() {
        assertTrue(config.isComplete)
        assertFalse(config.copy(token = "").isComplete)
        assertFalse(config.copy(username = "").isComplete)
        assertEquals(emptyList<SourceConfig>(), SourceRegistry.enabledConfigs(listOf(config.copy(enabled = false))))
        AudioQuality.entries.forEach { assertTrue(it.permits(SourceKind.NAVIDROME)) }
    }

    @Test fun serverTracksKeepTheirSourceIdentityAndStreamWithoutAYouTubeLookup() = runBlocking {
        val source = NavidromeSource(config)
        val song = source.songOf(NavidromeSong(id = "track", title = "Title", artist = "Artist", suffix = "flac", bitDepth = 24))
        assertEquals("personal" to "track", SourceRegistry.parseTrackKey(song.videoId))
        val raw = source.stream("track", StreamRequest.Lossless)
        assertTrue(raw.url.contains("/rest/stream?"))
        assertTrue(raw.url.contains("format=raw"))
        assertFalse(raw.url.contains("secret"))
        assertEquals(24, raw.format.bitDepth)
        val capped = source.stream("track", StreamRequest.Capped(96))
        assertTrue(capped.url.contains("format=opus") && capped.url.contains("maxBitRate=96"))
        assertEquals(false, capped.format.isLossless)
    }
}
