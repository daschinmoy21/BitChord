package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUiScaleTest {
    @Test
    fun `scale snaps to a valid step including rounded and out of range input`() {
        assertEquals(1f, DesktopUiScale.nearestStep(1.0000001f))
        assertEquals(1.25f, DesktopUiScale.nearestStep(1.24f))
        assertEquals(0.7f, DesktopUiScale.nearestStep(-10f))
        assertEquals(2f, DesktopUiScale.nearestStep(10f))
    }

    @Test
    fun `keyboard zoom counts at the limit while settings do not trigger it`() {
        val original = DesktopUiScale.scale.value
        try {
            val before = DesktopUiScale.keyboardZooms.value
            DesktopUiScale.select(2f)
            assertEquals(before, DesktopUiScale.keyboardZooms.value)
            DesktopUiScale.zoomIn()
            assertEquals(2f, DesktopUiScale.scale.value)
            assertEquals(before + 1, DesktopUiScale.keyboardZooms.value)
            DesktopUiScale.zoomOut()
            assertEquals(1.75f, DesktopUiScale.scale.value)
            DesktopUiScale.reset()
            assertEquals(1f, DesktopUiScale.scale.value)
            assertEquals(before + 3, DesktopUiScale.keyboardZooms.value)
        } finally {
            DesktopUiScale.select(original)
        }
    }
}
