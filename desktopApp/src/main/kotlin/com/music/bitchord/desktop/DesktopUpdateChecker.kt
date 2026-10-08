package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Looks at this fork's GitHub releases once per launch and says whether one is newer than the
 * running build. The installer is not run from here: the dialog opens the asset in the browser, so
 * the user installs it the way they installed the first one.
 *
 * The releases list is read rather than "latest", because the fork publishes its versioned builds
 * as pre-releases, which "latest" skips. Its rolling `build-*` pre-releases carry no version
 * number, so they are never offered.
 */
internal object DesktopUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        /** The installer for this platform, or null when the release has none. */
        val downloadUrl: String?,
        val notes: String?,
    )

    /**
     * The repository whose releases this build is offered. It must be this fork: upstream's
     * installer would replace the fork's build with a different program. Change it if the fork moves.
     */
    private const val REPOSITORY = "daschinmoy21/BitChord"

    private const val RELEASES_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"

    val currentVersion: String = System.getProperty("bitchord.version") ?: "1.8-beta1"

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun check(): UpdateInfo? = runCatching {
        val response = http.get(RELEASES_URL) {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return@runCatching null
        newerRelease(response.bodyAsText(), currentVersion)
    }.getOrNull()

    /**
     * The newest versioned, non-draft release in [releasesJson] that is newer than [current], or
     * null when there is none. Tags that are not versions are skipped rather than guessed at.
     */
    internal fun newerRelease(releasesJson: String, current: String): UpdateInfo? {
        val releases = json.parseToJsonElement(releasesJson) as? JsonArray ?: return null
        val newest = releases.mapNotNull { element ->
            val release = element as? JsonObject ?: return@mapNotNull null
            if (release["draft"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val version = Version.parse(tag) ?: return@mapNotNull null
            Triple(release, tag, version)
        }.maxByOrNull { it.third } ?: return null
        val (release, tag, _) = newest
        val latest = tag.removePrefix("v")
        if (!isNewer(latest, current)) return null
        return UpdateInfo(
            version = latest,
            releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return null,
            downloadUrl = installerUrl(release),
            notes = release["body"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /**
     * The release names its files `BitChord-<version>-windows-x64-setup.exe` and
     * `BitChord-<version>-linux-x86_64.AppImage` (see .github/workflows/release.yml); match on the
     * platform part so a version change does not matter.
     */
    private fun installerUrl(release: JsonObject): String? {
        val suffixes = when {
            DesktopPlatform.isWindows -> listOf("-windows-x64-setup.exe")
            DesktopPlatform.isLinux -> listOf(".AppImage", "-linux-amd64.deb")
            else -> return null
        }
        val assets = release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        for (suffix in suffixes) {
            val hit = assets.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(suffix, ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            hit?.get("browser_download_url")?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        return null
    }

    /** Whether [latest] is a strictly newer version than [current]. Anything unparseable is not. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = Version.parse(latest) ?: return false
        val c = Version.parse(current) ?: return false
        return l > c
    }

    /**
     * A version as this project writes them: `1.8`, `v1.8`, the fork's `1.8-fork.N`, or a `-betaN`
     * build. Numbers compare as numbers, so 1.10 is newer than 1.9. At the same base a beta comes
     * before the plain release, and a fork build comes after it, because it is that release with
     * this fork's changes on top. Fork builds compare by their N.
     */
    private class Version(val parts: List<Int>, val stage: Int, val build: Int) : Comparable<Version> {

        override fun compareTo(other: Version): Int {
            for (i in 0 until maxOf(parts.size, other.parts.size)) {
                val diff = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return compareValuesBy(this, other, { it.stage }, { it.build })
        }

        companion object {
            private const val BETA = 0
            private const val PLAIN = 1
            private const val FORK = 2

            private val pattern = Regex("""v?(\d+(?:\.\d+)*)(?:-(beta|fork)\.?(\d+))?""")

            fun parse(raw: String): Version? {
                val match = pattern.matchEntire(raw.trim()) ?: return null
                val parts = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
                val stage = when (match.groupValues[2]) {
                    "" -> PLAIN
                    "fork" -> FORK
                    else -> BETA
                }
                val build = match.groupValues[3].toIntOrNull() ?: 0
                return Version(parts, stage, build)
            }
        }
    }
}
