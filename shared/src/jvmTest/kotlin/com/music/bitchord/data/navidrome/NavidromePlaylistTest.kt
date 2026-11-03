package com.music.bitchord.data.navidrome

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavidromePlaylistTest {
    @Test fun refreshingReflectsRenamesRemovedPlaylistsAndChangedTracks() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = NavidromeClient(server.url("/").toString(), NavidromeCredentials("u", "s", "t"))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlists":{"playlist":[
                {"id":"kept","name":"Old name","songCount":2},{"id":"removed","name":"Removed"}
            ]}}}"""))
            assertEquals(2, client.playlists().getOrThrow().size)
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlist":{
                "id":"kept","name":"Old name","entry":[{"id":"old"}]
            }}}"""))
            assertEquals(listOf("old"), client.playlist("kept").getOrThrow().entry.map { it.id })
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlists":{"playlist":[
                {"id":"kept","name":"New name","songCount":1}
            ]}}}"""))
            assertEquals("New name", client.playlists().getOrThrow().single().name)
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlist":{
                "id":"kept","name":"New name","entry":[{"id":"new"}]
            }}}"""))
            assertEquals(listOf("new"), client.playlist("kept").getOrThrow().entry.map { it.id })
        } finally { server.shutdown() }
    }

    @Test fun listsPlaylistsAndFetchesEntriesInServerOrderIncludingDuplicates() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlists":{"playlist":[
                {"id":"p / 1","name":"Favourites","songCount":3,"owner":"listener","coverArt":"pl1"}
            ]}}}"""))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlist":{
                "id":"p / 1","name":"Favourites","songCount":3,"entry":[
                    {"id":"second","title":"Second"},{"id":"first","title":"First"},{"id":"second","title":"Second"}
                ]
            }}}"""))
            val client = NavidromeClient(server.url("/music").toString(), NavidromeCredentials("u", "s", "t"))
            val summaries = client.playlists().getOrThrow()
            assertEquals("Favourites", summaries.single().name)
            assertEquals(3, summaries.single().songCount)
            val listUrl = assertNotNull(server.takeRequest().requestUrl)
            assertEquals("/music/rest/getPlaylists", listUrl.encodedPath)
            assertEquals("u", listUrl.queryParameter("u"))
            val playlist = client.playlist(summaries.single().id).getOrThrow()
            assertEquals(listOf("second", "first", "second"), playlist.entry.map { it.id })
            val detailUrl = assertNotNull(server.takeRequest().requestUrl)
            assertEquals("/music/rest/getPlaylist", detailUrl.encodedPath)
            assertEquals("p / 1", detailUrl.queryParameter("id"))
        } finally { server.shutdown() }
    }

    @Test fun emptyPlaylistsAndMissingOrRejectedDetailsAreHandled() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = NavidromeClient(server.url("/").toString(), NavidromeCredentials("u", "s", "t"))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlists":{}}}"""))
            assertTrue(client.playlists().getOrThrow().isEmpty())
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","playlist":{"id":"empty","name":"Empty"}}}"""))
            assertTrue(client.playlist("empty").getOrThrow().entry.isEmpty())
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
            assertEquals(70, (client.playlist("missing").exceptionOrNull() as NavidromeException).code)
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":50,"message":"Access denied"}}}"""))
            assertEquals(50, (client.playlist("private").exceptionOrNull() as NavidromeException).code)
        } finally { server.shutdown() }
    }
}
