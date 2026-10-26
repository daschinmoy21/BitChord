package com.music.bitchord.data.navidrome

import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.ArtistRef
import java.util.Locale

/** Collection identities and conversion shared by desktop and Android. */
object NavidromeLibrary {
    data class Ref(val sourceId: String, val id: String)

    fun albumKey(sourceId: String, id: String) = "navidrome-album:$sourceId/$id"
    fun artistKey(sourceId: String, id: String) = "navidrome-artist:$sourceId/$id"
    fun parseAlbum(key: String) = parse(key, "navidrome-album:")
    fun parseArtist(key: String) = parse(key, "navidrome-artist:")

    private fun parse(key: String, prefix: String): Ref? {
        if (!key.startsWith(prefix)) return null
        val body = key.removePrefix(prefix)
        val slash = body.indexOf('/')
        if (slash <= 0 || slash == body.lastIndex) return null
        return Ref(body.substring(0, slash), body.substring(slash + 1))
    }

    suspend fun albums(client: NavidromeClient): List<NavidromeAlbum> {
        val found = linkedMapOf<String, NavidromeAlbum>()
        repeat(40) { page ->
            val batch = client.albums("alphabeticalByName", 500, page * 500).getOrThrow()
            if (batch.firstOrNull()?.id in found) return found.values.toList()
            batch.filter { it.id.isNotBlank() }.forEach { found[it.id] = it }
            if (batch.size < 500) return found.values.toList()
        }
        return found.values.toList()
    }

    fun albumCard(sourceId: String, client: NavidromeClient, album: NavidromeAlbum, serverName: String = "") = ShelfItem(
        title = album.name,
        subtitle = listOf(album.artist, serverName).filter(String::isNotBlank).joinToString(" • "),
        thumbnailUrl = album.coverArt?.takeIf(String::isNotBlank)?.let(client::coverArtUrl),
        videoId = null,
        browseId = albumKey(sourceId, album.id),
    )

    fun song(client: NavidromeClient, row: NavidromeSong, sourceId: String? = null, trackKey: (String) -> String) = Song(
        videoId = trackKey(row.id),
        title = row.title,
        artist = row.artist,
        albumName = row.album.ifBlank { null },
        albumId = sourceId?.let { source -> row.albumId?.let { albumKey(source, it) } },
        artistId = sourceId?.let { source -> row.artistId?.let { artistKey(source, it) } },
        artists = listOf(ArtistRef(row.artist, sourceId?.let { source -> row.artistId?.let { artistKey(source, it) } })),
        thumbnailUrl = row.coverArt?.let(client::coverArtUrl),
        durationText = row.duration?.let { "${it / 60}:${"%02d".format(Locale.ROOT, it % 60)}" },
        sourceQuality = if (row.suffix?.lowercase(Locale.ROOT) in setOf("flac", "alac", "wav", "aiff")) "LOSSLESS" else null,
    )

    fun searchRows(
        sourceId: String,
        client: NavidromeClient,
        result: NavidromeSearchResult,
        filter: SearchFilter,
        songOf: (NavidromeSong) -> Song,
    ): List<SearchResult> = buildList {
        if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
            result.song.filter { it.id.isNotBlank() && it.title.isNotBlank() }.forEach {
                add(SearchResult.Track(songOf(it)))
            }
        }
        if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
            result.album.filter { it.id.isNotBlank() }.forEach {
                val card = albumCard(sourceId, client, it)
                add(SearchResult.Browse(BrowseItem(card.browseId!!, card.title, card.subtitle, card.thumbnailUrl, BrowseType.ALBUM)))
            }
        }
        if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
            result.artist.filter { it.id.isNotBlank() }.forEach {
                add(SearchResult.Browse(BrowseItem(
                    artistKey(sourceId, it.id), it.name, "", it.coverArt?.let(client::coverArtUrl), BrowseType.ARTIST,
                )))
            }
        }
    }
}
