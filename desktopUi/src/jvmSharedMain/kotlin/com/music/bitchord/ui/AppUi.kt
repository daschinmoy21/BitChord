package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.LibraryViewType
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared pages — Home, Explore, Library, Search and the rows they are
 * built from — need from the app around them: a handful of the user's settings
 * and which tracks are on disk. The phone answers from `AppSettings` and
 * `Downloads`, the desktop from its own stores; the pages read the same values
 * either way. Installed once at start, beside [com.music.bitchord.ui.player.PlayerPlatform].
 */
interface AppUiHost {
    /** A swipe on a track row plays it next rather than adding it to the end of the queue. */
    val swipeToPlayNext: StateFlow<Boolean>

    /** Home's Recents shelf as a paged list of rows or as a row of cards. */
    val homeRecentsViewType: StateFlow<LibraryViewType>

    fun setHomeRecentsViewType(value: LibraryViewType)

    /** Playlist browse ids pinned to the front of the Library's Playlists shelf, in pin order. */
    val pinnedPlaylists: StateFlow<List<String>>

    /** How a Library "Show all" grid is ordered. */
    val librarySort: StateFlow<LibrarySort>

    /**
     * When each album or playlist was last played, in epoch milliseconds, for
     * [LibrarySort.RECENTLY_PLAYED]. Keyed by browse id, and by
     * [libraryRecentTitleKey] of the album's title for plays that only carry a name.
     */
    val libraryRecentlyPlayed: StateFlow<Map<String, Long>>

    /** Video ids of the tracks already downloaded, for the mark on their rows. */
    @Composable
    fun downloadedIds(): Set<String>
}

/** The [AppUiHost.libraryRecentlyPlayed] key for a play known only by its album's title. */
fun libraryRecentTitleKey(title: String): String = "title:" + title.trim().lowercase()

object AppUi {
    @Volatile
    private var installed: AppUiHost? = null

    fun install(host: AppUiHost) {
        installed = host
    }

    val host: AppUiHost
        get() = checkNotNull(installed) { "AppUi.install was not called" }
}

/**
 * Whether the feeds answer a pull at the top with a refresh.
 *
 * On a phone that is a drag with a finger. A window has no such gesture — only
 * the mouse wheel, which scrolling up past the top of a page would turn into a
 * refresh nobody asked for — so the desktop switches it off.
 */
val LocalPullToRefreshEnabled = staticCompositionLocalOf { true }
