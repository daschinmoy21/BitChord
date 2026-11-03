package com.music.bitchord.data.navidrome

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavidromeStarTest {
    @Test fun favouritesReadAndWriteThroughAuthenticatedSubsonicEndpoints() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = NavidromeClient(server.url("/music").toString(), NavidromeCredentials("listener", "salt", "token"))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","starred2":{"song":[{"id":"track / 1","title":"Favourite"}]}}}"""))
            assertEquals(listOf("track / 1"), client.starredSongs().getOrThrow().map { it.id })
            assertEquals("/music/rest/getStarred2", server.takeRequest().requestUrl!!.encodedPath)
            for (starred in listOf(true, false)) {
                server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
                client.setStarred("track / 1", starred).getOrThrow()
                val url = assertNotNull(server.takeRequest().requestUrl)
                assertEquals("/music/rest/${if (starred) "star" else "unstar"}", url.encodedPath)
                assertEquals("track / 1", url.queryParameter("id"))
                assertEquals("listener", url.queryParameter("u"))
                assertEquals("token", url.queryParameter("t"))
            }
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong password"}}}"""))
            assertTrue(client.setStarred("track", true).isFailure)
        } finally { server.shutdown() }
    }
}
