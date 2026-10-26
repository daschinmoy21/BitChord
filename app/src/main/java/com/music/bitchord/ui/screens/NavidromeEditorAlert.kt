package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.navidrome.NavidromeClient
import com.music.bitchord.data.navidrome.NavidromeCredentials
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceRegistry
import kotlinx.coroutines.launch

@Composable
internal fun NavidromeEditorAlert(config: SourceConfig, onDismiss: () -> Unit, onSaved: () -> Unit, onDelete: () -> Unit) {
    val isNew = SourceRegistry.config(config.id) == null
    var url by remember(config.id) { mutableStateOf(config.baseUrl) }
    var username by remember(config.id) { mutableStateOf(config.username) }
    var password by remember(config.id) { mutableStateOf("") }
    var label by remember(config.id) { mutableStateOf(config.label) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val passwordRequired = isNew || username.trim() != config.username ||
        NavidromeClient.normalizeBase(url) != config.baseUrl
    val canSubmit = url.isNotBlank() && username.isNotBlank() && (!passwordRequired || password.isNotEmpty()) && !busy
    val duplicateMessage = stringResource(R.string.source_already_added)

    fun check(save: Boolean) {
        val credentials = if (password.isNotEmpty()) NavidromeCredentials.fromPassword(username, password)
            else NavidromeCredentials(config.username, config.salt, config.token)
        val candidate = config.copy(
            baseUrl = NavidromeClient.normalizeBase(url), username = username.trim(), label = label.trim(),
            salt = credentials.salt, token = credentials.token,
        )
        val duplicate = SourceRegistry.configs.value.firstOrNull {
            it.id != config.id && it.baseUrl == candidate.baseUrl && it.username == candidate.username
        }
        if (duplicate != null) {
            message = String.format(duplicateMessage, duplicate.displayName)
            return
        }
        busy = true
        message = null
        scope.launch {
            NavidromeClient(candidate.baseUrl, credentials).ping().fold(
                onSuccess = {
                    message = "${it.name} ${it.version}"
                    if (save) {
                        if (isNew) SourceRegistry.add(candidate) else SourceRegistry.update(candidate)
                        onSaved()
                    }
                },
                onFailure = { message = it.message },
            )
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isNew) stringResource(R.string.add_navidrome) else config.displayName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.navidrome_description))
                OutlinedTextField(
                    value = url, onValueChange = { url = it; message = null }, enabled = !busy,
                    label = { Text(stringResource(R.string.navidrome_server_address)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it; message = null }, enabled = !busy,
                    label = { Text(stringResource(R.string.username)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; message = null }, enabled = !busy,
                    label = { Text(stringResource(if (passwordRequired) R.string.password else R.string.navidrome_keep_password)) },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, enabled = !busy,
                    label = { Text(stringResource(R.string.navidrome_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                message?.let { Text(it) }
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.remove)) }
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { check(false) }, enabled = canSubmit) { Text(stringResource(R.string.test)) }
                TextButton(onClick = { check(true) }, enabled = canSubmit) { Text(stringResource(R.string.save)) }
            }
        },
    )
}
