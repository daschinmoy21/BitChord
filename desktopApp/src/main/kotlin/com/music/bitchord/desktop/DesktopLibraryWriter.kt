package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

internal data class DesktopLibrarySnapshot(
    val downloads: List<Song>,
    val playlists: List<DesktopPlaylist>,
    val syncRecords: List<DesktopPlaylistSyncRecord>,
)

/** One conflated IO writer: a download batch does not serialize the entire library on the UI thread. */
internal class DesktopLibraryWriter(
    private val save: (DesktopLibrarySnapshot, DesktopLibrarySnapshot?) -> Unit,
    private val shutdownTimeoutMs: Long = 5_000,
) : AutoCloseable {
    constructor(persistence: DesktopPersistence) : this({ latest, saved ->
        if (latest.downloads != saved?.downloads) persistence.saveDownloads(latest.downloads)
        if (latest.playlists != saved?.playlists) persistence.savePlaylists(latest.playlists)
        if (latest.syncRecords != saved?.syncRecords) persistence.saveSyncedPlaylists(latest.syncRecords)
    })

    private val pending = Channel<DesktopLibrarySnapshot>(Channel.CONFLATED)
    private val closed = AtomicBoolean(false)
    private val writer = CoroutineScope(Dispatchers.IO).launch {
        var saved: DesktopLibrarySnapshot? = null
        for (snapshot in pending) {
            delay(250)
            var latest = snapshot
            while (true) latest = pending.tryReceive().getOrNull() ?: break
            try {
                save(latest, saved)
                saved = latest
            } catch (failure: Exception) {
                DesktopTrackLog.log("Could not save the offline library: ${failure.message}")
            }
        }
    }

    fun submit(snapshot: DesktopLibrarySnapshot) { pending.trySend(snapshot) }

    /** Give the final snapshot a chance to drain, but never hold app exit on stalled storage. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pending.close()
        val drained = runBlocking { withTimeoutOrNull(shutdownTimeoutMs) { writer.join(); true } } ?: false
        if (!drained) DesktopTrackLog.log("Offline library save is still pending at shutdown")
    }
}
