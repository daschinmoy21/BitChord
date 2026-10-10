package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Every key the window answers to, for the cheat sheet that "?" opens. */
@Composable
internal fun DesktopShortcutsDialog(onDismiss: () -> Unit) {
    val rows = listOf(
        "Space" to DesktopStrings["d_shortcut_play_pause", "Play or pause"],
        "←  →" to DesktopStrings["d_shortcut_seek", "Seek 5 seconds (Shift for 15)"],
        "Ctrl + ←  →" to DesktopStrings["d_shortcut_prev_next", "Previous or next song"],
        "↑  ↓" to DesktopStrings["d_shortcut_volume", "Volume up or down"],
        "M" to DesktopStrings["d_shortcut_mute", "Mute or unmute"],
        "/  or  Ctrl + K" to DesktopStrings["d_shortcut_search", "Focus search"],
        "Ctrl + +  −  0" to DesktopStrings["d_shortcut_zoom", "Zoom in, out or reset"],
        "Ctrl + 1 … 9" to DesktopStrings["d_shortcut_pages", "Open a sidebar page"],
        "Ctrl (tap)" to DesktopStrings["d_shortcut_hints", "Label everything to click it by keyboard"],
        "Shift + H  L" to DesktopStrings["d_shortcut_back_forward", "Back or forward"],
        "J  K" to DesktopStrings["d_shortcut_scroll", "Scroll down or up"],
        "D  U" to DesktopStrings["d_shortcut_page_scroll", "Scroll half a page"],
        "Alt + ←" to DesktopStrings["d_shortcut_back", "Go back"],
        "?" to DesktopStrings["d_shortcut_help", "Show this list"],
    )
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 440) {
        Column(
            Modifier.padding(horizontal = panelInset(22.dp), vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                DesktopStrings["d_keyboard_shortcuts", "Keyboard shortcuts"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            rows.forEach { (keys, action) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(action, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        keys,
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
