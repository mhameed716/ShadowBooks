package com.shadowbooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLayoutTest {
    @Test fun wholePageIsCenteredAndNeverCropped() {
        val portrait = layoutPage(300, 600, 0.75f, PageFit.WholePage, 1f)
        assertEquals(300f, portrait.width, 0.01f)
        assertEquals(400f, portrait.height, 0.01f)
        assertEquals(100f, portrait.top, 0.01f)
        val landscape = layoutPage(1000, 600, 0.75f, PageFit.WholePage, 1f)
        assertEquals(600f, landscape.height, 0.01f)
        assertEquals(275f, landscape.left(1000, 1f), 0.01f)
    }

    @Test fun fitWidthAndZoomGrowTheScrollablePageWithoutChangingItsAspect() {
        val page = layoutPage(1000, 600, 0.75f, PageFit.PageWidth, 2f)
        assertEquals(2000f, page.width, 0.01f)
        assertEquals(0.75f, page.width / page.height, 0.0001f)
        assertEquals(page.height, page.slotHeight, 0.01f)
        assertEquals(0f, page.top, 0.01f)
        assertEquals(0f, page.left(1000, -1f), 0.01f)
        assertEquals(-500f, page.left(1000, 0.5f), 0.01f)
        assertEquals(-1000f, page.left(1000, 2f), 0.01f)
    }

    @Test fun unusualPageSizesDoNotOverflowLayoutConstraints() {
        for (ratio in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY, 0.00001f)) {
            val page = layoutPage(1080, 1920, ratio, PageFit.PageWidth, 4f)
            assertTrue(page.width.isFinite() && page.width > 0f)
            assertTrue(page.slotHeight in 1920f..30_001f)
        }
    }

    @Test fun jumpAcceptsTheThreeNumberFormsAndChecksBothEnds() {
        for (text in listOf("24", "٢٤", "۲۴", " ٢4 ")) assertEquals(23, pageIndexForInput(text, 180))
        assertEquals(0, pageIndexForInput("١", 180))
        assertEquals(179, pageIndexForInput("١٨٠", 180))
        assertEquals("١٨٠", 180.arabicDigits())
        for (bad in listOf("", "٠", "١٨١", "-1", "٢٣ب", "٢.٤", "9999999999999")) {
            assertNull(pageIndexForInput(bad, 180))
        }
    }
}
