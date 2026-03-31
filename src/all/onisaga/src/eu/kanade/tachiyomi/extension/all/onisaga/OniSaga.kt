package eu.kanade.tachiyomi.extension.all.onisaga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class OniSaga : HttpSource() {
    override val baseUrl = "https://onisaga.com"
    override val lang = "all"
    override val name = "OniSaga"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "view")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "created_at")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ========================= Search =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search/$query".toHttpUrl().newBuilder()
        } else {
            "$baseUrl/".toHttpUrl().newBuilder()
        }

        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("platform", filter.toUriPart())
                    }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                }
                is GenreFilter -> {
                    val selectedGenres = filter.getSelectedGenres()
                    selectedGenres.forEachIndexed { index, genre ->
                        url.addQueryParameter("genre[$index]", genre)
                    }
                    val excludedGenres = filter.getExcludedGenres()
                    excludedGenres.forEachIndexed { index, genre ->
                        url.addQueryParameter("excludeGenre[$index]", genre)
                    }
                }
                is MinChapterFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("min_chapters", filter.toUriPart())
                    }
                }
                is ReleaseDateFilter -> {
                    val startDate = filter.startDate?.state?.trim()
                    val endDate = filter.endDate?.state?.trim()

                    if (!startDate.isNullOrBlank()) {
                        url.addQueryParameter("release_start", startDate)
                    }
                    if (!endDate.isNullOrBlank()) {
                        url.addQueryParameter("release_end", endDate)
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ========================= Common Parsing =========================
    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.flex.flex-col.gap-2").map { element ->
            mangaFromElement(element)
        }

        val hasNextPage = document.selectFirst("button[wire\\:click*=\"gotoPage(2)\"]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val linkElement = element.selectFirst("a[href*=\"/manga/\"]")
            ?: throw Exception("Could not find manga link")
        val imageElement = element.selectFirst("picture > img")
        val titleElement = element.selectFirst("h3.font-semibold")
            ?: throw Exception("Could not find manga title")

        return SManga.create().apply {
            setUrlWithoutDomain(linkElement.attr("href"))
            thumbnail_url = imageElement?.attr("src")
            title = titleElement.text().trim()

            genre = element.select("div.flex.flex-wrap.items-center.text-center.gap-2 > div")
                .joinToString(", ") { it.text().trim() }
        }
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            status = SManga.COMPLETED
        }
    }

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select("chapter-item").forEach { element ->
            chapters.add(
                SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("href"))
                    name = element.attr("chapter-name")
                    chapter_number = element.attr("chapter-number").toFloatOrNull() ?: -1f
                },
            )
        }

        return chapters.reversed()
    }

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        document.select("img.chapter-page").forEachIndexed { idx, element ->
            pages.add(
                Page(idx, imageUrl = element.attr("src")),
            )
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = ""

    // ========================= Filters =========================
    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
        MinChapterFilter(),
        ReleaseDateFilter(),
        SortFilter(),
    )
}
