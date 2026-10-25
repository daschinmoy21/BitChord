package com.music.bitchord.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import com.music.bitchord.R
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.replay.Fonts
import com.music.bitchord.ui.replay.drawArtwork
import com.music.bitchord.ui.replay.ellipsised
import com.music.bitchord.ui.replay.loadBitmap
import com.music.bitchord.ui.replay.paletteOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.NumberFormat

internal class StoryArt(val images: Map<String, Bitmap>, val colors: List<Int>)

internal suspend fun StoryCard.loadArt(context: Context): StoryArt = coroutineScope {
    val images = artworkUrls().map { url ->
        async { loadBitmap(context, url)?.let { url to it } }
    }.awaitAll().filterNotNull().toMap()
    withContext(Dispatchers.Default) {
        StoryArt(images, paletteOf(artworkUrls().firstOrNull()?.let(images::get)))
    }
}

/** Preview and export use this same bitmap: a 9:16 image with no app branding. */
internal suspend fun renderStoryPoster(
    context: Context,
    card: StoryCard,
    style: StoryStyle,
    art: StoryArt,
): Bitmap = withContext(Dispatchers.Default) {
    val bitmap = Bitmap.createBitmap(STORY_WIDTH, STORY_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val type = Fonts(context)
    val songs = card.songs()
    val hero = card.artworkUrls().firstOrNull()?.let(art.images::get)
    drawBackground(canvas, style, hero, art.colors)

    when (card) {
        is StoryCard.Track -> {
            drawArtwork(canvas, hero, card.song.title, 135f, 350f, 810f, false)
            var y = 1262f
            y += text(canvas, card.song.title, type.heading(84f, Color.WHITE), 90f, y, 900f, 2)
            y += 22f
            y += text(canvas, card.song.artist, type.body(54f, WHITE_80), 90f, y, 900f, 2)
            card.song.albumName?.takeIf { it.isNotBlank() && it != card.song.title }?.let { album ->
                text(canvas, album, type.body(42f, WHITE_60), 90f, y + 18f, 900f, 1)
            }
        }
        is StoryCard.Replay, is StoryCard.Recent -> {
            val replay = (card as? StoryCard.Replay)?.summary
            var y = if (replay == null) 330f else 240f
            val title = context.getString(if (replay == null) R.string.story_recent_title else R.string.your_replay)
            y += text(canvas, title, type.heading(102f, Color.WHITE), 90f, y, 900f, 2)
            if (replay != null) {
                y += text(canvas, replay.label, type.body(45f, WHITE_60), 90f, y + 14f, 900f, 1) + 14f
                y += 72f
                val leftHeight = stat(canvas, type, context.getString(R.string.minutes_listened),
                    NumberFormat.getIntegerInstance().format(replay.minutes), 90f, y)
                val rightHeight = replay.artists.firstOrNull()?.let {
                    stat(canvas, type, context.getString(R.string.top_artist), it.title, 552f, y)
                } ?: 0f
                y += maxOf(leftHeight, rightHeight) + 62f
                y += text(canvas, context.getString(R.string.top_songs), type.label(33f, WHITE_60), 90f, y, 900f, 1)
            }
            y += 54f
            val size = if (replay == null) 168f else 138f
            songs.forEachIndexed { index, song ->
                canvas.drawText("${index + 1}", 90f, y + size / 2f + 16f, type.body(45f, WHITE_60, true))
                val cover = song.artworkAt(720)?.let(art.images::get)
                drawArtwork(canvas, cover, song.title, 162f, y, size, false)
                val x = 162f + size + 36f
                val width = 990f - x
                canvas.drawText(ellipsised(song.title, type.body(45f, Color.WHITE, true), width),
                    x, y + size / 2f - 8f, type.body(45f, Color.WHITE, true))
                canvas.drawText(ellipsised(song.artist, type.body(39f, WHITE_60), width),
                    x, y + size / 2f + 48f, type.body(39f, WHITE_60))
                y += size + if (replay == null) 42f else 30f
            }
        }
    }
    bitmap
}

private fun stat(canvas: Canvas, type: Fonts, label: String, value: String, x: Float, y: Float): Float {
    val labelHeight = text(canvas, label, type.label(30f, WHITE_60), x, y, 420f, 2)
    return labelHeight + 18f + text(canvas, value, type.heading(78f, Color.WHITE), x, y + labelHeight + 18f, 420f, 2)
}

private fun text(canvas: Canvas, value: String, paint: Paint, x: Float, y: Float, width: Float, lines: Int): Float {
    val textPaint = TextPaint(paint).apply { setShadowLayer(14f, 0f, 2f, 0x66000000) }
    val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width.toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setMaxLines(lines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setLineSpacing(6f, 1f)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
    return layout.height.toFloat()
}

private fun drawBackground(canvas: Canvas, style: StoryStyle, hero: Bitmap?, colors: List<Int>) {
    val width = STORY_WIDTH.toFloat()
    val height = STORY_HEIGHT.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    when {
        style == StoryStyle.ARTWORK && hero != null -> {
            // Downsampling softens the backdrop on API 26 too, without GPU-only blur APIs.
            val blurred = Bitmap.createScaledBitmap(hero, 24, 24, true)
            try {
                canvas.drawBitmap(blurred, null, RectF(-54f, -96f, width + 54f, height + 96f), paint)
            } finally {
                if (blurred !== hero) blurred.recycle()
            }
            paint.shader = LinearGradient(0f, 0f, 0f, height,
                intArrayOf(0x38000000, 0x4D000000, 0x94000000.toInt()), null, Shader.TileMode.CLAMP)
        }
        style == StoryStyle.DARK -> {
            canvas.drawColor(0xFF0B0B0F.toInt())
            paint.shader = RadialGradient(width / 2f, height * 0.2f, height * 0.6f,
                intArrayOf(ColorUtils.setAlphaComponent(colors.first(), 115), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        else -> {
            paint.shader = LinearGradient(0f, 0f, 0f, height,
                intArrayOf(colors[0], colors[1]), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = LinearGradient(0f, 0f, 0f, height,
                intArrayOf(0x1A000000, 0x80000000.toInt()), null, Shader.TileMode.CLAMP)
        }
    }
    canvas.drawRect(0f, 0f, width, height, paint)
}

private const val WHITE_80 = 0xCCFFFFFF.toInt()
private const val WHITE_60 = 0x99FFFFFF.toInt()
