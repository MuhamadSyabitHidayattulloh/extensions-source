package eu.kanade.tachiyomi.multisrc.scanreader

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

abstract class ScanReader(
    override val name: String,
    final override val lang: String,
    override val baseUrl: String,
) : HttpSource() {

    override val supportsLatest = true

    // ===
    // ====
    // =====
    // Popular
    // =====
    // ====
    // ===
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/bibliotheque/".toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("sort", "views")
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (genreList.isEmpty()) {
            genreList = document.select("#genre-filter option")
                .filter { it.attr("value").isNotBlank() }
                .map { it.text() to it.attr("value") }
                .toTypedArray()
        }

        val mangas = document.select(popularMangaSelector).map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst(mangaAnchorSelector)!!
                setUrlWithoutDomain(anchor.absUrl("href"))
                title = anchor.selectFirst(mangaTitleSelector)!!.text()
                thumbnail_url = element.selectFirst("img")?.let {
                    it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
                }
            }
        }
        val hasNextPage = document.selectFirst(nextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    // ===
    // ====
    // =====
    // Latest
    // =====
    // ====
    // ===
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/bibliotheque/".toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("sort", "date")
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ===
    // ====
    // =====
    // Search
    // =====
    // ====
    // ===
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("bibliotheque")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.state < filter.genres.size) {
                            val genre = filter.genres[filter.state].second
                            if (genre.isNotBlank()) addQueryParameter("genre", genre)
                        }
                    }
                    is StatusFilter -> {
                        val status = filter.statuses[filter.state].second
                        if (status.isNotBlank()) addQueryParameter("status", status)
                    }
                    is SortFilter -> {
                        val sort = filter.sorts[filter.state].second
                        if (sort.isNotBlank()) addQueryParameter("sort", sort)
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(detailsTitleSelector)!!.text()
            description = document.selectFirst(detailsDescriptionSelector)?.text()
            genre = document.select(detailsGenreSelector).joinToString { it.text() }
            author = document.select(detailsAuthorSelector).firstOrNull()?.text()
            artist = author
            status = parseStatus(document.select(detailsStatusSelector).firstOrNull()?.text())
            thumbnail_url = document.selectFirst(detailsThumbnailSelector)?.let {
                it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
            }
        }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("En cours", ignoreCase = true) -> SManga.ONGOING
        status.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
        status.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        status.contains("Licencié", ignoreCase = true) -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val container = document.getElementById(chapterContainerId)
            ?: return emptyList()

        val mangaId = container.attr("data-manga-id")
        val nonce = container.attr("data-nonce")

        val formBody = FormBody.Builder()
            .add("action", "load_protected_chapters_html")
            .add("manga_id", mangaId)
            .add("nonce", nonce)
            .build()

        val ajaxRequest = POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)
        val ajaxHtml = client.newCall(ajaxRequest).execute().use { ajaxResponse ->
            if (!ajaxResponse.isSuccessful) {
                throw Exception("HTTP error ${ajaxResponse.code}")
            }
            ajaxResponse.body.string()
        }

        val ajaxDocument = org.jsoup.Jsoup.parseBodyFragment(ajaxHtml, baseUrl)

        return ajaxDocument.select(chapterAnchorSelector).map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(chapterNameSelector)!!.text()
                val chapterNumberText = element.selectFirst(chapterNumberSelector)?.text()
                chapter_number = chapterNumberText?.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(element.selectFirst(chapterDateSelector)?.text()?.trim())
            }
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select(pagesScriptSelector).firstOrNull()?.data()
            ?: throw Exception("Pages not found")

        val pagesJson = script.substringAfter("const chapterPages = [").substringBefore("];")
        val encodedPages = pagesJson.split(",").map { it.trim().trim('"') }.filter { it.isNotBlank() }

        return encodedPages.mapIndexed { index, encoded ->
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT)).reversed()
            Page(index, "", decoded)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Filters work with search. Press 'Reset' to refresh genres."),
        Filter.Separator(),
        SortFilter("Tri", getSortList()),
        StatusFilter("Statut", getStatusList()),
        GenreFilter("Genre", genreList),
    )

    protected var genreList: Array<Pair<String, String>> = emptyArray()

    private val dateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    }

    // Selectors
    protected open val popularMangaSelector = ".manga-card"
    protected open val mangaAnchorSelector = "a"
    protected open val mangaTitleSelector = "h3"
    protected open val nextPageSelector = "a.next"

    protected open val detailsTitleSelector = ".manga-title"
    protected open val detailsDescriptionSelector = ".manga-content > div:not(.manga-info-grid)"
    protected open val detailsGenreSelector = ".manga-info-grid > div:contains(Genres :) span"
    protected open val detailsAuthorSelector = ".manga-info-grid > div:contains(Auteur :) > div:nth-child(2)"
    protected open val detailsStatusSelector = ".manga-info-grid > div:contains(Statut :) > div:nth-child(2)"
    protected open val detailsThumbnailSelector = ".manga-image img"

    protected open val chapterContainerId = "secure-chapters-container"
    protected open val chapterAnchorSelector = "a"
    protected open val chapterNameSelector = "h4"
    protected open val chapterNumberSelector = "div[style*=background: linear-gradient(135deg, #00aaff, #0099cc)]"
    protected open val chapterDateSelector = "div:has(i.fa-calendar-alt)"

    protected open val pagesScriptSelector = "script:containsData(chapterPages)"
}
