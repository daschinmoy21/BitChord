package com.music.bitchord.desktop

import com.music.bitchord.data.navidrome.NavidromePlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopNavidromePlaylistCardTest {
    @Test fun serverPlaylistCardsHaveSourceScopedNavigationArtworkAndCounts() {
        val config = DesktopSourceConfig("home", DesktopSourceKind.NAVIDROME, "Home server", "https://music.example.com", "listener")
        val playlist = NavidromePlaylist(id = "weekly", name = "Weekly", songCount = 28, coverArt = "pl-weekly")
        val ref = DesktopNavidromeSource.ServerPlaylist(config, playlist)
        val client = DesktopNavidromeClient(config.baseUrl, DesktopNavidromeCredentials("listener", "salt", "token"))
        val card = ref.shelfItem(client)
        assertEquals("Weekly", card.title)
        assertEquals("28 songs · Home server", card.subtitle)
        assertEquals("navidrome-playlist:home/weekly", card.browseId)
        assertTrue(card.thumbnailUrl.orEmpty().contains("getCoverArt"))
        assertTrue(card.thumbnailUrl.orEmpty().contains("id=pl-weekly"))
        assertNotEquals(card.browseId, ref.copy(source = config.copy(id = "other")).shelfItem(client).browseId)
    }

    @Test fun missingArtworkOrNameStillProducesAnOpenableCard() {
        val source = DesktopSourceConfig("home", DesktopSourceKind.NAVIDROME)
        val ref = DesktopNavidromeSource.ServerPlaylist(source, NavidromePlaylist(id = "empty"))
        val card = ref.shelfItem(null)
        assertEquals("Untitled playlist", card.title)
        assertEquals("navidrome-playlist:home/empty", card.browseId)
    }
}
