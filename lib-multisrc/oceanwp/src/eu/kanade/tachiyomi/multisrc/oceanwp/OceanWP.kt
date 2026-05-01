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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

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
        parseFilters(document)
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector() = "article.blog-entry"

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(popularMangaTitleSelector())!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst(popularMangaThumbnailSelector())?.absUrl("src")
    }

    protected open fun popularMangaTitleSelector() = "h2.blog-entry-title a"
    protected open fun popularMangaThumbnailSelector() = "div.thumbnail img"

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
        parseFilters(document)
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun searchMangaSelector() = "article"

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(searchMangaTitleSelector())!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst(searchMangaThumbnailSelector())?.absUrl("src")
    }

    protected open fun searchMangaTitleSelector() = "h2.search-entry-title a, h2.blog-entry-title a"
    protected open fun searchMangaThumbnailSelector() = "div.thumbnail img"

    protected open fun searchMangaNextPageSelector() = SELECTOR_PAGINATION_NEXT

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val content = document.selectFirst(mangaDetailsContentSelector()) ?: document
            title = content.selectFirst(mangaDetailsTitleSelector())!!.text()
            description = content.selectFirst(mangaDetailsDescriptionSelector())?.text()
            genre = content.select(mangaDetailsGenreSelector()).joinToString { it.text() }
            thumbnail_url = content.selectFirst(mangaDetailsThumbnailSelector())?.absUrl("src")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    protected open fun mangaDetailsContentSelector() = "div#content"
    protected open fun mangaDetailsTitleSelector() = ".entry-title"
    protected open fun mangaDetailsDescriptionSelector() = "div.entry-content"
    protected open fun mangaDetailsGenreSelector() = "li.meta-cat a, li.meta-category a"
    protected open fun mangaDetailsThumbnailSelector() = "div.thumbnail img"

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            setUrlWithoutDomain(manga.url)
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(pageListSelector()).mapIndexed { i, img ->
            val url = img.absUrl("src")
            Page(i, document.location(), url)
        }
    }

    protected open fun pageListSelector() = "div.entry-content img"

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(Filter.Header("Filter tidak bisa dikombinasikan dengan pencarian teks"))

        if (categoryList.isEmpty() && (tagList.isEmpty() || !hasTagFilter)) {
            filters.add(Filter.Header("Gunakan 'Reset' untuk memuat filter"))
        }

        if (categoryList.size > 1 && tagList.size > 1) {
            filters.add(Filter.Header("Filter di bawah ini tidak bisa dikombinasikan satu sama lain"))
        }

        filters.add(Filter.Separator())

        if (categoryList.size > 1) {
            filters.add(CategoryFilter(categoryList))
        }

        if (tagList.size > 1 && hasTagFilter) {
            filters.add(TagFilter(tagList))
        }

        return FilterList(filters)
    }

    protected open val hasTagFilter = true

    private var categoryList: List<Pair<String, String>> = emptyList()
    private var tagList: List<Pair<String, String>> = emptyList()

    private fun parseFilters(document: Document) {
        if (categoryList.size <= 1) {
            categoryList = listOf(Pair("Default", "")) + document.select(categorySelector()).map {
                Pair(it.text(), it.absUrl("href"))
            }
        }

        if (tagList.size <= 1 && hasTagFilter) {
            tagList = listOf(Pair("Default", "")) + document.select(tagSelector()).map {
                Pair(it.text(), it.absUrl("href"))
            }
        }
    }

    protected open fun categorySelector() = "ul.sub-menu li[class*=menu-item-type-taxonomy] a"
    protected open fun tagSelector() = "div.tagcloud a"

    companion object {
        const val SELECTOR_PAGINATION_NEXT = "ul.page-numbers li a.next"
    }
}
