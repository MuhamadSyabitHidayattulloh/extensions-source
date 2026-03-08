package eu.kanade.tachiyomi.extension.id.komiknextgonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class KomikNextGOnline : ParsedHttpSource() {
    override val name = "Komik Next G Online"

    override val baseUrl = "https://komiknextgonline.com"

    override val lang = "id"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addQueryParameter("comics_paged", page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "div#left-content .comic"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(".comic-title a, .entry-title a, a")!!
        title = element.select(".comic-title, .entry-title").text().substringAfter(". ")
        setUrlWithoutDomain(link.attr("abs:href"))
        thumbnail_url = transformThumbnailUrl(element.select(".thmb img, .post-thumbnail img, img").attr("abs:src"))
    }

    override fun popularMangaNextPageSelector() = ".next.page-numbers"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val slug = query.removePrefix(PREFIX_ID_SEARCH)
            val manga = SManga.create().apply {
                url = "/comic/$slug"
            }
            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it.apply { url = "/comic/$slug" }), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }
            if (page > 1) {
                addQueryParameter("comics_paged", page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".entry-title")!!.text().substringAfter(". ")
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dateIssued = document.selectFirst("meta[name=dc.date.issued]")?.attr("content")
        return listOf(
            SChapter.create().apply {
                name = "Chapter 1"
                chapter_number = 1f
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = dateFormat.tryParse(dateIssued)
            },
        )
    }

    override fun chapterListSelector(): String = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    // Pages
    override fun pageListParse(document: Document): List<Page> = document.select("#spliced-comic img, #comic img.size-full").mapIndexed { i, element ->
        Page(i, "", element.attr("abs:src"))
    }.distinctBy { it.imageUrl }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun transformThumbnailUrl(url: String): String = url.replace(thumbnailRegex) { result ->
        "." + result.groupValues[1]
    }

    private val thumbnailRegex = Regex("""-\d+x\d+\.(jpe?g|png)$""")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
