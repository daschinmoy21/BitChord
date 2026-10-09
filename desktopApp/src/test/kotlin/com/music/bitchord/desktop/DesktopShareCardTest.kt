package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.music.bitchord.data.model.Song
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopShareCardTest {
    private val songs = (1..7).map { n ->
        Song(
            videoId = "id$n",
            title = if (n == 1) "A Title Long Enough That It Has To Wrap Onto A Second Line Somewhere" else "Song $n",
            artist = "Artist $n",
            thumbnailUrl = "https://lh3.googleusercontent.com/art$n=w60-h60",
            albumName = "Album $n",
        )
    }

    private val replay = DesktopReplaySummary(
        period = DesktopReplayPeriod.THIS_MONTH,
        label = "October 2026",
        totalMs = 4_321L * 60_000L,
        totalPlays = 812,
        songs = songs.mapIndexed { i, song -> DesktopReplayEntry(song, (10 - i) * 600_000L, 30 - i) },
        artists = listOf(DesktopRankedEntry("Artist 1", null, null, null, 3_600_000L, 40)),
    )

    @Test
    fun `every card renders a full story frame in every style`() {
        val out = File("build/share-cards").apply { mkdirs() }
        val cards = listOf(
            "track" to DesktopShareCard.Track(songs.first()),
            "replay" to DesktopShareCard.Replay(replay),
            "recent" to DesktopShareCard.Recent(songs + songs.first()),
        )
        for ((name, card) in cards) {
            val art = DesktopShareArt(
                card.artworkUrls().mapIndexed { i, url -> url to artwork(i) }.toMap(),
                DesktopArtworkPalette(androidx.compose.ui.graphics.Color(0xFF7A3B8F), androidx.compose.ui.graphics.Color(0xFF2B3A7A)),
            )
            for (style in DesktopShareStyle.entries) {
                val png = card.renderPng(style, art)
                File(out, "$name-${style.name.lowercase()}.png").writeBytes(png)
                val image = ImageIO.read(png.inputStream())
                assertEquals(1080, image.width)
                assertEquals(1920, image.height)
                val colours = (0 until 1920 step 40).flatMap { y -> (0 until 1080 step 40).map { x -> image.getRGB(x, y) } }.toSet()
                assertTrue(colours.size > 20, "$name/$style drew next to nothing")
            }
        }
    }

    @Test
    fun `recent cards list a song once and the hero is fetched large`() {
        val card = DesktopShareCard.Recent(listOf(songs[0], songs[1], songs[0]))
        val urls = card.artworkUrls()
        assertEquals("https://lh3.googleusercontent.com/art1=w720-h720", urls.first())
        assertEquals(3, urls.size)
    }

    @Test
    fun `file names lose what a file system cannot carry`() {
        assertEquals("AC_DC - Back In Black", DesktopShareFiles.safeName("AC/DC - Back In Black"))
        assertEquals("Share card", DesktopShareFiles.safeName(" ... "))
        assertEquals("Recently played - 2026-10-09", DesktopShareCard.Recent(songs).fileName(LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun `saving twice keeps both images`() {
        val root = Files.createTempDirectory("bitchord-share").toFile()
        try {
            val first = DesktopShareFiles.save(byteArrayOf(1), "Artist - Song", root)
            val second = DesktopShareFiles.save(byteArrayOf(2), "Artist - Song", root)
            assertEquals("Artist - Song.png", first.name)
            assertEquals("Artist - Song (2).png", second.name)
            assertEquals(File(root, "BitChord"), second.parentFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun artwork(seed: Int): ImageBitmap {
        val image = BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = GradientPaint(0f, 0f, Color(40 * seed % 255, 120, 200), 240f, 240f, Color(240, 90, 60 + 20 * seed % 195))
            fillRect(0, 0, 240, 240)
            // Shapes, so a render shows whether the cover still reads through the blur.
            color = Color(250, 210, 60)
            fillOval(30, 40, 110, 110)
            color = Color(20, 20, 30)
            fillRect(130, 120, 90, 100)
            color = Color.WHITE
            fillRect(0, 200, 240, 14)
            dispose()
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return bytes.decodeToImageBitmap()
    }
}
