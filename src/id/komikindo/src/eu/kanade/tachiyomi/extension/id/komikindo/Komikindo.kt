package eu.kanade.tachiyomi.extension.id.komikindo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

class Komikindo :
    MangaThemesia(
        "Komikindo",
        "https://komikindo.club",
        "id",
    ) {
    // Some covers fail to load with no Accept header + no resize parameter.
    // Hence the workarounds:

    private val cdnHeaders = imageRequest(Page(0, "$baseUrl/", baseUrl)).headers

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains("/wp-content/uploads/")) {
                return@addInterceptor chain.proceed(request.newBuilder().headers(cdnHeaders).build())
            }
            chain.proceed(request)
        }
        .build()

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        thumbnail_url = thumbnail_url
            ?.toHttpUrlOrNull()
            ?.takeIf { it.queryParameter("resize") == null }
            ?.newBuilder()
            ?.setEncodedQueryParameter("resize", "165,225")
            ?.build()
            ?.toString()
    }

    override fun pageListParse(document: Document): List<Page> = super.pageListParse(document).onEach { page ->
        page.imageUrl = page.imageUrl?.replace(LINKSAYA_CDN_REGEX, "https://linksaya.com")
    }

    override val hasProjectPage = true

    companion object {
        private val LINKSAYA_CDN_REGEX = """https://.*\.linksaya\.com""".toRegex()
    }
}
