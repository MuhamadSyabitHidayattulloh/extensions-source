package eu.kanade.tachiyomi.multisrc.oceanwp

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
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

abstract class OceanWP(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(if (page > 1) "$baseUrl/page/$page/" else baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open val popularMangaSelector = SELECTOR_POPULAR_MANGA

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(popularMangaTitleSelector)!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst(popularMangaThumbnailSelector)?.absUrl("src")
    }

    protected open val popularMangaTitleSelector = SELECTOR_POPULAR_MANGA_TITLE
    protected open val popularMangaThumbnailSelector = SELECTOR_POPULAR_MANGA_THUMBNAIL

    protected open val popularMangaNextPageSelector = SELECTOR_PAGINATION_NEXT

    // Latest
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val categoryFilter = filters.filterIsInstance<CategoryFilter>().firstOrNull()
            val tagFilter = filters.filterIsInstance<TagFilter>().firstOrNull()

            when {
                query.isNotEmpty() -> {
                    addQueryParameter("s", query)
                    if (page > 1) addPathSegments("page/$page/")
                }
                categoryFilter != null && categoryFilter.state > 0 -> {
                    return GET(if (page > 1) "${categoryFilter.selected}page/$page/" else categoryFilter.selected, headers)
                }
                tagFilter != null && tagFilter.state > 0 -> {
                    return GET(if (page > 1) "${tagFilter.selected}page/$page/" else tagFilter.selected, headers)
                }
                else -> {
                    if (page > 1) addPathSegments("page/$page/")
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val content = document.selectFirst(mangaDetailsContentSelector)!!
            title = content.selectFirst(mangaDetailsTitleSelector)!!.text()
            description = content.selectFirst(mangaDetailsDescriptionSelector)?.text()
            genre = content.select(mangaDetailsGenreSelector).joinToString { it.text() }
            thumbnail_url = content.selectFirst(mangaDetailsThumbnailSelector)?.absUrl("src")
        }
    }

    protected open val mangaDetailsContentSelector = SELECTOR_MANGA_DETAILS_CONTENT
    protected open val mangaDetailsTitleSelector = SELECTOR_MANGA_DETAILS_TITLE
    protected open val mangaDetailsDescriptionSelector = SELECTOR_MANGA_DETAILS_DESCRIPTION
    protected open val mangaDetailsGenreSelector = SELECTOR_MANGA_DETAILS_GENRE
    protected open val mangaDetailsThumbnailSelector = SELECTOR_MANGA_DETAILS_THUMBNAIL

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Gallery"
            setUrlWithoutDomain(response.request.url.toString())
        },
    )

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(pageListSelector).mapIndexed { i, img ->
            Page(i, document.location(), img.absUrl("src"))
        }
    }

    protected open val pageListSelector = SELECTOR_PAGE_LIST

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Use query for text search"),
            Filter.Separator(),
            CategoryFilter(getCategoryList()),
        )
        val tags = getTagList()
        if (tags.size > 1) {
            filters.add(TagFilter(tags))
        }
        return FilterList(filters)
    }

    protected open fun getCategoryList(): List<Pair<String, String>> = emptyList()
    protected open fun getTagList(): List<Pair<String, String>> = emptyList()

    companion object {
        const val SELECTOR_POPULAR_MANGA = "article.blog-entry"
        const val SELECTOR_POPULAR_MANGA_TITLE = "h2.blog-entry-title a"
        const val SELECTOR_POPULAR_MANGA_THUMBNAIL = "div.thumbnail img"
        const val SELECTOR_PAGINATION_NEXT = "ul.page-numbers li a.next"

        const val SELECTOR_MANGA_DETAILS_CONTENT = "div#content"
        const val SELECTOR_MANGA_DETAILS_TITLE = "h1.entry-title"
        const val SELECTOR_MANGA_DETAILS_DESCRIPTION = "div.entry-content"
        const val SELECTOR_MANGA_DETAILS_GENRE = "span.posted-on + .entry-meta li.meta-category a"
        const val SELECTOR_MANGA_DETAILS_THUMBNAIL = "div.thumbnail img"

        const val SELECTOR_PAGE_LIST = "div.entry-content img"
    }
}
