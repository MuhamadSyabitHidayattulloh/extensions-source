package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import kotlin.math.abs

private const val DOMAIN = "doujin.desu.xxx"

class DoujinDesu :
    HttpSource(),
    ConfigurableSource {

    override val name = "Doujindesu"
    override val baseUrl = "https://$DOMAIN"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addNetworkInterceptor(CookieInterceptor(DOMAIN, "sec_v_session" to "verified_human_0000000000000"))
        .build()

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-App-Secret", APP_SECRET)

    // Decryption logic
    private fun wH(e: Long): String {
        val t = "${SALT}_$e"
        var a = 0
        for (char in t) {
            a = ((a shl 5) - a) + char.code
        }

        var i = ""
        var l = if (a == 0) 123456789L else abs(a.toLong())
        for (idx in 0 until 32) {
            l = (l * 1664525 + 1013904223) % 4294967296L
            i += (33 + (l % 93)).toInt().toChar()
        }
        return i
    }

    private fun lU(): List<String> {
        val t = System.currentTimeMillis() / 3600000
        return listOf(wH(t), wH(t - 1), wH(t + 1))
    }

    private fun yre(e: String, t: String): String {
        val a = mutableListOf<Int>()
        for (u in e.indices step 2) {
            val p = e.substring(u, u + 2)
            a.add(p.toInt(16))
        }

        val i = StringBuilder()
        val l = t.length
        var d = 42
        for (u in a.indices) {
            val p = a[u]
            val f = t[u % l].code
            val k = p xor f xor (u * 13) xor d
            i.append((k and 255).toChar())
            d = (d + p) % 256
        }
        return i.toString()
    }

    private inline fun <reified T> Response.parseEncrypted(): T {
        val encryptedResponse = parseAs<EncryptedResponseDto>()
        val keys = lU()
        for (key in keys) {
            try {
                val decrypted = URLDecoder.decode(yre(encryptedResponse.encResp, key), "UTF-8")
                return decrypted.parseAs<T>()
            } catch (_: Exception) {
            }
        }
        throw Exception("Failed to decrypt server response")
    }

    // Popular Manga
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * LIMIT).toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaList = response.parseEncrypted<List<MangaDto>>()
        return MangasPage(
            mangaList.map {
                it.toSManga().apply {
                    initialized = true
                }
            },
            mangaList.size == LIMIT,
        )
    }

    // Latest Updates
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "latest_chapter")
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * LIMIT).toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search Manga
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * LIMIT).toString())

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is StatusFilter -> url.addQueryParameter("status", filter.selected)
                is SortFilter -> url.addQueryParameter("sort", filter.selected)
                is AuthorFilter -> if (filter.state.isNotEmpty()) url.addQueryParameter("author", filter.state)
                is GroupFilter -> if (filter.state.isNotEmpty()) url.addQueryParameter("group", filter.state)
                is SeriesFilter -> if (filter.state.isNotEmpty()) url.addQueryParameter("series", filter.state)
                is CharacterFilter -> if (filter.state.isNotEmpty()) url.addQueryParameter("character", filter.state)
                is GenreList -> {
                    val genres = filter.state
                        .filter { it.state }
                        .joinToString(",") { it.id }
                    if (genres.isNotEmpty()) {
                        url.addQueryParameter("genre", genres)
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseEncrypted<MangaDto>().toSManga().apply { initialized = true }

    // Chapter List
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseEncrypted<MangaDto>()
        return manga.chapters?.map { it.toSChapter() } ?: emptyList()
    }

    // Page List
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseEncrypted<ChapterDto>()
        return chapter.contentUrls?.mapIndexed { index, url ->
            Page(index, imageUrl = url.replace(" ", "%20"))
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        AuthorFilter(),
        GroupFilter(),
        SeriesFilter(),
        CharacterFilter(),
        GenreList(genreList),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {}

    companion object {
        private const val SALT = "doujindesu-scrapers-cannot-read-this-super-secret-salt-2026-v2"
        private const val APP_SECRET = "dfdf72051dbfdc7d76889ebd31324e74"
        private const val LIMIT = 24
    }
}
