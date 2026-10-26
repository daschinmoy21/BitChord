package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private fun item(title: String, credit: String = "") = ShelfItem(title, credit, null, null, title)

    @Test
    fun searchCombinesTitleAndArtistWordsWithoutChangingOrder() {
        val first = item("In Rainbows", "Album • Radiohead")
        val second = item("Rainbows Live", "Radiohead")
        val items = listOf(first, item("In Rainbows", "Another artist"), second)

        assertEquals(listOf(first, second), items.matchingLibraryQuery("  RADIOHEAD \t rainbows  "))
        assertTrue(items.matchingLibraryQuery("radiohead missing").isEmpty())
        assertSame(items, items.matchingLibraryQuery(" \n "))
    }

    @Test
    fun artistFilterIncludesFollowedArtistsAndCombinesWithSearch() {
        val artist = item("Radiohead", "Artist")
        val shelves = listOf(
            HomeShelf("Artists", listOf(artist)),
            HomeShelf("Subscriptions", listOf(artist, item("Björk"))),
            HomeShelf("Albums", listOf(item("In Rainbows", "Radiohead"))),
        )

        val result = shelves.filterLibrary("radiohead", LibraryFilter.ARTISTS)
        assertEquals(listOf("Artists", "Subscriptions"), result.map { it.title })
        assertEquals(listOf(listOf(artist), listOf(artist)), result.map { it.items })
        assertEquals(2, shelves[1].items.size)
    }

    @Test
    fun filteringUsesTheEntireShelfIncludingCardsBeyondThePreview() {
        val target = item("Last album", "Target artist")
        val shelf = HomeShelf("Albums", List(20) { item("Album $it") } + target)

        assertEquals(listOf(target), listOf(shelf).filterLibrary("target", LibraryFilter.ALBUMS).single().items)
        assertTrue(listOf(shelf).filterLibrary("target", LibraryFilter.PLAYLISTS).isEmpty())
        assertTrue(listOf(shelf).filterLibrary("target", LibraryFilter.ON_DEVICE).isEmpty())
        assertTrue(listOf(shelf).filterLibrary("unknown", LibraryFilter.ALL).isEmpty())
    }
}
