package com.music.bitchord.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException

/** What a share card shows: one song, a Replay, or the latest plays. */
internal sealed interface DesktopShareCard {
    data class Track(val song: Song) : DesktopShareCard
    data class Replay(val summary: DesktopReplaySummary) : DesktopShareCard
    data class Recent(val songs: List<Song>) : DesktopShareCard
}

/** The backdrop behind a card. Clicking the preview steps through them, as Spotify's sheet does. */
internal enum class DesktopShareStyle(val label: String) {
    ARTWORK("Artwork"),
    COLOUR("Colour"),
    DARK("Dark"),
}

/** A phone story, 9:16. The PNG is three times this: 1080 × 1920. */
private val CardWidth = 360.dp
private val CardHeight = 640.dp
internal const val SHARE_EXPORT_DENSITY = 3f
private const val PREVIEW_SCALE = 0.65f
private const val HERO_PX = 720
private const val ROW_PX = 144
private const val LIST_ROWS = 5

private fun DesktopShareCard.heroSong(): Song? = when (this) {
    is DesktopShareCard.Track -> song
    is DesktopShareCard.Replay -> summary.songs.firstOrNull()?.song
    is DesktopShareCard.Recent -> songs.firstOrNull()
}

private fun DesktopShareCard.heroUrl(): String? = heroSong()?.artworkAt(HERO_PX)

private fun DesktopShareCard.listSongs(): List<Song> = when (this) {
    is DesktopShareCard.Track -> emptyList()
    is DesktopShareCard.Replay -> summary.songs.take(LIST_ROWS).map { it.song }
    // History keeps a song once per play; the card lists it once.
    is DesktopShareCard.Recent -> songs.distinctBy(Song::videoId).take(LIST_ROWS)
}

/** Every artwork a card draws, at the size it draws it. */
internal fun DesktopShareCard.artworkUrls(): List<String> =
    (listOf(heroUrl()) + listSongs().map { it.artworkAt(ROW_PX) }).filterNotNull().distinct()

/** What the PNG is called, minus the extension. */
internal fun DesktopShareCard.fileName(today: LocalDate = LocalDate.now()): String = when (this) {
    is DesktopShareCard.Track -> "${song.artist} - ${song.title}"
    is DesktopShareCard.Replay -> "Replay - ${summary.label.ifBlank { summary.period.chip }}"
    is DesktopShareCard.Recent -> "Recently played - $today"
}

/** The artwork a card needs, fetched, and the colours drawn from its hero. */
internal class DesktopShareArt(
    val images: Map<String, ImageBitmap>,
    val palette: DesktopArtworkPalette,
)

internal suspend fun DesktopShareCard.loadArt(): DesktopShareArt = withContext(Dispatchers.IO) {
    val images = coroutineScope {
        artworkUrls().map { url -> async { DesktopArtworkCache.load(url)?.let { url to it } } }.awaitAll()
    }.filterNotNull().toMap()
    DesktopShareArt(images, DesktopArtworkPalette.from(heroUrl()?.let(images::get)))
}

// ── The card ────────────────────────────────────────────────────────────────

/** The card at its own 360 × 640 dp, for the preview to scale down and the export to scale up. */
@Composable
internal fun DesktopShareCardContent(
    card: DesktopShareCard,
    style: DesktopShareStyle,
    art: DesktopShareArt,
    modifier: Modifier = Modifier,
) {
    val hero = card.heroUrl()?.let(art.images::get)
    Box(modifier.requiredSize(CardWidth, CardHeight).clipToBounds()) {
        ShareBackdrop(style, hero, art.palette)
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 36.dp)) {
            // A lone cover sits mid-frame; a list sits high, with a third of the spare room above it.
            Spacer(Modifier.weight(1f))
            when (card) {
                is DesktopShareCard.Track -> TrackBody(card.song, hero)
                is DesktopShareCard.Replay -> ReplayBody(card.summary, card.listSongs(), art.images)
                is DesktopShareCard.Recent -> ListBody(
                    title = DesktopStrings["d_recently_played", "Recently played"],
                    subtitle = null,
                    songs = card.listSongs(),
                    images = art.images,
                )
            }
            Spacer(Modifier.weight(if (card is DesktopShareCard.Track) 1f else 2f))
        }
    }
}

@Composable
private fun ShareBackdrop(style: DesktopShareStyle, hero: ImageBitmap?, palette: DesktopArtworkPalette) {
    when {
        style == DesktopShareStyle.ARTWORK && hero != null -> {
            Image(
                bitmap = hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Soft enough that the cover is still recognisable across the whole frame, and
                // scaled a little past the edges so the blur has no rim to darken.
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }.blur(22.dp),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.58f)),
                    ),
                ),
            )
        }
        style == DesktopShareStyle.DARK -> Box(
            Modifier.fillMaxSize()
                .background(Color(0xFF0B0B0F))
                // Sized from the card, so the preview and the export glow alike.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(palette.primary.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.2f),
                            radius = size.height * 0.6f,
                        ),
                    )
                },
        )
        // The artwork style falls back here when there is no artwork to blur.
        else -> Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.primary, palette.secondary)))
                // White type has to read on a pale cover's average too.
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.50f))),
                ),
        )
    }
}

@Composable
private fun ColumnScope.TrackBody(song: Song, hero: ImageBitmap?) {
    Artwork(
        hero,
        Modifier.align(Alignment.CenterHorizontally).size(270.dp)
            .shadow(28.dp, RoundedCornerShape(14.dp)),
        corner = 14,
    )
    Spacer(Modifier.height(34.dp))
    Text(
        song.title,
        color = Color.White,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.5).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.displayLarge.onCard(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        song.artist,
        color = Color.White.copy(alpha = 0.80f),
        fontSize = 18.sp,
        fontWeight = FontWeight.W500,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge.onCard(),
    )
    song.albumName?.takeIf { it.isNotBlank() && it != song.title }?.let { album ->
        Spacer(Modifier.height(4.dp))
        Text(
            album,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.onCard(),
        )
    }
}

@Composable
private fun ReplayBody(summary: DesktopReplaySummary, songs: List<Song>, images: Map<String, ImageBitmap>) {
    ListBody(
        title = DesktopStrings["d_my_replay", "My Replay"],
        subtitle = summary.label.ifBlank { summary.period.chip },
        songs = songs,
        images = images,
        stats = {
            Row(Modifier.fillMaxWidth()) {
                Stat(DesktopStrings["minutes_listened", "Minutes listened"], grouped(summary.minutes), Modifier.weight(1f))
                summary.artists.firstOrNull()?.let {
                    Stat(DesktopStrings["top_artist", "Top artist"], it.title, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(26.dp))
            SectionLabel(DesktopStrings["top_songs", "Top songs"])
        },
    )
}

@Composable
private fun ListBody(
    title: String,
    subtitle: String?,
    songs: List<Song>,
    images: Map<String, ImageBitmap>,
    stats: (@Composable () -> Unit)? = null,
) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        color = Color.White,
        fontSize = 34.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.8).sp,
        style = MaterialTheme.typography.displayLarge.onCard(),
    )
    subtitle?.let {
        Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(26.dp))
    stats?.invoke()
    val rowArt = if (stats == null) 56.dp else 46.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (stats == null) 14.dp else 10.dp)) {
        songs.forEachIndexed { index, song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.titleMedium.onCard(),
                )
                Artwork(song.artworkAt(ROW_PX)?.let(images::get), Modifier.size(rowArt), corner = 6)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.onCard(),
                    )
                    Text(
                        song.artist,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.onCard(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 12.dp)) {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.W800,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.displayLarge.onCard(),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.60f),
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.2.sp,
        style = MaterialTheme.typography.labelMedium.onCard(),
    )
    Spacer(Modifier.height(10.dp))
}

/** A soft shadow under the type, so it still reads where a bright cover shows through. */
private fun TextStyle.onCard() = copy(shadow = Shadow(Color.Black.copy(alpha = 0.40f), Offset(0f, 2f), blurRadius = 14f))

@Composable
private fun Artwork(bitmap: ImageBitmap?, modifier: Modifier, corner: Int) {
    val shape = RoundedCornerShape(corner.dp)
    if (bitmap == null) Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.12f)))
    else Image(bitmap, null, modifier.clip(shape), contentScale = ContentScale.Crop)
}

// ── Export ──────────────────────────────────────────────────────────────────

/** The card as a 1080 × 1920 PNG, drawn off screen by the same composable the preview shows. */
internal fun DesktopShareCard.renderPng(style: DesktopShareStyle, art: DesktopShareArt): ByteArray {
    val density = Density(SHARE_EXPORT_DENSITY)
    val width = with(density) { CardWidth.roundToPx() }
    val height = with(density) { CardHeight.roundToPx() }
    val card = this
    val image = renderComposeScene(width, height, density) {
        MaterialTheme(colorScheme = desktopColorScheme(), typography = desktopTypography()) {
            DesktopShareCardContent(card, style, art)
        }
    }
    return checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "Could not encode the image" }.bytes
}

/** Images on the clipboard, which a window under XWayland cannot always hand to Wayland itself. */
internal object DesktopImageClipboard {

    /** Copies [png], through the session's own clipboard tool where there is one. */
    fun copyPng(png: ByteArray) {
        if (DesktopPlatform.isLinux) {
            val tools = buildList {
                if (System.getenv("WAYLAND_DISPLAY") != null) add(listOf("wl-copy", "--type", "image/png"))
                if (System.getenv("DISPLAY") != null) add(listOf("xclip", "-selection", "clipboard", "-t", "image/png"))
            }
            if (tools.any { pipeTo(it, png) }) return
        }
        val image = checkNotNull(ImageIO.read(png.inputStream())) { "Could not read the image" }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
                override fun getTransferData(flavor: DataFlavor): Any =
                    if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
            },
            null,
        )
    }

    /** Both tools fork to serve the clipboard; the process started here exits once it has the data. */
    private fun pipeTo(command: List<String>, bytes: ByteArray): Boolean = runCatching {
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.outputStream.use { it.write(bytes) }
        process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)
}

/** Where saved cards go: a BitChord folder in the user's Pictures. */
internal object DesktopShareFiles {

    fun save(png: ByteArray, name: String, root: File = picturesDir()): File {
        val folder = File(root, "BitChord").apply { mkdirs() }
        val base = safeName(name)
        val file = generateSequence(1) { it + 1 }
            .map { n -> File(folder, if (n == 1) "$base.png" else "$base ($n).png") }
            .first { !it.exists() }
        file.writeBytes(png)
        return file
    }

    /** [name] with what a file name cannot carry on any of the three systems taken out. */
    internal fun safeName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim().trim('.').take(120).ifBlank { "Share card" }

    private fun picturesDir(): File {
        val home = File(System.getProperty("user.home"))
        if (DesktopPlatform.isLinux) {
            // XDG's answer, which a localised desktop may have moved off "Pictures".
            val xdg = runCatching {
                val process = ProcessBuilder("xdg-user-dir", "PICTURES").redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().readText().trim()
                out.takeIf { process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0 }
            }.getOrNull()
            // It answers with the home directory itself when Pictures is not set.
            if (!xdg.isNullOrBlank() && File(xdg) != home) return File(xdg)
        }
        return File(home, "Pictures")
    }
}

// ── The sheet ───────────────────────────────────────────────────────────────

@Composable
internal fun DesktopShareCardDialog(card: DesktopShareCard, onDismiss: () -> Unit) {
    var style by remember(card) { mutableStateOf(DesktopShareStyle.ARTWORK) }
    var status by remember(card) { mutableStateOf<String?>(null) }
    var busy by remember(card) { mutableStateOf(false) }
    val art by produceState<DesktopShareArt?>(null, card) { value = card.loadArt() }
    val scope = rememberCoroutineScope()

    fun export(action: (ByteArray) -> String) {
        val loaded = art ?: return
        if (busy) return
        busy = true
        status = null
        scope.launch {
            status = try {
                val png = withContext(Dispatchers.Default) { card.renderPng(style, loaded) }
                withContext(Dispatchers.IO) { action(png) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failure.message ?: "Could not make the image"
            } finally {
                busy = false
            }
        }
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                DesktopStrings["share", "Share"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                Modifier
                    .size(CardWidth * PREVIEW_SCALE, CardHeight * PREVIEW_SCALE)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(enabled = art != null) {
                        style = DesktopShareStyle.entries[(style.ordinal + 1) % DesktopShareStyle.entries.size]
                    },
                contentAlignment = Alignment.Center,
            ) {
                val loaded = art
                if (loaded == null) {
                    CircularProgressIndicator(color = DesktopAccent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    DesktopShareCardContent(
                        card,
                        style,
                        loaded,
                        Modifier.graphicsLayer { scaleX = PREVIEW_SCALE; scaleY = PREVIEW_SCALE },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DesktopShareStyle.entries.forEach { option ->
                    val active = option == style
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.Black else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f))
                            .clickable { style = option }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareAction(Icons.Rounded.ContentCopy, DesktopStrings["d_copy_image", "Copy image"], art != null && !busy) {
                    export { png ->
                        DesktopImageClipboard.copyPng(png)
                        DesktopStrings["d_image_copied", "Image copied"]
                    }
                }
                ShareAction(Icons.Rounded.Download, DesktopStrings["d_save_image", "Save image"], art != null && !busy) {
                    export { png -> "Saved to ${DesktopShareFiles.save(png, card.fileName()).path}" }
                }
                if (card is DesktopShareCard.Track) {
                    ShareAction(Icons.Rounded.Link, DesktopStrings["d_copy_link", "Copy link"], !busy) {
                        DesktopExternalLinks.copy("https://music.youtube.com/watch?v=${card.song.videoId}")
                        DesktopTrackLog.log("copied a link to '${card.song.title}'")
                        status = DesktopStrings["d_link_copied", "Link copied"]
                    }
                }
            }
            status?.let {
                Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShareAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) Color.White else DesktopSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) Color.White else DesktopSecondary)
    }
}
