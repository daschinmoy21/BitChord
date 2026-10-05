package com.music.bitchord.desktop

import androidx.compose.ui.focus.FocusManager
import com.music.bitchord.ui.components.TextEntryFocus
import java.awt.event.KeyEvent
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

/** The pieces that keep a Space (and its typed echo) from reaching what it was taken from. */
class DesktopSpaceKeystrokeTest {

    @Test
    fun `a typed event has no key code, so it is told apart by its character`() {
        assertTrue(DesktopGlobalKeys.isPartOfSpace(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, ' '))
        assertFalse(DesktopGlobalKeys.isPartOfSpace(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'a'))
    }

    @Test
    fun `the typed echo of a zoom key is taken, and nothing else`() {
        "=+-0".forEach { assertTrue(DesktopGlobalKeys.isZoomTyped(it, ctrl = true, alt = false, meta = false)) }
        // Without Ctrl they are ordinary text.
        "=+-0".forEach { assertFalse(DesktopGlobalKeys.isZoomTyped(it, ctrl = false, alt = false, meta = false)) }
        assertFalse(DesktopGlobalKeys.isZoomTyped('a', ctrl = true, alt = false, meta = false))
        assertFalse(DesktopGlobalKeys.isZoomTyped('=', ctrl = true, alt = true, meta = false))
        assertFalse(DesktopGlobalKeys.isZoomTyped('=', ctrl = true, alt = false, meta = true))
    }

    @Test
    fun `pressed and released events are told apart by their key code`() {
        assertTrue(DesktopGlobalKeys.isPartOfSpace(KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE, ' '))
        assertTrue(DesktopGlobalKeys.isPartOfSpace(KeyEvent.KEY_RELEASED, KeyEvent.VK_SPACE, ' '))
        assertFalse(DesktopGlobalKeys.isPartOfSpace(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a'))
    }
}

/** A press away from a text box lets go of the focus; a press on it keeps what it took. */
class TextEntryFocusReleaseTest {

    private var cleared = 0

    private val focusManager: FocusManager = Proxy.newProxyInstance(
        FocusManager::class.java.classLoader,
        arrayOf(FocusManager::class.java),
    ) { _, method, _ ->
        if (method.name == "clearFocus") cleared++
        if (method.returnType == java.lang.Boolean.TYPE) false else null
    } as FocusManager

    @Test
    fun `a press that is not on a text box clears the focus`() {
        TextEntryFocus.forgetPress()
        TextEntryFocus.releaseUnlessOnField(focusManager)
        assertEquals(1, cleared)
    }

    @Test
    fun `a press on a text box leaves the focus where it went`() {
        TextEntryFocus.forgetPress()
        TextEntryFocus.pressedOnField()
        TextEntryFocus.releaseUnlessOnField(focusManager)
        assertEquals(0, cleared)
    }

    @Test
    fun `a press on a text box only protects that press`() {
        TextEntryFocus.pressedOnField()
        TextEntryFocus.releaseUnlessOnField(focusManager)
        TextEntryFocus.releaseUnlessOnField(focusManager)
        assertEquals(1, cleared)
    }

    @Test
    fun `a mark left by a press the window never saw is forgotten when the next one begins`() {
        // A text box in a dialog: its press never reached the window's own listener.
        TextEntryFocus.pressedOnField()
        TextEntryFocus.forgetPress()
        TextEntryFocus.releaseUnlessOnField(focusManager)
        assertEquals(1, cleared)
    }
}
