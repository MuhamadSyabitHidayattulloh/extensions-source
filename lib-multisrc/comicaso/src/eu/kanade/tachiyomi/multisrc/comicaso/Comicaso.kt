package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable

abstract class Comicaso(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val pageSize: Int = 12,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private var cachedMangaList: List<MangaDto>? = null

    private fun getMangaList(): Observable<List<MangaDto>> = if (cachedMangaList != null) {
        Observable.just(cachedMangaList!!)
    } else {
        client.newCall(GET("$baseUrl/wp-content/static/manga/index.json", headers))
            .asObservableSuccess()
            .map { response ->
                response.parseAs<List<MangaDto>>().also {
                    cachedMangaList = it
                }
            }
    }

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return getMangaList().map { mangas ->
            val start = (page - 1) * pageSize
            if (start >= mangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + pageSize, mangas.size)
            MangasPage(mangas.subList(start, end).map { it.toSManga() }, end < mangas.size)
        }
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return getMangaList().map { mangas ->
            val sortedMangas = mangas.sortedByDescending { it.updatedAt ?: it.mangaDate ?: 0L }
            val start = (page - 1) * pageSize
            if (start >= sortedMangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + pageSize, sortedMangas.size)
            MangasPage(sortedMangas.subList(start, end).map { it.toSManga() }, end < sortedMangas.size)
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val url = query.removePrefix(URL_SEARCH_PREFIX).trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            return fetchMangaDetails(SManga.create().apply { this.url = mangaUrl })
                .map { MangasPage(listOf(it), false) }
        }

        if (query.startsWith("http://") || query.startsWith("https://")) {
            val url = query.trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            return fetchMangaDetails(SManga.create().apply { this.url = mangaUrl })
                .map { MangasPage(listOf(it), false) }
        }

        return getMangaList().map { mangas ->
            var filteredMangas = mangas

            if (query.isNotEmpty()) {
                filteredMangas = filteredMangas.filter { it.title.contains(query, ignoreCase = true) }
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            val genre = filter.values[filter.state]
                            filteredMangas = filteredMangas.filter { it.genres?.contains(genre) == true }
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            val status = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.status == status || (status == "completed" && it.status == "end") }
                        }
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            val type = filter.values[filter.state].lowercase()
                            filteredMangas = filteredMangas.filter { it.type == type }
                        }
                    }
                    else -> {}
                }
            }

            val start = (page - 1) * pageSize
            if (start >= filteredMangas.size) return@map MangasPage(emptyList(), false)
            val end = minOf(start + pageSize, filteredMangas.size)
            MangasPage(filteredMangas.subList(start, end).map { it.toSManga() }, end < filteredMangas.size)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")
        return GET("$baseUrl/wp-content/static/manga/$slug.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailDto>()
        return SManga.create().apply {
            url = "/komik/${result.slug}/"
            title = result.title
            thumbnail_url = result.thumbnail
            description = buildString {
                result.synopsis?.let { append(Jsoup.parse(it).text()) }
                result.alternative?.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative: $it")
                }
            }
            author = result.author
            artist = result.artist
            genre = result.genres?.joinToString()
            status = when (result.status) {
                "on-going" -> SManga.ONGOING
                "completed", "end" -> SManga.COMPLETED
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailDto>()
        val mangaUrl = "/komik/${result.slug}/"
        return result.chapters?.map { it.toSChapter(mangaUrl) }?.reversed() ?: emptyList()
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.mjv2-page-image").mapIndexed { index, img ->
            val imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(index, document.location(), imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filter
    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(Filter.Header("Pencarian teks, genre, status, dan tipe dapat dikombinasikan."))
        filters.add(Filter.Separator())

        cachedMangaList?.let { mangas ->
            val genres = mangas.flatMap { it.genres ?: emptyList() }.distinct().sorted()
            val statuses = mangas.mapNotNull { it.status }.distinct().map { it.replaceFirstChar { c -> c.uppercase() } }.sorted()
            val types = mangas.mapNotNull { it.type }.distinct().map { it.replaceFirstChar { c -> c.uppercase() } }.sorted()

            if (genres.isNotEmpty()) filters.add(GenreFilter(arrayOf("All") + genres.toTypedArray()))
            if (statuses.isNotEmpty()) filters.add(StatusFilter(arrayOf("All") + statuses.toTypedArray()))
            if (types.isNotEmpty()) filters.add(TypeFilter(arrayOf("All") + types.toTypedArray()))
        } ?: filters.add(Filter.Header("Tekan 'Reset' untuk memuat filter"))

        return FilterList(filters)
    }

    protected class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)
    protected class StatusFilter(statuses: Array<String>) : Filter.Select<String>("Status", statuses)
    protected class TypeFilter(types: Array<String>) : Filter.Select<String>("Type", types)

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
    }
}
