package com.shadowbooks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMaterialsTest {
    @Test fun goldenEnglishUnitsAreGroupedAndOrdered() {
        val units = StudyMaterialCatalog.goldenEnglishUnits(listOf(
            "02-الوحدة_الثانية.pdf", "01-الوحدة_الأولى.pdf", "notes.txt",
        ))
        assertEquals(listOf("الوحدة الأولى", "الوحدة الثانية"), units.map { it.title })
        assertEquals("study/summaries/${StudyMaterialCatalog.GOLDEN_ENGLISH_FOLDER}/01-الوحدة_الأولى.pdf", units.first().assetPath)
        assertEquals(StudyMaterialCatalog.GOLDEN_ENGLISH_TITLE, StudyMaterialCatalog.goldenEnglishGroup(units)?.title)
        assertEquals(null, StudyMaterialCatalog.goldenEnglishGroup(emptyList()))
    }

    @Test fun onlyNumberedPdfsForKnownSubjectsAreShown() {
        val files = StudyMaterialCatalog.fromAssetNames(
            StudyMaterialKind.Summary,
            listOf("09-chemistry-review.pdf", "13-english-final.pdf", "14-outside-catalog.pdf", "bad-name.pdf", "02-note.txt"),
        )
        assertEquals(listOf(9, 13), files.map { it.bookNumber })
        assertEquals("chemistry-review", files[0].title)
        assertEquals("study/summaries/09-chemistry-review.pdf", files[0].assetPath)
        assertEquals(StudyMaterialKind.Summary, files[0].kind)
    }

    @Test fun examsUseTheirOwnFolderAndCanBeOpenedAsOrdinaryReaderVolumes() {
        val file = StudyMaterialCatalog.fromAssetNames(StudyMaterialKind.Exam, listOf("01-midyear.pdf")).single()
        assertEquals("study/exams/01-midyear.pdf", file.assetPath)
        assertTrue(file.title.contains("midyear"))
        assertEquals("exam", file.kind.name.lowercase())
    }

    @Test fun islamicGroupIncludesFaithFiqhHadithAndSeerahButKeepsQuranSeparate() {
        val files = StudyMaterialCatalog.fromAssetNames(
            StudyMaterialKind.Exam,
            listOf(
                "00-general-islamic-models.pdf",
                "01-quran.pdf",
                "02-faith.pdf",
                "03-fiqh.pdf",
                "04-hadith.pdf",
                "05-seerah.pdf",
                "06-grammar.pdf",
            ),
        ).groupBy(StudyMaterialFile::bookNumber)

        assertEquals(listOf(0, 2, 3, 4, 5), StudyMaterialCatalog.islamicGroupFiles(files).map { it.bookNumber })
        assertTrue(StudyMaterialCatalog.belongsToIslamicGroup(0))
        assertTrue(StudyMaterialCatalog.isIslamicBook(4))
        assertFalse(StudyMaterialCatalog.belongsToIslamicGroup(1))
        assertFalse(StudyMaterialCatalog.isIslamicBook(1))
        assertFalse(StudyMaterialCatalog.belongsToIslamicGroup(6))
        assertTrue(StudyMaterialCatalog.fromAssetNames(StudyMaterialKind.Summary, listOf("00-general-islamic-models.pdf")).isEmpty())
    }

    @Test fun arabicGroupIncludesGrammarReadingAndLiteratureOnly() {
        val files = StudyMaterialCatalog.fromAssetNames(
            StudyMaterialKind.Exam,
            listOf(
                "01-quran.pdf",
                "06-grammar.pdf",
                "07-reading.pdf",
                "08-literature.pdf",
                "09-chemistry.pdf",
            ),
        ).groupBy(StudyMaterialFile::bookNumber)

        assertEquals(listOf(6, 7, 8), StudyMaterialCatalog.arabicGroupFiles(files).map { it.bookNumber })
        assertTrue(StudyMaterialCatalog.isArabicBook(6))
        assertTrue(StudyMaterialCatalog.isArabicBook(7))
        assertTrue(StudyMaterialCatalog.isArabicBook(8))
        assertFalse(StudyMaterialCatalog.isArabicBook(1))
        assertFalse(StudyMaterialCatalog.isArabicBook(9))
    }
}
