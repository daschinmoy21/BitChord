package com.music.bitchord.data.navidrome

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem

fun LibraryPage.withNavidromeAlbums(albums: List<ShelfItem>): LibraryPage {
    val cleaned = shelves.map { shelf ->
        shelf.copy(items = shelf.items.filter { NavidromeLibrary.parseAlbum(it.browseId.orEmpty()) == null })
    }.filterNot { it.title == "Albums" && it.items.isEmpty() }.toMutableList()
    if (albums.isNotEmpty()) {
        val index = cleaned.indexOfFirst { it.title == "Albums" }
        if (index >= 0) cleaned[index] = cleaned[index].copy(items = cleaned[index].items + albums)
        else cleaned.add(HomeShelf("Albums", albums))
    }
    return copy(shelves = cleaned)
}
