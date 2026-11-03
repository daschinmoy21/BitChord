package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import java.util.UUID

internal const val PLAYLIST_SYNC_INTERVAL_MS = 60 * 60_000L
internal const val PLAYLIST_SYNC_AUTO_LIMIT = 50

/** An explicit subscription. Audio files are never deleted by playlist synchronization. */
@Serializable
internal data class DesktopPlaylistSyncRecord(
    val browseId: String,
    val title: String,
    /** YouTube account/channel, or the Navidrome source identity. */
    val identity: String,
    val localPlaylistId: String = UUID.randomUUID().toString(),
    val songIds: List<String> = emptyList(),
    val ignoredIds: Set<String> = emptySet(),
    val lastChecked: Long = 0,
    val enabled: Boolean = true,
    val approvedIds: Set<String> = emptySet(),
) {
    fun offlinePlaylist(downloads: List<Song>): DesktopPlaylist {
        val saved = downloads.associateBy { it.videoId }
        return DesktopPlaylist(
            id = localPlaylistId,
            title = title,
            songs = songIds.filterNot { it in ignoredIds }.mapNotNull { saved[it] },
        )
    }
}

/** A file explicitly removed by the listener stays removed; duplicate entries need one download. */
internal fun missingPlaylistDownloads(
    songs: List<Song>,
    downloads: List<Song>,
    ignoredIds: Set<String>,
): List<Song> {
    val have = downloads.mapTo(mutableSetOf()) { it.videoId }
    return songs.distinctBy { it.videoId }.filter {
        it.videoId.isNotBlank() && it.localPath == null && it.videoId !in have && it.videoId !in ignoredIds
    }
}

/** Collect the entire playlist before committing a new snapshot; never save a partial page walk. */
internal suspend fun loadPlaylistForSync(browseId: String): DesktopCollection {
    DesktopNavidromeSource.parsePlaylist(browseId)?.let { return DesktopNavidromeSource.openPlaylist(it) }
    val snapshot = com.music.bitchord.data.YtMusicRepository.playlistSnapshot(browseId).getOrThrow()
    return DesktopCollection(
        browseId = browseId,
        title = snapshot.header?.title.orEmpty(),
        subtitle = snapshot.header?.subtitle.orEmpty(),
        thumbnailUrl = snapshot.header?.thumbnailUrl,
        type = com.music.bitchord.data.model.BrowseType.PLAYLIST,
        songs = snapshot.songs,
    )
}

internal data class DesktopPlaylistSyncPlan(val downloads: List<Song>, val needsApproval: Boolean)

/** Active transfers and retries are not new additions and must not trip the large-update gate. */
internal fun planPlaylistSync(
    record: DesktopPlaylistSyncRecord,
    songs: List<Song>,
    downloads: List<Song>,
    active: Map<String, DesktopDownloadItem>,
    approved: Boolean,
): DesktopPlaylistSyncPlan {
    if (!record.enabled) return DesktopPlaylistSyncPlan(emptyList(), false)
    val missing = missingPlaylistDownloads(songs, downloads, record.ignoredIds)
    val waiting = missing.filter { active[it.videoId]?.state.let { state -> state == null || state is DesktopDownloadState.Failed } }
    val newSongs = waiting.count { it.videoId !in record.approvedIds && active[it.videoId]?.state !is DesktopDownloadState.Failed }
    return DesktopPlaylistSyncPlan(waiting, !approved && newSongs > PLAYLIST_SYNC_AUTO_LIMIT)
}

internal fun canSyncPlaylist(record: DesktopPlaylistSyncRecord, identity: String, signedIn: Boolean): Boolean =
    record.enabled && (DesktopNavidromeSource.parsePlaylist(record.browseId) != null || (signedIn && record.identity == identity))
