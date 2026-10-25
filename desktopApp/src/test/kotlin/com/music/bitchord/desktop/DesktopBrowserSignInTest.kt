package com.music.bitchord.desktop

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopBrowserSignInTest {
    @Test fun `offers Brave alongside Chromium and deduplicates Brave executable aliases`() {
        if (DesktopPlatform.isWindows) return
        val directory = Files.createTempDirectory("bitchord-browser-test")
        try {
            for (command in listOf("chromium", "brave", "brave-browser-stable")) {
                Files.createFile(directory.resolve(command)).toFile().setExecutable(true)
            }
            val browsers = DesktopBrowserSignIn.onPath(listOf(directory))
            assertEquals(listOf("Chromium", "Brave"), browsers.map { it.name })
            assertEquals(directory.resolve("brave-browser-stable"), browsers.last().executable)
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun `Spotify cookie capture excludes unrelated and lookalike domains`() {
        val cookies = Json.parseToJsonElement("""[
            {"domain":".spotify.com","name":"sp_dc","value":"session"},
            {"domain":"accounts.spotify.com","name":"sp_key","value":"key"},
            {"domain":"spotify.com.evil.example","name":"sp_dc","value":"wrong"},
            {"domain":"evilspotify.com","name":"sp_dc","value":"wrong"},
            {"domain":".youtube.com","name":"SAPISID","value":"other-service"}
        ]""").jsonArray
        assertEquals("sp_dc=session; sp_key=key", DesktopBrowserSignIn.cookieHeader(cookies, "spotify.com"))
        assertEquals("SAPISID=other-service", DesktopBrowserSignIn.cookieHeader(cookies, "youtube.com"))
    }
}
