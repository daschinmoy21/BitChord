package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import kotlin.math.roundToInt

/**
 * A clip's decoded frames, as images that do not outlive their turn on screen.
 *
 * Each frame used to be copied three times — the array, a Skia image, then a Skia bitmap made from
 * that image — and every copy waited on the garbage collector to be freed. The Java side of a Skia
 * object is a few bytes, so nothing about it ever looked like memory pressure: a portrait clip is
 * ~3.7 MB a frame at 25 frames a second, and whatever the collector had not yet got round to sat in
 * the process. Now a frame is one native copy, and the copy two frames back is closed outright.
 *
 * Main thread only. Frames handed out here may be closed under anyone who keeps them, so anything
 * that holds on to a frame — the backdrop, the shared player's palette — gets a [snapshot] instead.
 */
internal class DesktopCanvasFrames(private val info: ImageInfo) {

    private val live = ArrayDeque<Bitmap>()

    /** The frame in [pixels], copied out of it so the decoder can reuse the array. */
    fun next(pixels: ByteArray): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.installPixels(info, pixels, info.minRowBytes)
        bitmap.setImmutable()
        live.addLast(bitmap)
        // The one on screen and the one before it, which a frame already under way may still draw.
        // A drawn frame holds Skia's own reference to the pixels, so closing ours is safe beyond that.
        while (live.size > KEPT) live.removeFirst().close()
        return bitmap.asComposeImageBitmap()
    }

    private companion object {
        const val KEPT = 2
    }
}

/**
 * A copy of [frame] no larger than [maxEdge] on its longest side, owning its own pixels.
 *
 * What a caller keeps of a clip, rather than the frame itself: everything that keeps one only reads
 * its colours, and a thumbnail answers that as well as the full frame does for a fraction of it.
 */
internal fun canvasSnapshot(frame: ImageBitmap, maxEdge: Int): ImageBitmap {
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(frame.width, frame.height, 1))
    val width = (frame.width * scale).roundToInt().coerceAtLeast(1)
    val height = (frame.height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.OPAQUE))
    Image.makeFromBitmap(frame.asSkiaBitmap()).use { source ->
        Canvas(bitmap).use { canvas ->
            canvas.drawImageRect(
                source,
                Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                null,
                true,
            )
        }
    }
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
