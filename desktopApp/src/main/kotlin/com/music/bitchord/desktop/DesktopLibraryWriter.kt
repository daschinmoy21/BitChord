package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal data class DesktopLibrarySnapshot(
    val downloads: List<Song>,
    val playlists: List<DesktopPlaylist>,
    val syncRecords: List<DesktopPlaylistSyncRecord>,
)

/** One conflated IO writer: a download batch does not serialize the entire library on the UI thread. */
internal class DesktopLibraryWriter(private val persistence: DesktopPersistence) : AutoCloseable {
    private val pending = Channel<DesktopLibrarySnapshot>(Channel.CONFLATED)
    private val writer = CoroutineScope(Dispatchers.IO).launch {
        var saved: DesktopLibrarySnapshot? = null
        for (snapshot in pending) {
            delay(250)
            var latest = snapshot
            while (true) latest = pending.tryReceive().getOrNull() ?: break
            try {
                if (latest.downloads != saved?.downloads) persistence.saveDownloads(latest.downloads)
                if (latest.playlists != saved?.playlists) persistence.savePlaylists(latest.playlists)
                if (latest.syncRecords != saved?.syncRecords) persistence.saveSyncedPlaylists(latest.syncRecords)
                saved = latest
            } catch (failure: Exception) {
                DesktopTrackLog.log("Could not save the offline library: ${failure.message}")
            }
        }
    }

    fun submit(snapshot: DesktopLibrarySnapshot) { pending.trySend(snapshot) }

    /** Drain the last snapshot when the app exits rather than abandoning its final downloads. */
    override fun close() {
        pending.close()
        runBlocking { writer.join() }
    }
}
