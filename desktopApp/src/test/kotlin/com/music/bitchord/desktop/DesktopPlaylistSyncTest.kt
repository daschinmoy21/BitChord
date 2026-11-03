package com.music.bitchord.desktop

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopPlaylistSyncTest {
    private fun song(id: String, saved: Boolean = false) = Song(
        id, id, "Artist", null,
        localPath = if (saved) "/music/$id.flac" else null,
    )

    @Test fun newTracksDownloadOnceAndExplicitlyDeletedTracksStayDeleted() {
        val remote = listOf(song("old"), song("new"), song("new"), song("deleted"))
        assertEquals(listOf("new"), missingPlaylistDownloads(remote, listOf(song("old", true)), setOf("deleted")).map { it.videoId })
        // An unfinished or failed download is not in the saved list, so the next pass can retry it.
        assertEquals(listOf("new"), missingPlaylistDownloads(listOf(song("new")), emptyList(), emptySet()).map { it.videoId })
    }

    @Test fun offlineSnapshotFollowsRemoteOrderAndDuplicatesWithoutDeletingOtherDownloads() {
        val downloads = listOf(song("a", true), song("b", true), song("removed", true))
        val record = DesktopPlaylistSyncRecord("VLtest", "My playlist", "youtube:account", songIds = listOf("b", "a", "b", "pending"))
        val offline = record.offlinePlaylist(downloads)
        assertEquals(listOf("b", "a", "b"), offline.songs.map { it.videoId })
        assertTrue(offline.songs.all { it.localPath != null })
        assertEquals(3, downloads.size)
        assertEquals(listOf("a"), record.copy(ignoredIds = setOf("b")).offlinePlaylist(downloads).songs.map { it.videoId })
    }

    @Test fun syncSubscriptionsSurviveRestartWithAccountAndDeletionRules() {
        val node = Preferences.userRoot().node("bitchord-sync-test-${System.nanoTime()}")
        try {
            val record = DesktopPlaylistSyncRecord("VLtest", "A # playlist", "youtube:user:channel", songIds = listOf("a", "a", "b"), ignoredIds = setOf("b"), lastChecked = 123)
            DesktopPersistence(node).saveSyncedPlaylists(listOf(record))
            assertEquals(listOf(record), DesktopPersistence(node).syncedPlaylists())
        } finally { node.removeNode() }
    }

    @Test fun paginationPreservesAllEntriesAndRejectsARepeatedContinuation() = runBlocking<Unit> {
        val first = DesktopCollection("VLtest", "Playlist", "", null, BrowseType.PLAYLIST, listOf(song("a")), continuation = "next")
        val full = collectPlaylistSyncPages(first) { listOf(song("b"), song("a")) to null }
        assertEquals(listOf("a", "b", "a"), full.songs.map { it.videoId })
        assertFailsWith<IllegalStateException> { collectPlaylistSyncPages(first) { listOf(song("b")) to "next" } }
        assertFailsWith<IllegalStateException> { collectPlaylistSyncPages(first) { error("Server offline") } }
    }
}
