package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
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
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = false

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        set("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36")
        set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        set("Sec-CH-UA", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
        set("Sec-CH-UA-Mobile", "?1")
        set("Sec-CH-UA-Platform", "\"Android\"")
        set("Referer", "https://mgkomik.id/")
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val headers = request.headers.newBuilder().apply {
                when {
                    url.contains("admin-ajax.php") || url.contains("/ajax/chapters") -> {
                        set("Accept", "*/*")
                        set("X-Requested-With", "XMLHttpRequest")
                        set("Sec-Fetch-Dest", "empty")
                        set("Sec-Fetch-Mode", "cors")
                        set("Sec-Fetch-Site", "same-origin")
                        set("Priority", "u=1")
                    }
                    url.contains(baseUrl) && (url.contains(".jpg") || url.contains(".png") || url.contains(".webp") || url.contains(".jpeg")) -> {
                        set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        set("Sec-Fetch-Dest", "image")
                        set("Sec-Fetch-Mode", "no-cors")
                        set("Sec-Fetch-Site", "same-origin")
                        set("Priority", "u=1, i")
                    }
                    else -> {
                        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        removeAll("X-Requested-With")
                        set("Sec-Fetch-Dest", "document")
                        set("Sec-Fetch-Mode", "navigate")
                        set("Sec-Fetch-Site", "none")
                        set("Sec-Fetch-User", "?1")
                        set("Upgrade-Insecure-Requests", "1")
                        set("Priority", "u=0, i")
                    }
                }
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(3)
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
