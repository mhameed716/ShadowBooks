package com.shadowbooks.data

import androidx.compose.ui.graphics.Color

data class BookVolume(
    val title: String,
    val fileName: String,
    val description: String = "",
)

data class Book(
    val number: Int,
    val title: String,
    val fileName: String,
    val coverTop: Color,
    val coverBottom: Color,
    val accent: Color,
    val volumes: List<BookVolume> = emptyList(),
)

object BookCatalog {
    val books = listOf(
        Book(1, "القرآن الكريم وعلومه", "quran-and-its-sciences.pdf", Color(0xFF197B68), Color(0xFF0B403A), Color(0xFFFFD783)),
        Book(2, "الإيمان والتزكية", "faith-and-purification.pdf", Color(0xFF4266A6), Color(0xFF172D5B), Color(0xFFC2D9FF)),
        Book(3, "الفقه", "fiqh.pdf", Color(0xFF9D6B32), Color(0xFF563519), Color(0xFFFFDB9B)),
        Book(
            number = 4,
            title = "الحديث الشريف",
            fileName = "hadith-part-1.pdf",
            coverTop = Color(0xFF18827D),
            coverBottom = Color(0xFF0D434A),
            accent = Color(0xFFB7F1E9),
            volumes = listOf(
                BookVolume("الحديث الشريف — الجزء الأول", "hadith-part-1.pdf", "الجزء الأول"),
                BookVolume("الحديث الشريف — الجزء الثاني", "hadith-part-2.pdf", "الجزء الثاني"),
            ),
        ),
        Book(5, "السيرة النبوية", "seerah.pdf", Color(0xFFB2763B), Color(0xFF603819), Color(0xFFFFE0A3)),
        Book(6, "النحو والصرف", "grammar-and-morphology.pdf", Color(0xFF7356A6), Color(0xFF35234F), Color(0xFFE3D1FF)),
        Book(7, "القراءة والتعبير", "reading-and-expression.pdf", Color(0xFF3476A8), Color(0xFF173B60), Color(0xFFBEEBFF)),
        Book(8, "الأدب والنصوص والنقد", "literature-and-criticism.pdf", Color(0xFF9B4F63), Color(0xFF512536), Color(0xFFFFC8D5)),
        Book(9, "الكيمياء", "chemistry.pdf", Color(0xFF248779), Color(0xFF104B51), Color(0xFFA9F4DE)),
        Book(10, "الفيزياء", "physics.pdf", Color(0xFF4C65A0), Color(0xFF202C5D), Color(0xFFCAE0FF)),
        Book(11, "الأحياء", "biology.pdf", Color(0xFF56864C), Color(0xFF2D4C2E), Color(0xFFD2F3B5)),
        Book(12, "الرياضيات", "mathematics.pdf", Color(0xFFBC7739), Color(0xFF66401F), Color(0xFFFFD5A0)),
        Book(
            number = 13,
            title = "اللغة الإنجليزية",
            fileName = "english-student-book.pdf",
            coverTop = Color(0xFF416D8B),
            coverBottom = Color(0xFF1D354E),
            accent = Color(0xFFD0EDFF),
            volumes = listOf(
                BookVolume("اللغة الإنجليزية — كتاب الطالب", "english-student-book.pdf", "الكتاب المدرسي"),
                BookVolume("اللغة الإنجليزية — كتاب الواجب", "english-workbook.pdf", "الواجبات والتدريبات"),
            ),
        ),
    )
}
