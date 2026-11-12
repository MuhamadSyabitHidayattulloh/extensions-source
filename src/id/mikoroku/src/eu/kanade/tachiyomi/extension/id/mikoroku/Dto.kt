package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

private val CHAPTER_REGEX = """Chapter\s+(\d+(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)

internal fun String.chapterLabel(): String? = CHAPTER_REGEX.find(this)?.value

internal fun String.chapterNumber(): Double = CHAPTER_REGEX.find(this)?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0

internal fun String.normalized(): String = lowercase().filter { it.isLetterOrDigit() }

@Serializable
class CatalogEntry(
    val title: String,
    val slug: String,
    val altTitle: String = "",
    val img: String = "",
    val desc: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double = 0.0,
    val status: String = "",
    val author: String = "",
    val artist: String = "",
) {
    fun toSManga() = SManga.create().apply {
        title = this@CatalogEntry.title
        thumbnail_url = img.takeIf { it.isNotBlank() }
        url = slug
    }
}

@Serializable
class ApiChaptersResponse(val chapters: List<ApiChapter> = emptyList())

@Serializable
class ApiChapter(
    val num: String,
    val title: String,
    val images: List<String> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
class FirestoreMap(val fields: Map<String, FirestoreValue> = emptyMap())

@Serializable
class FirestoreDoc(
    val name: String = "",
    val fields: Map<String, FirestoreValue> = emptyMap(),
)

@Serializable
class FirestoreValue(
    val stringValue: String? = null,
    val integerValue: String? = null,
    val booleanValue: Boolean? = null,
    val arrayValue: FirestoreArray? = null,
    val mapValue: FirestoreMap? = null,
)

@Serializable
class FirestoreArray(val values: List<FirestoreValue> = emptyList())

internal fun Map<String, FirestoreValue>.getString(key: String): String? = get(key)?.stringValue

internal fun Map<String, FirestoreValue>.getLong(key: String): Long? = get(key)?.integerValue?.toLongOrNull()

internal fun Map<String, FirestoreValue>.getBoolean(key: String): Boolean? = get(key)?.booleanValue

internal fun Map<String, FirestoreValue>.getStringList(key: String): List<String> =
    get(key)?.arrayValue?.values?.mapNotNull { it.stringValue }.orEmpty()

@Serializable
class BloggerEntryResponse(val entry: BloggerEntry)

@Serializable
class BloggerEntry(
    private val content: BloggerText? = null,
) {
    fun imageUrls(): List<String> = Jsoup.parseBodyFragment(content?.text.orEmpty())
        .select("img[src^=http]")
        .map { it.attr("src") }
        .distinct()
}

@Serializable
class BloggerText(@SerialName("\$t") val text: String)
