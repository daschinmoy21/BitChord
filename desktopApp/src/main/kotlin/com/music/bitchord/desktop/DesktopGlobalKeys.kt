package com.music.bitchord.desktop

import com.music.bitchord.ui.components.TextEntryFocus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/** What the whole-window keys can ask for. */
internal enum class DesktopGlobalAction(
    /** Whether holding the key keeps asking, as for seeking and volume, rather than once per press. */
    val repeats: Boolean = false,
    /** Whether the keystroke would also type a character into whatever has the focus. */
    val typesCharacter: Boolean = false,
) {
    PLAY_PAUSE,
    ZOOM_IN,
    ZOOM_OUT,
    ZOOM_RESET,
    SEEK_BACK(repeats = true),
    SEEK_FORWARD(repeats = true),
    SEEK_BACK_LONG(repeats = true),
    SEEK_FORWARD_LONG(repeats = true),
    PREVIOUS,
    NEXT,
    VOLUME_UP(repeats = true),
    VOLUME_DOWN(repeats = true),
    MUTE(typesCharacter = true),
    FOCUS_SEARCH(typesCharacter = true),
    SHOW_SHORTCUTS(typesCharacter = true),
}

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

    /**
     * Set by the app, each answering whether it did anything (nothing loaded, or a party that has
     * locked the controls, answers false and the key is left alone). Run on the UI thread.
     */
    @Volatile
    var seekBy: ((deltaMs: Long) -> Boolean)? = null

    @Volatile
    var skipPrevious: (() -> Boolean)? = null

    @Volatile
    var skipNext: (() -> Boolean)? = null

    @Volatile
    var stepVolume: ((delta: Float) -> Boolean)? = null

    @Volatile
    var toggleMute: (() -> Boolean)? = null

    @Volatile
    var focusSearch: (() -> Boolean)? = null

    @Volatile
    var showShortcuts: (() -> Boolean)? = null

    /** Which action [keyCode] with these modifiers asks for, or null when it asks for nothing here. */
    internal fun actionFor(
        keyCode: Int,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
        textEntryActive: Boolean,
        shift: Boolean = false,
    ): DesktopGlobalAction? {
        if (alt || meta) return null
        if (ctrl) {
            return when (keyCode) {
                KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> DesktopGlobalAction.ZOOM_IN
                KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> DesktopGlobalAction.ZOOM_OUT
                KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> DesktopGlobalAction.ZOOM_RESET
                // Ctrl+K types nothing, so it is the way to the search box from inside another one.
                KeyEvent.VK_K -> if (shift) null else DesktopGlobalAction.FOCUS_SEARCH
                // Ctrl with the arrows moves a caret by words, and a slider by more.
                KeyEvent.VK_LEFT -> if (textEntryActive || shift) null else DesktopGlobalAction.PREVIOUS
                KeyEvent.VK_RIGHT -> if (textEntryActive || shift) null else DesktopGlobalAction.NEXT
                else -> null
            }
        }
        // A space, an "m" or an arrow typed into a text box belongs to the box.
        if (textEntryActive) return null
        return when (keyCode) {
            KeyEvent.VK_SPACE -> DesktopGlobalAction.PLAY_PAUSE
            KeyEvent.VK_LEFT ->
                if (shift) DesktopGlobalAction.SEEK_BACK_LONG else DesktopGlobalAction.SEEK_BACK
            KeyEvent.VK_RIGHT ->
                if (shift) DesktopGlobalAction.SEEK_FORWARD_LONG else DesktopGlobalAction.SEEK_FORWARD
            KeyEvent.VK_UP -> if (shift) null else DesktopGlobalAction.VOLUME_UP
            KeyEvent.VK_DOWN -> if (shift) null else DesktopGlobalAction.VOLUME_DOWN
            KeyEvent.VK_M -> if (shift) null else DesktopGlobalAction.MUTE
            // "?" is Shift and the slash key.
            KeyEvent.VK_SLASH ->
                if (shift) DesktopGlobalAction.SHOW_SHORTCUTS else DesktopGlobalAction.FOCUS_SEARCH
            else -> null
        }
    }

    internal const val SEEK_STEP_MS = 5_000L
    internal const val SEEK_LONG_STEP_MS = 15_000L
    internal const val VOLUME_STEP = 0.05f

    /**
     * Whether an AWT key event belongs to a Space keystroke. The KEY_TYPED event of a keystroke has
     * no key code at all (VK_UNDEFINED), only the character, so it is told apart by that.
     */
    internal fun isPartOfSpace(id: Int, keyCode: Int, keyChar: Char): Boolean = when (id) {
        KeyEvent.KEY_TYPED -> keyChar == ' '
        else -> keyCode == KeyEvent.VK_SPACE
    }

    /**
     * Whether a KEY_TYPED event is the character a Ctrl+plus, minus or zero keystroke would
     * otherwise type into a focused text box. A typed event has no key code, so [actionFor] cannot
     * see it; without this the zoom worked but the box got an "=" or "-" as well.
     */
    internal fun isZoomTyped(keyChar: Char, ctrl: Boolean, alt: Boolean, meta: Boolean): Boolean =
        ctrl && !alt && !meta && keyChar in ZOOM_CHARS

    private const val ZOOM_CHARS = "=+-0"

    private var spaceHeld = false

    // Keys taken on their press, so their release (and typed echo) is taken with them.
    private val heldKeys = mutableMapOf<Int, DesktopGlobalAction>()
    private var typedEchoPending = false

    /** Runs what [action] asks of the app, and says whether the app did it. */
    private fun perform(action: DesktopGlobalAction): Boolean = when (action) {
        DesktopGlobalAction.SEEK_BACK -> seekBy?.invoke(-SEEK_STEP_MS)
        DesktopGlobalAction.SEEK_FORWARD -> seekBy?.invoke(SEEK_STEP_MS)
        DesktopGlobalAction.SEEK_BACK_LONG -> seekBy?.invoke(-SEEK_LONG_STEP_MS)
        DesktopGlobalAction.SEEK_FORWARD_LONG -> seekBy?.invoke(SEEK_LONG_STEP_MS)
        DesktopGlobalAction.PREVIOUS -> skipPrevious?.invoke()
        DesktopGlobalAction.NEXT -> skipNext?.invoke()
        DesktopGlobalAction.VOLUME_UP -> stepVolume?.invoke(VOLUME_STEP)
        DesktopGlobalAction.VOLUME_DOWN -> stepVolume?.invoke(-VOLUME_STEP)
        DesktopGlobalAction.MUTE -> toggleMute?.invoke()
        DesktopGlobalAction.FOCUS_SEARCH -> focusSearch?.invoke()
        DesktopGlobalAction.SHOW_SHORTCUTS -> showShortcuts?.invoke()
        else -> false
    } ?: false

    private fun isPlaybackKeyAction(action: DesktopGlobalAction) = when (action) {
        DesktopGlobalAction.PLAY_PAUSE,
        DesktopGlobalAction.ZOOM_IN,
        DesktopGlobalAction.ZOOM_OUT,
        DesktopGlobalAction.ZOOM_RESET,
        -> false
        else -> true
    }

    private val dispatcher = KeyEventDispatcher { event ->
        // The release and typed echo of a key that was taken go with it.
        if (event.id == KeyEvent.KEY_RELEASED && heldKeys.remove(event.keyCode) != null) {
            typedEchoPending = heldKeys.values.any { it.typesCharacter }
            return@KeyEventDispatcher true
        }
        if (event.id == KeyEvent.KEY_TYPED && typedEchoPending) return@KeyEventDispatcher true
        val action = actionFor(
            keyCode = event.keyCode,
            ctrl = event.isControlDown,
            alt = event.isAltDown,
            meta = event.isMetaDown,
            textEntryActive = TextEntryFocus.active,
            shift = event.isShiftDown,
        )
        if (action != null && isPlaybackKeyAction(action)) {
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            return@KeyEventDispatcher handlePlaybackPress(event.keyCode, action)
        }
        // The typed echo of a zoom key (it carries the character but no key code) is taken too.
        if (action == null && event.id == KeyEvent.KEY_TYPED &&
            isZoomTyped(event.keyChar, event.isControlDown, event.isAltDown, event.isMetaDown)
        ) {
            return@KeyEventDispatcher true
        }
        when (action) {
            null -> {
                // The release of a Space that was taken must be taken too, or the control that
                // would have had it sees half a keystroke.
                if (spaceHeld && isPartOfSpace(event.id, event.keyCode, event.keyChar)) {
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

    /** Tracks the action as well as the physical key, so changed modifiers start a new action. */
    internal fun handlePlaybackPress(keyCode: Int, action: DesktopGlobalAction): Boolean {
        if (heldKeys[keyCode] == action && !action.repeats) return true
        if (!perform(action)) {
            heldKeys.remove(keyCode)
            typedEchoPending = heldKeys.values.any { it.typesCharacter }
            return false
        }
        heldKeys[keyCode] = action
        typedEchoPending = heldKeys.values.any { it.typesCharacter }
        return true
    }

    internal fun releasePlaybackKey(keyCode: Int) {
        heldKeys.remove(keyCode)
        typedEchoPending = heldKeys.values.any { it.typesCharacter }
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
