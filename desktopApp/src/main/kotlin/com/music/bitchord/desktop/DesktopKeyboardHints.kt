package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** How a layout takes part in the keyboard hints; see [hintBarrier] and [hintSkip]. */
private enum class DesktopHintRole { BARRIER, SKIP }

private val DesktopHintRoleKey = SemanticsPropertyKey<DesktopHintRole>("DesktopHintRole")

/**
 * Marks a layer that covers what was drawn before it — a dialog's scrim, the player sheet — so the
 * hints label only what is on it and above it.
 */
internal fun Modifier.hintBarrier(): Modifier = semantics { this[DesktopHintRoleKey] = DesktopHintRole.BARRIER }

/** Marks a click target that does nothing worth a hint, such as a panel's own background. */
internal fun Modifier.hintSkip(): Modifier = semantics { this[DesktopHintRoleKey] = DesktopHintRole.SKIP }

/**
 * Clicking with the keyboard, the way Vimium does in a browser.
 *
 * Ctrl pressed and let go on its own puts a short label on everything on screen that can be
 * clicked, and the sidebar shows the digit that opens each page with Ctrl. Typing a label clicks
 * it, Backspace takes a letter back, and Esc, a click or Ctrl again puts the labels away; Ctrl and a
 * digit still works while they are up. The targets come from the semantics tree, the same one
 * screen readers walk, so nothing has to opt in.
 */
internal object DesktopKeyboardHints {

    internal class Hint(val label: String, val bounds: Rect, val click: () -> Unit)

    private val _hints = MutableStateFlow<List<Hint>>(emptyList())

    /** The labels on screen; empty when the hints are not up. */
    val hints: StateFlow<List<Hint>> = _hints

    private val _typed = MutableStateFlow("")

    /** What has been typed towards a label so far. */
    val typed: StateFlow<String> = _typed

    // Home row first, as Vimium does, so the commonest labels are the easiest to type.
    private const val ALPHABET = "asdfjklghqweruiopzxcvnmbty"

    // Ctrl let go within this long, with nothing pressed in between, is a tap.
    private const val TAP_MS = 400L

    private var window: ComposeWindow? = null
    private var ctrlDownAt = 0L
    private var ctrlAlone = false

    fun install(window: ComposeWindow) {
        if (this.window != null) return
        this.window = window
        // A click or a scroll with Ctrl down is Ctrl being used for something else, and a window
        // that loses the focus may never hear Ctrl come back up.
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { event ->
                when (event.id) {
                    WindowEvent.WINDOW_LOST_FOCUS -> {
                        resetCtrl()
                        dismiss()
                    }
                    MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL -> ctrlAlone = false
                }
            },
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK,
        )
    }

    /** Takes the keys while the hints are up, and watches Ctrl; on the UI thread. */
    fun onKey(event: KeyEvent): Boolean {
        trackCtrl(event)
        if (_hints.value.isEmpty()) return false
        // Ctrl with another key is a shortcut of its own (a digit, K, a zoom): the labels give way to it.
        if (event.id == KeyEvent.KEY_PRESSED && event.keyCode != KeyEvent.VK_CONTROL && event.isControlDown) {
            dismiss()
            return false
        }
        if (event.id != KeyEvent.KEY_PRESSED) return true
        when {
            event.keyCode == KeyEvent.VK_ESCAPE -> dismiss()
            event.keyCode == KeyEvent.VK_BACK_SPACE -> _typed.value = _typed.value.dropLast(1)
            event.keyCode in KeyEvent.VK_A..KeyEvent.VK_Z && event.modifiersEx == 0 -> {
                val typed = _typed.value + (event.keyCode - KeyEvent.VK_A + 'a'.code).toChar()
                val matching = _hints.value.filter { it.label.startsWith(typed) }
                val exact = matching.singleOrNull { it.label == typed }
                when {
                    exact != null -> {
                        dismiss()
                        exact.click()
                    }
                    // A letter no label goes on with is left out, rather than ending the hints.
                    matching.isNotEmpty() -> _typed.value = typed
                }
            }
        }
        return true
    }

    private fun trackCtrl(event: KeyEvent) {
        when {
            event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_CONTROL -> {
                // Held down, the key repeats; only the first press counts.
                if (ctrlDownAt == 0L) {
                    ctrlDownAt = event.`when`
                    ctrlAlone = event.modifiersEx == InputEvent.CTRL_DOWN_MASK
                }
            }
            event.id == KeyEvent.KEY_PRESSED -> ctrlAlone = false
            event.id == KeyEvent.KEY_RELEASED && event.keyCode == KeyEvent.VK_CONTROL -> {
                val tapped = ctrlAlone && ctrlDownAt != 0L && event.`when` - ctrlDownAt < TAP_MS
                resetCtrl()
                if (tapped) {
                    if (_hints.value.isEmpty()) show() else dismiss()
                }
            }
        }
    }

    private fun resetCtrl() {
        ctrlDownAt = 0L
        ctrlAlone = false
    }

    fun dismiss() {
        _hints.value = emptyList()
        _typed.value = ""
    }

    private fun show() {
        val targets = visibleNodes()
            .filter { node ->
                val config = node.config
                config.getOrNull(SemanticsActions.OnClick)?.action != null &&
                    !config.contains(SemanticsProperties.Disabled) &&
                    config.getOrNull(DesktopHintRoleKey) == null
            }
            .sortedWith(compareBy({ it.boundsInWindow.top.roundToInt() }, { it.boundsInWindow.left }))
        if (targets.isEmpty()) return
        val labels = labels(targets.size)
        _typed.value = ""
        _hints.value = targets.zip(labels) { node, label ->
            Hint(label, node.boundsInWindow) { node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() }
        }
    }

    /** Scrolls the biggest scrollable area on screen by [fraction] of its height; true if it moved. */
    fun scroll(fraction: Float): Boolean {
        val target = visibleNodes()
            .filter { node ->
                node.config.getOrNull(SemanticsActions.ScrollBy)?.action != null &&
                    (node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f) > 0f
            }
            .maxByOrNull { it.boundsInWindow.width * it.boundsInWindow.height }
            ?: return false
        val dy = target.boundsInWindow.height * fraction
        target.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, dy)
        return true
    }

    /**
     * Every node that can be seen: the semantics trees in the order they are drawn (popups after the
     * window's own), from the last [hintBarrier] on, with nothing scrolled or clipped out of view.
     */
    // The window's semantics owners are the same list accessibility and test tools read.
    @OptIn(ComposeToolingApi::class)
    private fun visibleNodes(): List<SemanticsNode> {
        val window = window ?: return emptyList()
        val ordered = mutableListOf<SemanticsNode>()
        fun visit(node: SemanticsNode) {
            ordered += node
            node.children.forEach(::visit)
        }
        runCatching { window.semanticsOwners.forEach { visit(it.rootSemanticsNode) } }
        val barrier = ordered.indexOfLast { it.config.getOrNull(DesktopHintRoleKey) == DesktopHintRole.BARRIER }
        return ordered.drop(barrier.coerceAtLeast(0)).filter { node ->
            val bounds = node.boundsInWindow
            bounds.width >= 2f && bounds.height >= 2f
        }
    }

    /** [count] labels of equal length, so none is the start of another. */
    internal fun labels(count: Int): List<String> {
        if (count <= ALPHABET.length) return ALPHABET.take(count).map { it.toString() }
        return ALPHABET.flatMap { first -> ALPHABET.map { second -> "$first$second" } }.take(count)
    }
}

/** The labels, drawn over everything else in the window while the hints are up. */
@Composable
internal fun DesktopKeyboardHintsOverlay() {
    val hints by DesktopKeyboardHints.hints.collectAsState()
    val typed by DesktopKeyboardHints.typed.collectAsState()
    if (hints.isEmpty()) return
    Popup(
        popupPositionProvider = WindowOrigin,
        onDismissRequest = DesktopKeyboardHints::dismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // A click anywhere puts the labels away; it does not go on to what is under it.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        DesktopKeyboardHints.dismiss()
                    }
                },
        ) {
            hints.filter { it.label.startsWith(typed) }.forEach { hint ->
                DesktopKeyChip(
                    // What has been typed so far fades, so the eye goes to the letter still to type.
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f))) { append(typed.uppercase()) }
                        append(hint.label.drop(typed.length).uppercase())
                    },
                    Modifier.offset { IntOffset(hint.bounds.left.roundToInt(), hint.bounds.top.roundToInt()) },
                )
            }
        }
    }
}

/** Puts the popup's top-left corner on the window's. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/**
 * The small key cap the keyboard hints and the sidebar's Ctrl digits are drawn in: dark enough to
 * read over artwork, in the window's own greys rather than a colour of its own.
 */
@Composable
internal fun DesktopKeyChip(text: AnnotatedString, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Text(
        text,
        modifier = modifier
            .background(Color(0xF0262626), shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
internal fun DesktopKeyChip(text: String, modifier: Modifier = Modifier) =
    DesktopKeyChip(AnnotatedString(text), modifier)
