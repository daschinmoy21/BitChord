package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val NAVIDROME_PLAYLIST_REFRESH_MS = 60_000L

/** An inline disclosure heading; the lazy sidebar owns the playlist rows beneath it. */
@Composable
internal fun DesktopNavidromePlaylistHeading(
    expanded: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).desktopHoverFill()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Navidrome playlists",
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = DesktopSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        DesktopNavidromeRefreshButton(loading, onRefresh)
    }
}

@Composable
internal fun DesktopNavidromeRefreshButton(loading: Boolean, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(32.dp)) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DesktopSecondary)
        } else {
            Icon(Icons.Rounded.Refresh, "Refresh Navidrome playlists", tint = DesktopSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
