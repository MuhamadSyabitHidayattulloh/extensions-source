package eu.kanade.tachiyomi.extension.id.holotoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class Holotoon : HttpSource() {

    override val name = "Holotoon"

    override val baseUrl = "https://v1.holotoon.site"

    override val lang = "id"

    override val supportsLatest = true

    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href^=/comic/]").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst("a:contains(Berikutnya), a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
        url.addQueryParameter("q", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is TypeFilter -> url.addQueryParameter("type", filter.toUriPart())
                is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())
                is MediaFilter -> url.addQueryParameter("media", filter.toUriPart())
                is GenreFilter -> url.addQueryParameter("genre", filter.toUriPart())
                is TeamFilter -> url.addQueryParameter("team", filter.toUriPart())
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("div.text-sm.text-surface-300")?.text()

            val meta = document.select("div.flex.flex-col.gap-1, div.space-y-4")
            author = meta.select(":contains(Author) + span, :contains(Author) + div").text().takeIf { it.isNotEmpty() }
            artist = meta.select(":contains(Artist) + span, :contains(Artist) + div").text().takeIf { it.isNotEmpty() }
            status = when (meta.select(":contains(Status) + span, :contains(Status) + div").text().lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            genre = document.select("a[href*=/browse?genre=]").joinToString { it.text() }
            thumbnail_url = document.selectFirst("img[src*=/covers/]")?.absUrl("src")
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href^=/read/]").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                val spans = element.select("span")
                val chapterNum = spans.first()!!.text()
                val chapterTitle = spans.getOrNull(1)?.text()?.removePrefix("— ") ?: ""
                name = "$chapterNum $chapterTitle".trim()
                date_upload = parseRelativeDate(spans.last()?.text() ?: "")
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pagesData = document.extractAstroProp<PageListDto>("pages")
        return pagesData.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        MediaFilter(),
        GenreFilter(),
        TeamFilter(),
    )

    // ============================= Utilities =============================

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("detik") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            date.contains("menit") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("jam") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("hari") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("minggu") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            date.contains("bulan") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("tahun") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    private inline fun <reified T> Document.extractAstroProp(key: String): T {
        val prop = selectFirst("[props*=$key]")?.attr("props")
            ?: throw Exception("Unable to find prop with $key")
        val jsonElement = prop.parseAs<JsonElement>()
        val unwrapped = jsonElement.unwrapAstro()

        return unwrapped.parseAs()
    }

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            size == 2 && this[0] is JsonPrimitive -> this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }

        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }
}
