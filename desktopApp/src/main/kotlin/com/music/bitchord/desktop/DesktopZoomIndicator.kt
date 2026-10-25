package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * "Zoom 110%" for a moment after a keyboard zoom, then gone.
 *
 * It sits over the whole window and takes no pointer input, so it never blocks the page beneath.
 */
@Composable
internal fun DesktopZoomIndicator(modifier: Modifier = Modifier) {
    val scale by DesktopUiScale.scale.collectAsState()
    val zoomed by DesktopUiScale.keyboardZooms.collectAsState()
    var shown by remember { mutableStateOf(false) }
    // Restarting on each zoom, so a run of presses keeps it up until the last one has had its time.
    LaunchedEffect(zoomed) {
        if (zoomed == 0L) return@LaunchedEffect
        shown = true
        delay(SHOW_MS)
        shown = false
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                DesktopStrings.format(
                    "d_zoom_level",
                    (scale * 100).roundToInt(),
                    fallback = "Zoom %1\$d%%",
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** How long the indicator stays fully shown before it starts to fade. */
private const val SHOW_MS = 1_200L
