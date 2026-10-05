package com.music.bitchord.desktop

import com.music.bitchord.ui.components.TextEntryFocus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/** What the whole-window keys can ask for. */
internal enum class DesktopGlobalAction { PLAY_PAUSE, ZOOM_IN, ZOOM_OUT, ZOOM_RESET }

/**
 * The keys that mean the same wherever the focus happens to be.
 *
 * Compose only hands a key to the part of the UI that has focus, and clicking an empty stretch of
 * page leaves nothing focused — or leaves the focus on whichever button was last pressed, which then
 * takes Space for itself. These are caught one layer lower, in AWT, before Compose sees them.
 */
internal object DesktopGlobalKeys {
    /** Set by the app: plays or pauses when a track is loaded, and says whether it did. */
    @Volatile
    var togglePlayPause: (() -> Boolean)? = null

    /** Which action [keyCode] with these modifiers asks for, or null when it asks for nothing here. */
    internal fun actionFor(
        keyCode: Int,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
        textEntryActive: Boolean,
    ): DesktopGlobalAction? {
        if (alt || meta) return null
        if (ctrl) {
            return when (keyCode) {
                KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> DesktopGlobalAction.ZOOM_IN
                KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> DesktopGlobalAction.ZOOM_OUT
                KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> DesktopGlobalAction.ZOOM_RESET
                else -> null
            }
        }
        // A space typed into a text box is a space.
        if (keyCode == KeyEvent.VK_SPACE && !textEntryActive) return DesktopGlobalAction.PLAY_PAUSE
        return null
    }

    private var spaceHeld = false

    private val dispatcher = KeyEventDispatcher { event ->
        val action = actionFor(
            keyCode = event.keyCode,
            ctrl = event.isControlDown,
            alt = event.isAltDown,
            meta = event.isMetaDown,
            textEntryActive = TextEntryFocus.active,
        )
        when (action) {
            null -> {
                // The release of a Space that was taken must be taken too, or the control that
                // would have had it sees half a keystroke.
                if (event.keyCode == KeyEvent.VK_SPACE && spaceHeld) {
                    if (event.id == KeyEvent.KEY_RELEASED) spaceHeld = false
                    true
                } else {
                    false
                }
            }
            DesktopGlobalAction.PLAY_PAUSE -> when (event.id) {
                KeyEvent.KEY_PRESSED -> {
                    val toggle = togglePlayPause
                    if (spaceHeld) {
                        // Key repeat: already taken.
                        true
                    } else if (toggle != null && toggle()) {
                        spaceHeld = true
                        true
                    } else {
                        false
                    }
                }
                KeyEvent.KEY_TYPED -> spaceHeld
                KeyEvent.KEY_RELEASED -> (spaceHeld).also { spaceHeld = false }
                else -> false
            }
            else -> {
                if (event.id == KeyEvent.KEY_PRESSED) {
                    when (action) {
                        DesktopGlobalAction.ZOOM_IN -> DesktopUiScale.zoomIn()
                        DesktopGlobalAction.ZOOM_OUT -> DesktopUiScale.zoomOut()
                        else -> DesktopUiScale.reset()
                    }
                }
                // Taken on every phase, so a text box never receives the + or - it was not meant to.
                true
            }
        }
    }

    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    }
}

/** How big the interface is drawn, changed with Ctrl and plus, minus or zero. */
internal object DesktopUiScale {
    private const val KEY = "ui_scale"
    internal val STEPS = listOf(0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

    private val persistence = DesktopPersistence()
    private val _scale = MutableStateFlow(nearestStep(persistence.string(KEY, "").toFloatOrNull() ?: 1f))

    val scale: StateFlow<Float> = _scale

    fun zoomIn() = set(STEPS.firstOrNull { it > _scale.value + EPSILON } ?: STEPS.last())

    fun zoomOut() = set(STEPS.lastOrNull { it < _scale.value - EPSILON } ?: STEPS.first())

    fun reset() = set(1f)

    private fun set(value: Float) {
        _scale.value = value
        persistence.saveString(KEY, value.toString())
    }

    internal fun nearestStep(value: Float): Float = STEPS.minByOrNull { kotlin.math.abs(it - value) } ?: 1f

    private const val EPSILON = 0.001f
}
