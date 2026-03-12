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
import rx.Observable

abstract class Comicaso(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    protected val pageSize: Int = 12,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/wp-content/static/manga/index.json", headers)

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = client.newCall(popularMangaRequest(page)).asObservableSuccess().map { response ->
        response.parseAs<List<MangaDto>>()
            .toMangasPage(page) { it.manga_date }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map { response ->
        response.parseAs<List<MangaDto>>()
            .toMangasPage(page) { it.updated_at }
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX) || query.startsWith("http")) {
            val url = query.removePrefix(URL_SEARCH_PREFIX).trim().replace(baseUrl, "")
            val mangaUrl = if (url.startsWith("/")) url else "/$url"
            return fetchMangaDetails(SManga.create().apply { this.url = mangaUrl })
                .map { MangasPage(listOf(it), false) }
        }

        return client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess().map { response ->
            var result = response.parseAs<List<MangaDto>>()

            if (query.isNotEmpty()) {
                result = result.filter { it.title.contains(query, ignoreCase = true) }
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            val genre = filter.values[filter.state]
                            result = result.filter { it.genres.any { g -> g.contains(genre, ignoreCase = true) } }
                        }
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            val type = filter.values[filter.state].lowercase()
                            result = result.filter { it.type == type }
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            val status = filter.values[filter.state].lowercase()
                            result = result.filter { it.status == status }
                        }
                    }
                    else -> {}
                }
            }

            result.toMangasPage(page) { it.updated_at }
        }
    }

    private fun List<MangaDto>.toMangasPage(page: Int, sortSelector: ((MangaDto) -> Long?)? = null): MangasPage {
        val filtered = this.filter { it.slug.isNotEmpty() }
        val sorted = if (sortSelector != null) filtered.sortedByDescending(sortSelector) else filtered
        val chunked = sorted.chunked(pageSize)

        return if (chunked.isEmpty() || page > chunked.size) {
            MangasPage(emptyList(), false)
        } else {
            MangasPage(chunked[page - 1].map { it.toSManga() }, page < chunked.size)
        }
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removeSurrounding("/komik/", "/")
        return GET("$baseUrl/wp-content/static/manga/$slug.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga()

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDto>()
        return result.chapters.map { it.toSChapter(result.slug) }.reversed()
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
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filter akan diabaikan jika ada pencarian teks"),
        Filter.Separator(),
        GenreFilter(getGenreList()),
        TypeFilter(),
        StatusFilter(),
    )

    protected open fun getGenreList(): Array<String> = emptyArray()

    protected class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    protected class TypeFilter : Filter.Select<String>("Type", arrayOf("All", "Manga", "Manhua", "Manhwa"))

    protected class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "On-going", "Completed"))

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
    }
}
