package com.music.bitchord.desktop

import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Canvas frames: one native copy each, closed once they are off screen, and copies that last. */
class DesktopCanvasFramesTest {

    private val info = ImageInfo.makeN32(64, 32, ColorAlphaType.OPAQUE)

    /** A frame of one colour, BGRA as the decoder writes it. */
    private fun filled(blue: Int, green: Int, red: Int) = ByteArray(64 * 32 * 4).also { bytes ->
        for (at in bytes.indices step 4) {
            bytes[at] = blue.toByte()
            bytes[at + 1] = green.toByte()
            bytes[at + 2] = red.toByte()
            bytes[at + 3] = 0xFF.toByte()
        }
    }

    @Test
    fun aFrameIsClosedOnceTwoNewerOnesHaveArrived() {
        val frames = DesktopCanvasFrames(info)
        val pixels = filled(0, 0, 0)
        val first = frames.next(pixels).asSkiaBitmap()
        val second = frames.next(pixels).asSkiaBitmap()
        assertFalse(first.isClosed, "the frame still on screen was closed")
        frames.next(pixels)
        assertTrue(first.isClosed, "a frame two behind was kept")
        assertFalse(second.isClosed, "the frame before the newest was closed")
    }

    @Test
    fun aFrameIsCopiedOutOfTheDecodersArray() {
        val frames = DesktopCanvasFrames(info)
        val pixels = filled(0, 0, 200)
        val frame = frames.next(pixels)
        pixels.fill(0)
        val argb = IntArray(1)
        frame.readPixels(argb, 10, 10, 1, 1)
        assertEquals(200, (argb[0] shr 16) and 0xFF, "the frame changed with the array it came from")
    }

    @Test
    fun aSnapshotIsSmallKeepsTheColourAndOutlivesItsFrame() {
        val frames = DesktopCanvasFrames(info)
        val frame = frames.next(filled(40, 120, 200))
        val snapshot = canvasSnapshot(frame, maxEdge = 16)
        assertEquals(16, snapshot.width)
        assertEquals(8, snapshot.height)
        repeat(3) { frames.next(filled(0, 0, 0)) }
        assertTrue(frame.asSkiaBitmap().isClosed)
        val argb = IntArray(1)
        snapshot.readPixels(argb, 4, 4, 1, 1)
        assertEquals(200, (argb[0] shr 16) and 0xFF)
        assertEquals(120, (argb[0] shr 8) and 0xFF)
        assertEquals(40, argb[0] and 0xFF)
    }
}
