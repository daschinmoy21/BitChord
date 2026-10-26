package com.music.bitchord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/** Kept by each app so opening a collection and returning preserves the search. */
class LibrarySearchState internal constructor(query: String = "", filter: LibraryFilter = LibraryFilter.ALL) {
    var query by mutableStateOf(query)
    var collectionQuery by mutableStateOf("")
        internal set
    internal var filter by mutableStateOf(filter)
}

@Composable
fun rememberLibrarySearchState(): LibrarySearchState = rememberSaveable(
    saver = listSaver(
        save = { listOf(it.query, it.filter.name) },
        restore = { LibrarySearchState(it[0], LibraryFilter.valueOf(it[1])) },
    ),
) { LibrarySearchState() }

/** Categories use the repository's shelf names, independent of translated UI labels. */
internal enum class LibraryFilter {
    ALL, PLAYLISTS, ALBUMS, ARTISTS, PODCASTS, ON_DEVICE;

    fun includes(shelf: HomeShelf): Boolean = when (this) {
        ALL -> true
        PLAYLISTS -> shelf.title == YtMusicRepository.PLAYLISTS_SHELF
        ALBUMS -> shelf.title == "Albums"
        ARTISTS -> shelf.title == "Artists" || shelf.title == "Subscriptions"
        PODCASTS -> shelf.title == "Podcasts"
        ON_DEVICE -> false
    }
}

/** Every word can match either the collection name or its artist/owner credit. */
internal fun List<ShelfItem>.matchingLibraryQuery(query: String): List<ShelfItem> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (terms.isEmpty()) return this
    return filter { item ->
        terms.all { term ->
            item.title.contains(term, ignoreCase = true) || item.subtitle.contains(term, ignoreCase = true)
        }
    }
}

internal fun List<HomeShelf>.filterLibrary(query: String, filter: LibraryFilter): List<HomeShelf> =
    filter { filter.includes(it) }
        .map { it.copy(items = it.items.matchingLibraryQuery(query)) }
        .filter { it.items.isNotEmpty() }
