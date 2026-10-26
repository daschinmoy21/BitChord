package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.lyrics_sync_to_audio
import org.jetbrains.compose.resources.stringResource

/** Manual browsing ends only on request or when a new track opens the panel. */
@Stable
internal class LyricBrowsingState : NestedScrollConnection {
    var isBrowsing by mutableStateOf(false)
        private set

    fun browse() { isBrowsing = true }
    fun syncToAudio() { isBrowsing = false }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Wheel and trackpad input do not emit DragInteraction.Start. Observe
        // them here too, without confusing automatic following with user input.
        if (source == NestedScrollSource.UserInput && available.y != 0f) browse()
        return Offset.Zero
    }
}

@Composable
internal fun LyricsSyncButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF323232),
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.lyrics_sync_to_audio))
    }
}
