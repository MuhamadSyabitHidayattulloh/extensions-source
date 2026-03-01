package eu.kanade.tachiyomi.extension.id.roseveil

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Roseveil : HttpSource() {

    override val name = "Roseveil"

    override val baseUrl = "https://roseveil.org"

    private val apiUrl = "https://api.roseveil.org/api"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular & Latest ==============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "COMIC")
            addQueryParameter("limit", "20")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "views")
            addQueryParameter("order", "desc")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "COMIC")
            addQueryParameter("limit", "20")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "new")
            addQueryParameter("order", "desc")
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // =============================== Search =======================================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(SLUG_PREFIX)) {
            val slug = query.substringAfter(SLUG_PREFIX)
            val manga = SManga.create().apply { url = "/comic/$slug" }
            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it.apply { url = "/comic/$slug" }), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "COMIC")
            addQueryParameter("limit", "20")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (filter.toUriPart().isNotBlank()) {
                            addQueryParameter("status", filter.toUriPart())
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("sort", filter.toUriPart())
                    }
                    is GenreFilter -> {
                        if (filter.toUriPart().isNotBlank()) {
                            addQueryParameter("genre", filter.toUriPart())
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // =============================== Manga Details ================================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/series/comic/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val data = response.parseAs<MangaDetailDto>()
        title = data.title
        author = data.author_name
        artist = data.artist_name
        description = data.synopsis
        genre = data.genres.joinToString { it.name }
        status = when (data.comic_status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "ONHOLD" -> SManga.ON_HIATUS
            "CANCELED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = data.poster_image_url
        initialized = true
    }

    // =============================== Chapters =====================================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaDetailDto>()
        val seriesSlug = data.slug
        return data.units.map { unit ->
            SChapter.create().apply {
                url = "/comic/$seriesSlug/${unit.slug}"
                name = unit.title
                chapter_number = unit.number.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(unit.created_at)
            }
        }.reversed()
    }

    // =============================== Page List ====================================
    override fun pageListRequest(chapter: SChapter): Request {
        val seriesSlug = chapter.url.split("/")[2]
        val chapterSlug = chapter.url.split("/")[3]
        return GET("$apiUrl/series/comic/$seriesSlug/chapter/$chapterSlug", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListDto>()
        return data.chapter.pages.map { page ->
            Page(page.page_number - 1, "", page.image_url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Utilities ====================================
    private fun parseMangaPage(response: Response): MangasPage {
        val data = response.parseAs<SearchResponseDto>()
        val mangas = data.data.map { item ->
            SManga.create().apply {
                url = "/comic/${item.slug}"
                title = item.title
                thumbnail_url = item.poster_image_url
            }
        }
        return MangasPage(mangas, data.page < data.total_pages)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    // =============================== Filters ======================================
    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private class SortFilter :
        UriPartFilter(
            "Urutkan Berdasarkan",
            arrayOf(
                Pair("Paling Baru", "new"),
                Pair("Paling Banyak Dilihat", "views"),
                Pair("Rating Terbaik", "rating"),
                Pair("A-Z", "title"),
            ),
        )

    private class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                Pair("Semua", ""),
                Pair("Ongoing", "ONGOING"),
                Pair("Completed", "COMPLETED"),
            ),
        )

    private class GenreFilter :
        UriPartFilter(
            "Genre",
            arrayOf(
                Pair("Semua", ""),
                Pair("Action", "action"),
                Pair("Adult", "adult"),
                Pair("Adventure", "adventure"),
                Pair("Animals", "animals"),
                Pair("Boys Love", "boys-love"),
                Pair("Comedy", "comedy"),
                Pair("Crime", "crime"),
                Pair("Demon", "demon"),
                Pair("Drama", "drama"),
                Pair("Ecchi", "ecchi"),
                Pair("Fantasy", "fantasy"),
                Pair("Game", "game"),
                Pair("Gender Bender", "gender-bender"),
                Pair("Harem", "harem"),
                Pair("Historical", "historical"),
                Pair("Horror", "horror"),
                Pair("Isekai", "isekai"),
                Pair("Josei", "josei"),
                Pair("Magic", "magic"),
                Pair("Manhwa", "manhwa"),
                Pair("Martial Arts", "martial-arts"),
                Pair("Mature", "mature"),
                Pair("Medical", "medical"),
                Pair("Mirror", "mirror"),
                Pair("Mystery", "mystery"),
                Pair("Office Workers", "office-workers"),
                Pair("Project", "project"),
                Pair("Psychological", "psychological"),
                Pair("Regression", "regression"),
                Pair("Reincarnation", "reincarnation"),
                Pair("Revenge", "revenge"),
                Pair("Reverse Harem", "reverse-harem"),
                Pair("Romance", "romance"),
                Pair("Royalty", "royalty"),
                Pair("School Life", "school-life"),
                Pair("Sci Fi", "sci-fi"),
                Pair("Seinen", "seinen"),
                Pair("Shoujo", "shoujo"),
                Pair("Shounen", "shounen"),
                Pair("Shounen Ai", "shounen-ai"),
                Pair("Slice Of Life", "slice-of-life"),
                Pair("Smut", "smut"),
                Pair("Super Power", "super-power"),
                Pair("Supernatural", "supernatural"),
                Pair("Survival", "survival"),
                Pair("Thriller", "thriller"),
                Pair("Transmigration", "transmigration"),
                Pair("Yaoi", "yaoi"),
            ),
        )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // =============================== DTOs =========================================
    @Serializable
    data class SearchResponseDto(
        val data: List<MangaItemDto>,
        val page: Int,
        val total_pages: Int,
    )

    @Serializable
    data class MangaItemDto(
        val title: String,
        val slug: String,
        val poster_image_url: String? = null,
    )

    @Serializable
    data class MangaDetailDto(
        val title: String,
        val slug: String,
        val synopsis: String? = null,
        val poster_image_url: String? = null,
        val author_name: String? = null,
        val artist_name: String? = null,
        val comic_status: String? = null,
        val genres: List<GenreDto> = emptyList(),
        val units: List<ChapterUnitDto> = emptyList(),
    )

    @Serializable
    data class GenreDto(
        val name: String,
    )

    @Serializable
    data class ChapterUnitDto(
        val title: String,
        val slug: String,
        val number: String,
        val created_at: String? = null,
    )

    @Serializable
    data class PageListDto(
        val chapter: ChapterDetailDto,
    )

    @Serializable
    data class ChapterDetailDto(
        val pages: List<PageDto>,
    )

    @Serializable
    data class PageDto(
        val page_number: Int,
        val image_url: String,
    )

    companion object {
        const val SLUG_PREFIX = "slug:"
    }
}
