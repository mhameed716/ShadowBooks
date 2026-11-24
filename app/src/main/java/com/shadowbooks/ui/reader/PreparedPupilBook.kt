package com.shadowbooks.ui.reader

import java.io.File
import java.security.MessageDigest

/** The index and PDF are generated together; null entries denote already-Arabic or blank sheets. */
internal class PreparedPupilBook(
    private val sourceSha256: String,
    val arabicSha256: String,
    private val pages: List<String?>,
) {
    init {
        val sha256 = Regex("[0-9a-f]{64}")
        require(sourceSha256.matches(sha256) && arabicSha256.matches(sha256))
        require(pages.size == 81 && pages.indices.all { index ->
            if (index in ORIGINAL_ONLY) pages[index] == null else !pages[index].isNullOrBlank()
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
        private val ORIGINAL_ONLY = setOf(1, 2, 4, 54, 68, 80) // Zero-based PDF indices.
        const val SOURCE = "english-student-book.pdf"
        const val PDF = "reviewed/pupil6-ar.pdf"
        const val INDEX = "reviewed/pupil6-ar.json"

        fun isCandidate(fileName: String, trial: Boolean) = !trial && fileName == SOURCE

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
