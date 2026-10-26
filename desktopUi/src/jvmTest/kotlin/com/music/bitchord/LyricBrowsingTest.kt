package com.music.bitchord

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.music.bitchord.ui.player.LyricBrowsingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricBrowsingTest {
    @Test fun wheelAndTrackpadScrollingPauseFollowingWithoutConsumingScroll() {
        for (delta in listOf(-80f, 80f, -0.5f, 0.5f)) {
            val state = LyricBrowsingState()
            assertEquals(Offset.Zero, state.onPreScroll(Offset(0f, delta), NestedScrollSource.UserInput))
            assertTrue(state.isBrowsing)
        }
    }

    @Test fun automaticScrollDoesNotPauseFollowing() {
        val state = LyricBrowsingState()
        state.onPreScroll(Offset(0f, 80f), NestedScrollSource.SideEffect)
        state.onPreScroll(Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(state.isBrowsing)
    }

    @Test fun browsingPersistsUntilExplicitSync() {
        val state = LyricBrowsingState()
        state.browse()
        state.onPreScroll(Offset(0f, 80f), NestedScrollSource.SideEffect)
        assertTrue(state.isBrowsing)
        state.syncToAudio()
        assertFalse(state.isBrowsing)
        state.onPreScroll(Offset(0f, -80f), NestedScrollSource.SideEffect)
        assertFalse(state.isBrowsing)
        state.browse()
        assertTrue(state.isBrowsing)
    }
}
