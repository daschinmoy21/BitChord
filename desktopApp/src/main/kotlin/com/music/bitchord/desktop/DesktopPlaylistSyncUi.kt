package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * The opt-in download rows of a playlist's menu, for placing inside its `DropdownMenu`.
 *
 * [enabled] is whether this playlist is opted in, not whether the rows respond. The width is fixed so
 * that a changing status, or opening the explanation, does not resize the menu under the pointer.
 */
@Composable
internal fun DesktopPlaylistSyncMenu(
    enabled: Boolean,
    busy: Boolean,
    status: String?,
    onToggle: () -> Unit,
    onSync: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    Column(Modifier.width(280.dp)) {
        if (enabled) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (busy) {
                            DesktopStrings["d_playlist_sync_running", "Syncing…"]
                        } else {
                            DesktopStrings["d_playlist_sync_now", "Sync now"]
                        },
                    )
                },
                leadingIcon = {
                    if (busy) SyncSpinner() else Icon(Icons.Rounded.Sync, null)
                },
                enabled = !busy,
                onClick = {
                    onDismissMenu()
                    onSync()
                },
            )
            // Stays available mid-sync: leaving must never have to wait for the work it stops.
            DropdownMenuItem(
                text = {
                    SyncActionLabel(
                        title = DesktopStrings["d_playlist_sync_stop", "Stop automatic downloads"],
                        detail = DesktopStrings["d_playlist_sync_stop_detail", "Downloaded songs stay on this device"],
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.SyncDisabled, null) },
                onClick = {
                    onDismissMenu()
                    onToggle()
                },
            )
        } else {
            DropdownMenuItem(
                text = {
                    SyncActionLabel(
                        title = DesktopStrings["d_playlist_sync_start", "Download and keep updated"],
                        detail = DesktopStrings["d_playlist_sync_start_detail", "Checks for new songs hourly while BitChord is open"],
                    )
                },
                leadingIcon = {
                    if (busy) SyncSpinner() else Icon(BitChordIcons.Download, null)
                },
                enabled = !busy,
                onClick = {
                    onDismissMenu()
                    onToggle()
                },
            )
        }
        status?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SyncExplanation()
    }
}

/** An action's name over the one fact worth knowing before pressing it. */
@Composable
private fun SyncActionLabel(title: String, detail: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
    }
}

/** Takes on the row's own colour, so a disabled row's spinner dims with its label. */
@Composable
private fun SyncSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = LocalContentColor.current,
    )
}

/**
 * The rules behind the rows, one press away. Pressing it keeps the menu open; closing the menu
 * disposes this, so the rows are back to their short form the next time.
 */
@Composable
private fun SyncExplanation() {
    var open by remember { mutableStateOf(false) }
    val state = if (open) {
        DesktopStrings["d_playlist_sync_help_open", "Expanded"]
    } else {
        DesktopStrings["d_playlist_sync_help_closed", "Collapsed"]
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = state }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            DesktopStrings["d_playlist_sync_help", "How automatic downloads work"],
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
        )
        Icon(
            if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            null,
            tint = DesktopSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
    if (open) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                DesktopStrings["d_playlist_sync_help_new", "New songs download automatically."],
                DesktopStrings.format(
                    "d_playlist_sync_help_large",
                    PLAYLIST_SYNC_AUTO_LIMIT,
                    fallback = "More than %1\$d new songs at once wait for Sync now.",
                ),
                DesktopStrings["d_playlist_sync_help_deleted", "Songs you delete stay deleted."],
                DesktopStrings["d_playlist_sync_help_removed", "Songs removed from the playlist stay on this device."],
            ).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
            }
        }
    }
}
