package com.music.bitchord.data.navidrome

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavidromeLibraryTest {
    @Test fun catalogSearchRequestsAndParsesSongsAlbumsAndArtists() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","searchResult3":{
                "song":[{"id":"s1","title":"Track","artist":"Artist","album":"Album","suffix":"flac","duration":125}],
                "album":[{"id":"a1","name":"Album","artist":"Artist"}],
                "artist":[{"id":"r1","name":"Artist"}]
            }}}"""))
            val client = NavidromeClient(server.url("/music").toString(), NavidromeCredentials("u", "salt", "token"))
            val found = client.searchLibrary("Artist").getOrThrow()
            val url = assertNotNull(server.takeRequest().requestUrl)
            assertEquals("/music/rest/search3", url.encodedPath)
            assertEquals("50", url.queryParameter("albumCount"))
            assertEquals("50", url.queryParameter("artistCount"))
            assertEquals("50", url.queryParameter("songCount"))
            assertEquals("Artist", url.queryParameter("query"))
            val rows = NavidromeLibrary.searchRows("server", client, found, SearchFilter.ALL) {
                NavidromeLibrary.song(client, it) { id -> "src:server::$id" }
            }
            assertEquals(3, rows.size)
            assertEquals("2:05", (rows[0] as SearchResult.Track).song.durationText)
            assertEquals("LOSSLESS", (rows[0] as SearchResult.Track).song.sourceQuality)
            assertEquals("navidrome-album:server/a1", (rows[1] as SearchResult.Browse).item.browseId)
            assertEquals("navidrome-artist:server/r1", (rows[2] as SearchResult.Browse).item.browseId)
            assertEquals(1, NavidromeLibrary.searchRows("server", client, found, SearchFilter.ALBUMS) { error("not a song search") }.size)
            assertTrue(NavidromeLibrary.searchRows("server", client, found, SearchFilter.PLAYLISTS) { error("unsupported category") }.isEmpty())
        } finally { server.shutdown() }
    }

    @Test fun collectionReferencesAreSourceScopedAndRejectMalformedIds() {
        assertEquals(NavidromeLibrary.Ref("one", "album"), NavidromeLibrary.parseAlbum(NavidromeLibrary.albumKey("one", "album")))
        assertNull(NavidromeLibrary.parseAlbum("navidrome-album:source/"))
        assertNull(NavidromeLibrary.parseArtist("UC_youtube_artist"))
        assertNull(NavidromeLibrary.parseAlbum(NavidromeLibrary.artistKey("one", "artist")))
    }

    @Test fun albumsPageThroughTheServerAndAnExplicitReloadReadsFreshData() = runBlocking {
        val server = MockWebServer()
        server.start()
        fun response(albums: List<NavidromeAlbum>) = MockResponse().setBody(
            """{"subsonic-response":{"status":"ok","albumList2":${Json.encodeToString(NavidromeAlbumList.serializer(), NavidromeAlbumList(albums))}}}""",
        )
        try {
            server.enqueue(response(List(500) { NavidromeAlbum(id = "a$it", name = "Album $it") }))
            server.enqueue(response(listOf(NavidromeAlbum(id = "last", name = "Last album"))))
            server.enqueue(response(listOf(NavidromeAlbum(id = "new", name = "Newly scanned album"))))
            val client = NavidromeClient(server.url("/").toString(), NavidromeCredentials("u", "s", "t"))
            assertEquals(501, NavidromeLibrary.albums(client).size)
            assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
            assertEquals("500", server.takeRequest().requestUrl!!.queryParameter("offset"))
            assertEquals(listOf("new"), NavidromeLibrary.albums(client).map { it.id })
            assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
        } finally { server.shutdown() }
    }
}
