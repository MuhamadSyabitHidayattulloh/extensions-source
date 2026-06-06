package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        SimpleDateFormat("dd MMM yy", Locale.US),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = false

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
                if (url.host == baseUrl.toHttpUrl().host &&
                    url.pathSegments.any { it.contains("wp-content") || it.contains("uploads") }.not()
                ) {
                    set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    set("Sec-Fetch-Dest", "document")
                    set("Sec-Fetch-Mode", "navigate")
                    set("Sec-Fetch-Site", "none")
                    set("Upgrade-Insecure-Requests", "1")
                } else {
                    set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    set("Sec-Fetch-Dest", "image")
                    set("Sec-Fetch-Mode", "no-cors")
                    set("Sec-Fetch-Site", if (url.host == baseUrl.toHttpUrl().host) "same-origin" else "cross-site")
                }
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(2)
        .build()

    // ================================== Popular ======================================

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("div.item-thumb a").let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.attr("title")
            thumbnail_url = it.select("img").attr("abs:src")
        }
    }

    // ================================ Chapters ================================

    override val chapterUrlSuffix = ""

    // ================================ Filters ================================

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = super.getFilterList().list.toMutableList()

        filters += if (genresList.isNotEmpty()) {
            listOf(
                Filter.Separator(),
                GenreContentFilter(
                    title = intl["genre_filter_title"],
                    options = genresList.map { it.name to it.id },
                ),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    private class GenreContentFilter(title: String, options: List<Pair<String, String>>) :
        UriPartFilter(
            title,
            options.toTypedArray(),
        )

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    override fun parseGenres(document: Document): List<Genre> {
        val genres = mutableListOf<Genre>()
        genres += Genre("All", "")
        genres += document.select(".row.genres li a").map { a ->
            Genre(a.text(), a.absUrl("href"))
        }
        return genres
    }
}
