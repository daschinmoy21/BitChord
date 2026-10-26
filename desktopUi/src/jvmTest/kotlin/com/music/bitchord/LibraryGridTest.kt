package com.music.bitchord

import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.libraryGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGridTest {
    @Test fun wideWindowsFillTheRowBeyondFiveCovers() {
        val compact = libraryGrid(760.dp)
        val wide = libraryGrid(1280.dp)
        assertEquals(5, compact.columns)
        assertTrue(wide.columns > 5)
        assertTrue(wide.cardWidth in 140.dp..180.dp)
    }

    @Test fun resizingKeepsRowsAlignedWithBothPageGutters() {
        for (width in listOf(320, 600, 760, 1000, 1280, 1920, 3000)) {
            val grid = libraryGrid(width.dp)
            val used = grid.cardWidth * grid.columns + LIBRARY_GRID_SPACING * (grid.columns - 1)
            assertEquals(width.toFloat(), used.value, 0.01f)
            assertTrue(grid.cardWidth >= 140.dp)
        }
    }
}
