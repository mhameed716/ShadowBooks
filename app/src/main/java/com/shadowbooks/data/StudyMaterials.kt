package com.shadowbooks.data

/** Study PDFs are optional. Add them under app/src/main/assets/books/study/<folder>/. */
enum class StudyMaterialKind(val folder: String, val label: String) {
    Summary("summaries", "الملخصات"),
    Exam("exams", "الاختبارات"),
}

data class StudyMaterialFile(
    val bookNumber: Int,
    val kind: StudyMaterialKind,
    val assetPath: String,
    val title: String,
)

object StudyMaterialCatalog {
    /** Prefix 00 is reserved for a general Islamic-education resource shared by religious books. */
    const val GENERAL_ISLAMIC_NUMBER = 0

    private const val ROOT = "study"
    private val islamicBookNumbers = setOf(1, 2, 3, 4, 5)
    private val numberedPdf = Regex("^(\\d{1,2})[-_](.+)\\.pdf$", RegexOption.IGNORE_CASE)

    fun isIslamicBook(number: Int): Boolean = number in islamicBookNumbers

    fun belongsToIslamicGroup(number: Int): Boolean =
        number == GENERAL_ISLAMIC_NUMBER || isIslamicBook(number)

    /** Combine general Islamic PDFs (00) with Quran, faith, fiqh, hadith, and seerah (01–05). */
    fun islamicGroupFiles(filesByBook: Map<Int, List<StudyMaterialFile>>): List<StudyMaterialFile> =
        filesByBook.filterKeys(::belongsToIslamicGroup)
            .values.flatten()
            .sortedWith(compareBy(StudyMaterialFile::bookNumber, StudyMaterialFile::title))

    /** Prefix 00 is a shared Islamic-education resource; other prefixes must match the catalogue. */
    fun fromAssetNames(kind: StudyMaterialKind, fileNames: List<String>): List<StudyMaterialFile> =
        fileNames.mapNotNull { name ->
            val match = numberedPdf.matchEntire(name) ?: return@mapNotNull null
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (number == GENERAL_ISLAMIC_NUMBER && kind != StudyMaterialKind.Exam) return@mapNotNull null
            val book = BookCatalog.books.firstOrNull { it.number == number }
            if (number != GENERAL_ISLAMIC_NUMBER && book == null) return@mapNotNull null
            val title = match.groupValues[2].replace('_', ' ').trim().ifBlank { book?.title ?: "الإسلامية" }
            StudyMaterialFile(
                bookNumber = number,
                kind = kind,
                assetPath = "$ROOT/${kind.folder}/$name",
                title = title,
            )
        }.sortedWith(compareBy(StudyMaterialFile::bookNumber, StudyMaterialFile::title))
}
