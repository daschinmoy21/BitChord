package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.navidrome.NavidromeLibrary
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal typealias DesktopNavidromeCredentials = com.music.bitchord.data.navidrome.NavidromeCredentials
internal typealias DesktopNavidromeException = com.music.bitchord.data.navidrome.NavidromeException
internal typealias DesktopNavidromeServerInfo = com.music.bitchord.data.navidrome.NavidromeServerInfo
internal typealias DesktopNavidromeTranscode = com.music.bitchord.data.navidrome.NavidromeTranscode
internal typealias DesktopNavidromeClient = com.music.bitchord.data.navidrome.NavidromeClient
internal typealias NavidromeEnvelope = com.music.bitchord.data.navidrome.NavidromeEnvelope
internal typealias NavidromeResponse = com.music.bitchord.data.navidrome.NavidromeResponse
internal typealias NavidromeError = com.music.bitchord.data.navidrome.NavidromeError
internal typealias NavidromeSearchResult = com.music.bitchord.data.navidrome.NavidromeSearchResult
internal typealias NavidromeAlbumList = com.music.bitchord.data.navidrome.NavidromeAlbumList
internal typealias NavidromeAlbum = com.music.bitchord.data.navidrome.NavidromeAlbum
internal typealias NavidromeSong = com.music.bitchord.data.navidrome.NavidromeSong

// ── The source ──────────────────────────────────────────────────────────

/** The listener's own Navidrome, as a place to find a copy of a track. */
internal object DesktopNavidromeSource {
    private const val TRACK_PREFIX = "navidrome:"
    private const val ALBUM_PREFIX = "navidrome-album:"
    private const val SEPARATOR = '/'
    private const val ALBUM_LIST = "alphabeticalByName"

    private val clients = ConcurrentHashMap<String, Pair<String, DesktopNavidromeClient>>()
    private val rows = ConcurrentHashMap<String, NavidromeSong>()

    fun trackKey(sourceId: String, trackId: String): String = "$TRACK_PREFIX$sourceId$SEPARATOR$trackId"

    data class TrackRef(val sourceId: String, val trackId: String)

    fun parseTrack(videoId: String): TrackRef? {
        if (!videoId.startsWith(TRACK_PREFIX)) return null
        val encoded = videoId.removePrefix(TRACK_PREFIX)
        val at = encoded.indexOf(SEPARATOR)
        if (at < 1 || at == encoded.lastIndex) return null
        return TrackRef(encoded.substring(0, at), encoded.substring(at + 1))
    }

    fun rememberedSong(videoId: String): Song? {
        val ref = parseTrack(videoId) ?: return null
        val config = DesktopSourceRegistry.navidromeConfig(ref.sourceId) ?: return null
        val server = client(config) ?: return null
        return rows["${ref.sourceId}$SEPARATOR${ref.trackId}"]?.let { songOf(ref.sourceId, server, it) }
    }

    fun albumKey(sourceId: String, albumId: String): String = "$ALBUM_PREFIX$sourceId$SEPARATOR$albumId"

    data class AlbumRef(val sourceId: String, val albumId: String)

    fun parseAlbum(browseId: String): AlbumRef? {
        if (!browseId.startsWith(ALBUM_PREFIX)) return null
        val encoded = browseId.removePrefix(ALBUM_PREFIX)
        val at = encoded.indexOf(SEPARATOR)
        if (at < 1 || at == encoded.lastIndex) return null
        return AlbumRef(encoded.substring(0, at), encoded.substring(at + 1))
    }

    /** A client for [config], or null when there are no credentials to sign it with. */
    fun client(config: DesktopSourceConfig): DesktopNavidromeClient? {
        val credentials = DesktopNavidromeCredentialStore.load(config) ?: return null
        val fingerprint = config.baseUrl + credentials
        clients[config.id]?.takeIf { it.first == fingerprint }?.let { return it.second }
        return DesktopNavidromeClient(config.baseUrl, credentials).also { clients[config.id] = fingerprint to it }
    }

    /** Drops everything held for a source that was edited or removed. */
    fun forget(sourceId: String) {
        clients.remove(sourceId)
        rows.keys.removeIf { it.startsWith("$sourceId$SEPARATOR") }
    }

    suspend fun health(config: DesktopSourceConfig): Result<DesktopNavidromeServerInfo> =
        client(config)?.ping() ?: Result.failure(IllegalStateException("No login saved for this server"))

    /** Search rows as [Song]s, remembered so a later `stream` call needs no second lookup. */
    suspend fun search(config: DesktopSourceConfig, query: String, limit: Int = 25): Result<List<Song>> {
        if (query.isBlank()) return Result.success(emptyList())
        val client = client(config) ?: return Result.failure(IllegalStateException("No login saved"))
        return client.search(query, limit).map { found ->
            found.filter { it.id.isNotBlank() && it.title.isNotBlank() }.map { row ->
                rows["${config.id}$SEPARATOR${row.id}"] = row
                songOf(config.id, client, row)
            }
        }
    }

    suspend fun searchLibrary(config: DesktopSourceConfig, query: String, filter: SearchFilter): List<SearchResult> {
        if (filter == SearchFilter.PLAYLISTS || filter == SearchFilter.VIDEOS) return emptyList()
        val server = client(config) ?: error("No login saved for this server")
        return NavidromeLibrary.searchRows(config.id, server, server.searchLibrary(query).getOrThrow(), filter) { row ->
            rows["${config.id}$SEPARATOR${row.id}"] = row
            songOf(config.id, server, row)
        }
    }

    suspend fun openLibraryArtist(ref: NavidromeLibrary.Ref): Result<ArtistPage> {
        val config = DesktopSourceRegistry.navidromeConfig(ref.sourceId)?.takeIf { it.enabled }
            ?: return Result.failure(IllegalStateException("That server is disabled or no longer set up"))
        val server = client(config) ?: return Result.failure(IllegalStateException("No login saved"))
        return server.artist(ref.id).map { artist ->
            ArtistPage(
                songs = emptyList(), moreSongsBrowseId = null,
                sections = listOf(HomeShelf("Albums", artist.album.map { NavidromeLibrary.albumCard(config.id, server, it) })),
                thumbnailUrl = artist.coverArt?.let(server::coverArtUrl), name = artist.name,
            )
        }
    }

    /** Answers kept for a minute, so the tracks of one album do not each ask the same question. */
    private val recentSearches = ConcurrentHashMap<String, Pair<Long, List<Song>>>()

    private suspend fun cachedSearch(config: DesktopSourceConfig, query: String, limit: Int): List<Song> {
        val key = "${config.id}$SEPARATOR$limit$SEPARATOR$query"
        val now = System.currentTimeMillis()
        recentSearches[key]?.takeIf { now - it.first < SEARCH_TTL_MS }?.let { return it.second }
        val found = search(config, query, limit).getOrNull() ?: return emptyList()
        if (recentSearches.size > MAX_CACHED_SEARCHES) recentSearches.clear()
        recentSearches[key] = now to found
        return found
    }

    /** Every copy the server holds of [song], most confident first. */
    suspend fun matches(config: DesktopSourceConfig, song: Song): List<Song> {
        if (song.title.isBlank() || song.isVideo) return emptyList()
        for (query in DesktopTrackMatcher.queries(song)) {
            val candidates = cachedSearch(config, query, TITLE_RESULTS)
            DesktopTrackMatcher.ranked(candidates, song).ifEmpty { null }?.let { return it }
        }
        // The library may file the track under words the title search never asks for: the native
        // title with the romaji dropped, a subtitle added, a film name left off. Ask for where it
        // would be instead, the album and the artist, and let the matcher pick the row.
        for (query in fallbackQueries(song)) {
            val candidates = cachedSearch(config, query, FALLBACK_RESULTS)
            DesktopTrackMatcher.ranked(candidates, song).ifEmpty { null }?.let { return it }
        }
        return emptyList()
    }

    /** The album and the artist of [song] as searches, most specific first. */
    internal fun fallbackQueries(song: Song): List<String> {
        val artist = DesktopTrackMatcher.primaryArtist(song.artist).takeIf { it.isNotBlank() }
        val album = song.albumName?.let { DesktopTrackMatcher.searchableTitle(it) }?.takeIf { it.isNotBlank() }
        return buildList {
            if (album != null) add(if (artist != null) "$album $artist" else album)
            if (artist != null) add(artist)
        }.distinct()
    }

    private const val SEARCH_TTL_MS = 60_000L
    private const val MAX_CACHED_SEARCHES = 200
    private const val TITLE_RESULTS = 25
    private const val FALLBACK_RESULTS = 60

    suspend fun stream(
        config: DesktopSourceConfig,
        song: Song,
        transcode: DesktopNavidromeTranscode?,
    ): Result<DesktopStream?> = runCatching {
        val reference = parseTrack(song.videoId) ?: return@runCatching null
        val client = client(config) ?: return@runCatching null
        val row = rows["${config.id}$SEPARATOR${reference.trackId}"]
        val url = client.streamUrl(reference.trackId, transcode)
        client.probe(url).getOrThrow()
        DesktopStream(
            url = url,
            format = formatOf(row, transcode),
            sourceId = config.id,
            trackId = reference.trackId,
            durationSec = row?.duration,
        )
    }

    /** What the player will be handed, from what the server said about the file. */
    internal fun formatOf(row: NavidromeSong?, transcode: DesktopNavidromeTranscode?): DesktopStreamFormat {
        if (transcode != null) {
            return DesktopStreamFormat(codec = transcode.format, kbps = transcode.maxKbps)
        }
        val codec = row?.suffix?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: row?.contentType?.substringAfterLast('/')
        return DesktopStreamFormat(
            codec = codec,
            kbps = row?.bitRate?.takeIf { it > 0 },
            sampleRateHz = row?.samplingRate?.takeIf { it > 0 },
            bitDepth = row?.bitDepth?.takeIf { it > 0 },
            channels = row?.channelCount?.takeIf { it > 0 },
        )
    }

    internal fun songOf(sourceId: String, client: DesktopNavidromeClient, row: NavidromeSong): Song = Song(
        videoId = trackKey(sourceId, row.id),
        title = row.title,
        artist = row.artist,
        albumName = row.album.ifBlank { null },
        albumId = row.albumId?.let { albumKey(sourceId, it) },
        artistId = row.artistId?.let { NavidromeLibrary.artistKey(sourceId, it) },
        thumbnailUrl = row.coverArt?.let { client.coverArtUrl(it) },
        durationText = row.duration?.let { "${it / 60}:${"%02d".format(Locale.ROOT, it % 60)}" },
        sourceQuality = if (formatOf(row, null).isLossless) DesktopModuleSource.LOSSLESS else null,
    )

    /** Reports a play to the server that served it. */
    suspend fun report(config: DesktopSourceConfig, trackId: String, submission: Boolean, atMs: Long? = null) {
        val client = client(config) ?: return
        client.scrobble(trackId, submission, atMs).onFailure {
            DesktopTrackLog.log("${config.displayName}: could not report a play — ${it.message}")
        }
    }

    /**
     * Albums on every enabled, complete Navidrome server.
     * OFF, FIRST, and RACE still list albums. A disabled or incomplete server is skipped.
     */
    suspend fun libraryAlbums(configs: List<DesktopSourceConfig>, onFailure: (DesktopSourceConfig) -> Unit = {}): List<ShelfItem> {
        val servers = configs.filter { config ->
            config.kind == DesktopSourceKind.NAVIDROME && config.enabled && config.isComplete
        }
        val labelServer = servers.size > 1
        return buildList {
            for (config in servers) {
                val server = client(config) ?: continue
                val found = try {
                    collectAlbumPages { offset ->
                        server.albums(ALBUM_LIST, ALBUM_PAGE_SIZE, offset).getOrThrow()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onFailure(config)
                    DesktopTrackLog.log("${config.displayName}: could not list albums (${error.message})")
                    emptyList()
                }
                val serverName = if (labelServer) config.displayName else null
                found.forEach { album -> add(album.shelfItem(config.id, server, serverName)) }
            }
        }
    }

    /** One album's tracks, in the order [DesktopNavidromeClient.album] returned them. */
    suspend fun openLibraryAlbum(ref: AlbumRef, fallback: DesktopCollection): Result<DesktopCollection> {
        val config = DesktopSourceRegistry.navidromeConfig(ref.sourceId)?.takeIf { it.enabled }
            ?: return Result.failure(IllegalStateException("That server is no longer set up"))
        val server = client(config) ?: return Result.failure(IllegalStateException("No login saved for this server"))
        val loaded = server.album(ref.albumId)
        val failure = loaded.exceptionOrNull()
        if (failure is CancellationException) throw failure
        return loaded.map { album ->
            val songs = buildList {
                for (row in album.song) {
                    if (row.id.isBlank()) continue
                    // stream() reads this map for the file format.
                    rows["${config.id}$SEPARATOR${row.id}"] = row
                    add(songOf(config.id, server, row))
                }
            }
            DesktopCollection(
                browseId = albumKey(ref.sourceId, ref.albumId),
                title = album.name.ifBlank { fallback.title },
                subtitle = album.artist.ifBlank { fallback.subtitle },
                thumbnailUrl = album.coverArt?.takeIf(String::isNotBlank)?.let(server::coverArtUrl)
                    ?: fallback.thumbnailUrl,
                type = BrowseType.ALBUM,
                songs = songs,
            )
        }
    }

    private fun NavidromeAlbum.shelfItem(
        sourceId: String,
        server: DesktopNavidromeClient,
        serverName: String?,
    ): ShelfItem {
        val artistName = artist.trim()
        val subtitle = when {
            serverName == null -> artistName
            artistName.isEmpty() -> serverName
            else -> "$artistName • $serverName"
        }
        return ShelfItem(
            title = name,
            subtitle = subtitle,
            thumbnailUrl = coverArt?.takeIf(String::isNotBlank)?.let(server::coverArtUrl),
            videoId = null,
            browseId = albumKey(sourceId, id),
        )
    }
}

/** `getAlbumList2` page size. A shorter page is the end of the list. */
internal const val ALBUM_PAGE_SIZE = 500

/** How many album pages one server is asked for before the walk stops. */
internal const val ALBUM_PAGE_LIMIT = 40

/** The library shelf title YouTube uses for saved albums. */
internal const val ALBUMS_SHELF = "Albums"

/**
 * Pages [loadPage] until a page is shorter than [pageSize], its first id was already collected,
 * or [maxPages] is reached. Blank ids are skipped.
 */
internal suspend fun collectAlbumPages(
    pageSize: Int = ALBUM_PAGE_SIZE,
    maxPages: Int = ALBUM_PAGE_LIMIT,
    loadPage: suspend (offset: Int) -> List<NavidromeAlbum>,
): List<NavidromeAlbum> {
    val collected = ArrayList<NavidromeAlbum>()
    val seen = HashSet<String>()
    repeat(maxPages) { page ->
        val batch = loadPage(page * pageSize)
        val firstId = batch.firstOrNull()?.id
        if (!firstId.isNullOrBlank() && firstId in seen) return collected
        for (album in batch) {
            if (album.id.isBlank() || !seen.add(album.id)) continue
            collected += album
        }
        if (batch.size < pageSize) return collected
    }
    return collected
}

/**
 * Drops Navidrome album cards already on [this], then appends [albums] to the Albums shelf.
 * An empty [albums] leaves the YouTube cards in place.
 */
internal fun LibraryPage.withServerAlbums(albums: List<ShelfItem>): LibraryPage {
    val cleaned = shelves.map { shelf ->
        shelf.copy(
            items = shelf.items.filter { item ->
                DesktopNavidromeSource.parseAlbum(item.browseId.orEmpty()) == null
            },
        )
    }.filterNot { it.title == ALBUMS_SHELF && it.items.isEmpty() }
    if (albums.isEmpty()) return copy(shelves = cleaned)
    val next = cleaned.toMutableList()
    val at = next.indexOfFirst { it.title == ALBUMS_SHELF }
    if (at >= 0) {
        next[at] = next[at].copy(items = next[at].items + albums)
    } else {
        val playlists = next.indexOfFirst { it.title == YtMusicRepository.PLAYLISTS_SHELF }
        val insertAt = if (playlists >= 0) playlists + 1 else 0
        next.add(insertAt, HomeShelf(ALBUMS_SHELF, albums))
    }
    return copy(shelves = next)
}

/** Which albums the Library's Albums grid shows, by where they come from. */
internal enum class DesktopAlbumSourceFilter {
    ALL,
    NAVIDROME,
    YOUTUBE,
}

internal fun DesktopAlbumSourceFilter.label(): String = when (this) {
    DesktopAlbumSourceFilter.ALL -> DesktopStrings["album_filter_all", "All"]
    DesktopAlbumSourceFilter.NAVIDROME -> DesktopStrings["album_filter_navidrome", "Navidrome"]
    DesktopAlbumSourceFilter.YOUTUBE -> DesktopStrings["album_filter_youtube", "YouTube Music"]
}

/** This shelf's cards that [filter] lets through: a Navidrome card is one [DesktopNavidromeSource.parseAlbum] reads. */
internal fun HomeShelf.filteredBySource(filter: DesktopAlbumSourceFilter): HomeShelf = when (filter) {
    DesktopAlbumSourceFilter.ALL -> this
    DesktopAlbumSourceFilter.NAVIDROME -> copy(
        items = items.filter { DesktopNavidromeSource.parseAlbum(it.browseId.orEmpty()) != null },
    )
    DesktopAlbumSourceFilter.YOUTUBE -> copy(
        items = items.filter { DesktopNavidromeSource.parseAlbum(it.browseId.orEmpty()) == null },
    )
}

/**
 * What the Library screen renders. A YouTube load still in flight stays loading.
 * A failed YouTube load keeps the error when [albums] is empty, and shows [albums] otherwise.
 */
internal fun UiState<LibraryPage>.mergingServerAlbums(albums: List<ShelfItem>): UiState<LibraryPage> = when (this) {
    is UiState.Loading -> this
    is UiState.Error -> if (albums.isEmpty()) {
        this
    } else {
        UiState.Success(LibraryPage(emptyList(), emptyList(), emptyList()).withServerAlbums(albums))
    }
    is UiState.Success -> UiState.Success(data.withServerAlbums(albums))
}
