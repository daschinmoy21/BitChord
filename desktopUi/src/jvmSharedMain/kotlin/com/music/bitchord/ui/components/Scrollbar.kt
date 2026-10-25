package com.music.bitchord.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * A scrollbar down the right edge of the Box a list fills, to drag with the
 * mouse: the wheel alone is a long way through a big library or playlist.
 * Placed after the list, so it draws over it.
 *
 * [insets] keeps the track out from under the chrome floating over the list,
 * the top bar and the player: usually the list's own content padding. [ink]
 * is the colour the thumb is a faint wash of, the page's text by default.
 */

@Composable
fun BoxScope.PageScrollbar(
    state: LazyListState,
    insets: PaddingValues = PaddingValues(0.dp),
    ink: Color = MaterialTheme.colorScheme.onBackground,
) = PageScrollbar(rememberScrollbarAdapter(state), insets, ink)

@Composable
fun BoxScope.PageScrollbar(
    state: LazyGridState,
    insets: PaddingValues = PaddingValues(0.dp),
    ink: Color = MaterialTheme.colorScheme.onBackground,
) = PageScrollbar(rememberScrollbarAdapter(state), insets, ink)

@Composable
fun BoxScope.PageScrollbar(
    state: ScrollState,
    insets: PaddingValues = PaddingValues(0.dp),
    ink: Color = MaterialTheme.colorScheme.onBackground,
) = PageScrollbar(rememberScrollbarAdapter(state), insets, ink)

@Composable
private fun BoxScope.PageScrollbar(adapter: ScrollbarAdapter, insets: PaddingValues, ink: Color) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .padding(
                top = insets.calculateTopPadding() + 4.dp,
                bottom = insets.calculateBottomPadding() + 4.dp,
                end = 3.dp,
            ),
        style = ScrollbarStyle(
            minimalHeight = 32.dp,
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 150,
            unhoverColor = ink.copy(alpha = 0.25f),
            hoverColor = ink.copy(alpha = 0.5f),
        ),
    )
}
