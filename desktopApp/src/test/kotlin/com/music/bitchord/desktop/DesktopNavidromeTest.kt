package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Subsonic side of Navidrome: the signing, the wire format as a real Navidrome 0.64 sends it,
 * the rules that turn the settings and the connection into a plan, and a throwaway server for the
 * calls themselves.
 */
class DesktopNavidromeTest {

    // ── Signing ─────────────────────────────────────────────────────────

    @Test
    fun `the token is md5 of the password and salt, as the Subsonic docs give it`() {
        // The worked example in the Subsonic API documentation.
        val credentials = DesktopNavidromeCredentials.fromPassword("joe", "sesame", salt = "c19b2d")
        assertEquals("26719a1196d2a940705a59634eb18eab", credentials.token)
        assertEquals("c19b2d", credentials.salt)
    }

    @Test
    fun `a fresh login gets its own salt and never carries the password`() {
        val first = DesktopNavidromeCredentials.fromPassword("joe", "hunter2")
        val second = DesktopNavidromeCredentials.fromPassword("joe", "hunter2")
        assertTrue(first.salt != second.salt)
        assertFalse(first.toString().contains("hunter2"))
    }

    @Test
    fun `a signed url carries the token and salt but not the password`() {
        val client = DesktopNavidromeClient(
            "https://music.example.com/",
            DesktopNavidromeCredentials.fromPassword("joe", "sesame", salt = "c19b2d"),
        )
        val url = client.url("search3", "query" to "a b&c")
        assertTrue(url.startsWith("https://music.example.com/rest/search3?"))
        assertTrue("u=joe" in url)
        assertTrue("t=26719a1196d2a940705a59634eb18eab" in url)
        assertTrue("s=c19b2d" in url)
        assertTrue("c=BitChord" in url)
        assertTrue("f=json" in url)
        assertFalse("sesame" in url)
        // The query is encoded, so an & in a title cannot start a new parameter.
        assertFalse("query=a b&c" in url)
        assertTrue("query=a%20b%26c" in url)
    }

    @Test
    fun `original files are asked for raw and a re-encode names its format and ceiling`() {
        val client = DesktopNavidromeClient("https://m.example.com", DesktopNavidromeCredentials("u", "s", "t"))
        val raw = client.streamUrl("abc")
        assertTrue("format=raw" in raw && "id=abc" in raw && "maxBitRate" !in raw)
        val small = client.streamUrl("abc", DesktopNavidromeTranscode("opus", 96))
        assertTrue("format=opus" in small && "maxBitRate=96" in small && "format=raw" !in small)
    }

    @Test
    fun `an address is tidied into the base the api lives under`() {
        assertEquals("https://music.example.com", DesktopNavidromeClient.normalizeBase(" https://music.example.com/ "))
        assertEquals("https://music.example.com", DesktopNavidromeClient.normalizeBase("music.example.com"))
        assertEquals("http://10.0.0.5:4533", DesktopNavidromeClient.normalizeBase("http://10.0.0.5:4533/rest"))
        assertEquals("https://example.com/navidrome", DesktopNavidromeClient.normalizeBase("https://example.com/navidrome/"))
        assertEquals("", DesktopNavidromeClient.normalizeBase("  "))
    }

    // ── Wire format (bodies recorded from Navidrome 0.64.2) ─────────────

    @Test
    fun `a search reply from a real server is read in full`() {
        val response = DesktopNavidromeClient.parse(SEARCH_REPLY)
        assertEquals("ok", response.status)
        assertEquals("navidrome", response.type)
        val song = response.searchResult3!!.song.single()
        assertEquals("7rsQgbwasf6T1SP9mnVTWZ", song.id)
        assertEquals("Hello World", song.title)
        assertEquals("Test Artist", song.artist)
        assertEquals("flac", song.suffix)
        assertEquals(20, song.duration)
        assertEquals(44100, song.samplingRate)
        assertEquals(16, song.bitDepth)
    }

    @Test
    fun `a refusal carries the server's own code`() {
        val response = DesktopNavidromeClient.parse(ERROR_REPLY)
        assertEquals("failed", response.status)
        assertEquals(70, response.error?.code)
        assertTrue(DesktopNavidromeException(70, "x").isNotFound)
        assertTrue(DesktopNavidromeException(40, "x").isBadCredentials)
        assertFalse(DesktopNavidromeException(70, "x").isBadCredentials)
    }

    @Test
    fun `a reply with fields this build has never heard of still parses`() {
        val response = DesktopNavidromeClient.parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1","futureThing":{"a":1},"searchResult3":{}}}""",
        )
        assertEquals("ok", response.status)
        assertEquals(emptyList(), response.searchResult3?.song)
    }

    // ── Track identity and format ───────────────────────────────────────

    @Test
    fun `a navidrome row round-trips through its key`() {
        val key = DesktopNavidromeSource.trackKey("source-1", "7rsQgbwasf6T1SP9mnVTWZ")
        assertEquals(DesktopNavidromeSource.TrackRef("source-1", "7rsQgbwasf6T1SP9mnVTWZ"), DesktopNavidromeSource.parseTrack(key))
        assertNull(DesktopNavidromeSource.parseTrack("dQw4w9WgXcQ"))
        assertNull(DesktopNavidromeSource.parseTrack("navidrome:nosource"))
        assertNull(DesktopNavidromeSource.parseTrack("navidrome:source/"))
        assertNull(DesktopNavidromeSource.parseTrack("addon:source/id"))
    }

    @Test
    fun `an album key round-trips and rejects a youtube id and a track key`() {
        val key = DesktopNavidromeSource.albumKey("source-1", "7orvCZZyWRqsduCdqXoguY")
        assertEquals("navidrome-album:source-1/7orvCZZyWRqsduCdqXoguY", key)
        assertEquals(
            DesktopNavidromeSource.AlbumRef("source-1", "7orvCZZyWRqsduCdqXoguY"),
            DesktopNavidromeSource.parseAlbum(key),
        )
        assertEquals(
            key,
            DesktopNavidromeSource.parseAlbum(key)?.let { DesktopNavidromeSource.albumKey(it.sourceId, it.albumId) },
        )
        assertNull(DesktopNavidromeSource.parseAlbum("MPREb_youtube"))
        assertNull(DesktopNavidromeSource.parseAlbum(DesktopNavidromeSource.trackKey("source-1", "7rsQgbwasf6T1SP9mnVTWZ")))
        assertNull(DesktopNavidromeSource.parseAlbum("navidrome-album:nosource"))
        assertNull(DesktopNavidromeSource.parseAlbum("navidrome-album:source/"))
    }

    @Test
    fun `merging server albums keeps a youtube album and replaces the previous navidrome set`() {
        val youtube = shelfCard("YouTube", "MPREb_yt")
        val previous = shelfCard("Old", DesktopNavidromeSource.albumKey("s", "old"))
        val page = LibraryPage(
            likedSongs = emptyList(),
            librarySongs = emptyList(),
            shelves = listOf(
                HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, listOf(shelfCard("Liked", "VLLM"))),
                HomeShelf(ALBUMS_SHELF, listOf(youtube, previous)),
                HomeShelf("Artists", listOf(shelfCard("Artist", "UCartist"))),
            ),
        )
        val fresh = shelfCard("New", DesktopNavidromeSource.albumKey("s", "new"))
        val once = page.withServerAlbums(listOf(fresh))
        assertEquals(
            listOf("MPREb_yt", DesktopNavidromeSource.albumKey("s", "new")),
            once.shelves.first { it.title == ALBUMS_SHELF }.items.map { it.browseId },
        )
        assertEquals(listOf("Playlists", ALBUMS_SHELF, "Artists"), once.shelves.map { it.title })
        assertEquals(once.shelves, once.withServerAlbums(listOf(fresh)).shelves)
    }

    @Test
    fun `a later empty merge removes only the navidrome cards`() {
        val youtube = shelfCard("YouTube", "MPREb_yt")
        val page = LibraryPage(
            emptyList(),
            emptyList(),
            listOf(
                HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, listOf(shelfCard("Liked", "VLLM"))),
                HomeShelf(ALBUMS_SHELF, listOf(youtube)),
                HomeShelf("Artists", listOf(shelfCard("Artist", "UCartist"))),
            ),
        )
        val server = shelfCard("Server", DesktopNavidromeSource.albumKey("s", "a"))
        val merged = page.withServerAlbums(listOf(server))
        assertEquals(
            listOf("MPREb_yt", server.browseId),
            merged.shelves.first { it.title == ALBUMS_SHELF }.items.map { it.browseId },
        )
        val cleared = merged.withServerAlbums(emptyList())
        assertEquals(listOf(youtube.browseId), cleared.shelves.first { it.title == ALBUMS_SHELF }.items.map { it.browseId })
        assertEquals(page.shelves.map { it.title }, cleared.shelves.map { it.title })
        assertEquals(
            page.shelves.first { it.title == "Artists" }.items,
            cleared.shelves.first { it.title == "Artists" }.items,
        )
    }

    @Test
    fun `server albums are inserted after playlists when the page has no albums shelf`() {
        val album = shelfCard("Album", DesktopNavidromeSource.albumKey("s", "a"))
        val afterPlaylists = LibraryPage(
            emptyList(),
            emptyList(),
            listOf(
                HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, emptyList()),
                HomeShelf("Artists", emptyList()),
            ),
        ).withServerAlbums(listOf(album))
        assertEquals(listOf("Playlists", ALBUMS_SHELF, "Artists"), afterPlaylists.shelves.map { it.title })
        val atStart = LibraryPage(
            emptyList(),
            emptyList(),
            listOf(HomeShelf("Artists", emptyList())),
        ).withServerAlbums(listOf(album))
        assertEquals(listOf(ALBUMS_SHELF, "Artists"), atStart.shelves.map { it.title })
        val removed = LibraryPage(
            emptyList(),
            emptyList(),
            listOf(HomeShelf(ALBUMS_SHELF, listOf(album))),
        ).withServerAlbums(emptyList())
        assertTrue(removed.shelves.none { it.title == ALBUMS_SHELF })
    }

    @Test
    fun `album pages stop on a short page and when an id repeats`() = runBlocking {
        assertEquals(500, ALBUM_PAGE_SIZE)
        assertEquals(40, ALBUM_PAGE_LIMIT)
        val offsets = mutableListOf<Int>()
        val paged = collectAlbumPages(pageSize = 3) { offset ->
            offsets += offset
            when (offset) {
                0 -> listOf(
                    NavidromeAlbum(id = "a", name = "A"),
                    NavidromeAlbum(id = "b", name = "B"),
                    NavidromeAlbum(id = " ", name = "blank"),
                )
                3 -> listOf(NavidromeAlbum(id = "c", name = "C"))
                else -> error("fetched past the short page")
            }
        }
        // A full page is followed. The blank id is skipped, and the short page ends the walk.
        assertEquals(listOf(0, 3), offsets)
        assertEquals(listOf("a", "b", "c"), paged.map { it.id })

        var calls = 0
        val repeated = collectAlbumPages { offset ->
            calls++
            if (offset == 0) {
                List(ALBUM_PAGE_SIZE) { NavidromeAlbum(id = "p$it", name = "n$it") }
            } else {
                listOf(NavidromeAlbum(id = "p0", name = "again")) +
                    List(10) { NavidromeAlbum(id = "new$it", name = "new$it") }
            }
        }
        assertEquals(2, calls)
        assertEquals(ALBUM_PAGE_SIZE, repeated.size)
        assertTrue(repeated.none { it.id.startsWith("new") })

        var capped = 0
        val stopped = collectAlbumPages(pageSize = 1, maxPages = 2) {
            capped++
            listOf(NavidromeAlbum(id = "c$capped", name = "c"))
        }
        assertEquals(2, capped)
        assertEquals(listOf("c1", "c2"), stopped.map { it.id })
    }

    @Test
    fun `a loading youtube library stays loading and a failure can still show server albums`() {
        val album = shelfCard("Album", DesktopNavidromeSource.albumKey("s", "a"))
        assertTrue(UiState.Loading.mergingServerAlbums(listOf(album)) is UiState.Loading)
        val failed = UiState.Error("offline")
        assertEquals(failed, failed.mergingServerAlbums(emptyList()))
        val shown = failed.mergingServerAlbums(listOf(album)) as UiState.Success
        assertEquals(listOf(ALBUMS_SHELF), shown.data.shelves.map { it.title })
        assertEquals(album.browseId, shown.data.shelves.single().items.single().browseId)
    }

    private fun shelfCard(title: String, browseId: String) = ShelfItem(
        title = title,
        subtitle = "sub",
        thumbnailUrl = null,
        videoId = null,
        browseId = browseId,
    )

    @Test
    fun `an original flac is lossless and states what the file is`() {
        val row = DesktopNavidromeClient.parse(SEARCH_REPLY).searchResult3!!.song.single()
        val format = DesktopNavidromeSource.formatOf(row, transcode = null)
        assertTrue(format.isLossless)
        assertEquals("flac", format.codec)
        assertEquals(44100, format.sampleRateHz)
        assertEquals(16, format.bitDepth)
        assertEquals(1, format.channels)
    }

    @Test
    fun `an opus file is not lossless`() {
        val row = NavidromeSong(id = "1", title = "t", suffix = "opus", bitRate = 128, contentType = "audio/ogg")
        val format = DesktopNavidromeSource.formatOf(row, null)
        assertFalse(format.isLossless)
        assertEquals(128, format.kbps)
    }

    @Test
    fun `a re-encode is described as what it will be, not what the file was`() {
        val row = NavidromeSong(id = "1", title = "t", suffix = "flac", bitRate = 900)
        val format = DesktopNavidromeSource.formatOf(row, DesktopNavidromeTranscode("opus", 96))
        assertFalse(format.isLossless)
        assertEquals("opus", format.codec)
        assertEquals(96, format.kbps)
    }

    @Test
    fun `a row from the server becomes a song the player can queue`() {
        val client = DesktopNavidromeClient("https://m.example.com", DesktopNavidromeCredentials("u", "s", "t"))
        val row = NavidromeSong(
            id = "abc", title = "Hello World", artist = "Test Artist", album = "Test Album",
            duration = 125, coverArt = "al-1", suffix = "flac",
        )
        val song: Song = DesktopNavidromeSource.songOf("src", client, row)
        assertEquals("navidrome:src/abc", song.videoId)
        assertEquals("2:05", song.durationText)
        assertEquals("Test Album", song.albumName)
        assertTrue(song.thumbnailUrl!!.contains("getCoverArt") && song.thumbnailUrl!!.contains("id=al-1"))
        assertEquals(DesktopModuleSource.LOSSLESS, song.sourceQuality)
    }

    // ── Kind and ordering ───────────────────────────────────────────────

    @Test
    fun `navidrome is tried before every other source`() {
        val order = listOf(
            DesktopSourceConfig("yt", DesktopSourceKind.YOUTUBE),
            DesktopSourceConfig("jio", DesktopSourceKind.JIOSAAVN),
            DesktopSourceConfig("addon", DesktopSourceKind.ADDON, baseUrl = "https://a.example"),
            DesktopSourceConfig("nd", DesktopSourceKind.NAVIDROME, baseUrl = "https://n.example", username = "u"),
        ).inSourceOrder().map { it.id }
        assertEquals(listOf("nd", "addon", "jio", "yt"), order)
    }

    @Test
    fun `navidrome needs an address and a username before it counts as set up`() {
        assertFalse(DesktopSourceConfig("nd", DesktopSourceKind.NAVIDROME).isComplete)
        assertFalse(DesktopSourceConfig("nd", DesktopSourceKind.NAVIDROME, baseUrl = "https://n.example").isComplete)
        assertTrue(DesktopSourceConfig("nd", DesktopSourceKind.NAVIDROME, baseUrl = "https://n.example", username = "u").isComplete)
    }

    @Test
    fun `no audio quality ceiling keeps the listener's own server out`() {
        DesktopAudioQuality.entries.forEach { quality ->
            assertTrue(quality.permits(DesktopSourceKind.NAVIDROME), "$quality must permit Navidrome")
        }
        // ...and the ceilings still mean what they did for everything else.
        assertFalse(DesktopAudioQuality.MEDIUM.permits(DesktopSourceKind.JIOSAAVN))
        assertFalse(DesktopAudioQuality.HIGH.permits(DesktopSourceKind.ADDON))
    }

    @Test
    fun `a saved source list from before navidrome still reads`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = """[{"id":"a","kind":"ADDON","baseUrl":"https://x.example"}]"""
        val read = json.decodeFromString<List<DesktopSourceConfig>>(old)
        assertEquals("", read.single().username)
    }

    // ── The plan ────────────────────────────────────────────────────────

    private fun plan(
        mode: DesktopNavidromeMode = DesktopNavidromeMode.FIRST,
        wait: Int = 5,
        metered: Boolean = false,
        onMetered: DesktopMeteredBehaviour = DesktopMeteredBehaviour.OTHER_SOURCES,
        kbps: Int = 96,
    ) = navidromePlan(mode, wait, metered, onMetered, kbps)

    @Test
    fun `off a metered connection the chosen mode applies untouched`() {
        val plan = plan(mode = DesktopNavidromeMode.FIRST, wait = 7)
        assertEquals(DesktopNavidromeMode.FIRST, plan.mode)
        assertEquals(7_000L, plan.waitMs)
        assertNull(plan.transcode)
        assertTrue(plan.enabled)
    }

    @Test
    fun `on a metered connection navidrome can be left out entirely`() {
        val plan = plan(metered = true, onMetered = DesktopMeteredBehaviour.OTHER_SOURCES)
        assertEquals(DesktopNavidromeMode.OFF, plan.mode)
        assertFalse(plan.enabled)
    }

    @Test
    fun `on a metered connection navidrome can be kept at a smaller opus`() {
        val plan = plan(metered = true, onMetered = DesktopMeteredBehaviour.TRANSCODE, kbps = 80)
        assertTrue(plan.enabled)
        assertEquals(DesktopNavidromeTranscode("opus", 80), plan.transcode)
    }

    @Test
    fun `on a metered connection navidrome can be left as it is`() {
        val plan = plan(metered = true, onMetered = DesktopMeteredBehaviour.SAME)
        assertTrue(plan.enabled)
        assertNull(plan.transcode)
    }

    @Test
    fun `a metered connection does not turn a switched-off navidrome back on`() {
        val plan = plan(mode = DesktopNavidromeMode.OFF, metered = true, onMetered = DesktopMeteredBehaviour.TRANSCODE)
        assertFalse(plan.enabled)
    }

    @Test
    fun `the wait is kept within sensible bounds`() {
        assertEquals(1_000L, plan(wait = 0).waitMs)
        assertEquals(30_000L, plan(wait = 999).waitMs)
    }

    @Test
    fun `networkmanager's metered values are read as it defines them`() {
        // NM_METERED_UNKNOWN 0, YES 1, NO 2, GUESS_YES 3, GUESS_NO 4.
        assertFalse(DesktopNetwork.isMeteredValue(0))
        assertTrue(DesktopNetwork.isMeteredValue(1))
        assertFalse(DesktopNetwork.isMeteredValue(2))
        assertTrue(DesktopNetwork.isMeteredValue(3))
        assertFalse(DesktopNetwork.isMeteredValue(4))
    }

    // ── Against a server ────────────────────────────────────────────────

    private class Seen(val path: String, val query: Map<String, String>, val range: String?)

    private fun withServer(block: suspend (base: String, seen: CopyOnWriteArrayList<Seen>, server: HttpServer) -> Unit) =
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val seen = CopyOnWriteArrayList<Seen>()
            server.createContext("/rest/") { ex ->
                seen += Seen(ex.requestURI.path, ex.queryMap(), ex.requestHeaders.getFirst("Range"))
                when (ex.requestURI.path) {
                    "/rest/ping" -> ex.reply(200, "application/json", PING_REPLY)
                    "/rest/search3" -> ex.reply(200, "application/json", SEARCH_REPLY)
                    "/rest/scrobble" -> ex.reply(200, "application/json", PING_REPLY)
                    "/rest/stream" -> {
                        if (ex.queryMap()["id"] == "missing") {
                            ex.reply(200, "application/json", ERROR_REPLY)
                        } else {
                            ex.reply(206, "audio/flac", "f")
                        }
                    }
                    else -> ex.reply(404, "text/plain", "no")
                }
            }
            server.start()
            try {
                block("http://127.0.0.1:${server.address.port}", seen, server)
            } finally {
                server.stop(0)
            }
        }

    private fun client(base: String) =
        DesktopNavidromeClient(base, DesktopNavidromeCredentials.fromPassword("joe", "sesame", salt = "c19b2d"))

    @Test
    fun `ping names the server`() = withServer { base, _, _ ->
        val info = client(base).ping().getOrThrow()
        assertEquals("Navidrome", info.name)
        assertEquals("0.64.2 (v0.64.2)", info.version)
    }

    @Test
    fun `search sends the query signed and returns the songs`() = withServer { base, seen, _ ->
        val songs = client(base).search("hello world", 10).getOrThrow()
        assertEquals(listOf("Hello World"), songs.map { it.title })
        val call = seen.single { it.path == "/rest/search3" }
        assertEquals("hello world", call.query["query"])
        assertEquals("10", call.query["songCount"])
        assertEquals("0", call.query["albumCount"])
        assertEquals("joe", call.query["u"])
        assertEquals("26719a1196d2a940705a59634eb18eab", call.query["t"])
    }

    @Test
    fun `a stream that starts is accepted`() = withServer { base, seen, _ ->
        val client = client(base)
        assertTrue(client.probe(client.streamUrl("good")).isSuccess)
        // Only the first byte is asked for: this is a check, not a download.
        assertEquals("bytes=0-0", seen.single { it.path == "/rest/stream" }.range)
    }

    @Test
    fun `a stream the server refuses is caught before playback is promised`() = withServer { base, _, _ ->
        val client = client(base)
        assertTrue(client.probe(client.streamUrl("missing")).isFailure)
    }

    @Test
    fun `a play is reported with its id and whether it counts`() = withServer { base, seen, _ ->
        val client = client(base)
        client.scrobble("abc", submission = false).getOrThrow()
        client.scrobble("abc", submission = true, atMs = 1_700_000_000_000L).getOrThrow()
        val calls = seen.filter { it.path == "/rest/scrobble" }
        assertEquals("false", calls[0].query["submission"])
        assertNull(calls[0].query["time"])
        assertEquals("true", calls[1].query["submission"])
        assertEquals("1700000000000", calls[1].query["time"])
        assertEquals("abc", calls[1].query["id"])
    }

    @Test
    fun `a wrong password reads as one`() = withServer { base, _, server ->
        server.removeContext("/rest/")
        server.createContext("/rest/") {
            it.reply(
                200,
                "application/json",
                """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}""",
            )
        }
        val failure = assertFailsWith<DesktopNavidromeException> { client(base).ping().getOrThrow() }
        assertTrue(failure.isBadCredentials)
    }

    @Test
    fun `an unreachable server is a failure, not a hang`() = runBlocking<Unit> {
        val result = client("http://127.0.0.1:1").ping()
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    private fun HttpExchange.queryMap(): Map<String, String> =
        requestURI.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate {
            val (key, value) = it.split('=', limit = 2).let { parts -> parts[0] to parts.getOrElse(1) { "" } }
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun HttpExchange.reply(status: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val PING_REPLY =
            """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.64.2 (v0.64.2)","openSubsonic":true}}"""

        const val ERROR_REPLY =
            """{"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome","serverVersion":"0.64.2 (v0.64.2)","openSubsonic":true,"error":{"code":70,"message":"data not found"}}}"""

        /** Recorded from Navidrome 0.64.2 for `search3?query=hello`. */
        const val SEARCH_REPLY =
            """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.64.2 (v0.64.2)","openSubsonic":true,"searchResult3":{"song":[{"id":"7rsQgbwasf6T1SP9mnVTWZ","parent":"7orvCZZyWRqsduCdqXoguY","isDir":false,"title":"Hello World","album":"Test Album","artist":"Test Artist","track":1,"year":2020,"size":315950,"contentType":"audio/flac","suffix":"flac","duration":20,"bitRate":123,"path":"Test Artist/Test Album/01 - Hello World.flac","created":"2026-10-06T00:12:21.724739666+05:30","albumId":"7orvCZZyWRqsduCdqXoguY","artistId":"4kqFOAfzcbEE22Bin8jHjV","type":"music","bpm":0,"comment":"","sortName":"hello world","mediaType":"song","musicBrainzId":"","isrc":[],"genres":[],"replayGain":{},"channelCount":1,"samplingRate":44100,"bitDepth":16,"moods":[],"artists":[{"id":"4kqFOAfzcbEE22Bin8jHjV","name":"Test Artist"}],"displayArtist":"Test Artist","albumArtists":[{"id":"4kqFOAfzcbEE22Bin8jHjV","name":"Test Artist"}],"displayAlbumArtist":"Test Artist","contributors":[],"displayComposer":"","explicitStatus":"","groupings":[],"works":[],"movements":[]}]}}}"""
    }
}
