package com.music.bitchord.data.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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

/**
 * What a Subsonic request is signed with.
 *
 * Subsonic never needs the password itself: every request carries `md5(password + salt)` and the
 * salt, and the server accepts the same pair again. Keeping only that pair means the password the
 * listener may use elsewhere is never stored, and it is also what goes in each stream URL.
 */
data class NavidromeCredentials(
    val username: String,
    val salt: String,
    val token: String,
) {
    companion object {
        fun fromPassword(
            username: String,
            password: String,
            salt: String = randomSalt(),
        ) = NavidromeCredentials(username.trim(), salt, md5Hex(password + salt))

        private fun randomSalt(): String =
            ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

        fun md5Hex(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

// ── Wire model ──────────────────────────────────────────────────────────

@Serializable
data class NavidromeEnvelope(
    @SerialName("subsonic-response") val response: NavidromeResponse = NavidromeResponse(),
)

@Serializable
data class NavidromeResponse(
    val status: String = "",
    val version: String = "",
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: NavidromeError? = null,
    val searchResult3: NavidromeSearchResult? = null,
    val albumList2: NavidromeAlbumList? = null,
    val album: NavidromeAlbum? = null,
    val artist: NavidromeArtist? = null,
)

@Serializable
data class NavidromeError(val code: Int = 0, val message: String = "")

@Serializable
data class NavidromeSearchResult(
    val song: List<NavidromeSong> = emptyList(),
    val album: List<NavidromeAlbum> = emptyList(),
    val artist: List<NavidromeArtist> = emptyList(),
)

@Serializable
data class NavidromeArtist(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val album: List<NavidromeAlbum> = emptyList(),
)

@Serializable
data class NavidromeAlbumList(val album: List<NavidromeAlbum> = emptyList())

@Serializable
data class NavidromeAlbum(
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
data class NavidromeSong(
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
class NavidromeException(val code: Int, message: String) : Exception(message) {
    val isBadCredentials: Boolean get() = code == 40 || code == 41 || code == 44
    val isNotFound: Boolean get() = code == 70
}

data class NavidromeServerInfo(val name: String, val version: String)

/** The server-side re-encode asked for when the listener is on a metered connection. */
data class NavidromeTranscode(val format: String, val maxKbps: Int)

// ── Client ──────────────────────────────────────────────────────────────

class NavidromeClient(
    baseUrl: String,
    private val credentials: NavidromeCredentials,
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

    suspend fun ping(): Result<NavidromeServerInfo> = call("ping").map {
        NavidromeServerInfo(
            name = it.type?.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } ?: "Subsonic server",
            version = it.serverVersion ?: it.version,
        )
    }

    suspend fun search(query: String, limit: Int = 25): Result<List<NavidromeSong>> =
        call("search3", "query" to query, "songCount" to "$limit", "albumCount" to "0", "artistCount" to "0")
            .map { it.searchResult3?.song.orEmpty() }

    suspend fun searchLibrary(query: String, limit: Int = 50): Result<NavidromeSearchResult> =
        call("search3", "query" to query, "songCount" to "$limit", "albumCount" to "$limit", "artistCount" to "$limit")
            .map { it.searchResult3 ?: NavidromeSearchResult() }

    /** One page of albums, for browsing. */
    suspend fun albums(type: String, size: Int, offset: Int = 0): Result<List<NavidromeAlbum>> =
        call("getAlbumList2", "type" to type, "size" to "$size", "offset" to "$offset")
            .map { it.albumList2?.album.orEmpty() }

    suspend fun album(id: String): Result<NavidromeAlbum> =
        call("getAlbum", "id" to id).mapCatching {
            it.album ?: throw NavidromeException(70, "album not found")
        }

    suspend fun artist(id: String): Result<NavidromeArtist> =
        call("getArtist", "id" to id).mapCatching {
            it.artist ?: throw NavidromeException(70, "artist not found")
        }

    /**
     * The file itself. `raw` means the server sends the original untouched, with range requests,
     * and with a [transcode] it re-encodes on the way out instead.
     */
    fun streamUrl(id: String, transcode: NavidromeTranscode? = null): String =
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
            try {
                val reply = http.get(url(name, *params)) { header("User-Agent", USER_AGENT) }
                if (!reply.status.isSuccess()) {
                    error("${reply.status.value} from the server")
                }
                val parsed = parse(reply.bodyAsText())
                if (parsed.status != "ok") {
                    throw NavidromeException(parsed.error?.code ?: 0, parsed.error?.message ?: "request failed")
                }
                Result.success(parsed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Transport exceptions can embed the signed URL; expose a safe message.
                Result.failure(if (failure is NavidromeException) failure else
                    IllegalStateException("Could not reach the music server"))
            }
        }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "BitChord"
        private const val USER_AGENT = "BitChord"

        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun parse(body: String): NavidromeResponse = json.decodeFromString<NavidromeEnvelope>(body).response

        /** `https://host/music/` → `https://host/music`, and a bare host gets a scheme. */
        fun normalizeBase(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return withScheme.removeSuffix("/rest")
        }

        private val sharedHttp by lazy {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 8_000
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
        }
    }
}
