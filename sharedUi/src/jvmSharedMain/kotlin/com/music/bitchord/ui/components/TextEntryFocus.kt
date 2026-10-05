package com.music.bitchord.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether a text field currently has keyboard focus, for global shortcuts that must not swallow
 * what is being typed (the desktop's Space play/pause).
 */
object TextEntryFocus {
    private val focused = AtomicInteger(0)

    val active: Boolean get() = focused.get() > 0

    internal fun enter() { focused.incrementAndGet() }

    internal fun leave() { focused.updateAndGet { (it - 1).coerceAtLeast(0) } }
}

/** Marks a text field so [TextEntryFocus] knows while it, or anything inside it, has focus. */
fun Modifier.reportsTextEntryFocus(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    // Keyed on [focused] so leaving composition while focused (a dialog closing) still lets go.
    DisposableEffect(focused) {
        if (focused) TextEntryFocus.enter()
        onDispose { if (focused) TextEntryFocus.leave() }
    }
    onFocusChanged { focused = it.hasFocus }
}
