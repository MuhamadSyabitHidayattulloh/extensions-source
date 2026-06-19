package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.rateLimit
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        dateFormat,
    ) {
    override val useNewChapterEndpoint = true

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val headers = request.headers.newBuilder().apply {
                when {
                    url.contains("admin-ajax.php") || url.contains("/ajax/chapters") -> {
                        set("Accept", "application/json, text/javascript, */*; q=0.01")
                        set("Referer", "$baseUrl/")
                        set("X-Requested-With", "XMLHttpRequest")
                        set("Sec-Fetch-Dest", "empty")
                        set("Sec-Fetch-Mode", "cors")
                        set("Sec-Fetch-Site", "same-origin")
                    }
                    url.contains("wp-content") || url.contains("uploads") || !url.startsWith(baseUrl) -> {
                        set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        set("Referer", "$baseUrl/")
                        removeAll("X-Requested-With")
                        set("Sec-Fetch-Dest", "image")
                        set("Sec-Fetch-Mode", "no-cors")
                        set("Sec-Fetch-Site", if (url.startsWith(baseUrl)) "same-origin" else "cross-site")
                    }
                    else -> {
                        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        removeAll("Referer")
                        removeAll("X-Requested-With")
                        set("Sec-Fetch-Dest", "document")
                        set("Sec-Fetch-Mode", "navigate")
                        set("Sec-Fetch-Site", "none")
                        set("Sec-Fetch-User", "?1")
                        set("Upgrade-Insecure-Requests", "1")
                    }
                }
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(1)
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
        val filters = super.getFilterList().list.filterNot {
            it.name.contains("Adult Content", ignoreCase = true)
        }

        return FilterList(filters)
    }

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select(".row.genres li a").map { a ->
        Genre(
            a.ownText(),
            a.absUrl("href")
                .trimEnd('/')
                .substringAfterLast('/'),
        )
    }

    companion object {
        private val dateFormat = SimpleDateFormat("dd MMM yy", Locale.US)
    }
}
