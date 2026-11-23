package com.shadowbooks.data

import com.shadowbooks.ui.reader.PreparedPupilBook
import com.shadowbooks.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCatalogTest {
    @Test fun shelvesFollowCatalogueOrderWithEnglishAloneAtTheEnd() {
        val books = BookCatalog.books
        assertEquals((1..13).toList(), books.map { it.number })
        assertEquals(listOf(3, 3, 3, 3, 1), books.chunked(3).map { it.size })
        assertEquals(
            listOf(
                R.drawable.library_cover_quran, R.drawable.library_cover_faith, R.drawable.library_cover_fiqh,
                R.drawable.library_cover_hadith, R.drawable.library_cover_seerah, R.drawable.library_cover_grammar,
                R.drawable.library_cover_reading, R.drawable.library_cover_literature, R.drawable.library_cover_chemistry,
                R.drawable.library_cover_physics, R.drawable.library_cover_biology, R.drawable.library_cover_math,
                R.drawable.library_cover_english,
            ),
            books.map { it.coverRes },
        )
    }

    @Test fun onlyFullStudentVolumeIsAPreparedTranslationCandidate() {
        val files = BookCatalog.books.flatMap { b -> b.volumes.map { it.fileName }.ifEmpty { listOf(b.fileName) } }
        assertEquals(listOf("english-student-book.pdf"), files.filter { PreparedPupilBook.isCandidate(it, trial = false) })
        assertEquals(emptyList<String>(), files.filter { PreparedPupilBook.isCandidate(it, trial = true) })
        assertEquals(false, PreparedPupilBook.isCandidate("reader-sample.pdf", trial = false))
    }

    @Test fun thirteenCoversStillAddressFifteenDistinctVolumes() {
        val books = BookCatalog.books
        assertEquals(
            mapOf(
                4 to listOf("hadith-part-1.pdf", "hadith-part-2.pdf"),
                13 to listOf("english-student-book.pdf", "english-workbook.pdf"),
            ),
            books.filter { it.volumes.isNotEmpty() }.associate { book ->
                book.number to book.volumes.map { it.fileName }
            },
        )
        val files = books.flatMap { book -> book.volumes.map { it.fileName }.ifEmpty { listOf(book.fileName) } }
        assertEquals(15, files.size)
        assertEquals(files.size, files.distinct().size)
    }
}
