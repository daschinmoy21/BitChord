package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher

/**
 * Cross-catalogue matching for source fallback.
 *
 * The judgement itself is the shared [TrackMatcher] — the one Android uses — so a title is read the
 * same way on both: version markers, native-script and romanised titles, album-carried markers. This
 * only adapts it to the [Song] the desktop passes around.
 */
internal object DesktopTrackMatcher {

    fun best(candidates: List<Song>, target: Song): Song? = TrackMatcher.best(candidates, TrackMatcher.targetOf(target))

    /** Every candidate that is the same recording, most confident first. */
    fun ranked(candidates: List<Song>, target: Song): List<Song> =
        TrackMatcher.ranked(candidates, TrackMatcher.targetOf(target))

    /** Whether two rows agree on runtime to within [seconds]. */
    fun withinSeconds(candidate: Song, target: Song, seconds: Int): Boolean =
        TrackMatcher.withinSeconds(candidate, TrackMatcher.targetOf(target), seconds)

    /** What to ask a catalogue for, in the order to ask it. */
    fun queries(target: Song): List<String> = TrackMatcher.queries(TrackMatcher.targetOf(target))

    /** The title with the packaging taken off, version markers kept. */
    internal fun searchableTitle(title: String, artist: String = ""): String =
        TrackMatcher.searchableTitle(title, artist)

    /** The first credited artist — who a catalogue is most likely to file the track under. */
    internal fun primaryArtist(artist: String): String = TrackMatcher.primaryArtist(artist)
}
