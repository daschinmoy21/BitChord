package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Audio kept on disk so playing a song again does not resolve it and does not download it again.
 *
 * The bytes are fetched in bounded ranges, the same way Android's song cache does it. A single
 * open-ended read of a googlevideo URL is paced down to about playback speed after the first
 * megabyte, so a file fetched that way is only finished when the song is. A two-megabyte range
 * comes back at line rate, and the rest of the file can land while the first seconds play.
 *
 * A finished file is opened as a path. An unfinished one is not: a FLAC seek table and a WebM
 * cue sit at the end, and a short file fails to open.
 */
internal object DesktopSongCache {

    /** Tests point this at a scratch directory. Production leaves it alone. */
    @Volatile
    internal var directoryOverride: Path? = null

    /** Tests shrink this so a small fixture still takes more than one request. */
    @Volatile
    internal var chunkBytes: Int = DEFAULT_CHUNK_BYTES

    /** Tests that only want the bytes a read asked for turn the background fill off. */
    @Volatile
    internal var prefetchEnabled: Boolean = true

    private val directory: Path
        get() = (directoryOverride ?: DesktopMediaCache.directory.resolve("songs")).also {
            runCatching { Files.createDirectories(it) }
        }

    private val stores = ConcurrentHashMap<String, Store>()
    private val complete = ConcurrentHashMap<String, MutableList<CachedSong>>()
    private val indexLock = Any()
    private var indexed = false

    /** At most two whole-file fills at once, so a fast skip does not download the whole radio. */
    private val prefetchSlots = Semaphore(PREFETCH_SLOTS)

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /**
     * A finished copy of [videoId], preferring a lossless file over a larger bitrate.
     * Null when nothing has been kept in full.
     */
    fun bestComplete(videoId: String): CachedSong? {
        synchronized(indexLock) {
            if (!indexed) rebuildIndex()
            val hits = complete[videoId].orEmpty().filter { it.present() }
            complete[videoId] = hits.toMutableList()
            return hits.maxWithOrNull(compareBy<CachedSong> { it.format.isLossless }
                .thenBy { it.format.kbps ?: 0 }
                .thenBy { it.format.sampleRateHz ?: 0 }
                .thenBy { it.length })
        }
    }

    /** Opens a reader for [stream] and, unless told not to, keeps filling the file after the read. */
    fun open(song: Song, stream: DesktopStream): DesktopByteSource {
        val key = keyOf(song.videoId, stream)
        val store = stores.compute(key) { _, existing ->
            if (existing != null && !existing.abandoned) {
                existing.retarget(stream)
                existing
            } else {
                Store(key, song, stream)
            }
        }!!
        store.ensurePrefetch()
        return Reader(store)
    }

    /** Files a trim must not delete out from under a reader. */
    fun pinned(): Set<Path> = stores.values.mapNotNull { store ->
        store.bin.takeIf { !store.abandoned }
    }.toSet()

    /** Drops the in-memory readers. The files stay until [DesktopMediaCache] deletes them. */
    fun abandon() {
        val running = stores.values.toList()
        stores.clear()
        running.forEach { it.abandon() }
        running.forEach { runCatching { it.worker?.join(1_000) } }
        synchronized(indexLock) {
            complete.clear()
            indexed = false
        }
    }

    /** For tests, after [abandon], so the next case starts from an empty directory. */
    internal fun resetForTests() {
        abandon()
        directoryOverride = null
        chunkBytes = DEFAULT_CHUNK_BYTES
        prefetchEnabled = true
    }

    private fun rebuildIndex() {
        val found = HashMap<String, MutableList<CachedSong>>()
        val pinned = pinned()
        runCatching {
            Files.list(directory).use { stream ->
                stream.filter { it.toString().endsWith(".meta") }.forEach { meta ->
                    val cached = readMeta(meta) ?: return@forEach
                    if (!cached.present()) {
                        if (cached.bin !in pinned) {
                            runCatching { Files.deleteIfExists(cached.bin) }
                            runCatching { Files.deleteIfExists(meta) }
                        }
                        return@forEach
                    }
                    found.getOrPut(cached.videoId) { mutableListOf() }.add(cached)
                }
            }
        }
        complete.clear()
        complete.putAll(found)
        indexed = true
    }

    private fun remember(cached: CachedSong) {
        synchronized(indexLock) {
            val list = complete.getOrPut(cached.videoId) { mutableListOf() }
            list.removeAll { it.bin == cached.bin }
            list.add(cached)
        }
    }

    private fun readMeta(meta: Path): CachedSong? {
        val lines = runCatching { Files.readAllLines(meta) }.getOrNull() ?: return null
        if (lines.size < 9) return null
        val length = lines[7].toLongOrNull() ?: return null
        val finished = lines[8] == "1"
        if (!finished || length <= 0L) {
            return CachedSong(
                videoId = lines[0],
                sourceId = lines[1].ifBlank { null },
                bin = meta.resolveSibling(meta.fileName.toString().removeSuffix(".meta") + ".bin"),
                format = DesktopStreamFormat(),
                length = length,
                finished = false,
            )
        }
        return CachedSong(
            videoId = lines[0],
            sourceId = lines[1].ifBlank { null },
            bin = meta.resolveSibling(meta.fileName.toString().removeSuffix(".meta") + ".bin"),
            format = DesktopStreamFormat(
                codec = lines[2].ifBlank { null },
                kbps = lines[3].toIntOrNull(),
                sampleRateHz = lines[4].toIntOrNull(),
                bitDepth = lines[5].toIntOrNull(),
                channels = lines[6].toIntOrNull(),
            ),
            length = length,
            finished = true,
        )
    }

    private fun keyOf(videoId: String, stream: DesktopStream): String {
        val format = stream.format
        val raw = listOf(
            videoId,
            stream.sourceId.orEmpty(),
            format.codec.orEmpty(),
            format.kbps?.toString().orEmpty(),
            format.sampleRateHz?.toString().orEmpty(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    private class Span(val start: Long, val end: Long)

    private class Store(
        val key: String,
        song: Song,
        stream: DesktopStream,
    ) {
        val bin: Path = directory.resolve("$key.bin")
        private val meta: Path = directory.resolve("$key.meta")
        private val title = song.title
        private val videoId = song.videoId
        private val sourceId = stream.sourceId
        private val format = stream.format

        @Volatile var url: String = stream.url
        @Volatile var headers: Map<String, String> = stream.headers
        @Volatile var length: Long = -1L
        @Volatile var complete: Boolean = false
        @Volatile var abandoned: Boolean = false
        @Volatile var worker: Thread? = null

        private val file = java.io.RandomAccessFile(bin.toFile(), "rw")
        private val lock = ReentrantLock()
        private val progress = lock.newCondition()
        private val spans = ArrayList<Span>()
        private val inflight = HashSet<Long>()
        private var refused = false

        init {
            val existing = readMeta(meta)
            if (existing != null && existing.finished && existing.present()) {
                length = existing.length
                spans.add(Span(0L, existing.length))
                complete = true
            } else {
                // A partial file from a previous run has no record of which bytes are real.
                file.setLength(0)
            }
        }

        fun retarget(stream: DesktopStream) {
            url = stream.url
            headers = stream.headers
            if (refused && !complete) {
                refused = false
                ensurePrefetch()
            }
        }

        fun ensurePrefetch() {
            if (!prefetchEnabled || complete || abandoned) return
            if (worker?.isAlive == true) return
            val thread = Thread(::fill, "BitChord-Cache ${title.take(24)}")
            thread.isDaemon = true
            worker = thread
            thread.start()
        }

        fun abandon() {
            abandoned = true
            worker?.interrupt()
            lock.withLock { progress.signalAll() }
            runCatching { file.close() }
        }

        fun copyAt(position: Long, into: ByteArray, count: Int): Int = lock.withLock {
            val span = spans.firstOrNull { position >= it.start && position < it.end } ?: return 0
            val have = minOf(count.toLong(), span.end - position).toInt()
            if (have <= 0) return 0
            file.seek(position)
            val read = file.read(into, 0, have)
            if (read < 0) 0 else read
        }

        /** Makes sure the byte at [at] is in the file. Shared by the reader and the fill thread. */
        fun pull(at: Long): Boolean {
            if (abandoned) return false
            val start = chunkStart(at)
            lock.lock()
            try {
                if (contains(start)) return true
                while (start in inflight) {
                    if (contains(start) || abandoned) return contains(start)
                    if (!progress.await(5, TimeUnit.SECONDS)) break
                }
                if (contains(start) || abandoned) return contains(start)
                inflight.add(start)
            } catch (_: InterruptedException) {
                return false
            } finally {
                lock.unlock()
            }
            val wrote = try {
                download(start)
            } catch (_: InterruptedException) {
                false
            } finally {
                lock.withLock {
                    inflight.remove(start)
                    progress.signalAll()
                }
            }
            return wrote && contains(start)
        }

        private fun fill() {
            if (!prefetchSlots.tryAcquire()) {
                try {
                    prefetchSlots.acquire()
                } catch (_: InterruptedException) {
                    return
                }
            }
            try {
                var failures = 0
                while (!complete && !abandoned && !refused && failures < FILL_ATTEMPTS) {
                    val hole = nextMissing(0L) ?: break
                    if (pull(hole)) {
                        failures = 0
                    } else {
                        failures++
                        if (abandoned || refused) break
                        try {
                            Thread.sleep(400)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            } finally {
                prefetchSlots.release()
            }
            if (!complete && !abandoned && failuresWorthLogging()) {
                DesktopTrackLog.log("could not finish caching '$title'")
            }
        }

        private fun failuresWorthLogging(): Boolean = lock.withLock {
            !refused && !complete && nextMissing(0L) != null
        }

        private fun download(start: Long): Boolean {
            val end = start + chunkBytes - 1
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Range", "bytes=$start-$end")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
            headers.forEach { (name, value) -> runCatching { request.header(name, value) } }
            val response = runCatching {
                client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
            }.getOrNull() ?: return false
            if (response.statusCode() !in 200..299) {
                StreamResolver.onPlaybackRefused(url, response.statusCode())
                if (response.statusCode() == 403 || response.statusCode() == 410) refused = true
                return false
            }
            val body = response.body()
            if (body.isEmpty()) return false
            val stated = response.headers().firstValue("content-range").orElse(null)
            val (at, total) = if (stated != null && response.statusCode() == 206) {
                val from = stated.substringAfter("bytes ").substringBefore('-').trim().toLongOrNull() ?: start
                val size = stated.substringAfterLast('/').trim().toLongOrNull()
                from to size
            } else {
                // No range acknowledgement: the body is the file from the first byte.
                0L to body.size.toLong()
            }
            val finishedNow = lock.withLock {
                if (abandoned) return false
                file.seek(at)
                file.write(body)
                addSpan(at, at + body.size)
                if (total != null && total > 0) length = total
                if (length < 0 && body.size < chunkBytes) length = at + body.size
                val done = length > 0 && nextMissing(0L) == null
                if (done) finish()
                done
            }
            // Tests point [directory] at a scratch folder. Trimming would still walk the real cache.
            if (finishedNow && directoryOverride == null) DesktopMediaCache.trim()
            return true
        }

        private fun finish() {
            if (complete) return
            complete = true
            val cached = CachedSong(
                videoId = videoId,
                sourceId = sourceId,
                bin = bin,
                format = format,
                length = length,
                finished = true,
            )
            writeMeta(cached)
            remember(cached)
            DesktopTrackLog.log("cached '$title' (${length} bytes)")
        }

        private fun writeMeta(cached: CachedSong) {
            val format = cached.format
            val text = listOf(
                cached.videoId,
                cached.sourceId.orEmpty(),
                format.codec.orEmpty(),
                format.kbps?.toString().orEmpty(),
                format.sampleRateHz?.toString().orEmpty(),
                format.bitDepth?.toString().orEmpty(),
                format.channels?.toString().orEmpty(),
                cached.length.toString(),
                "1",
            ).joinToString("\n")
            Files.writeString(
                meta,
                text,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }

        private fun contains(at: Long): Boolean = spans.any { at >= it.start && at < it.end }

        private fun nextMissing(from: Long): Long? {
            var at = from
            val ordered = spans.sortedBy { it.start }
            for (span in ordered) {
                if (at < span.start) return at
                if (at < span.end) at = span.end
            }
            val known = length
            if (known >= 0 && at >= known) return null
            return at
        }

        private fun addSpan(start: Long, end: Long) {
            if (end <= start) return
            var from = start
            var to = end
            val kept = ArrayList<Span>()
            for (span in spans) {
                if (span.end < from || span.start > to) {
                    kept.add(span)
                } else {
                    from = minOf(from, span.start)
                    to = maxOf(to, span.end)
                }
            }
            kept.add(Span(from, to))
            spans.clear()
            spans.addAll(kept.sortedBy { it.start })
        }

        private fun chunkStart(at: Long): Long {
            val size = chunkBytes.toLong()
            return at - Math.floorMod(at, size)
        }
    }

    private class Reader(private val store: Store) : DesktopByteSource {
        private var position = 0L

        override val length: Long get() = store.length

        override fun position(): Long = position

        override fun seek(offset: Long) {
            position = offset.coerceAtLeast(0)
        }

        override fun read(into: ByteArray, count: Int): Int {
            if (count <= 0) return 0
            val known = store.length
            if (known >= 0 && position >= known) return -1
            var taken = store.copyAt(position, into, count)
            if (taken <= 0) {
                if (!store.pull(position)) return -1
                taken = store.copyAt(position, into, count)
                if (taken <= 0) return -1
            }
            position += taken
            return taken
        }
    }

    private const val DEFAULT_CHUNK_BYTES = 2 * 1024 * 1024
    private const val PREFETCH_SLOTS = 2
    private const val FILL_ATTEMPTS = 4
}

/**
 * A song the cache can play without asking a source.
 *
 * [finished] is false only for a meta file the index is about to delete.
 */
internal class CachedSong(
    val videoId: String,
    val sourceId: String?,
    val bin: Path,
    val format: DesktopStreamFormat,
    val length: Long,
    val finished: Boolean,
) {
    fun present(): Boolean = finished && length > 0 &&
        runCatching { Files.isRegularFile(bin) && Files.size(bin) >= length }.getOrDefault(false)

    fun asStream(): DesktopStream = DesktopStream(
        url = bin.toAbsolutePath().toString(),
        format = format,
        sourceId = sourceId,
    )
}
