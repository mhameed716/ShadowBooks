package com.shadowbooks.ui

import com.shadowbooks.data.progressKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueReadingTest {
    @Test fun welcomeAndCodeGateNeverExposeResume() {
        assertFalse(AppDestination.Welcome.canContinueReading())
        assertFalse(AppDestination.Reader.canContinueReading())
    }

    @Test fun allPostGateBrowsingScreensAllowResumeIncludingDevicePdfs() {
        listOf(
            AppDestination.Library, AppDestination.Timetable, AppDestination.StudyResources,
            AppDestination.DevicePdfs, AppDestination.VolumeChooser, AppDestination.Store,
        ).forEach { assertTrue(it.canContinueReading()) }
    }

    @Test fun documentProgressKeysAreStableAndIndependent() {
        val quran = progressKey("quran-and-its-sciences.pdf", "")
        assertEquals(quran, progressKey("quran-and-its-sciences.pdf", ""))
        assertNotEquals(quran, progressKey("physics.pdf", ""))
        assertNotEquals(progressKey("lesson.pdf", "content://one"), progressKey("lesson.pdf", "content://two"))
        assertNotEquals(progressKey("a", "bc"), progressKey("ab", "c"))
    }
}
