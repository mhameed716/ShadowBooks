package com.shadowbooks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePdfLibraryTest {
    @Test fun selectedDocumentBecomesAReaderVolumeWithItsPersistedUri() {
        val entry = DevicePdfEntry("content://downloads/document/42", "lesson.pdf")
        val volume = entry.asVolume()

        assertEquals("lesson.pdf", volume.title)
        assertEquals(entry.uri, volume.contentUri)
        assertTrue(volume.fileName.startsWith("device-pdf-"))
    }

    @Test fun readerKeyIsStableAndDifferentUrisGetDifferentKeys() {
        val first = DevicePdfEntry("content://documents/one", "one.pdf")
        val same = DevicePdfEntry("content://documents/one", "renamed.pdf")
        val other = DevicePdfEntry("content://documents/two", "two.pdf")

        assertEquals(first.readerKey, same.readerKey)
        assertNotEquals(first.readerKey, other.readerKey)
    }
}
