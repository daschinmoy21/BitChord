package com.music.bitchord.desktop

import com.music.bitchord.data.model.artworkAt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.decodeToImageBitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import java.net.URI

/**
 * Decoded artwork, kept to a budget.
 *
 * It used to be an unbounded map. Every distinct URL a session ever loaded stayed decoded for the
 * life of the process, so browsing Explore and Search for a while was hundreds of megabytes of
 * bitmaps nothing would ever ask for again — which is where the gigabyte people reported was going.
 */
internal object DesktopArtworkCache {

    /**
     * A sixth of the heap, within reason.
     *
     * Derived rather than fixed so the budget still makes sense against whatever `-Xmx` the
     * launcher was given, and clamped at both ends so a small heap still caches something and a
     * large one does not decide it may hold half a gigabyte of thumbnails.
     */
    internal val budgetBytes: Long =
        (Runtime.getRuntime().maxMemory() / 6).coerceIn(32L * 1024 * 1024, 128L * 1024 * 1024)

    /** Four bytes a pixel, which is what every format here decodes to. */
    private val images = DesktopByteBudgetLru<ImageBitmap>(budgetBytes) {
        it.width.toLong() * it.height.toLong() * 4L
    }

    /** Everything held, dropped — what the Storage settings' "Clear image cache" does. */
    fun clear() {
        images.clear()
        SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear()
    }

    /**
     * The shared UI's image loader (Coil), with a memory budget of its own choosing rather than
     * Coil's: on the JVM that is 15% of a fixed 512 MB, ~77 MB of decoded bitmaps on top of this
     * cache's. What it shows is thumbnails and palette samples, which it can re-read from its disk
     * cache, so a smaller one costs a decode and not a download.
     */
    fun installImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizeBytes(COIL_MEMORY_BYTES).build() }
                .build()
        }
    }

    private const val COIL_MEMORY_BYTES = 48L * 1024 * 1024

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        images.get(url)?.let { return it }
        return runCatching {
            URI(url).toURL().openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }.getInputStream().use { stream -> stream.readBytes().decodeToImageBitmap() }
        }.getOrNull()?.also { images.put(url, it) }
    }
}

/** Artwork, fetched at the size the surface drawing it actually needs. */
@Composable
fun DesktopArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    px: Int? = null,
) {
    val sized = if (px != null) url.artworkAt(px) else url
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = sized) {
        value = withContext(Dispatchers.IO) {
            DesktopArtworkCache.load(sized)
        }
    }
    if (bitmap == null) {
        Box(modifier.background(Color(0xFF2B2B35)))
    } else {
        Image(
            painter = BitmapPainter(bitmap!!),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
