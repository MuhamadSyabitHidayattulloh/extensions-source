package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class EncryptedResponseDto(
    @SerialName("_enc_resp_") val encResp: String,
)

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    @SerialName("alt_titles") private val altTitles: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val serialization: String? = null,
    val chapters: List<ChapterDto>? = null,
    private val terms: String? = null,
    @SerialName("term_list") private val termList: String? = null,
    @SerialName("manga_genres") private val mangaGenres: List<MangaGenreDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        url = "/manga/$slug"
        thumbnail_url = coverUrl
        description = buildString {
            this@MangaDto.description?.let { append(Jsoup.parseBodyFragment(it).text()).append("\n\n") }
            if (!altTitles.isNullOrEmpty()) append("Judul Alternatif: ").append(altTitles).append("\n")
            if (!serialization.isNullOrEmpty()) append("Serialisasi: ").append(serialization).append("\n")
            if (!type.isNullOrEmpty()) append("Tipe: ").append(type.replaceFirstChar { it.uppercase() })
        }
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "finished", "published" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = this@MangaDto.author ?: artist
        genre = mangaGenres?.joinToString { it.genres.name }
            ?: (termList ?: terms)?.split(Regex("[,|]"))
                ?.filter { it.contains(":genre") }
                ?.joinToString { it.substringBefore(":") }
    }
}

@Serializable
class MangaGenreDto(
    val genres: GenreDto,
)

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("chapter_number") private val chapterNumber: Double,
    private val title: String? = null,
    @SerialName("content_urls") val contentUrls: List<String>? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/chapters/$id"
        name = title ?: "Chapter ${chapterNumber.toString().removeSuffix(".0")}"
        chapter_number = chapterNumber.toFloat()
        date_upload = dateFormat.tryParse(createdAt)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

@Serializable
class GenreDto(
    val name: String,
)
