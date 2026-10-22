package com.music.bitchord.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Whether a text field currently has keyboard focus, for global shortcuts that must not swallow
 * what is being typed (the desktop's Space play/pause).
 */
object TextEntryFocus {
    private val focused = AtomicInteger(0)

    /** Set when the press being delivered right now landed on a text field; see [releaseUnlessOnField]. */
    private var pressOnField = false

    val active: Boolean get() = focused.get() > 0

    internal fun enter() { focused.incrementAndGet() }

    internal fun leave() { focused.updateAndGet { (it - 1).coerceAtLeast(0) } }

    fun pressedOnField() { pressOnField = true }

    fun forgetPress() { pressOnField = false }

    /**
     * A press somewhere in the window that is not on a text field lets go of the focus. A sidebar
     * item or button takes the click without taking the focus, and consumes it, so the box that was
     * being typed into stayed focused (and kept taking Space) after the user had clicked away.
     *
     * Called once per press, after the content has had its turn: [pressedOnField] has by then
     * said whether the press was on a field, and a field it was on keeps the focus it just took.
     */
    fun releaseUnlessOnField(focusManager: FocusManager) {
        val onField = pressOnField
        pressOnField = false
        if (!onField) focusManager.clearFocus()
    }
}

/**
 * Lets go of the focus held by a text field when the window is pressed anywhere else. Goes on the
 * window's root; each field reports its own presses through [reportsTextEntryFocus].
 */
fun Modifier.releasesTextEntryFocusOnOutsidePress(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) {
        coroutineScope {
            // Presses on a field inside a dialog never reach this window's root, so a mark they
            // left is dropped as each new press begins.
            launch {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) TextEntryFocus.forgetPress()
                    }
                }
            }
            launch {
                awaitPointerEventScope {
                    while (true) {
                        // Final: after every child, including the field that may have been pressed,
                        // has handled the event, whether or not one of them consumed it.
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.type == PointerEventType.Press) {
                            TextEntryFocus.releaseUnlessOnField(focusManager)
                        }
                    }
                }
            }
        }
    }

/** Marks a text field so [TextEntryFocus] knows while it, or anything inside it, has focus. */
fun Modifier.reportsTextEntryFocus(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    // Keyed on the value so leaving composition while focused (a dialog closing) still lets go.
    // The value is copied out first: [focused] is a state read, so inside onDispose it would give
    // the value the field has by then rather than the one this effect entered with, and a field
    // that lost the focus would never be let go of (the counter only ever went up).
    val hasFocus = focused
    DisposableEffect(hasFocus) {
        if (hasFocus) TextEntryFocus.enter()
        onDispose { if (hasFocus) TextEntryFocus.leave() }
    }
    onFocusChanged { focused = it.hasFocus }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Initial: ahead of the window's own look at the same press, in the Final pass.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) TextEntryFocus.pressedOnField()
                }
            }
        }
}
