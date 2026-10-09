package com.music.bitchord.ui.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.music.bitchord.R
import com.music.bitchord.ui.replay.ShareAction
import com.music.bitchord.ui.replay.saveToGallery
import com.music.bitchord.ui.replay.sendIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun StoryShareSheet(card: StoryCard, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember(card) { mutableStateOf(StoryStyle.ARTWORK) }
    var art by remember(card) { mutableStateOf<StoryArt?>(null) }
    var image by remember(card) { mutableStateOf<Bitmap?>(null) }
    var renderFailed by remember(card) { mutableStateOf(false) }
    var exportFailed by remember(card) { mutableStateOf(false) }
    var saved by remember(card) { mutableStateOf(false) }
    var busy by remember(card) { mutableStateOf(false) }
    var retry by remember(card) { mutableStateOf(0) }
    var pendingSave by remember(card) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(card, retry) {
        renderFailed = false
        try {
            art = card.loadArt(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            renderFailed = true
        }
    }
    LaunchedEffect(card, art, style, retry) {
        image = null
        saved = false
        exportFailed = false
        val loaded = art ?: return@LaunchedEffect
        renderFailed = false
        try {
            image = renderStoryPoster(context, card, style, loaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            renderFailed = true
        }
    }

    val save: (Bitmap) -> Unit = { bitmap ->
        busy = true
        exportFailed = false
        scope.launch {
            try {
                saved = saveToGallery(context, bitmap, UUID.randomUUID().toString(), "story-card")
                exportFailed = !saved
            } finally {
                busy = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val bitmap = pendingSave
        pendingSave = null
        if (granted && bitmap != null) save(bitmap)
        else {
            busy = false
            exportFailed = true
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.story_share_title), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800)
        Text(stringResource(R.string.story_share_description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(
            Modifier.fillMaxWidth(0.42f).align(Alignment.CenterHorizontally).aspectRatio(9f / 16f)
                .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = image
            when {
                bitmap != null -> Image(bitmap.asImageBitmap(), stringResource(R.string.story_share_title),
                    modifier = Modifier.fillMaxWidth())
                renderFailed -> Text(stringResource(R.string.couldnt_draw_picture),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                else -> CircularProgressIndicator()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StoryStyle.entries.forEach { option ->
                FilterChip(
                    selected = style == option,
                    onClick = {
                        if (style != option) {
                            image = null
                            saved = false
                            style = option
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(stringResource(when (option) {
                            StoryStyle.ARTWORK -> R.string.story_style_artwork
                            StoryStyle.COLOUR -> R.string.story_style_colour
                            StoryStyle.DARK -> R.string.story_style_dark
                        }), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        if (renderFailed) TextButton(onClick = { art = null; retry++ }) { Text(stringResource(R.string.try_again)) }
        if (exportFailed) Text(stringResource(R.string.story_export_error), color = MaterialTheme.colorScheme.error)

        val ready = image != null && !busy
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareAction(stringResource(R.string.save), Icons.Rounded.Download, accent = false,
                enabled = ready && !saved, saved = saved, savedLabel = stringResource(R.string.saved_bang),
                modifier = Modifier.weight(1f)) {
                val bitmap = image ?: return@ShareAction
                if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    busy = true
                    pendingSave = bitmap
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else save(bitmap)
            }
            ShareAction(stringResource(R.string.share), Icons.Rounded.IosShare, accent = true,
                enabled = ready, modifier = Modifier.weight(1f)) {
                val bitmap = image ?: return@ShareAction
                busy = true
                exportFailed = false
                scope.launch {
                    try {
                        val uri = cacheStoryForSharing(context, bitmap)
                            ?: error("Unable to write image")
                        context.startActivity(Intent.createChooser(sendIntent(uri), context.getString(R.string.story_share_title)))
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        exportFailed = true
                    } finally {
                        busy = false
                    }
                }
            }
        }
        card.songLink()?.let { url ->
            ShareAction(stringResource(R.string.story_share_link), Icons.Rounded.Link, accent = false,
                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                try {
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.story_share_link)))
                    onDismiss()
                } catch (_: Exception) {
                    exportFailed = true
                }
            }
        }
    }
}
