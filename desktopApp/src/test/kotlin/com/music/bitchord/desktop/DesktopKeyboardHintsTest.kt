package com.music.bitchord.desktop

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The keyboard's way around the window: Ctrl and a digit, j and k, and the hint labels. */
class DesktopKeyboardHintsTest {

    private fun action(key: Int, ctrl: Boolean = false, typing: Boolean = false, shift: Boolean = false) =
        DesktopGlobalKeys.actionFor(key, ctrl, alt = false, meta = false, textEntryActive = typing, shift = shift)

    @Test
    fun `ctrl and a digit opens a page, even from a text box`() {
        (KeyEvent.VK_1..KeyEvent.VK_9).forEach {
            assertEquals(DesktopGlobalAction.NAVIGATE, action(it, ctrl = true))
            assertEquals(DesktopGlobalAction.NAVIGATE, action(it, ctrl = true, typing = true))
        }
        assertNull(action(KeyEvent.VK_1, ctrl = true, shift = true))
        // Ctrl and 0 stays the zoom reset.
        assertEquals(DesktopGlobalAction.ZOOM_RESET, action(KeyEvent.VK_0, ctrl = true))
        // A digit alone is left to whatever has the focus.
        assertNull(action(KeyEvent.VK_1))
    }

    @Test
    fun `j k d and u scroll, but not while typing`() {
        assertEquals(DesktopGlobalAction.SCROLL_DOWN, action(KeyEvent.VK_J))
        assertEquals(DesktopGlobalAction.SCROLL_UP, action(KeyEvent.VK_K))
        assertEquals(DesktopGlobalAction.PAGE_DOWN, action(KeyEvent.VK_D))
        assertEquals(DesktopGlobalAction.PAGE_UP, action(KeyEvent.VK_U))
        listOf(KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_D, KeyEvent.VK_U).forEach {
            assertNull(action(it, typing = true))
        }
        // Ctrl and K is still the search box.
        assertEquals(DesktopGlobalAction.FOCUS_SEARCH, action(KeyEvent.VK_K, ctrl = true))
    }

    @Test
    fun `shift with h or l goes back or forward, plain h and l do nothing`() {
        assertEquals(DesktopGlobalAction.BACK, action(KeyEvent.VK_H, shift = true))
        assertEquals(DesktopGlobalAction.FORWARD, action(KeyEvent.VK_L, shift = true))
        assertNull(action(KeyEvent.VK_H))
        assertNull(action(KeyEvent.VK_L))
        assertNull(action(KeyEvent.VK_H, shift = true, typing = true))
    }

    @Test
    fun `labels are distinct and none starts another`() {
        listOf(1, 26, 27, 300).forEach { count ->
            val labels = DesktopKeyboardHints.labels(count)
            assertEquals(count, labels.size)
            assertEquals(count, labels.toSet().size)
            labels.forEach { label ->
                assertFalse(labels.any { it != label && it.startsWith(label) }, "$label starts another")
            }
        }
    }
}
