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
