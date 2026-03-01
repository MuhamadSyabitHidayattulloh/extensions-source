package eu.kanade.tachiyomi.extension.id.roseveil

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
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
                    is OrderFilter -> {
                        addQueryParameter("order", filter.toUriPart())
                    }
                    is TypeFilter -> {
                        if (filter.toUriPart().isNotBlank()) {
                            addQueryParameter("subtype", filter.toUriPart())
                        }
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
        author = data.author
        artist = data.artist
        description = data.synopsis
        genre = data.genres.joinToString { it.name }
        status = when (data.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = data.thumbnail
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
                date_upload = dateFormat.tryParse(unit.date)
            }
        }
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
            Page(page.index - 1, "", page.url)
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
                thumbnail_url = item.thumbnail
            }
        }
        return MangasPage(mangas, data.page < data.totalPages)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    // =============================== Filters ======================================
    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        OrderFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    companion object {
        const val SLUG_PREFIX = "slug:"
    }
}
