package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun largeUpdateGateCountsOnlyUnapprovedNewDownloads() {
        val songs = (1..300).map { song("track-$it") }
        val record = DesktopPlaylistSyncRecord("VLtest", "Playlist", "youtube:a")
        assertTrue(planPlaylistSync(record, songs, emptyList(), emptyMap(), false).needsApproval)
        assertTrue(!planPlaylistSync(record, songs, emptyList(), emptyMap(), true).needsApproval)
        // Persisting the fetched track list without approval cannot bypass the next automatic check.
        assertTrue(planPlaylistSync(record.copy(songIds = songs.map { it.videoId }), songs, emptyList(), emptyMap(), false).needsApproval)
        val running = songs.associate { it.videoId to DesktopDownloadItem(it, DesktopDownloadState.Running(0.5f), 1) }
        assertTrue(!planPlaylistSync(record, songs, emptyList(), running, false).needsApproval)
        val failed = songs.associate { it.videoId to DesktopDownloadItem(it, DesktopDownloadState.Failed("offline"), 1) }
        assertTrue(!planPlaylistSync(record, songs, emptyList(), failed, false).needsApproval)
        val previouslyApproved = record.copy(approvedIds = songs.map { it.videoId }.toSet())
        assertTrue(!planPlaylistSync(previouslyApproved, songs, emptyList(), emptyMap(), false).needsApproval)
    }

    @Test fun pausedAndAccountScopedSubscriptionsDoNotRunWithoutTheRightSession() {
        val record = DesktopPlaylistSyncRecord("VLLM", "Liked", "youtube:a", ignoredIds = setOf("deleted"))
        assertTrue(!canSyncPlaylist(record, "youtube:a", false))
        assertTrue(!canSyncPlaylist(record, "youtube:b", true))
        assertTrue(canSyncPlaylist(record, "youtube:a", true))
        val resumed = record.copy(enabled = false).copy(enabled = true)
        assertEquals(record.localPlaylistId, resumed.localPlaylistId)
        assertEquals(record.ignoredIds, resumed.ignoredIds)
        assertTrue(!canSyncPlaylist(record.copy(enabled = false), "youtube:a", true))
        assertTrue(canSyncPlaylist(record.copy(browseId = "navidrome-playlist:server/id"), "youtube:b", false))
    }

    @Test fun libraryWriterDrainsTheLatestConflatedSnapshotOnClose() {
        val node = Preferences.userRoot().node("bitchord-writer-test-${System.nanoTime()}")
        try {
            val persistence = DesktopPersistence(node)
            val record = DesktopPlaylistSyncRecord("VLtest", "Playlist", "youtube:a", enabled = false)
            DesktopLibraryWriter(persistence).use { writer ->
                writer.submit(DesktopLibrarySnapshot(listOf(song("old", true)), emptyList(), emptyList()))
                writer.submit(DesktopLibrarySnapshot(listOf(song("new", true)), listOf(record.offlinePlaylist(emptyList())), listOf(record)))
            }
            assertEquals(listOf("new"), persistence.downloads().map { it.videoId })
            assertEquals(listOf(record), persistence.syncedPlaylists())
            assertEquals(record.localPlaylistId, persistence.playlists().single().id)
        } finally { node.removeNode() }
    }

    @Test fun deletingADownloadDoesNotExcludeItFromUnrelatedPlaylists() {
        val member = DesktopPlaylistSyncRecord("VLa", "A", "youtube:a", songIds = listOf("deleted"))
        val unrelated = DesktopPlaylistSyncRecord("VLb", "B", "youtube:a", songIds = listOf("other"))
        assertEquals(setOf("deleted"), member.ignoreDownload("deleted").ignoredIds)
        assertEquals(unrelated, unrelated.ignoreDownload("deleted"))
        assertEquals(emptySet(), unrelated.ignoreDownload("deleted").copy(songIds = listOf("deleted")).ignoredIds)
    }

    @Test fun writerShutdownReturnsWhenStorageStallsAndRepeatedCloseDoesNotWaitAgain() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val writer = DesktopLibraryWriter(
            save = { _, _ -> entered.countDown(); release.await() },
            shutdownTimeoutMs = 50,
        )
        try {
            writer.submit(DesktopLibrarySnapshot(emptyList(), emptyList(), emptyList()))
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val before = System.nanoTime()
            writer.close()
            writer.close()
            assertTrue((System.nanoTime() - before) / 1_000_000 < 2_000)
        } finally { release.countDown(); writer.close() }
    }

    @Test fun aThrowingSaveDoesNotPreventWriterShutdown() {
        val writer = DesktopLibraryWriter(save = { _, _ -> error("storage failed") })
        writer.submit(DesktopLibrarySnapshot(emptyList(), emptyList(), emptyList()))
        writer.close()
    }

}
