package com.music.bitchord.desktop

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import java.util.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSessionRestoreTest {
    private val song = Song(videoId = "saved", title = "Title", artist = "Artist", thumbnailUrl = null, durationText = "3:00")

    @Test
    fun `restore stays paused and first play loads at the saved or seeked position`() {
        val scope = CoroutineScope(Job().apply { cancel() })
        val engine = DesktopPlaybackEngine(onEnded = {}, scope = scope)
        try {
            engine.restorePaused(song, 42_000L)
            assertEquals(song, engine.state.value.song)
            assertEquals(42_000L, engine.state.value.positionMs)
            assertFalse(engine.state.value.isPlaying)
            assertFalse(engine.state.value.isLoading)
            engine.play()
            assertTrue(engine.state.value.isLoading)
            assertEquals(42_000L, engine.state.value.positionMs)
            engine.restorePaused(song, 42_000L)
            engine.seekTo(20_000L)
            assertFalse(engine.state.value.isLoading)
            engine.play()
            assertEquals(20_000L, engine.state.value.positionMs)
            engine.restorePaused(song, 42_000L)
            engine.seekTo(-1L)
            assertEquals(0L, engine.state.value.positionMs)
        } finally { engine.shutdown() }
    }

    @Test
    fun `loading another song clears the restored slot`() {
        val engine = DesktopPlaybackEngine(onEnded = {}, scope = CoroutineScope(Job().apply { cancel() }))
        try {
            engine.restorePaused(song, 42_000L)
            val other = song.copy(videoId = "other")
            engine.load(other, playWhenReady = false)
            engine.play()
            assertEquals(other, engine.state.value.song)
            assertEquals(0L, engine.state.value.positionMs)
            assertTrue(engine.state.value.isPlaying)
        } finally { engine.shutdown() }
    }

    @Test
    fun `positions reject stale negative or damaged saved values`() = withStore { store ->
        store.savePosition(song.videoId, 42_000L)
        assertEquals(42_000L, store.savedPosition(song.videoId))
        assertEquals(0L, store.savedPosition("other"))
        for (bad in listOf("garbage", "%%%|42", "c2F2ZWQ|bad", "c2F2ZWQ|-10")) {
            store.saveString("queue_position", bad)
            assertEquals(0L, store.savedPosition(song.videoId))
        }
    }

    @Test
    fun `queue metadata round trips while old queue and history rows keep their defaults`() = withStore { store ->
        val queued = song.copy(queueTier = QueueTier.USER_QUEUE, queueEntryId = "entry",
            playbackSource = "Album", playbackSourceType = PlaybackSourceType.BROWSE, playbackSourceId = "album")
        store.saveQueue(listOf(queued))
        assertEquals(queued, store.queue().single())
        val old = store.string("queue").split("|").take(18).joinToString("|")
        store.saveString("queue", old)
        assertEquals(song, store.queue().single())
        store.saveHistory(listOf(queued))
        assertNull(store.history().single().queueEntryId)
        assertNull(store.history().single().playbackSource)
    }

    private fun withStore(block: (DesktopPersistence) -> Unit) {
        val node = Preferences.userRoot().node("bitchord-test-restore-${System.nanoTime()}")
        try { block(DesktopPersistence(node)) } finally { node.removeNode() }
    }
}
