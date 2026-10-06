package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cache exists so a song that already played opens from disk. The server here refuses a
 * request for the whole file, the way googlevideo does.
 */
class DesktopSongCacheTest {

    private val scratch = Files.createTempDirectory("bitchord-song-cache")

    @AfterTest
    fun cleanUp() {
        DesktopSongCache.resetForTests()
        scratch.toFile().deleteRecursively()
    }

    private fun song(id: String = "video") = Song(
        videoId = id,
        title = "Track",
        artist = "Artist",
        thumbnailUrl = null,
    )

    private fun serve(
        body: ByteArray,
        allowance: Int = body.size,
        block: (url: String, requested: MutableList<String>) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requested = mutableListOf<String>()
        server.createContext("/media") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            synchronized(requested) { requested += range ?: "(none)" }
            val match = Regex("bytes=(\\d+)-(\\d+)").find(range.orEmpty())
            if (match == null) {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return@createContext
            }
            val from = match.groupValues[1].toLong()
            val asked = match.groupValues[2].toLong()
            val to = minOf(asked, from + allowance - 1, body.size - 1L)
            if (from > to || from >= body.size) {
                exchange.sendResponseHeaders(416, -1)
                exchange.close()
                return@createContext
            }
            val slice = body.copyOfRange(from.toInt(), (to + 1).toInt())
            exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${body.size}")
            exchange.sendResponseHeaders(206, slice.size.toLong())
            exchange.responseBody.use { it.write(slice) }
        }
        server.start()
        DesktopSongCache.directoryOverride = scratch
        DesktopSongCache.abandon()
        try {
            block("http://127.0.0.1:${server.address.port}/media", requested)
        } finally {
            server.stop(0)
        }
    }

    private fun readAll(source: DesktopByteSource): ByteArray {
        val out = ArrayList<Byte>()
        val buffer = ByteArray(1024)
        while (true) {
            val taken = source.read(buffer, buffer.size)
            if (taken < 0) break
            for (index in 0 until taken) out += buffer[index]
        }
        return out.toByteArray()
    }

    @Test
    fun `a song that was read in full is played from disk afterwards`() {
        val body = ByteArray(20_000) { (it % 251).toByte() }
        serve(body, allowance = 8 * 1024) { url, requested ->
            DesktopSongCache.chunkBytes = 8 * 1024
            DesktopSongCache.prefetchEnabled = false
            val stream = DesktopStream(url, DesktopStreamFormat(codec = "opus", kbps = 160), sourceId = "youtube")
            val reader = DesktopSongCache.open(song(), stream)
            assertContentEquals(body, readAll(reader))
            val cached = DesktopSongCache.bestComplete("video")
            assertTrue(cached != null, "a finished read should leave a complete file")
            val requests = synchronized(requested) { requested.size }
            DesktopSongCache.abandon()
            val again = DesktopSongCache.bestComplete("video")
            assertEquals(cached!!.length, again?.length)
            assertContentEquals(body, Files.readAllBytes(again!!.bin))
            // The second lookup did not ask the server for anything.
            assertEquals(requests, synchronized(requested) { requested.size })
        }
    }

    @Test
    fun `the rest of the file arrives without the decoder reading it`() {
        val body = ByteArray(24_000) { (it % 97).toByte() }
        serve(body, allowance = 8 * 1024) { url, _ ->
            DesktopSongCache.chunkBytes = 8 * 1024
            DesktopSongCache.prefetchEnabled = true
            val stream = DesktopStream(url, DesktopStreamFormat(codec = "flac", kbps = 900), sourceId = "addon")
            DesktopSongCache.open(song("full"), stream)
            val deadline = System.nanoTime() + 5_000_000_000L
            var cached = DesktopSongCache.bestComplete("full")
            while (cached == null && System.nanoTime() < deadline) {
                Thread.sleep(20)
                cached = DesktopSongCache.bestComplete("full")
            }
            assertTrue(cached != null, "prefetch should finish the file on its own")
            assertContentEquals(body, Files.readAllBytes(cached!!.bin))
            assertTrue(cached.format.isLossless)
        }
    }

    @Test
    fun `a partial file is not a cache hit`() {
        val body = ByteArray(40_000) { (it % 113).toByte() }
        serve(body, allowance = 8 * 1024) { url, _ ->
            DesktopSongCache.chunkBytes = 8 * 1024
            DesktopSongCache.prefetchEnabled = false
            val stream = DesktopStream(url, sourceId = "youtube")
            val reader = DesktopSongCache.open(song("partial"), stream)
            val buffer = ByteArray(100)
            assertTrue(reader.read(buffer, buffer.size) > 0)
            assertNull(DesktopSongCache.bestComplete("partial"))
        }
    }

    @Test
    fun `a server that refuses the whole file is still cached`() {
        val body = ByteArray(12_000) { (it % 61).toByte() }
        serve(body, allowance = 4 * 1024) { url, requested ->
            DesktopSongCache.chunkBytes = 4 * 1024
            DesktopSongCache.prefetchEnabled = false
            val reader = DesktopSongCache.open(song("ranged"), DesktopStream(url))
            assertContentEquals(body, readAll(reader))
            assertTrue(synchronized(requested) { requested.none { it == "(none)" } })
            assertTrue(DesktopSongCache.bestComplete("ranged") != null)
        }
    }
}
