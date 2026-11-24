package com.shadowbooks.ui.reader

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PreparedPupilBookTest {
    private val digest = "ab".repeat(32)
    private val pages = List(81) { if (it in setOf(1, 2, 4, 54, 68, 80)) null else "صفحة ${it + 1}" }
    private val book = PreparedPupilBook(digest, "cd".repeat(32), pages)

    @Test fun wrongEditionOrCountCannotEnableTheCompanion() {
        assertTrue(book.matches(digest, 81))
        assertFalse(book.matches(digest, 5))
        assertFalse(book.matches("aa".repeat(32), 81))
        assertThrows(IllegalArgumentException::class.java) { PreparedPupilBook(digest, "", pages) }
        assertThrows(IllegalArgumentException::class.java) { PreparedPupilBook(digest, digest, pages.take(5)) }
    }

    @Test fun indexRequiresEveryLessonButNotEmptySeparators() {
        for ((i, badText) in listOf(6 to null, 79 to "  ", 1 to "Text on a separator")) {
            val broken = pages.toMutableList().apply { set(i, badText) }
            assertThrows(IllegalArgumentException::class.java) { PreparedPupilBook(digest, digest, broken) }
        }
    }

    @Test fun pageTogglesAreIndependentAndDoNotChangeThePreviousState() {
        val lost = book.toggle(6, intArrayOf())
        val two = book.toggle(79, lost)
        assertArrayEquals(intArrayOf(6), lost)
        assertArrayEquals(intArrayOf(6, 79), two)
        assertArrayEquals(intArrayOf(79), book.toggle(6, two))
        for (i in listOf(-1, 1, 2, 4, 54, 68, 80, 81)) {
            assertNull(book.text(i))
            assertArrayEquals(two, book.toggle(i, two))
        }
        assertEquals("صفحة 7", book.text(6))
        assertEquals("صفحة 80", book.text(79))
    }

    @Test fun digestsMeasureFileContents() {
        val f = File.createTempFile("pupil", ".pdf")
        try {
            f.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PreparedPupilBook.digest(f))
        } finally { f.delete() }
    }
}
