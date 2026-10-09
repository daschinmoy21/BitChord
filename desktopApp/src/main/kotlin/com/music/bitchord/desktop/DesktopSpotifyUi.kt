package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.SpotifyImporter
import com.music.bitchord.data.spotify.SpotifyPlaylist
import com.music.bitchord.data.spotify.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Spotify track id → its YouTube Music match. It outlives the page and is saved to disk, so a
 * playlist is matched once rather than each time it is played; misses are not kept and are
 * retried.
 */
internal class DesktopSpotifyMatches(private val persistence: DesktopPersistence) {
    private val matches by lazy { LinkedHashMap(persistence.spotifyMatches()) }

    suspend fun get(trackId: String): Song? =
        withContext(Dispatchers.IO) { synchronized(this@DesktopSpotifyMatches) { matches[trackId] } }

    /** Records [found], most recent last, and writes the whole cache back. */
    suspend fun saveAll(found: Map<String, Song>) {
        if (found.isEmpty()) return
        withContext(Dispatchers.IO) {
            // Keep the merge and the write under one lock: an older save must never
            // overwrite a newer snapshot. Initial loading also stays off the UI thread.
            synchronized(this@DesktopSpotifyMatches) {
                found.forEach { (id, song) -> matches.remove(id); matches[id] = song }
                val oldest = matches.entries.iterator()
                while (matches.size > MAX_SPOTIFY_MATCHES) {
                    oldest.next()
                    oldest.remove()
                }
                persistence.saveSpotifyMatches(matches)
            }
        }
    }

    companion object {
        val shared by lazy { DesktopSpotifyMatches(DesktopPersistence()) }
    }
}

/** Browsing stays as Spotify metadata; matching happens only for playback or a local import. */
@Composable
internal fun DesktopSpotifyPage(
    connected: Boolean,
    onConnect: () -> Unit,
    onPlay: (List<Song>, Int, String) -> Unit,
    onImport: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues,
) {
    var playlists by remember(connected) { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var selected by remember(connected) { mutableStateOf<SpotifyPlaylist?>(null) }
    var tracks by remember(selected?.id) { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var resolving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var actionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(connected, selected?.id, refresh) {
        if (!connected) return@LaunchedEffect
        loading = true
        error = null
        try {
            val playlist = selected
            if (playlist == null) playlists = DesktopSpotify.library.playlists()
            else tracks = DesktopSpotify.library.tracks(playlist.id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not load Spotify" }
        finally { loading = false }
    }

    fun resolve(save: Boolean, single: SpotifyTrack? = null) {
        if (resolving || loading) return
        val playlist = selected ?: return
        val sourceTracks = single?.let { listOf(it) } ?: tracks.toList()
        if (sourceTracks.isEmpty()) return
        resolving = true
        error = null
        actionJob = scope.launch {
            val matched = mutableMapOf<String, Song>()
            try {
                val songs = mutableListOf<Song>()
                sourceTracks.forEachIndexed { index, track ->
                    currentCoroutineContext().ensureActive()
                    progress = "Matching ${index + 1} of ${sourceTracks.size}…"
                    val song = matched[track.id] ?: DesktopSpotifyMatches.shared.get(track.id)
                        ?: SpotifyImporter.matchTrack(track)?.also { matched[track.id] = it }
                    if (song != null) songs += song
                }
                val missed = sourceTracks.size - songs.size
                if (songs.isEmpty()) error = "No matching YouTube Music tracks found."
                else {
                    if (save) onImport(playlist.name, songs) else onPlay(songs, 0, playlist.name)
                    progress = if (save) "Imported ${songs.size} songs" else "Matched ${songs.size} songs"
                    if (missed > 0) progress += "; $missed unmatched"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not match Spotify tracks" }
            finally {
                resolving = false
                // A cancelled run still keeps the tracks it got through.
                withContext(NonCancellable) { DesktopSpotifyMatches.shared.saveAll(matched) }
            }
        }
    }

    fun importLink() {
        val id = SpotifyImporter.extractPlaylistId(link)
        if (id == null) { error = "Enter a valid Spotify playlist link."; return }
        resolving = true
        error = null
        actionJob = scope.launch {
            try {
                val (title, sourceTracks) = if (connected) {
                    val playlist = playlists.firstOrNull { it.id == id } ?: DesktopSpotify.library.playlist(id)
                    playlist.name to DesktopSpotify.library.tracks(id).map {
                        com.music.bitchord.data.spotify.SpotifyImportTrack(it.title, it.artist)
                    }
                } else SpotifyImporter.fetchPlaylistTracks(id)
                val (songs, missed) = SpotifyImporter.resolveToSongs(sourceTracks) { completed, total ->
                    // Matching runs on IO workers; publish progress on the UI dispatcher.
                    scope.launch { progress = "Matching $completed of $total…" }
                }
                if (songs.isEmpty()) error = "No matching YouTube Music tracks found."
                else {
                    onImport(title, songs)
                    progress = "Imported ${songs.size} songs; ${missed.size} unmatched"
                    if (!connected && sourceTracks.size == 100) progress += ". Connect Spotify to import longer playlists in full."
                    link = ""
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not import Spotify playlist" }
            finally { resolving = false }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected?.name ?: "Spotify", style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null) TextButton(onClick = { selected = null; progress = "" }, enabled = !resolving) { Text("Back to playlists") }
                    TextButton(onClick = onConnect, enabled = !resolving) { Text(if (connected) "Manage account" else "Add account") }
                    if (connected) TextButton(onClick = { refresh++ }, enabled = !loading && !resolving) { Text("Refresh") }
                }
                if (!connected) Text("Sign in to browse your playlists and Liked Songs, or import a public playlist below.", color = DesktopSecondary)
                if (selected == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Spotify playlist link") },
                        singleLine = true, enabled = !resolving, modifier = Modifier.weight(1f))
                    TextButton(onClick = ::importLink, enabled = !resolving && link.isNotBlank()) { Text("Import") }
                } else Row {
                    TextButton(onClick = { resolve(false) }, enabled = !loading && !resolving && tracks.isNotEmpty()) { Text("Play all") }
                    TextButton(onClick = { resolve(true) }, enabled = !loading && !resolving && tracks.isNotEmpty()) { Text("Import to local playlists") }
                    Text("${tracks.size} songs", modifier = Modifier.padding(12.dp), color = DesktopSecondary)
                }
                if (loading || resolving) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DesktopAccent)
                if (resolving) TextButton(onClick = { actionJob?.cancel() }) { Text("Cancel matching") }
                if (progress.isNotBlank()) Text(progress, color = DesktopSecondary)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (connected && !loading && error == null && selected != null && tracks.isEmpty()) Text("This playlist has no tracks.", color = DesktopSecondary)
            }
        }
        if (selected == null) items(playlists, key = { it.id }) { playlist ->
            SpotifyRow(playlist.name, playlist.owner ?: "Spotify", playlist.imageUrl, !resolving) {
                selected = playlist; progress = ""
            }
        } else items(tracks.withIndex().toList(), key = { "${it.index}:${it.value.id}" }) { (_, track) ->
            SpotifyRow(track.title, track.artist, track.imageUrl, !loading && !resolving) { resolve(false, track) }
        }
    }
}

@Composable
private fun SpotifyRow(title: String, subtitle: String, artwork: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}
