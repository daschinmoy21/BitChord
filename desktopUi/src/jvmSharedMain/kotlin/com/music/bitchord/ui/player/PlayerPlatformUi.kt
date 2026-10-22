package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Milliseconds on the clock `SystemClock.uptimeMillis` reads on the phone. */
internal expect fun uptimeMillis(): Long

/** Whether `Modifier.blur` actually blurs here, rather than being a no-op. */
internal expect val renderEffectBlurSupported: Boolean

/** Holds off the display's idle timeout for as long as [enabled]. */
@Composable
internal expect fun KeepScreenOn(enabled: Boolean)

/** The app's language, as a BCP 47 tag. */
@Composable
internal expect fun appLanguageTag(): String

/**
 * Back, for one of the player's own layers. Call order is priority order: a
 * later call is the one back reaches first while both are enabled. On the
 * desktop, back is Escape.
 */
@Composable
internal expect fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Whether a scroll that runs out inside a [PlayerDrawer] should drag the drawer
 * with it.
 *
 * True only where scrolling *is* dragging. On the phone a finger both scrolls
 * the list and moves the drawer, so a pull down past the top of the list has to
 * carry the drawer — the way a sheet with a list in it behaves. On the desktop
 * the list is scrolled by the wheel, which is not a drag at all, and by the time
 * it arrives as a nested scroll the two are indistinguishable: a drawer that
 * took its leftover would slide out from under a reader who was only scrolling.
 * There it keeps to its own gesture, a held drag.
 */
internal expect val drawerFollowsListScroll: Boolean

/**
 * Draws [block] clipped to the rectangle given, moved down by [dy] pixels.
 *
 * On the phone this is a plain clip and translate. Skia on the desktop places
 * glyphs at sub-pixel precision across a line but snaps them to whole pixels
 * down it, so a lyric lifted by a fraction of a pixel stays put and then jumps
 * a whole one — a two-pixel rise becomes three steps. The desktop's version
 * moves the fractional part through a filtered layer instead.
 */
internal expect fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
)

/**
 * Whether the lyric playhead should advance.
 *
 * On the phone this is true only while the activity is RESUMED, so a phone in
 * a pocket does not render lyrics at frame rate. On the desktop this is true
 * while the window is open on screen. Compose Desktop pauses the lifecycle
 * when the AWT window loses focus, and a visible unfocused window still has to
 * move the lyrics. The clock stops when the window is hidden to the tray.
 */
@Composable
internal expect fun rememberLyricClockActive(): Boolean

/**
 * Nanoseconds at which the lyric clock should take its next step.
 *
 * The phone returns the vsync time from `withFrameNanos`. The compose scene
 * produces a frame only on demand, and an unfocused AWT window may never
 * request one. Waiting there would leave the playhead blocked until focus
 * returned. The desktop waits about one frame and returns [System.nanoTime].
 */
internal expect suspend fun awaitLyricFrameNanos(): Long
