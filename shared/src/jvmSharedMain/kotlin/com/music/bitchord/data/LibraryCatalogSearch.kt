package com.music.bitchord.data

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class LibrarySearchProvider(
    val name: String,
    val search: suspend (String, SearchFilter) -> List<SearchResult>,
)

data class LibraryCatalogResult(val shelves: List<HomeShelf> = emptyList(), val failedSources: List<String> = emptyList())

/** Sources run independently: a sleeping personal server never discards YouTube's answer. */
suspend fun searchLibraryCatalog(
    query: String,
    filter: SearchFilter,
    providers: List<LibrarySearchProvider>,
): LibraryCatalogResult = coroutineScope {
    val answers = providers.map { provider ->
        async {
            try {
                provider.name to Result.success(provider.search(query, filter))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                provider.name to Result.failure<List<SearchResult>>(failure)
            }
        }
    }.map { it.await() }
    LibraryCatalogResult(
        shelves = answers.flatMap { (name, answer) -> librarySearchShelves(name, answer.getOrDefault(emptyList())) },
        failedSources = answers.filter { it.second.isFailure }.map { it.first },
    )
}

internal fun librarySearchShelves(source: String, rows: List<SearchResult>): List<HomeShelf> {
    val groups = linkedMapOf<String, MutableList<ShelfItem>>()
    rows.forEach { row ->
        val (category, card) = when (row) {
            is SearchResult.TopTrack -> "Songs" to ShelfItem(row.song.title, row.song.artist, row.song.thumbnailUrl, row.song.videoId, null)
            is SearchResult.Track -> "Songs" to ShelfItem(row.song.title, row.song.artist, row.song.thumbnailUrl, row.song.videoId, null)
            is SearchResult.Browse -> {
                val category = when (row.item.type) {
                    BrowseType.ALBUM -> "Albums"
                    BrowseType.ARTIST -> "Artists"
                    BrowseType.PLAYLIST -> "Playlists"
                    BrowseType.OTHER -> "More"
                }
                category to ShelfItem(row.item.title, row.item.subtitle, row.item.thumbnailUrl, null, row.item.browseId)
            }
        }
        groups.getOrPut(category, ::mutableListOf).add(card)
    }
    return groups.map { (category, cards) ->
        HomeShelf("$source · $category", cards.distinctBy { it.videoId ?: it.browseId })
    }
}
