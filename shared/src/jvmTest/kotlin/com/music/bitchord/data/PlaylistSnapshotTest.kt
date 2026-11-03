package com.music.bitchord.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaylistSnapshotTest {
    private fun row(id: String, entry: String) = """{"musicResponsiveListItemRenderer":{
        "playlistItemData":{"videoId":"$id","playlistSetVideoId":"$entry"},
        "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Track $id"}]}}}]
    }}"""
    private fun response(rows: String, token: String? = null, continuation: Boolean = false): JsonObject {
        val next = token?.let { """, "continuations":[{"nextContinuationData":{"continuation":"$it"}}]""" }.orEmpty()
        val shelf = """{"contents":[$rows]$next}"""
        val body = if (continuation) """{"continuationContents":{"musicPlaylistShelfContinuation":$shelf}}"""
        else """{"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
            "contents":[{"musicPlaylistShelfRenderer":$shelf}]
        }}}}}"""
        return Json.parseToJsonElement(body).jsonObject
    }

    @Test fun realRendererEntriesPreserveDuplicatesWithinAndAcrossPages() = runBlocking<Unit> {
        val first = response(listOf(row("a", "entry1"), row("a", "entry2")).joinToString(","), "next")
        val snapshot = YtMusicRepository.collectPlaylistSnapshot(first) {
            assertEquals("next", it)
            response(listOf(row("b", "entry3"), row("a", "entry4")).joinToString(","), continuation = true)
        }
        assertEquals(listOf("a", "a", "b", "a"), snapshot.songs.map { it.videoId })
        assertEquals(listOf("entry1", "entry2", "entry3", "entry4"), snapshot.songs.map { it.setVideoId })
    }

    @Test fun unavailableAndUnknownResponsesFailWhileAGenuineEmptyPlaylistSucceeds() = runBlocking<Unit> {
        val unavailable = Json.parseToJsonElement("""{"alerts":[{"alertRenderer":{"type":"ERROR","text":{"runs":[{"text":"Private playlist"}]}}}]}""").jsonObject
        assertFailsWith<IllegalStateException> { YtMusicRepository.collectPlaylistSnapshot(unavailable) { error("No continuation") } }
        assertFailsWith<IllegalStateException> { YtMusicRepository.collectPlaylistSnapshot(JsonObject(emptyMap())) { error("No continuation") } }
        assertTrue(YtMusicRepository.collectPlaylistSnapshot(response("")) { error("No continuation") }.songs.isEmpty())
    }

    @Test fun brokenOrRepeatedContinuationNeverReturnsAPartialSnapshot() = runBlocking<Unit> {
        val first = response(row("a", "entry1"), "next")
        assertFailsWith<IllegalStateException> {
            YtMusicRepository.collectPlaylistSnapshot(first) { response(row("b", "entry2"), "next", true) }
        }
        assertFailsWith<IllegalStateException> {
            YtMusicRepository.collectPlaylistSnapshot(first) { JsonObject(emptyMap()) }
        }
    }
}
