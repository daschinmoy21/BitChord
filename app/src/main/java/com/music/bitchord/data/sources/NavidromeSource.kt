package com.music.bitchord.data.sources

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.navidrome.NavidromeClient
import com.music.bitchord.data.navidrome.NavidromeCredentials
import com.music.bitchord.data.navidrome.NavidromeException
import com.music.bitchord.data.navidrome.NavidromeLibrary
import com.music.bitchord.data.navidrome.NavidromeSong
import com.music.bitchord.data.navidrome.NavidromeTranscode
import java.util.concurrent.ConcurrentHashMap

class NavidromeSource(override val config: SourceConfig) : MusicSource, SourceRegistry.ConfigBacked {
    override val configId get() = config.id
    override val kind get() = SourceKind.NAVIDROME
    override val displayName get() = config.displayName
    val client = NavidromeClient(config.baseUrl, NavidromeCredentials(config.username, config.salt, config.token))
    private val rows = ConcurrentHashMap<String, NavidromeSong>()

    override suspend fun health(): SourceHealth = client.ping().fold(
        onSuccess = { SourceHealth.Ok("${it.name} ${it.version}") },
        onFailure = {
            if ((it as? NavidromeException)?.isBadCredentials == true) SourceHealth.Rejected(it.message ?: "Login rejected")
            else SourceHealth.Unreachable(it.message ?: "Server unavailable")
        },
    )

    fun songOf(row: NavidromeSong): Song {
        rows[row.id] = row
        return NavidromeLibrary.song(client, row, config.id) { SourceRegistry.trackKey(config.id, it) }
    }

    fun rememberedSong(trackId: String): Song? = rows[trackId]?.let(::songOf)

    override suspend fun search(query: String, limit: Int, waitForAll: Boolean, request: StreamRequest?): List<Song> =
        client.search(query, limit).getOrThrow().map(::songOf)

    suspend fun searchLibrary(query: String, filter: SearchFilter): List<SearchResult> {
        if (filter == SearchFilter.PLAYLISTS || filter == SearchFilter.VIDEOS) return emptyList()
        return NavidromeLibrary.searchRows(config.id, client, client.searchLibrary(query).getOrThrow(), filter, ::songOf)
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream {
        val transcode = (request as? StreamRequest.Capped)?.let { NavidromeTranscode("opus", it.maxKbps) }
        val row = rows[trackId]
        return SourceStream(
            url = client.streamUrl(trackId, transcode),
            format = if (transcode != null) StreamFormat(codec = "opus", kbps = transcode.maxKbps)
                else StreamFormat(codec = row?.suffix, kbps = row?.bitRate, sampleRateHz = row?.samplingRate, bitDepth = row?.bitDepth),
            durationSec = row?.duration,
        )
    }

    suspend fun albums(): List<ShelfItem> = NavidromeLibrary.albums(client).map {
        NavidromeLibrary.albumCard(config.id, client, it, displayName)
    }

    suspend fun openAlbum(ref: NavidromeLibrary.Ref): DetailPage {
        val album = client.album(ref.id).getOrThrow()
        return DetailPage(
            browseId = NavidromeLibrary.albumKey(config.id, ref.id),
            title = album.name,
            subtitle = album.artist,
            thumbnailUrl = album.coverArt?.let(client::coverArtUrl),
            songs = UiState.Success(album.song.map(::songOf)),
            type = BrowseType.ALBUM,
        )
    }

    suspend fun openArtist(ref: NavidromeLibrary.Ref): DetailPage {
        val artist = client.artist(ref.id).getOrThrow()
        return DetailPage(
            browseId = NavidromeLibrary.artistKey(config.id, ref.id),
            title = artist.name,
            subtitle = displayName,
            thumbnailUrl = artist.coverArt?.let(client::coverArtUrl),
            songs = UiState.Success(emptyList()),
            type = BrowseType.ARTIST,
            sections = listOf(HomeShelf("Albums", artist.album.map { NavidromeLibrary.albumCard(config.id, client, it) })),
        )
    }

    companion object {
        fun active(): List<NavidromeSource> = SourceRegistry.active().filterIsInstance<NavidromeSource>()

        fun forRef(ref: NavidromeLibrary.Ref): NavidromeSource =
            active().firstOrNull { it.config.id == ref.sourceId }
                ?: error("This music server is disabled or no longer configured")
    }
}
