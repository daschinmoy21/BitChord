package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The update check must never offer a build that is older or equal to the running one, and must
 * never offer a release that is not this fork's versioned build.
 */
class DesktopUpdateCheckerTest {

    @Test
    fun forkBuildsCompareByTheirNumber() {
        assertTrue(DesktopUpdateChecker.isNewer("1.8-fork.4", "1.8-fork.3"))
        assertFalse(DesktopUpdateChecker.isNewer("1.8-fork.3", "1.8-fork.3"))
        assertFalse(DesktopUpdateChecker.isNewer("1.8-fork.2", "1.8-fork.3"))
        assertTrue(DesktopUpdateChecker.isNewer("1.8-fork.10", "1.8-fork.9"))
    }

    @Test
    fun aForkBuildIsNewerThanTheBetasBeforeIt() {
        assertTrue(DesktopUpdateChecker.isNewer("1.8-fork.1", "1.8-beta1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.8-beta1", "1.8-fork.1"))
    }

    @Test
    fun aForkBuildIsNewerThanThePlainReleaseItIsBuiltOn() {
        assertTrue(DesktopUpdateChecker.isNewer("1.8", "1.8-beta1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.8-beta1", "1.8"))
        assertFalse(DesktopUpdateChecker.isNewer("1.8", "1.8-fork.5"))
        assertTrue(DesktopUpdateChecker.isNewer("1.8-fork.5", "1.8"))
    }

    @Test
    fun numbersCompareAsNumbers() {
        assertTrue(DesktopUpdateChecker.isNewer("1.10", "1.9"))
        assertTrue(DesktopUpdateChecker.isNewer("2.0-fork.1", "1.99"))
        assertFalse(DesktopUpdateChecker.isNewer("1.8", "1.8.0"))
    }

    @Test
    fun aLeadingVPrefixIsAccepted() {
        assertTrue(DesktopUpdateChecker.isNewer("v1.9", "1.8-fork.2"))
    }

    @Test
    fun unparseableVersionsAreNeverOffered() {
        assertFalse(DesktopUpdateChecker.isNewer("build-main", "1.8-beta1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.9", "garbage"))
        assertFalse(DesktopUpdateChecker.isNewer("1.9-rc1", "1.8-beta1"))
    }

    @Test
    fun theNewestVersionedReleaseIsOfferedAndTheRollingBuildIsIgnored() {
        val releases = """
            [
              {"tag_name":"build-main","name":"Fork build: main (00d34e7)","draft":false,"prerelease":true,
               "html_url":"https://example.test/build-main","body":"rolling","assets":[]},
              {"tag_name":"1.8-fork.2","draft":false,"prerelease":true,
               "html_url":"https://example.test/1.8-fork.2","body":"second","assets":[]},
              {"tag_name":"1.8-fork.3","draft":false,"prerelease":true,
               "html_url":"https://example.test/1.8-fork.3","body":"third","assets":[]}
            ]
        """.trimIndent()

        val update = DesktopUpdateChecker.newerRelease(releases, "1.8-fork.1")

        assertEquals("1.8-fork.3", update?.version)
        assertEquals("https://example.test/1.8-fork.3", update?.releaseUrl)
    }

    @Test
    fun aReleaseNotNewerThanTheRunningBuildIsNotOffered() {
        val releases = """
            [
              {"tag_name":"1.8-fork.2","draft":false,"html_url":"https://example.test/a","assets":[]}
            ]
        """.trimIndent()

        assertNull(DesktopUpdateChecker.newerRelease(releases, "1.8-fork.2"))
        assertNull(DesktopUpdateChecker.newerRelease(releases, "1.8-fork.3"))
    }

    @Test
    fun draftsAndRollingBuildsAloneOfferNothing() {
        val releases = """
            [
              {"tag_name":"1.9-fork.1","draft":true,"html_url":"https://example.test/draft","assets":[]},
              {"tag_name":"build-main","draft":false,"html_url":"https://example.test/build","assets":[]}
            ]
        """.trimIndent()

        assertNull(DesktopUpdateChecker.newerRelease(releases, "1.8-beta1"))
    }

    @Test
    fun aListThatIsNotAnArrayOffersNothing() {
        assertNull(DesktopUpdateChecker.newerRelease("""{"message":"Not Found"}""", "1.8-beta1"))
    }
}
