package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val NAVIDROME_PLAYLIST_REFRESH_MS = 60_000L

/** The server's live playlists, shared by the sidebar and Library. */
@Composable
internal fun DesktopNavidromePlaylistDropdown(
    configs: List<DesktopSourceConfig>,
    playlists: List<DesktopNavidromeSource.ServerPlaylist>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpen: (DesktopNavidromeSource.ServerPlaylist) -> Unit,
) {
    if (configs.none { it.kind == DesktopSourceKind.NAVIDROME && it.enabled && it.isComplete }) return
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { expanded = true; onRefresh() },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text("Navidrome playlists ▾", color = DesktopAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(32.dp)) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DesktopAccent)
                } else {
                    Icon(Icons.Rounded.Refresh, "Refresh Navidrome playlists", tint = DesktopAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 360.dp).heightIn(max = 420.dp),
        ) {
            when {
                loading && playlists.isEmpty() -> DropdownMenuItem(text = { Text("Loading playlists…") }, onClick = {}, enabled = false)
                playlists.isEmpty() && error == null -> DropdownMenuItem(
                    text = { Text("No Navidrome playlists") }, onClick = {}, enabled = false,
                )
            }
            playlists.forEach { ref ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(ref.playlist.name.ifBlank { "Untitled playlist" })
                            Text(
                                "${ref.source.displayName} · ${ref.playlist.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                            )
                        }
                    },
                    onClick = { expanded = false; onOpen(ref) },
                )
            }
            if (error != null) DropdownMenuItem(text = { Text("Retry") }, onClick = onRefresh)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
