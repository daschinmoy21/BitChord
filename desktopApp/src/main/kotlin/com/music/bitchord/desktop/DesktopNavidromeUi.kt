package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.reportsTextEntryFocus
import kotlinx.coroutines.launch

/**
 * Adds or edits the listener's Navidrome: where it is, who they are on it, and a check that the
 * two work before anything is kept.
 */
@Composable
internal fun DesktopNavidromeEditorDialog(
    config: DesktopSourceConfig,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (DesktopSourceConfig) -> Unit,
    onRemove: () -> Unit,
) {
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var username by remember(config.id) { mutableStateOf(config.username) }
    var password by remember(config.id) { mutableStateOf("") }
    var label by remember(config.id) { mutableStateOf(config.label) }
    var checking by remember(config.id) { mutableStateOf(false) }
    var message by remember(config.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canCheck = baseUrl.isNotBlank() && username.isNotBlank() &&
        // Editing an existing server may leave the password alone: the stored login still stands.
        (password.isNotEmpty() || !isNew) && !checking

    /** Signs in with what is typed (or, when the password is blank, what is saved) and says how it went. */
    fun check(thenSave: Boolean) {
        val url = DesktopNavidromeClient.normalizeBase(baseUrl)
        val candidate = config.copy(
            kind = DesktopSourceKind.NAVIDROME,
            baseUrl = url,
            username = username.trim(),
            label = label.trim(),
        )
        val fresh = password.takeIf { it.isNotEmpty() }
            ?.let { DesktopNavidromeCredentials.fromPassword(candidate.username, it) }
        val credentials = fresh ?: DesktopNavidromeCredentialStore.load(candidate)
        if (credentials == null) {
            message = "Enter the password for this login"
            return
        }
        checking = true
        message = null
        scope.launch {
            val outcome = DesktopNavidromeClient(url, credentials).ping()
            checking = false
            outcome.fold(
                onSuccess = { info ->
                    message = "${info.name} ${info.version}"
                    if (thenSave) {
                        if (fresh != null) DesktopNavidromeCredentialStore.save(candidate, fresh)
                        onSave(candidate)
                    }
                },
                onFailure = { failure ->
                    message = when {
                        (failure as? DesktopNavidromeException)?.isBadCredentials == true ->
                            "The server rejected that username or password"
                        else -> failure.message ?: "Nothing answered at that address"
                    }
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Navidrome" else config.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; message = null },
                    modifier = Modifier.fillMaxWidth().reportsTextEntryFocus(),
                    label = { Text("Server address") },
                    placeholder = { Text("https://music.example.com") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; message = null },
                    modifier = Modifier.fillMaxWidth().reportsTextEntryFocus(),
                    label = { Text("Username") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    modifier = Modifier.fillMaxWidth().reportsTextEntryFocus(),
                    label = { Text(if (isNew) "Password" else "Password (leave blank to keep it)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth().reportsTextEntryFocus(),
                    label = { Text("Name (optional)") },
                    placeholder = { Text("My Navidrome") },
                    singleLine = true,
                )
                Text(
                    "Only a salted token is kept, in your keyring when there is one — never the password. " +
                        "Use an https address: the token travels with each request.",
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                message?.let { Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { check(thenSave = false) }, enabled = canCheck) {
                    Text(if (checking) "Checking…" else "Test")
                }
                TextButton(onClick = { check(thenSave = true) }, enabled = canCheck) { Text("Save") }
            }
        },
    )
}

/** How Navidrome is used for playback, and what changes on a metered connection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopNavidromePlaybackControls() {
    val mode by DesktopNavidromeSettings.mode.collectAsState()
    val wait by DesktopNavidromeSettings.waitSeconds.collectAsState()
    val onMetered by DesktopNavidromeSettings.onMetered.collectAsState()
    val meteredKbps by DesktopNavidromeSettings.meteredKbps.collectAsState()
    val forceMetered by DesktopNavidromeSettings.forceMetered.collectAsState()
    val report by DesktopNavidromeSettings.reportPlays.collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("When a track is on Navidrome", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DesktopNavidromeMode.FIRST to "Navidrome first",
                DesktopNavidromeMode.RACE to "Start fast, switch when ready",
                DesktopNavidromeMode.OFF to "Don't use Navidrome",
            ).forEach { (value, text) ->
                FilterChip(
                    colors = desktopChipColors(),
                    selected = mode == value,
                    onClick = { DesktopNavidromeSettings.setMode(value) },
                    label = { Text(text) },
                )
            }
        }
        Text(
            when (mode) {
                DesktopNavidromeMode.FIRST ->
                    "Navidrome gets $wait seconds to start the track. Past that, playback starts from " +
                        "YouTube Music and your other sources, and switches to Navidrome if it catches up."
                DesktopNavidromeMode.RACE ->
                    "Whichever source answers first starts playing, then Navidrome takes over mid-track " +
                        "when it is ready."
                DesktopNavidromeMode.OFF -> "Tracks play from your other sources only."
            },
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (mode == DesktopNavidromeMode.FIRST) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wait up to", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
                TextButton(onClick = { DesktopNavidromeSettings.setWaitSeconds(wait - 1) }) { Text("−") }
                Text("$wait s", modifier = Modifier.width(36.dp))
                TextButton(onClick = { DesktopNavidromeSettings.setWaitSeconds(wait + 1) }) { Text("+") }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("On mobile data or a metered connection", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DesktopMeteredBehaviour.OTHER_SOURCES to "Use other sources",
                DesktopMeteredBehaviour.TRANSCODE to "Smaller Opus from Navidrome",
                DesktopMeteredBehaviour.SAME to "Same as usual",
            ).forEach { (value, text) ->
                FilterChip(
                    colors = desktopChipColors(),
                    selected = onMetered == value,
                    onClick = { DesktopNavidromeSettings.setOnMetered(value) },
                    label = { Text(text) },
                )
            }
        }
        if (onMetered == DesktopMeteredBehaviour.TRANSCODE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Up to", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
                TextButton(onClick = { DesktopNavidromeSettings.setMeteredKbps(meteredKbps - 16) }) { Text("−") }
                Text("$meteredKbps kbps", modifier = Modifier.width(72.dp))
                TextButton(onClick = { DesktopNavidromeSettings.setMeteredKbps(meteredKbps + 16) }) { Text("+") }
            }
        }
        ToggleLine(
            "Treat this connection as metered",
            "For when your system doesn't flag it itself, such as a hotspot it doesn't recognise",
            forceMetered,
            DesktopNavidromeSettings::setForceMetered,
        )
        ToggleLine(
            "Tell Navidrome what I play",
            "Plays from Navidrome are sent to it, and kept out of Last.fm and ListenBrainz here",
            report,
            DesktopNavidromeSettings::setReportPlays,
        )
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = desktopSwitchColors())
    }
}
