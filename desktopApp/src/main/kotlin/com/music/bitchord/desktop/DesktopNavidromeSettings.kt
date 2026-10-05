package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** How a track that Navidrome holds is played. */
internal enum class DesktopNavidromeMode {
    /** Navidrome is not asked at all. */
    OFF,

    /**
     * Navidrome is asked first and given a few seconds to start the track; past that, playback
     * starts from the other sources, and Navidrome still takes over mid-track if it answers later.
     */
    FIRST,

    /** Playback starts from whichever source answers first, and Navidrome swaps in when it is ready. */
    RACE,
}

/** What to do on a metered connection (a phone hotspot, mobile data). */
internal enum class DesktopMeteredBehaviour {
    /** Leave Navidrome out and play from YouTube Music and the other sources. */
    OTHER_SOURCES,

    /** Still use Navidrome, but ask it for a smaller Opus re-encode. */
    TRANSCODE,

    /** No different from any other connection. */
    SAME,
}

/** What the playback path should do with Navidrome for the next track. */
internal data class DesktopNavidromePlan(
    val mode: DesktopNavidromeMode,
    val transcode: DesktopNavidromeTranscode?,
    val waitMs: Long,
) {
    val enabled: Boolean get() = mode != DesktopNavidromeMode.OFF
}

/** The rule that turns the listener's settings and their connection into a [DesktopNavidromePlan]. */
internal fun navidromePlan(
    mode: DesktopNavidromeMode,
    waitSeconds: Int,
    metered: Boolean,
    onMetered: DesktopMeteredBehaviour,
    meteredKbps: Int,
): DesktopNavidromePlan {
    val waitMs = waitSeconds.coerceIn(DesktopNavidromeSettings.MIN_WAIT_SECONDS, DesktopNavidromeSettings.MAX_WAIT_SECONDS) * 1_000L
    if (!metered) return DesktopNavidromePlan(mode, null, waitMs)
    return when (onMetered) {
        DesktopMeteredBehaviour.SAME -> DesktopNavidromePlan(mode, null, waitMs)
        DesktopMeteredBehaviour.OTHER_SOURCES -> DesktopNavidromePlan(DesktopNavidromeMode.OFF, null, waitMs)
        DesktopMeteredBehaviour.TRANSCODE ->
            DesktopNavidromePlan(mode, DesktopNavidromeTranscode("opus", meteredKbps), waitMs)
    }
}

/** The Navidrome preferences. */
internal object DesktopNavidromeSettings {
    const val MIN_WAIT_SECONDS = 1
    const val MAX_WAIT_SECONDS = 30
    const val DEFAULT_WAIT_SECONDS = 5
    const val DEFAULT_METERED_KBPS = 96

    private const val KEY_MODE = "navidrome_mode"
    private const val KEY_WAIT = "navidrome_wait_seconds"
    private const val KEY_METERED = "navidrome_on_metered"
    private const val KEY_METERED_KBPS = "navidrome_metered_kbps"
    private const val KEY_FORCE_METERED = "navidrome_force_metered"
    private const val KEY_REPORT = "navidrome_report_plays"

    private val persistence = DesktopPersistence()

    private val _mode = MutableStateFlow(
        enumOr(persistence.string(KEY_MODE, ""), DesktopNavidromeMode.FIRST),
    )
    private val _wait = MutableStateFlow(persistence.int(KEY_WAIT, DEFAULT_WAIT_SECONDS))
    private val _onMetered = MutableStateFlow(
        enumOr(persistence.string(KEY_METERED, ""), DesktopMeteredBehaviour.OTHER_SOURCES),
    )
    private val _meteredKbps = MutableStateFlow(persistence.int(KEY_METERED_KBPS, DEFAULT_METERED_KBPS))
    private val _forceMetered = MutableStateFlow(persistence.boolean(KEY_FORCE_METERED, false))
    private val _reportPlays = MutableStateFlow(persistence.boolean(KEY_REPORT, true))

    val mode: StateFlow<DesktopNavidromeMode> = _mode
    val waitSeconds: StateFlow<Int> = _wait
    val onMetered: StateFlow<DesktopMeteredBehaviour> = _onMetered
    val meteredKbps: StateFlow<Int> = _meteredKbps

    /** "Treat this connection as metered", for when the system does not say so itself. */
    val forceMetered: StateFlow<Boolean> = _forceMetered

    /** Whether plays from Navidrome are reported to it (and kept out of Last.fm and ListenBrainz). */
    val reportPlays: StateFlow<Boolean> = _reportPlays

    fun setMode(value: DesktopNavidromeMode) {
        _mode.value = value
        persistence.saveString(KEY_MODE, value.name)
    }

    fun setWaitSeconds(value: Int) {
        val clamped = value.coerceIn(MIN_WAIT_SECONDS, MAX_WAIT_SECONDS)
        _wait.value = clamped
        persistence.saveInt(KEY_WAIT, clamped)
    }

    fun setOnMetered(value: DesktopMeteredBehaviour) {
        _onMetered.value = value
        persistence.saveString(KEY_METERED, value.name)
    }

    fun setMeteredKbps(value: Int) {
        val clamped = value.coerceIn(32, 320)
        _meteredKbps.value = clamped
        persistence.saveInt(KEY_METERED_KBPS, clamped)
    }

    fun setForceMetered(value: Boolean) {
        _forceMetered.value = value
        persistence.saveBoolean(KEY_FORCE_METERED, value)
    }

    fun setReportPlays(value: Boolean) {
        _reportPlays.value = value
        persistence.saveBoolean(KEY_REPORT, value)
    }

    /** The plan for a track about to be resolved, given the connection as it is now. */
    fun plan(): DesktopNavidromePlan = navidromePlan(
        mode = _mode.value,
        waitSeconds = _wait.value,
        metered = _forceMetered.value || DesktopNetwork.isMetered(),
        onMetered = _onMetered.value,
        meteredKbps = _meteredKbps.value,
    )

    private inline fun <reified T : Enum<T>> enumOr(raw: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default
}

/**
 * Where a Navidrome login is kept: the keyring first, a preference only when there is none — the
 * same arrangement as the YouTube session. Only the salted token is stored, never the password.
 */
internal object DesktopNavidromeCredentialStore {
    private val persistence = DesktopPersistence()

    private fun attributes(sourceId: String) = mapOf("application" to "bitchord-navidrome", "source" to sourceId)

    private fun fallbackKey(sourceId: String) = "navidrome_login_$sourceId"

    fun save(config: DesktopSourceConfig, credentials: DesktopNavidromeCredentials) {
        val packed = "${credentials.salt}\n${credentials.token}"
        val stored = DesktopSecretStore.store(
            label = "BitChord — Navidrome login",
            attributes = attributes(config.id),
            secret = packed.toByteArray(Charsets.UTF_8),
        )
        if (stored) {
            DesktopPreferenceChunks.remove(persistence.preferences, fallbackKey(config.id))
        } else {
            DesktopTrackLog.log("no keyring available; keeping the Navidrome login in preferences instead")
            DesktopPreferenceChunks.write(persistence.preferences, fallbackKey(config.id), packed)
        }
        DesktopNavidromeSource.forget(config.id)
    }

    fun load(config: DesktopSourceConfig): DesktopNavidromeCredentials? {
        if (config.username.isBlank()) return null
        val packed = DesktopSecretStore.lookup(attributes(config.id))?.toString(Charsets.UTF_8)
            ?: DesktopPreferenceChunks.read(persistence.preferences, fallbackKey(config.id))
            ?: return null
        val (salt, token) = packed.split('\n', limit = 2).takeIf { it.size == 2 } ?: return null
        if (salt.isBlank() || token.isBlank()) return null
        return DesktopNavidromeCredentials(config.username, salt, token)
    }

    fun remove(sourceId: String) {
        DesktopSecretStore.remove(attributes(sourceId))
        DesktopPreferenceChunks.remove(persistence.preferences, fallbackKey(sourceId))
        DesktopNavidromeSource.forget(sourceId)
    }
}

/**
 * Whether the connection in use is one the system counts as metered.
 *
 * NetworkManager's `Metered` is how Linux says it: phone hotspots are marked on their own, and a
 * connection can be marked by hand. Without NetworkManager, or off Linux, the answer is no, and
 * [DesktopNavidromeSettings.forceMetered] is the way to say otherwise.
 */
internal object DesktopNetwork {
    private const val REFRESH_MS = 15_000L

    @Volatile private var metered = false
    private val firstRead = CountDownLatch(1)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isMetered(): Boolean {
        if (!DesktopPlatform.isLinux) return false
        if (started.compareAndSet(false, true)) {
            Thread(::watch, "bitchord-metered").apply { isDaemon = true }.start()
        }
        // The very first answer is worth a moment; after that this is a plain read.
        firstRead.await(1, TimeUnit.SECONDS)
        return metered
    }

    private fun watch() {
        while (true) {
            metered = runCatching { readNetworkManager() }.getOrDefault(false)
            firstRead.countDown()
            Thread.sleep(REFRESH_MS)
        }
    }

    private fun readNetworkManager(): Boolean =
        DBusConnectionBuilder.forSystemBus().withShared(false).build().use { bus ->
            val manager = bus.getRemoteObject(
                "org.freedesktop.NetworkManager",
                "/org/freedesktop/NetworkManager",
                Properties::class.java,
            )
            isMeteredValue(manager.Get<UInt32>("org.freedesktop.NetworkManager", "Metered").toLong())
        }

    /** NM_METERED_YES = 1 and NM_METERED_GUESS_YES = 3. */
    internal fun isMeteredValue(value: Long): Boolean = value == 1L || value == 3L
}
