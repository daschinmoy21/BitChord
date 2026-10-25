package com.music.bitchord.desktop

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Caps how many malloc arenas glibc keeps, which is what `MALLOC_ARENA_MAX` would do had the
 * launcher a way to set it.
 *
 * glibc gives each thread that allocates its own arena, up to eight per core, and a freed block in
 * one is never reused by another. Skia, FFmpeg, WebKit and the HTTP clients allocate natively from
 * dozens of threads, so memory freed on one went unused while another mapped more. Called first
 * thing in [main], before those threads exist; arenas already made by the JVM's own threads are
 * kept.
 */
internal object DesktopMallocArenas {

    private const val M_ARENA_MAX = -8
    private const val ARENAS = 2

    private interface LibC : Library {
        fun mallopt(param: Int, value: Int): Int
    }

    fun cap() {
        if (!DesktopPlatform.isLinux) return
        // Someone who set it on purpose knows better.
        if (System.getenv("MALLOC_ARENA_MAX") != null) return
        // musl and other libcs have no mallopt; nothing to cap there.
        runCatching { Native.load("c", LibC::class.java).mallopt(M_ARENA_MAX, ARENAS) }
    }
}
