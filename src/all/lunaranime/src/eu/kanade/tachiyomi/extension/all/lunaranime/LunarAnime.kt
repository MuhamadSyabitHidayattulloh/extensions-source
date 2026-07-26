package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class LunarAnime : KeiSource() {

    private val internalLang: String = when (lang) {
        "pt-BR" -> "pt-br"
        else -> lang
    }

    private val apiurlHost by lazy { API_URL.toHttpUrl().host }
    private val cdnurlHost by lazy { CDN_URL.toHttpUrl().host }

    override val supportsLatest = true

    private val signer = LunarWebViewSigner(baseUrl, API_URL)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        .addInterceptor(signer.dpopInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains("storage.lunaranime.ru")) {
                val newRequest = request.newBuilder()
                    .header("Referer", "$baseUrl/")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .rateLimit(2) { it.host == apiurlHost || it.host == cdnurlHost }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Referer", "$baseUrl/")

    private val crypto = LunarDecryptor(client, API_URL)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/recent")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")

            if (lang != "all") {
                addQueryParameter("language", internalLang)
            }
        }.build()
        val response = client.get(url, headers)
        val result = response.parseAs<LunarRecentResponse>()
        return MangasPage(
            mangas = result.mangas.map { it.toSManga() },
            hasNextPage = (result.page * result.limit) < result.totalCount,
        )
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/search")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")
            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            if (lang != "all") {
                addQueryParameter("language", internalLang)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.toValue()?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toValue()?.let { addQueryParameter("country", it) }
                    is LanguageFilter -> filter.toValue()?.let { addQueryParameter("language", it) }
                    is YearFilter -> {
                        val year = filter.state
                        if (year.isNotBlank() && year.toIntOrNull() != null) {
                            addQueryParameter("year", year)
                        }
                    }
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
        val response = client.get(url, headers)
        val result = response.parseAs<LunarSearchResponse>()
        return MangasPage(
            mangas = result.manga.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // =========================== Manga Updates ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")

        val updatedManga = if (fetchDetails) {
            val url = API_URL.toHttpUrl().newBuilder()
                .addPathSegments("api/manga/title")
                .addPathSegment(slug)
                .build()
            val response = client.get(url, headers)
            val result = response.parseAs<LunarMangaResponse>()
            result.manga.toSManga()
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val passwordUrl = API_URL.toHttpUrl().newBuilder()
                .addPathSegments("api/manga/password/info")
                .addPathSegment(slug)
                .build()
            val passwordResponse = client.get(passwordUrl, headers)
            val passwordInfo = passwordResponse.parseAs<LunarPasswordInfoResponse>()

            val requestUrl = API_URL.toHttpUrl().newBuilder()
                .addPathSegments("api/manga")
                .addPathSegment(slug)
                .build()
            val response = client.get(requestUrl, headers)
            val result = response.parseAs<LunarChapterListResponse>()

            result.data.filter {
                lang == "all" || it.language == internalLang
            }.map { chapter ->
                val isLocked = passwordInfo.hasSeriesPassword ||
                    passwordInfo.chapterPasswords.any {
                        it.chapterNumber == chapter.chapter && (it.language == null || it.language == chapter.language)
                    }
                chapter.toSChapter(slug, isLocked)
            }.reversed()
        } else {
            chapters
        }

        return SMangaUpdate(
            manga = updatedManga,
            chapters = updatedChapters,
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url.substringBefore("?")
        return baseUrl + url
    }

    // =============================== Pages ================================

    private suspend fun viewChapter(slug: String, number: String, lang: String) {
        val statusUrl = "$API_URL/api/manga/rating/status/$slug/$number"
        client.get(statusUrl, headers).close()

        val body = ViewRequestBody(slug, number, lang).toJsonRequestBody()
        val viewUrl = "$API_URL/api/manga/chapter/view"
        client.post(viewUrl, headers, body).close()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val language = chapterUrl.queryParameter("lang") ?: "en"
        val (slug, chapterNumber) = chapterUrl.pathSegments.takeLast(2)

        val response = client.get(chapterUrl, headers)

        // Required requests or fake images are returned
        viewChapter(slug, chapterNumber, language)

        val fingerprint = signer.getFingerprint()

        // I see decryption is always required now
        val decryptedImages = crypto.decryptChapterImages(response, slug, chapterNumber, language, fingerprint)
        return decryptedImages.mapIndexed { index, imageUrl ->
            Page(index, chapter.url, imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", baseUrl + page.url)
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            StatusFilter(),
            TypeFilter(),
        )

        if (lang == "all") {
            filters.add(LanguageFilter())
        }

        filters.addAll(
            listOf(
                YearFilter(),
                GenreFilter(),
            ),
        )

        return FilterList(filters)
    }

    companion object {
        private const val API_URL = "https://api.lunaranime.ru"
        private const val CDN_URL = "https://storage.lunaranime.ru"
    }
}
