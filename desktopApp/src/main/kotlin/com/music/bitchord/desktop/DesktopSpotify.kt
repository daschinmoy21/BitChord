package com.music.bitchord.desktop

import com.music.bitchord.data.spotify.SpotifyLibraryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Spotify credentials use the browser's dedicated BitChord profile, never its personal profile. */
internal object DesktopSpotify {
    const val ENABLED_KEY = "spotify_enabled"
    private const val BROWSER_KEY = "spotify_browser"
    private val refreshLock = Mutex()
    val library = SpotifyLibraryClient(
        accessToken = { accessToken() },
        clientToken = { DesktopSpotifyToken.clientToken() },
    )

    suspend fun connect(browser: DesktopBrowserSignIn.Browser) {
        val cookie = DesktopBrowserSignIn.capture(browser, DesktopBrowserSignIn.Service.SPOTIFY)
        val persistence = DesktopPersistence()
        val previousCookie = DesktopSpotifyToken.cookie()
        val previousBrowser = persistence.string(BROWSER_KEY)
        DesktopSpotifyToken.setCookie(cookie)
        persistence.saveString(BROWSER_KEY, browser.name)
        try {
            // Validate against the account library before reporting a successful connection.
            library.playlists()
        } catch (failure: Exception) {
            DesktopSpotifyToken.setCookie(previousCookie)
            persistence.saveString(BROWSER_KEY, previousBrowser)
            throw failure
        }
    }

    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        if (DesktopSpotifyToken.cookie().isBlank()) return@withContext null
        refreshLock.withLock {
            DesktopSpotifyToken.freshBrowserToken()?.let { return@withLock it }
            val browserName = DesktopPersistence().string(BROWSER_KEY)
            if (browserName.isBlank()) return@withLock DesktopSpotifyToken.accessToken()
            val browser = DesktopBrowserSignIn.installed().firstOrNull { it.name == browserName }
                ?: error("Install $browserName again or reconnect Spotify with another browser.")
            DesktopSpotifyToken.acceptBrowserToken(DesktopBrowserSignIn.spotifyToken(browser))
            DesktopSpotifyToken.freshBrowserToken()
        }
    }

    fun disconnect() {
        DesktopBrowserSignIn.clearSpotifyProfiles()
        DesktopPersistence().saveString(BROWSER_KEY, "")
        DesktopSpotifyToken.setCookie("")
    }
}
