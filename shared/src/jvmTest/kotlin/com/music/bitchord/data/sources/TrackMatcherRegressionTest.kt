package com.music.bitchord.data.sources

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMatcherRegressionTest {
    private fun song(title: String) = Song("id", title, "Artist", null)

    @Test fun portugueseGuestCreditDoesNotBecomeAVersionMarker() {
        val wanted = song("O Mundo E Mal (part. Clara Lima)")
        assertNotNull(TrackMatcher.best(listOf(song("O Mundo E Mal")), TrackMatcher.targetOf(wanted)))
        assertTrue(TrackMatcher.queries(TrackMatcher.targetOf(wanted)).none { "part" in it })
        assertEquals("part of me", TrackMatcher.searchableTitle("Part of Me"))
        assertTrue(TrackMatcher.searchableTitle("Part. 2").contains("part"))
        assertEquals("", TrackMatcher.primaryArtist("Música"))
    }

    @Test fun wholeBracketedTranslationMatchesButOneWordOfItDoesNot() {
        assertNotNull(TrackMatcher.best(listOf(song("Night")), TrackMatcher.targetOf(song("夜 (Night)"))))
        assertNull(TrackMatcher.best(listOf(song("Night")), TrackMatcher.targetOf(song("夜に駆ける (Racing Into The Night)"))))
        assertNull(TrackMatcher.best(listOf(song("Different")), TrackMatcher.targetOf(song("Song (Different)"))))
    }
}
