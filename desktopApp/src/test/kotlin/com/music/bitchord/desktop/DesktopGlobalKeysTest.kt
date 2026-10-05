package com.music.bitchord.desktop

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which keys the whole window answers to, and which it leaves to whatever has the focus. */
class DesktopGlobalKeysTest {

    private fun action(
        key: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
        typing: Boolean = false,
    ) = DesktopGlobalKeys.actionFor(key, ctrl, alt, meta, typing)

    @Test
    fun `space plays and pauses`() {
        assertEquals(DesktopGlobalAction.PLAY_PAUSE, action(KeyEvent.VK_SPACE))
    }

    @Test
    fun `space typed into a text box stays a space`() {
        assertNull(action(KeyEvent.VK_SPACE, typing = true))
    }

    @Test
    fun `space with a modifier is somebody else's shortcut`() {
        assertNull(action(KeyEvent.VK_SPACE, ctrl = true))
        assertNull(action(KeyEvent.VK_SPACE, alt = true))
        assertNull(action(KeyEvent.VK_SPACE, meta = true))
    }

    @Test
    fun `ctrl with plus, minus or zero resizes the interface, on any layout's keys`() {
        listOf(KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD).forEach {
            assertEquals(DesktopGlobalAction.ZOOM_IN, action(it, ctrl = true))
        }
        listOf(KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT).forEach {
            assertEquals(DesktopGlobalAction.ZOOM_OUT, action(it, ctrl = true))
        }
        listOf(KeyEvent.VK_0, KeyEvent.VK_NUMPAD0).forEach {
            assertEquals(DesktopGlobalAction.ZOOM_RESET, action(it, ctrl = true))
        }
    }

    @Test
    fun `resizing works while typing, since ctrl combinations type nothing`() {
        assertEquals(DesktopGlobalAction.ZOOM_IN, action(KeyEvent.VK_EQUALS, ctrl = true, typing = true))
    }

    @Test
    fun `plus and minus alone are left to the text they type`() {
        assertNull(action(KeyEvent.VK_EQUALS))
        assertNull(action(KeyEvent.VK_MINUS))
        assertNull(action(KeyEvent.VK_0))
    }

    @Test
    fun `other keys are not taken`() {
        assertNull(action(KeyEvent.VK_A))
        assertNull(action(KeyEvent.VK_ENTER))
        assertNull(action(KeyEvent.VK_ESCAPE))
        assertNull(action(KeyEvent.VK_MINUS, ctrl = true, alt = true))
    }

    @Test
    fun `a stored size that is not one of the steps lands on the nearest`() {
        assertEquals(1.0f, DesktopUiScale.nearestStep(1.02f))
        assertEquals(1.5f, DesktopUiScale.nearestStep(1.4f))
        assertEquals(0.7f, DesktopUiScale.nearestStep(0.1f))
        assertEquals(2.0f, DesktopUiScale.nearestStep(9f))
    }

    @Test
    fun `the steps run from small to large and include normal size`() {
        assertTrue(DesktopUiScale.STEPS.contains(1.0f))
        assertEquals(DesktopUiScale.STEPS.sorted(), DesktopUiScale.STEPS)
    }
}
