package com.music.bitchord.data.spotify

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SpotifyLibraryClientTest {
    @Test fun paginationAdvancesPastFilteredLibraryEntriesAndIncludesLikedSongs() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val playlist = """{"item":{"__typename":"PlaylistResponseWrapper","_uri":"spotify:playlist:first","data":{"__typename":"Playlist","name":"First"}}}"""
            val folder = """{"item":{"__typename":"Folder","data":{}}}"""
            server.enqueue(MockResponse().setBody("""{"data":{"me":{"libraryV3":{"totalCount":3,"items":[$playlist,$folder]}}}}"""))
            server.enqueue(MockResponse().setBody("""{"data":{"me":{"libraryV3":{"totalCount":3,"items":[${playlist.replace("first", "second").replace("First", "Second")}]}}}}"""))
            val client = SpotifyLibraryClient({ "bearer" }, { "client-token" }, server.url("/query").toString())
            assertEquals(listOf("liked", "first", "second"), client.playlists().map { it.id })
            for (offset in listOf(0, 2)) {
                val request = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                assertEquals("Bearer bearer", request.getHeader("Authorization"))
                assertEquals("client-token", request.getHeader("Client-Token"))
                val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                assertEquals(offset.toString(), body["variables"]!!.jsonObject["offset"]!!.jsonPrimitive.content)
            }
        } finally { server.shutdown() }
    }

    @Test fun missingCredentialsFailBeforeSendingARequest() = runBlocking {
        val client = SpotifyLibraryClient({ null }, { null })
        assertFailsWith<IllegalStateException> { client.playlists() }
        Unit
    }

    @Test fun likedSongsReadTheirDistinctTrackWrapper() {
        val root = Json.parseToJsonElement("""{"data":{"me":{"library":{"tracks":{"totalCount":2,"items":[
            {"track":{"_uri":"spotify:track:liked1","data":{"name":"Liked track",
            "artists":{"items":[{"profile":{"name":"Artist"}}]},"trackDuration":{"totalMilliseconds":123000}}}},
            {"track":{"data":null}}
        ]}}}}}""").jsonObject
        val (tracks, total, raw) = parseLikedPage(root)
        assertEquals(2, total)
        assertEquals(2, raw)
        assertEquals("liked1", tracks.single().id)
        assertEquals("Artist", tracks.single().artist)
        assertEquals(123000, tracks.single().durationMs)
    }
}
