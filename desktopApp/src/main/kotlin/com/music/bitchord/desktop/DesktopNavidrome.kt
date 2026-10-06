package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What a Subsonic request is signed with.
 *
 * Subsonic never needs the password itself: every request carries `md5(password + salt)` and the
 * salt, and the server accepts the same pair again. Keeping only that pair means the password the
 * listener may use elsewhere is never stored, and it is also what goes in each stream URL.
 */
internal data class DesktopNavidromeCredentials(
    val username: String,
    val salt: String,
    val token: String,
) {
    companion object {
        fun fromPassword(
            username: String,
            password: String,
            salt: String = randomSalt(),
        ) = DesktopNavidromeCredentials(username.trim(), salt, md5Hex(password + salt))

        private fun randomSalt(): String =
            ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

        internal fun md5Hex(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

// ── Wire model ──────────────────────────────────────────────────────────

@Serializable
internal data class NavidromeEnvelope(
    @SerialName("subsonic-response") val response: NavidromeResponse = NavidromeResponse(),
)

@Serializable
internal data class NavidromeResponse(
    val status: String = "",
    val version: String = "",
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: NavidromeError? = null,
    val searchResult3: NavidromeSearchResult? = null,
    val albumList2: NavidromeAlbumList? = null,
    val album: NavidromeAlbum? = null,
)

@Serializable
internal data class NavidromeError(val code: Int = 0, val message: String = "")

@Serializable
internal data class NavidromeSearchResult(val song: List<NavidromeSong> = emptyList())

@Serializable
internal data class NavidromeAlbumList(val album: List<NavidromeAlbum> = emptyList())

@Serializable
internal data class NavidromeAlbum(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val year: Int? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val song: List<NavidromeSong> = emptyList(),
)

@Serializable
internal data class NavidromeSong(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    /** Seconds. */
    val duration: Int? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val coverArt: String? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
)

/** The server said no, as opposed to the network failing. */
internal class DesktopNavidromeException(val code: Int, message: String) : Exception(message) {
    val isBadCredentials: Boolean get() = code == 40 || code == 41 || code == 44
    val isNotFound: Boolean get() = code == 70
}

internal data class DesktopNavidromeServerInfo(val name: String, val version: String)

/** The server-side re-encode asked for when the listener is on a metered connection. */
internal data class DesktopNavidromeTranscode(val format: String, val maxKbps: Int)

// ── Client ──────────────────────────────────────────────────────────────

internal class DesktopNavidromeClient(
    baseUrl: String,
    private val credentials: DesktopNavidromeCredentials,
    private val http: HttpClient = sharedHttp,
) {
    val baseUrl: String = normalizeBase(baseUrl)

    /** A signed call to `/rest/[name]`. The token is in the URL, so never log what this returns. */
    fun url(name: String, vararg params: Pair<String, String>): String {
        val query = buildList {
            add("u" to credentials.username)
            add("t" to credentials.token)
            add("s" to credentials.salt)
            add("v" to API_VERSION)
            add("c" to CLIENT_NAME)
            add("f" to "json")
            addAll(params)
        }.joinToString("&") { (key, value) -> "$key=${value.encodeURLParameter()}" }
        return "$baseUrl/rest/$name?$query"
    }

    suspend fun ping(): Result<DesktopNavidromeServerInfo> = call("ping").map {
        DesktopNavidromeServerInfo(
            name = it.type?.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } ?: "Subsonic server",
            version = it.serverVersion ?: it.version,
        )
    }

    suspend fun search(query: String, limit: Int = 25): Result<List<NavidromeSong>> =
        call("search3", "query" to query, "songCount" to "$limit", "albumCount" to "0", "artistCount" to "0")
            .map { it.searchResult3?.song.orEmpty() }

    /** One page of albums, for browsing. */
    suspend fun albums(type: String, size: Int, offset: Int = 0): Result<List<NavidromeAlbum>> =
        call("getAlbumList2", "type" to type, "size" to "$size", "offset" to "$offset")
            .map { it.albumList2?.album.orEmpty() }

    suspend fun album(id: String): Result<NavidromeAlbum> =
        call("getAlbum", "id" to id).mapCatching {
            it.album ?: throw DesktopNavidromeException(70, "album not found")
        }

    /**
     * The file itself. `raw` means the server sends the original untouched, with range requests,
     * and with a [transcode] it re-encodes on the way out instead.
     */
    fun streamUrl(id: String, transcode: DesktopNavidromeTranscode? = null): String =
        if (transcode == null) {
            url("stream", "id" to id, "format" to "raw")
        } else {
            url("stream", "id" to id, "format" to transcode.format, "maxBitRate" to "${transcode.maxKbps}")
        }

    fun coverArtUrl(id: String, size: Int = 512): String = url("getCoverArt", "id" to id, "size" to "$size")

    /**
     * Asks for the first byte of [streamUrl]. A stream URL is only a URL until something is read
     * through it, so this is what says the server can actually start the file — and a server that
     * answers a search but then stalls on the file fails here, inside the time allowed for it.
     */
    suspend fun probe(streamUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = http.get(streamUrl) {
                header("User-Agent", USER_AGENT)
                header("Range", "bytes=0-0")
            }
            // Subsonic reports a refusal as a JSON body rather than a status.
            if (!reply.status.isSuccess() || reply.headers["Content-Type"]?.contains("json") == true) {
                error("the server would not start the file (${reply.status.value})")
            }
        }
    }

    /**
     * Tells the server a track is playing (`submission = false`) or was played ([atMs] is when it
     * started). The server counts the play and passes it on to whatever it scrobbles to.
     */
    suspend fun scrobble(id: String, submission: Boolean, atMs: Long? = null): Result<Unit> {
        val params = buildList {
            add("id" to id)
            add("submission" to "$submission")
            if (atMs != null) add("time" to "$atMs")
        }
        return call("scrobble", *params.toTypedArray()).map { }
    }

    private suspend fun call(name: String, vararg params: Pair<String, String>): Result<NavidromeResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val reply = http.get(url(name, *params)) { header("User-Agent", USER_AGENT) }
                if (!reply.status.isSuccess()) {
                    error("${reply.status.value} from the server")
                }
                val parsed = parse(reply.bodyAsText())
                if (parsed.status != "ok") {
                    throw DesktopNavidromeException(parsed.error?.code ?: 0, parsed.error?.message ?: "request failed")
                }
                parsed
            }
        }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "BitChord"
        private val USER_AGENT get() = DesktopAddonClient.USER_AGENT

        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        internal fun parse(body: String): NavidromeResponse = json.decodeFromString<NavidromeEnvelope>(body).response

        /** `https://host/music/` → `https://host/music`, and a bare host gets a scheme. */
        fun normalizeBase(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return withScheme.removeSuffix("/rest")
        }

        private val sharedHttp by lazy {
            HttpClient(CIO) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 8_000
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
        }
    }
}

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
        albumId = null,
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
    suspend fun libraryAlbums(configs: List<DesktopSourceConfig>): List<ShelfItem> {
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
        val config = DesktopSourceRegistry.navidromeConfig(ref.sourceId)
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
