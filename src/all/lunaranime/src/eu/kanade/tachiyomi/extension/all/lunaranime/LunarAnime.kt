package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LunarAnime : HttpSource() {

    override val name = "Lunar Manga"

    override val baseUrl = "https://lunaranime.ru"

    private val apiUrl = "https://api.lunaranime.ru"

    override val lang = "all"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "30")
            .addQueryParameter("sort", SORT_POPULAR)
            .build()
        return GET(url.toString(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "30")
            .addQueryParameter("sort", SORT_LATEST)
            .build()
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "30")

        if (query.isNotBlank()) {
            url.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    if (query.isBlank()) {
                        url.addQueryParameter("sort", filter.toValue())
                    } else if (filter.toValue() != SORT_POPULAR) {
                        url.addQueryParameter("sort", filter.toValue())
                    }
                }
                is StatusFilter -> {
                    filter.toValue()?.let { url.addQueryParameter("publication_status", it) }
                }
                is FormatFilter -> {
                    filter.toValue()?.let { url.addQueryParameter("format", it) }
                }
                is CountryFilter -> {
                    filter.toValue()?.let { url.addQueryParameter("original_language", it) }
                }
                is YearFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("publication_year", filter.state)
                    }
                }
                is ScanGroupFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("scan_group", filter.state)
                    }
                }
                is AuthorFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is ArtistFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("artist", filter.state)
                    }
                }
                is LanguageFilter -> {
                    val langs = filter.toLanguages()
                    if (langs.isNotEmpty()) {
                        url.addQueryParameter("translated_languages", langs.joinToString(","))
                    }
                }
                is GenreFilter -> {
                    val genres = filter.toGenres()
                    if (genres.isNotEmpty()) {
                        url.addQueryParameter("genres", genres.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        return MangasPage(
            mangas = result.manga.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/api/manga/search?limit=1&query=${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SearchResponse>()
        val dto = result.manga.find { it.slug == response.request.url.queryParameter("query") }
            ?: result.manga.firstOrNull()
            ?: throw Exception("Manga not found")
        return dto.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/api/manga/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListResponse>()
        val slug = response.request.url.pathSegments.last()
        return result.data.map { chapter ->
            SChapter.create().apply {
                url = "$slug/${formatChapterNumber(chapter.number)}?lang=${chapter.language}"
                name = "Chapter ${formatChapterNumber(chapter.number)}${if (chapter.title.isNullOrBlank()) "" else ": ${chapter.title}"} [${chapter.language.uppercase(Locale.ROOT)}]"
                chapter_number = chapter.number
                date_upload = chapter.uploadedAt?.let { parseDate(it) } ?: 0L
            }
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    // ============================== Pages ==============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/api/manga/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListResponse>()
        return result.data.images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        FormatFilter(),
        CountryFilter(),
        GenreFilter(),
        LanguageFilter(),
        YearFilter(),
        ScanGroupFilter(),
        AuthorFilter(),
        ArtistFilter(),
    )

    // ============================== Helpers ==============================

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        title = this@toSManga.title
        thumbnail_url = coverUrl
        url = slug
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.description?.let { Jsoup.parse(it).text() }
        genre = genres?.removePrefix("[")?.removeSuffix("]")?.replace("\"", "")?.replace(",", ", ")
        status = this@toSManga.status.toMangaStatus()
        initialized = true
    }

    private fun String?.toMangaStatus(): Int = when (this?.lowercase(Locale.ROOT)) {
        "releasing", "ongoing" -> SManga.ONGOING
        "finished", "completed" -> SManga.COMPLETED
        "not_yet_released", "upcoming" -> SManga.UNKNOWN
        "cancelled" -> SManga.CANCELLED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun formatChapterNumber(number: Float): String {
        val asInt = number.toInt()
        return if (asInt.toFloat() == number) {
            asInt.toString()
        } else {
            number.toString()
        }
    }

    private fun parseDate(dateStr: String): Long = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.tryParse(dateStr) ?: 0L
}
