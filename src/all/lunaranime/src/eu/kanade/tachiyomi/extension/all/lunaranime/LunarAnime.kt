package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class LunarAnime : HttpSource() {

    override val name = "Lunar Manga"

    override val baseUrl = "https://lunaranime.ru"

    override val lang = "all"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(API_URL.toHttpUrl(), 2)
        .rateLimitHost(CDN_URL.toHttpUrl(), 2)
        .build()

    private val apiHttpUrl = API_URL.toHttpUrl()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiHttpUrl.newBuilder()
            .addPathSegments("api/manga/recent")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "30")
            .build()
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LunarRecentResponse>()
        return MangasPage(
            mangas = result.mangas.map { it.toSManga(json) },
            hasNextPage = result.mangas.isNotEmpty(),
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiHttpUrl.newBuilder().apply {
            addPathSegments("api/manga/search")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")
            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.toValue()?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toValue()?.let { addQueryParameter("country", it) }
                    is YearFilter -> if (filter.state.isNotBlank()) addQueryParameter("year", filter.state)
                    is GenreFilter -> {
                        val genres = filter.toGenres()
                        if (genres.isNotEmpty()) {
                            addQueryParameter("genres", genres.joinToString(","))
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("sort", "relevance")
        }.build()
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<LunarSearchResponse>()
        return MangasPage(
            mangas = result.manga.map { it.toSManga(json) },
            hasNextPage = result.page < result.totalPages,
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = apiHttpUrl.newBuilder()
            .addPathSegments("api/manga/title")
            .addPathSegment(slug)
            .build()
        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<LunarMangaResponse>()
        return result.manga.toSManga(json)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val slug = manga.url.substringAfterLast("/")
        val requestUrl = apiHttpUrl.newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(slug)
            .build()
        val request = GET(requestUrl.toString(), headers)

        val result = client.newCall(request).execute().use { response ->
            response.parseAs<LunarChapterListResponse>()
        }

        result.data.map { it.toSChapter(slug) }.reversed()
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException("Not used.")

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used.")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val slug = chapter.url.split("/")[2]
        val chapterNumber = chapter.url.split("/")[3]
        val url = apiHttpUrl.newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(slug)
            .addPathSegment(chapterNumber)
            .build()
        val request = GET(url.toString(), headers)
        val result = client.newCall(request).execute().use { response ->
            response.parseAs<LunarPageListResponse>()
        }

        result.data?.images?.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        } ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException("Not used.")

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(),
        TypeFilter(),
        YearFilter(),
        GenreFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    companion object {
        private const val API_URL = "https://api.lunaranime.ru"
        private const val CDN_URL = "https://storage.lunaranime.ru"
    }
}
