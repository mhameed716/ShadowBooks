package com.shadowbooks.ui

import android.content.Context
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.StudyMaterialCatalog
import com.shadowbooks.data.StudyMaterialFile
import com.shadowbooks.data.StudyMaterialKind
import com.shadowbooks.store.StoreContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One search over everything: school books, summaries and exams — whether
 * the file is bundled in the APK, downloaded from the store, or still
 * waiting in the store. Available hits open straight into the reader;
 * store-only hits point at the store instead.
 */
internal data class GlobalSearchHit(
    val key: String,
    val title: String,
    val subject: String,
    val section: String,
    val volume: BookVolume,
    val fromResources: Boolean,
    val available: Boolean,
    val dest: String,
)

internal class GlobalSearchIndex(private val hits: List<GlobalSearchHit>) {
    fun query(raw: String): List<GlobalSearchHit> {
        val needle = normalizeArabic(raw)
        if (needle.length < 2) return emptyList()
        return hits.filter { hit ->
            normalizeArabic("${hit.title} ${hit.subject} ${hit.section}").contains(needle)
        }.take(60)
    }
}

internal suspend fun buildGlobalSearchIndex(context: Context): GlobalSearchIndex =
    withContext(Dispatchers.IO) {
        val hits = ArrayList<GlobalSearchHit>()

        // School books: each volume, openable when downloaded or bundled.
        BookCatalog.books.forEach { book ->
            val volumes = book.volumes.ifEmpty { listOf(BookVolume(book.title, book.fileName)) }
            val available = StoreContent.isBookAvailable(context, book)
            volumes.forEach { volume ->
                hits += GlobalSearchHit(
                    key = "book:${volume.fileName}",
                    title = volume.title,
                    subject = book.title,
                    section = "الكتب",
                    volume = volume,
                    fromResources = false,
                    available = available,
                    dest = "books/${volume.fileName}",
                )
            }
        }

        // Study files present on this device (bundled assets + downloaded).
        fun studyFileHit(
            file: StudyMaterialFile,
            categoryTitle: String,
            available: Boolean,
        ): GlobalSearchHit {
            val dest = "books/${file.assetPath}"
            return GlobalSearchHit(
                key = "study:$dest",
                title = file.title,
                subject = categoryTitle,
                section = file.kind.label,
                volume = BookVolume(
                    "${categoryTitle} — ${file.title}",
                    file.assetPath,
                    "${file.kind.label} · $categoryTitle",
                ),
                fromResources = true,
                available = available,
                dest = dest,
            )
        }

        val seenStudy = HashSet<String>()
        StudyMaterialKind.values().forEach { kind ->
            val names = (
                runCatching { context.assets.list("books/study/${kind.folder}")?.toList() }
                    .getOrNull().orEmpty() +
                    StoreContent.localFileNames(context, "books/study/${kind.folder}")
                ).distinct()
            StudyMaterialCatalog.fromAssetNames(kind, names).forEach { file ->
                val dest = "books/${file.assetPath}"
                if (!seenStudy.add(dest)) return@forEach
                hits += studyFileHit(file, categoryTitleFor(file), available = true)
            }
        }
        val goldenNames = (
            runCatching {
                context.assets.list("books/${StudyMaterialCatalog.GOLDEN_ENGLISH_PATH}")?.toList()
            }.getOrNull().orEmpty() +
                StoreContent.localFileNames(
                    context,
                    "books/${StudyMaterialCatalog.GOLDEN_ENGLISH_PATH}",
                )
            ).distinct()
        StudyMaterialCatalog.goldenEnglishUnits(goldenNames).forEach { unit ->
            val dest = "books/${unit.assetPath}"
            if (seenStudy.add(dest)) {
                hits += studyFileHit(unit, StudyMaterialCatalog.GOLDEN_ENGLISH_TITLE, true)
            }
        }

        // Store-only study files: visible in search, opened after downloading.
        val catalog = StoreContent.cachedCatalog(context) +
            StoreContent.downloadedEntries(context)
        catalog.distinctBy { it.dest }.forEach { entry ->
            if (!entry.dest.startsWith("books/study/") || entry.dest in seenStudy) {
                return@forEach
            }
            val kind = StudyMaterialKind.values()
                .firstOrNull { entry.dest.startsWith("books/study/${it.folder}/") }
                ?: return@forEach
            val fileName = entry.dest.substringAfterLast('/')
            val parsed = if (entry.dest.startsWith("books/${StudyMaterialCatalog.GOLDEN_ENGLISH_PATH}/")) {
                StudyMaterialCatalog.goldenEnglishUnits(listOf(fileName)).firstOrNull()
            } else {
                StudyMaterialCatalog.fromAssetNames(kind, listOf(fileName)).firstOrNull()
            }
            val file = parsed ?: StudyMaterialFile(
                bookNumber = 0,
                kind = kind,
                assetPath = entry.dest.removePrefix("books/"),
                title = fileName.removeSuffix(".pdf").replace('_', ' '),
            )
            hits += studyFileHit(
                file,
                categoryTitleFor(file),
                available = StoreContent.isInstalled(context, entry),
            )
        }

        GlobalSearchIndex(hits)
    }

private fun categoryTitleFor(file: StudyMaterialFile): String = when {
    file.assetPath.startsWith(StudyMaterialCatalog.GOLDEN_ENGLISH_PATH) ->
        StudyMaterialCatalog.GOLDEN_ENGLISH_TITLE
    file.bookNumber == StudyMaterialCatalog.GENERAL_ISLAMIC_NUMBER -> "التربية الإسلامية"
    else -> BookCatalog.books.firstOrNull { it.number == file.bookNumber }?.title ?: file.title
}

private fun com.shadowbooks.data.Book.subjectTitle(): String = title

internal fun normalizeArabic(text: String): String = text
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
    .replace('ة', 'ه')
    .replace('ى', 'ي')
    .replace("_", " ")
    .lowercase()
    .trim()
