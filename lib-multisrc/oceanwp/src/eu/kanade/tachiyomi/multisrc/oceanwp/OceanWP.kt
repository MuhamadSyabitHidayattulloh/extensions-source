package eu.kanade.tachiyomi.multisrc.oceanwp

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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

    override val supportsLatest = false

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(if (page > 1) "$baseUrl/page/$page/" else baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
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

    protected open fun popularMangaNextPageSelector() = SELECTOR_PAGINATION_NEXT

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

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

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open val searchMangaSelector = SELECTOR_SEARCH_MANGA

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(searchMangaTitleSelector)!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst(searchMangaThumbnailSelector)?.absUrl("src")
    }

    protected open val searchMangaTitleSelector = SELECTOR_SEARCH_MANGA_TITLE
    protected open val searchMangaThumbnailSelector = SELECTOR_SEARCH_MANGA_THUMBNAIL

    protected open fun searchMangaNextPageSelector() = SELECTOR_PAGINATION_NEXT

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val content = document.selectFirst(mangaDetailsContentSelector) ?: document
            title = content.selectFirst(mangaDetailsTitleSelector)!!.text()
            description = content.selectFirst(mangaDetailsDescriptionSelector)?.text()
            genre = content.select(mangaDetailsGenreSelector).joinToString { it.text() }
            thumbnail_url = content.selectFirst(mangaDetailsThumbnailSelector)?.absUrl("src")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    protected open val mangaDetailsContentSelector = SELECTOR_MANGA_DETAILS_CONTENT
    protected open val mangaDetailsTitleSelector = SELECTOR_MANGA_DETAILS_TITLE
    protected open val mangaDetailsDescriptionSelector = SELECTOR_MANGA_DETAILS_DESCRIPTION
    protected open val mangaDetailsGenreSelector = SELECTOR_MANGA_DETAILS_GENRE
    protected open val mangaDetailsThumbnailSelector = SELECTOR_MANGA_DETAILS_THUMBNAIL

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.selectFirst(mangaDetailsTitleSelector)?.text() ?: "Chapter 1"
        return listOf(
            SChapter.create().apply {
                name = mangaTitle
                setUrlWithoutDomain(response.request.url.toString())
            },
        )
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(pageListSelector).mapIndexed { i, img ->
            val url = img.absUrl("src")
            Page(i, document.location(), url)
        }
    }

    protected open val pageListSelector = SELECTOR_PAGE_LIST

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(Filter.Header("Filter tidak bisa dikombinasikan dengan pencarian teks"))

        val categories = getCategoryList()
        val tags = if (hasTagFilter) getTagList() else emptyList()

        if (categories.size > 1 && tags.size > 1) {
            filters.add(Filter.Header("Filter di bawah ini tidak bisa dikombinasikan satu sama lain"))
        }

        filters.add(Filter.Separator())

        if (categories.size > 1) {
            filters.add(CategoryFilter(categories))
        }

        if (tags.size > 1) {
            filters.add(TagFilter(tags))
        }

        return FilterList(filters)
    }

    protected open val hasTagFilter = true

    private var categoryList: List<Pair<String, String>>? = null
    private var tagList: List<Pair<String, String>>? = null

    protected open fun getCategoryList(): List<Pair<String, String>> {
        if (categoryList != null) return categoryList!!
        return runCatching {
            val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
            val list = listOf(Pair("Default", "")) + document.select(categorySelector).map {
                Pair(it.text(), it.absUrl("href"))
            }
            if (list.size > 1) categoryList = list
            list
        }.getOrDefault(emptyList())
    }

    protected open fun getTagList(): List<Pair<String, String>> {
        if (tagList != null) return tagList!!
        return runCatching {
            val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
            val list = listOf(Pair("Default", "")) + document.select(tagSelector).map {
                Pair(it.text(), it.absUrl("href"))
            }
            if (list.size > 1) tagList = list
            list
        }.getOrDefault(emptyList())
    }

    protected open val categorySelector = SELECTOR_CATEGORY
    protected open val tagSelector = SELECTOR_TAG

    companion object {
        const val SELECTOR_POPULAR_MANGA = "article.blog-entry"
        const val SELECTOR_POPULAR_MANGA_TITLE = "h2.blog-entry-title a"
        const val SELECTOR_POPULAR_MANGA_THUMBNAIL = "div.thumbnail img"

        const val SELECTOR_SEARCH_MANGA = "article"
        const val SELECTOR_SEARCH_MANGA_TITLE = "h2.search-entry-title a, h2.blog-entry-title a"
        const val SELECTOR_SEARCH_MANGA_THUMBNAIL = "div.thumbnail img"

        const val SELECTOR_PAGINATION_NEXT = "ul.page-numbers li a.next"

        const val SELECTOR_MANGA_DETAILS_CONTENT = "div#content"
        const val SELECTOR_MANGA_DETAILS_TITLE = ".entry-title"
        const val SELECTOR_MANGA_DETAILS_DESCRIPTION = "div.entry-content"
        const val SELECTOR_MANGA_DETAILS_GENRE = "li.meta-cat a, li.meta-category a"
        const val SELECTOR_MANGA_DETAILS_THUMBNAIL = "div.thumbnail img"

        const val SELECTOR_PAGE_LIST = "div.entry-content img"

        const val SELECTOR_CATEGORY = "ul.sub-menu li[class*=menu-item-type-taxonomy] a"
        const val SELECTOR_TAG = "div.tagcloud a"
    }
}
