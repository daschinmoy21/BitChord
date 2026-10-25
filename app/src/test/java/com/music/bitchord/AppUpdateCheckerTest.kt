package com.music.bitchord

import com.music.bitchord.data.AppUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which of this fork's releases the in-app updater offers, and which file it downloads. */
class AppUpdateCheckerTest {

    private fun release(tag: String, draft: Boolean = false, vararg assets: String) = """
        {"tag_name":"$tag","draft":$draft,"html_url":"https://github.com/daschinmoy21/BitChord/releases/tag/$tag",
         "body":"notes for $tag","assets":[${assets.joinToString(",") {
            """{"name":"$it","state":"uploaded","browser_download_url":"https://dl/$tag/$it"}"""
        }}]}
    """.trimIndent()

    private val releases = "[" + listOf(
        release("build-main", false, "BitChord-android-dev.apk"),
        release("v1.9.0-fork.2", false, "BitChord-android-dev.apk", "BitChord-1.9.0-fork.2-linux-x86_64.AppImage"),
        release("v1.9.0-fork.3", true, "BitChord-android-dev.apk"),
        release("v1.9.0-fork.1", false, "BitChord-android-dev.apk"),
    ).joinToString(",") + "]"

    @Test
    fun `offers the newest tagged release and its dev apk, skipping drafts and rolling builds`() {
        val update = AppUpdateChecker.newerRelease(releases, "1.9.0-fork.1", "BitChord-android-dev.apk")!!
        assertEquals("1.9.0-fork.2", update.version)
        assertEquals("https://dl/v1.9.0-fork.2/BitChord-android-dev.apk", update.apkUrl)
        assertEquals("notes for v1.9.0-fork.2", update.notes)
    }

    @Test
    fun `nothing is offered when the build is already the newest`() {
        assertNull(AppUpdateChecker.newerRelease(releases, "1.9.0-fork.2", "BitChord-android-dev.apk"))
    }

    @Test
    fun `a release without this flavour's apk still opens its page`() {
        val update = AppUpdateChecker.newerRelease(releases, "1.9.0-fork.1", "BitChord-android.apk")!!
        assertNull(update.apkUrl)
        assertEquals("https://github.com/daschinmoy21/BitChord/releases/tag/v1.9.0-fork.2", update.releaseUrl)
    }

    @Test
    fun `fork builds count up and sit after the release they build on`() {
        assertTrue(AppUpdateChecker.isNewer("1.9.0-fork.2", "1.9.0-fork.1"))
        assertTrue(AppUpdateChecker.isNewer("1.9.0-fork.1", "1.9.0"))
        assertTrue(AppUpdateChecker.isNewer("1.9.0-fork.1", "1.8-fork.212"))
        assertTrue(AppUpdateChecker.isNewer("1.10", "1.9.0-fork.7"))
        assertTrue(AppUpdateChecker.isNewer("1.9", "1.9-beta2"))
        assertFalse(AppUpdateChecker.isNewer("1.9.0-fork.1", "1.9.0-fork.1"))
        assertFalse(AppUpdateChecker.isNewer("build-main", "1.8"))
    }
}
