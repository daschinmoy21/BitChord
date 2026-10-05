package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopParityTest {
    @Test
    fun losslessFormatCarriesDisplayMetadata() {
        val format = DesktopStreamFormat(codec = "flac", sampleRateHz = 96_000, bitDepth = 24)

        assertTrue(format.isLossless)
        assertEquals("FLAC · 24-bit · 96 kHz", format.summary)
    }

    @Test
    fun localLibrarySortKeepsStableTitleTieBreakers() {
        val songs = listOf(
            Song("b", "Beta", "Artist", null, localDateAddedSeconds = 2),
            Song("a", "alpha", "Artist", null, localDateAddedSeconds = 1),
        )

        assertEquals(listOf("alpha", "Beta"), songs.sortedForDesktopLibrary(DesktopLibrarySort.TITLE_ASC).map(Song::title))
        assertEquals(listOf("Beta", "alpha"), songs.sortedForDesktopLibrary(DesktopLibrarySort.DATE_ADDED).map(Song::title))
    }

    @Test
    fun unknownArtistMetadataDoesNotBlockAQualifiedSourceMatch() {
        val target = Song(
            videoId = "youtube-id",
            title = "Tera Mera Rishta - New Version (From \"Awarapan 2\")",
            artist = "Unknown Artist",
            thumbnailUrl = null,
            durationText = "6:05",
        )
        val sourceCopy = Song(
            videoId = "jiosaavn:track-id",
            title = "Tera Mera Rishta - New Version",
            artist = "Mithoon, Pritam, Sayeed Quadri, Saaj Bhatt, Subodhh Sharma",
            thumbnailUrl = null,
            durationText = "6:05",
        )

        assertEquals(sourceCopy, DesktopTrackMatcher.best(listOf(sourceCopy), target))
    }

    @Test
    fun sourceMatcherRejectsAConflictingArtist() {
        // A different credit on a different runtime is a different recording. (On the very same
        // runtime the shared matcher, like Android's, lets the length stand in for the credit: film
        // catalogues file one master under its composer on one service and its singer on another.)
        val target = Song("youtube-id", "Tera Mera Rishta", "Mithoon", null, durationText = "6:05")
        val cover = Song("jiosaavn:cover", "Tera Mera Rishta", "Different Artist", null, durationText = "5:12")

        assertEquals(null, DesktopTrackMatcher.best(listOf(cover), target))
    }

    @Test
    fun anExactRuntimeStandsInForADifferentlyCreditedCopyOfTheSameMaster() {
        val target = Song("youtube-id", "Tera Mera Rishta", "Mithoon", null, durationText = "6:05")
        val sameMaster = Song("jiosaavn:master", "Tera Mera Rishta", "Different Artist", null, durationText = "6:05")

        assertEquals(sameMaster, DesktopTrackMatcher.best(listOf(sameMaster), target))
    }
}
