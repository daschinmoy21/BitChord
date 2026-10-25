package com.music.bitchord

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import com.music.bitchord.ui.replay.sendIntent
import com.music.bitchord.ui.share.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryPosterTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun everyCardAndStyleExportsAnOpaque1080By1920Png() = runBlocking {
        val song = storySong("dQw4w9WgXcQ").copy(thumbnailUrl = "https://example.com/cover.png", albumName = "The album")
        val songs = (0..4).map { song.copy(videoId = "song-$it", title = "Track ${it + 1}") }
        val art = sampleArt(song)
        val cards = listOf(StoryCard.Track(song), StoryCard.Replay(storySummary(songs)), StoryCard.Recent(songs))
        val output = File("build/story-card-previews").apply { mkdirs() }
        for ((index, card) in cards.withIndex()) for (style in StoryStyle.entries) {
            val bitmap = renderStoryPoster(context, card, style, art)
            try {
                assertEquals(STORY_WIDTH, bitmap.width)
                assertEquals(STORY_HEIGHT, bitmap.height)
                assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
                assertTrue("Card needs visible white text", whitePixels(bitmap) > 100)
                File(output, "${index}-${style.name}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        }
        assertFalse("Rendering a background must not recycle cached artwork", art.images.values.first().isRecycled)
    }

    @Test fun missingArtworkStillProducesACardAndFallsBackToTheColourStyle() = runBlocking {
        val art = StoryArt(emptyMap(), listOf(Color.BLUE, Color.MAGENTA))
        val card = StoryCard.Track(storySong("local:42"))
        val artwork = renderStoryPoster(context, card, StoryStyle.ARTWORK, art)
        val colour = renderStoryPoster(context, card, StoryStyle.COLOUR, art)
        try { assertTrue(artwork.sameAs(colour)) }
        finally { artwork.recycle(); colour.recycle() }
    }

    @Test fun longTitlesAndNonLatinMetadataRenderWithoutRunningOffTheFrame() = runBlocking {
        val song = storySong("local:long").copy(
            title = "非常に長い曲のタイトル — أغنية جميلة — ".repeat(30),
            artist = "非常に長いアーティスト名 ".repeat(20), albumName = "Album ".repeat(30),
        )
        val bitmap = renderStoryPoster(context, StoryCard.Track(song), StoryStyle.DARK,
            StoryArt(emptyMap(), listOf(Color.BLUE, Color.MAGENTA)))
        try {
            assertTrue(whitePixels(bitmap) > 100)
            // The lower safe area contains backdrop, rather than text clipped by the frame.
            for (x in 0 until bitmap.width step 30) assertTrue(Color.red(bitmap.getPixel(x, 1850)) < 180)
        } finally { bitmap.recycle() }
    }

    @Test fun imageShareUsesContentUrisReadGrantsAndIndependentFiles() = runBlocking {
        val first = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val second = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            val a = cacheStoryForSharing(context, first)!!
            val b = cacheStoryForSharing(context, second)!!
            assertEquals("content", a.scheme)
            assertNotEquals(a, b)
            context.contentResolver.openInputStream(a).use { input ->
                val read = BitmapFactory.decodeStream(input)!!
                assertEquals(Color.RED, read.getPixel(0, 0))
                read.recycle()
            }
            val intent = sendIntent(a)
            assertEquals("image/png", intent.type)
            assertEquals(a, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals(a, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally { first.recycle(); second.recycle() }
    }

    private fun whitePixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 300 until 1800 step 4) for (x in 90 until 990 step 4) {
            val c = bitmap.getPixel(x, y)
            if (Color.red(c) > 240 && Color.green(c) > 240 && Color.blue(c) > 240) count++
        }
        return count
    }

    private fun sampleArt(song: com.music.bitchord.data.model.Song): StoryArt {
        val cover = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 720f, 720f, Color.rgb(98, 33, 189), Color.rgb(239, 89, 50), Shader.TileMode.CLAMP)
        }
        Canvas(cover).drawRect(0f, 0f, 720f, 720f, paint)
        return StoryArt(mapOf(song.artworkUrlsForTest() to cover), listOf(Color.rgb(98, 33, 189), Color.rgb(239, 89, 50)))
    }

    private fun com.music.bitchord.data.model.Song.artworkUrlsForTest() = StoryCard.Track(this).artworkUrls().single()
}
