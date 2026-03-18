package eu.kanade.tachiyomi.extension.id.narasininja

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class NarasiNinja :
    MangaThemesia(
        "Narasi Ninja",
        "https://narasininja.net",
        "id",
        "/komik",
    ) {
    override fun searchMangaSelector() = ".listupd .bs .bsx"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.imgAttr() ?: ""
        title = element.selectFirst("a")?.attr("title") ?: ""
        setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        if (!genrelist.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreListFilter(intl["genre_filter_title"], getGenreList()),
                ),
            )
        }
        return FilterList(filters)
    }

    override val statusOptions = arrayOf(
        Pair(intl["status_filter_option_all"], ""),
        Pair(intl["status_filter_option_ongoing"], "ongoing"),
        Pair(intl["status_filter_option_completed"], "completed"),
        Pair(intl["status_filter_option_hiatus"], "hiatus"),
    )

    override val typeFilterOptions = arrayOf(
        Pair(intl["type_filter_option_all"], ""),
        Pair(intl["type_filter_option_manga"], "manga"),
        Pair(intl["type_filter_option_manhwa"], "manhwa"),
        Pair(intl["type_filter_option_manhua"], "manhua"),
        Pair(intl["type_filter_option_comic"], "comic"),
        Pair("Novel", "novel"),
    )

    override val orderByFilterOptions = arrayOf(
        Pair(intl["order_by_filter_az"], "title"),
        Pair(intl["order_by_filter_za"], "titlereverse"),
        Pair(intl["order_by_filter_latest_update"], "update"),
        Pair(intl["order_by_filter_latest_added"], "added"),
        Pair(intl["order_by_filter_popular"], "popular"),
    )
}
