package com.music.bitchord.data.spotify

import com.music.bitchord.data.canvas.SpotifyToken

/** Android's browser-backed credentials for the shared Spotify library client. */
object SpotifyLibrary {
    const val LIKED_ID = SpotifyLibraryClient.LIKED_ID
    private val client = SpotifyLibraryClient(
        accessToken = { SpotifyToken.accessToken() },
        clientToken = { SpotifyToken.clientToken() },
    )

    suspend fun playlists(): List<SpotifyPlaylist> = client.playlists()
    suspend fun tracks(playlistId: String, onPage: (List<SpotifyTrack>) -> Unit = {}): List<SpotifyTrack> =
        client.tracks(playlistId, onPage)
    suspend fun cover(playlistId: String): String? = client.cover(playlistId)
}
