package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.share.StoryCard
import com.music.bitchord.ui.share.artworkUrls
import com.music.bitchord.ui.share.songLink
import com.music.bitchord.ui.share.songs
import org.junit.Assert.*
import org.junit.Test

class StoryCardTest {
    @Test fun recentCardListsFiveDifferentSongsInHistoryOrder() {
        val songs = (0..7).map { storySong("song-$it") }
        val recent = StoryCard.Recent(listOf(songs[0], songs[0], songs[1]) + songs.drop(2))
        assertEquals(songs.take(5), recent.songs())
    }

    @Test fun replayUsesTheRankedSongsWithoutSortingOrChangingTheSummary() {
        val ranked = (0..7).map { storySong("rank-$it") }
        val summary = storySummary(ranked)
        assertEquals(ranked.take(5), StoryCard.Replay(summary).songs())
        assertEquals(8, summary.songs.size)
    }

    @Test fun artworkRequestsAreDeduplicatedAndMissingArtDoesNotRemoveTheSong() {
        val missing = storySong("missing")
        val cover = storySong("cover").copy(thumbnailUrl = "https://example.com/cover.png")
        val recent = StoryCard.Recent(listOf(missing, cover, cover.copy(videoId = "same-art")))
        assertEquals(3, recent.songs().size)
        assertEquals(1, recent.artworkUrls().size)
        assertTrue(StoryCard.Track(missing).artworkUrls().isEmpty())
    }

    @Test fun songLinksAreKeptForYouTubeIncludingDownloadedSongs() {
        val song = storySong("dQw4w9WgXcQ")
        val expected = "https://music.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(expected, StoryCard.Track(song).songLink())
        assertEquals(expected, StoryCard.Track(song.copy(localUri = "content://downloads/1")).songLink())
    }

    @Test fun localAndSourceSongsCanMakeCardsWithoutInventingYouTubeLinks() {
        for (id in listOf("local:42", "module:track/1", "", "short")) {
            val song = storySong(id)
            assertEquals(listOf(song), StoryCard.Track(song).songs())
            assertNull(StoryCard.Track(song).songLink())
        }
        assertNull(StoryCard.Recent(listOf(storySong("dQw4w9WgXcQ"))).songLink())
    }
}

internal fun storySong(id: String) = Song(id, "A song with a memorable title", "An artist", null)

internal fun storySummary(songs: List<Song>) = ReplaySummary(
    period = ReplayPeriod.THIS_MONTH,
    label = "October 2026",
    totalMs = 120 * 60_000L,
    totalPlays = 42,
    songs = songs.map { RankedSong(it, 60_000, 1) },
    artists = listOf(RankedEntry("An artist", null, null, null, 60_000, 1)),
    albums = emptyList(), genres = emptyList(), hourOfDay = List(24) { 0L },
    busiestDay = null, busiestDayMs = 0, distinctSongs = songs.size,
    distinctArtists = 1, distinctAlbums = 0, since = "2026-10",
)
