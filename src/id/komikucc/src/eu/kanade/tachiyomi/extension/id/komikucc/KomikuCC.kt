package eu.kanade.tachiyomi.extension.id.komikucc

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class KomikuCC : HttpSource() {
    override val name = "Komiku.cc"

    override val baseUrl = "https://komiku.cc"

    private val cdnUrl = "https://cdn.komiku.cc"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiHeaders by lazy {
        headersBuilder()
            .add("Rsc", "1")
            .build()
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/list".toHttpUrl().newBuilder().apply {
            addQueryParameter("order", "popular")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/list".toHttpUrl().newBuilder().apply {
            addQueryParameter("order", "latest")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                if (page > 1) {
                    addQueryParameter("page", page.toString())
                }
            }.build()
        } else {
            "$baseUrl/list".toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addQueryParameter("page", page.toString())
                }
                filters.filterIsInstance<UriFilter>().forEach {
                    it.addToUri(this)
                }
            }.build()
        }
        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    private fun mangaListParse(response: Response): MangasPage {
        val dto = response.extractNextJs<MangaListResponseDto>() ?: return MangasPage(emptyList(), false)

        val mangas = dto.manga.data.map {
            SManga.create().apply {
                title = it.title
                url = "/komik/${it.link}"
                thumbnail_url = it.img?.let { img -> "$cdnUrl/$img" }
            }
        }

        return MangasPage(mangas, dto.manga.currentPage < dto.manga.lastPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.extractNextJs<MangaDetailsResponseDto>()?.manga ?: throw Exception("Manga not found")

        return SManga.create().apply {
            title = dto.title
            thumbnail_url = dto.img?.let { img -> "$cdnUrl/$img" }
            author = dto.author
            genre = dto.genre?.joinToString { it.title }
            status = parseStatus(dto.status)
            description = dto.des
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", true) -> SManga.ONGOING
        status.contains("completed", true) -> SManga.COMPLETED
        status.contains("hiatus", true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ===============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.extractNextJs<MangaDetailsResponseDto>() ?: return emptyList()

        return dto.chapters?.map {
            SChapter.create().apply {
                name = it.title
                url = "/${it.link}"
                date_upload = dateFormat.tryParse(it.createdAt)
            }
        } ?: emptyList()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, apiHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.extractNextJs<ChapterResponseDto>() ?: return emptyList()

        return dto.data.chapter.images?.mapIndexed { i, img ->
            Page(i, "", "$cdnUrl/$img")
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        StatusFilter(),
        TypeFilter(),
        OrderFilter(),
        GenreFilter(genreList),
    )
}
