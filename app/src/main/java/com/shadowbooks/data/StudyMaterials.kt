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
    const val GOLDEN_ENGLISH_FOLDER = "13-المميز_الذهبي_في_اللغة_الإنجليزية"
    const val GOLDEN_ENGLISH_TITLE = "المميز الذهبي في اللغة الإنجليزية"
    const val GOLDEN_ENGLISH_PATH = "$ROOT/summaries/$GOLDEN_ENGLISH_FOLDER"

    /** Unit filenames start with a two-digit ordering prefix. */
    fun goldenEnglishUnits(fileNames: List<String>): List<StudyMaterialFile> =
        fileNames.mapNotNull { name ->
            val match = numberedPdf.matchEntire(name) ?: return@mapNotNull null
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (number < 1) return@mapNotNull null
            StudyMaterialFile(13, StudyMaterialKind.Summary, "$GOLDEN_ENGLISH_PATH/$name", match.groupValues[2].replace('_', ' ')) to number
        }.sortedBy { it.second }.map { it.first }

    fun goldenEnglishGroup(units: List<StudyMaterialFile>): StudyMaterialFile? =
        if (units.isEmpty()) null else StudyMaterialFile(13, StudyMaterialKind.Summary, GOLDEN_ENGLISH_PATH, GOLDEN_ENGLISH_TITLE)
    private val islamicBookNumbers = setOf(2, 3, 4, 5)
    private val numberedPdf = Regex("^(\\d{1,2})[-_](.+)\\.pdf$", RegexOption.IGNORE_CASE)

    fun isIslamicBook(number: Int): Boolean = number in islamicBookNumbers

    fun belongsToIslamicGroup(number: Int): Boolean =
        number == GENERAL_ISLAMIC_NUMBER || isIslamicBook(number)

    /** Combine general Islamic PDFs (00) with faith, fiqh, hadith, and seerah (02–05); keep Quran (01) separate. */
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
