package com.music.bitchord.ui.share

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.stats.ReplaySummary

/** A snapshot of the content being shared, independent of the sheet and playback. */
internal sealed interface StoryCard {
    data class Track(val song: Song) : StoryCard
    data class Replay(val summary: ReplaySummary) : StoryCard
    data class Recent(val songs: List<Song>) : StoryCard
}

internal enum class StoryStyle { ARTWORK, COLOUR, DARK }

internal const val STORY_WIDTH = 1080
internal const val STORY_HEIGHT = 1920
internal const val STORY_ROWS = 5

internal fun StoryCard.songs(): List<Song> = when (this) {
    is StoryCard.Track -> listOf(song)
    is StoryCard.Replay -> summary.songs.take(STORY_ROWS).map { it.song }
    is StoryCard.Recent -> songs.distinctBy(Song::videoId).take(STORY_ROWS)
}

internal fun StoryCard.artworkUrls(): List<String> =
    songs().mapNotNull { it.artworkAt(720) }.distinct()

/** Only a real YouTube identity gets a link; local files still get an image card. */
internal fun StoryCard.songLink(): String? = (this as? StoryCard.Track)?.song?.videoId
    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    ?.let { "https://music.youtube.com/watch?v=$it" }
