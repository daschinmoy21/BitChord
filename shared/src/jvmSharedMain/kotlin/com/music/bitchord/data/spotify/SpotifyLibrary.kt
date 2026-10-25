package com.music.bitchord.data.spotify

/**
 * The application-wide Spotify library, backed by [SpotifyLibraryClient].
 *
 * Each application installs [auth] at start-up the way it installs
 * DebugLog.sink: the phone from its WebView-minted tokens, the desktop from
 * its browser sign-in. Until then every call fails as signed out.
 */
object SpotifyLibrary {
    /** The web player's bearer, and its client token when there is one. */
    class Tokens(val bearer: String, val clientToken: String?)

    /** Where the tokens come from. Null tokens mean signed out or expired. */
    fun interface Auth {
        suspend fun tokens(): Tokens?
    }

    @Volatile
    var auth: Auth? = null

    /** The pseudo-playlist id for the Liked Songs collection, which has no playlist uri. */
    const val LIKED_ID = SpotifyLibraryClient.LIKED_ID

    private val client = SpotifyLibraryClient(
        accessToken = { auth?.tokens()?.bearer },
        clientToken = { auth?.tokens()?.clientToken },
    )

    /** Every playlist in the library, Liked Songs first. */
    suspend fun playlists(): List<SpotifyPlaylist> = client.playlists()

    /** Every track of a playlist; [onPage] gets the running list after each page. */
    suspend fun tracks(playlistId: String, onPage: (List<SpotifyTrack>) -> Unit = {}): List<SpotifyTrack> =
        client.tracks(playlistId, onPage)

    /** The playlist's cover at its largest. */
    suspend fun cover(playlistId: String): String? = client.cover(playlistId)
}
