package com.shadowbooks.ui.reader

import java.io.File
import java.security.MessageDigest

/**
 * A prepared Arabic companion for one English volume: an index JSON (the
 * clean per-page Arabic text) paired with a typeset Arabic PDF whose pages
 * mirror the English original one to one. Null entries denote sheets kept
 * as-is (already Arabic or blank).
 */
internal class PreparedPupilBook(
    private val sourceSha256: String,
    val arabicSha256: String,
    private val pages: List<String?>,
    private val originalOnly: Set<Int>,
) {
    init {
        val sha256 = Regex("[0-9a-f]{64}")
        require(sourceSha256.matches(sha256) && arabicSha256.matches(sha256))
        require(pages.isNotEmpty() && pages.indices.all { index ->
            if (index in originalOnly) pages[index] == null else !pages[index].isNullOrBlank()
        })
    }

    fun matches(sourceDigest: String, pageCount: Int) = sourceSha256 == sourceDigest && pages.size == pageCount
    fun text(index: Int): String? = pages.getOrNull(index)

    fun toggle(index: Int, arabicPages: IntArray): IntArray = when {
        text(index) == null -> arabicPages
        index in arabicPages -> arabicPages.filterNot { it == index }.toIntArray()
        else -> arabicPages + index
    }

    companion object {
        const val SOURCE = "english-student-book.pdf"
        const val PDF = "reviewed/pupil6-ar.pdf"
        const val INDEX = "reviewed/pupil6-ar.json"

        /** Zero-based PDF indices kept from the original pupil edition. */
        private val PUPIL_ORIGINAL_ONLY = setOf(1, 2, 4, 54, 68, 80)

        /** One prepared translation pair per English volume, when present in the APK. */
        internal data class Companion(
            val sourceFile: String,
            val pdf: String,
            val index: String,
            val originalOnly: Set<Int>,
        )

        internal val COMPANIONS = listOf(
            Companion(SOURCE, PDF, INDEX, PUPIL_ORIGINAL_ONLY),
            Companion(
                "english-workbook.pdf",
                "reviewed/workbook6-ar.pdf",
                "reviewed/workbook6-ar.json",
                emptySet(),
            ),
        )

        fun isCandidate(fileName: String, trial: Boolean) =
            !trial && COMPANIONS.any { it.sourceFile == fileName }

        fun companionFor(fileName: String): Companion? =
            COMPANIONS.firstOrNull { it.sourceFile == fileName }

        fun digest(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    md.update(buffer, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
