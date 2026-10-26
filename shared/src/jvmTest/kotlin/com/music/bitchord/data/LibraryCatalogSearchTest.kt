package com.music.bitchord.data

import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryCatalogSearchTest {
    private val song = Song("track", "Track", "Artist", null)

    @Test fun partialSourceFailureKeepsOtherResultsAndForwardsTheCategory() = runBlocking {
        val calls = mutableListOf<Pair<String, SearchFilter>>()
        val result = searchLibraryCatalog("Artist", SearchFilter.ARTISTS, listOf(
            LibrarySearchProvider("YouTube Music") { query, filter ->
                calls += query to filter
                listOf(SearchResult.Browse(BrowseItem("UC1", "Artist", "", null, BrowseType.ARTIST)))
            },
            LibrarySearchProvider("My Navidrome") { query, filter ->
                calls += query to filter
                error("offline")
            },
        ))
        assertEquals(listOf("My Navidrome"), result.failedSources)
        assertEquals(listOf("YouTube Music · Artists"), result.shelves.map { it.title })
        assertEquals(listOf("Artist" to SearchFilter.ARTISTS, "Artist" to SearchFilter.ARTISTS), calls)
    }

    @Test fun promotedTracksAreDeduplicatedAndArtistAndAlbumIdentitiesSurvive() {
        val shelves = librarySearchShelves("Server", listOf(
            SearchResult.TopTrack(song), SearchResult.Track(song),
            SearchResult.Browse(BrowseItem("navidrome-album:s/a", "Album", "Artist", null, BrowseType.ALBUM)),
            SearchResult.Browse(BrowseItem("navidrome-artist:s/r", "Artist", "", null, BrowseType.ARTIST)),
        ))
        assertEquals(listOf("Server · Songs", "Server · Albums", "Server · Artists"), shelves.map { it.title })
        assertEquals(listOf("track"), shelves[0].items.map { it.videoId })
        assertEquals("navidrome-album:s/a", shelves[1].items.single().browseId)
        assertEquals("navidrome-artist:s/r", shelves[2].items.single().browseId)
    }

    @Test fun replacingASearchPropagatesCancellationInsteadOfReportingASourceFailure() = runBlocking {
        assertFailsWith<CancellationException> {
            searchLibraryCatalog("old query", SearchFilter.ALL, listOf(
                LibrarySearchProvider("server") { _, _ -> throw CancellationException("replaced") },
            ))
        }
        Unit
    }
}
