package com.shadowbooks.data

import androidx.annotation.DrawableRes
import com.shadowbooks.R

data class BookVolume(
    val title: String,
    val fileName: String,
    val description: String = "",
    /** Non-null only for user-selected PDFs opened through Android's Storage Access Framework. */
    val contentUri: String? = null,
)

data class Book(
    val number: Int,
    val title: String,
    val fileName: String,
    @DrawableRes val coverRes: Int,
    val volumes: List<BookVolume> = emptyList(),
)

object BookCatalog {
    val books = listOf(
        Book(1, "القرآن الكريم وعلومه", "quran-and-its-sciences.pdf", R.drawable.library_cover_quran),
        Book(2, "الإيمان والتزكية", "faith-and-purification.pdf", R.drawable.library_cover_faith),
        Book(3, "الفقه", "fiqh.pdf", R.drawable.library_cover_fiqh),
        Book(
            number = 4,
            title = "الحديث الشريف",
            fileName = "hadith-part-1.pdf",
            coverRes = R.drawable.library_cover_hadith,
            volumes = listOf(
                BookVolume("الحديث الشريف — الجزء الأول", "hadith-part-1.pdf", "الجزء الأول"),
                BookVolume("الحديث الشريف — الجزء الثاني", "hadith-part-2.pdf", "الجزء الثاني"),
            ),
        ),
        Book(5, "السيرة النبوية", "seerah.pdf", R.drawable.library_cover_seerah),
        Book(6, "النحو والصرف", "grammar-and-morphology.pdf", R.drawable.library_cover_grammar),
        Book(7, "القراءة والتعبير", "reading-and-expression.pdf", R.drawable.library_cover_reading),
        Book(8, "الأدب والنصوص والنقد", "literature-and-criticism.pdf", R.drawable.library_cover_literature),
        Book(9, "الكيمياء", "chemistry.pdf", R.drawable.library_cover_chemistry),
        Book(10, "الفيزياء", "physics.pdf", R.drawable.library_cover_physics),
        Book(11, "الأحياء", "biology.pdf", R.drawable.library_cover_biology),
        Book(12, "الرياضيات", "mathematics.pdf", R.drawable.library_cover_math),
        Book(
            number = 13,
            title = "اللغة الإنجليزية",
            fileName = "english-student-book.pdf",
            coverRes = R.drawable.library_cover_english,
            volumes = listOf(
                BookVolume("اللغة الإنجليزية — كتاب الطالب", "english-student-book.pdf", "الكتاب المدرسي"),
                BookVolume("اللغة الإنجليزية — كتاب الواجب", "english-workbook.pdf", "الواجبات والتدريبات"),
            ),
        ),
    )
}
