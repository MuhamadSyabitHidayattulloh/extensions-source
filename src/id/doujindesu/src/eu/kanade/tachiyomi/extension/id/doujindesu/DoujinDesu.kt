package eu.kanade.tachiyomi.extension.id.doujindesu

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinDesu :
    ParsedHttpSource(),
    ConfigurableSource {
    // Information : DoujinDesu use EastManga WordPress Theme
    override val name = "Doujindesu"
    override val baseUrl by lazy { preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!! }
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    // Private stuff

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val dateFormat by lazy {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id"))
    }

    private fun parseStatus(status: String) = when {
        status.lowercase(Locale.US).contains("publishing") -> SManga.ONGOING
        status.lowercase(Locale.US).contains("finished") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun basicInformationFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").let {
            manga.title = element.selectFirst("h3.title")!!.text()
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        element.select("a > figure.thumbnail > img").first()?.let {
            manga.thumbnail_url = imageFromElement(it)
        }

        return manga
    }

    private fun imageFromElement(element: Element): String = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
        element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
        else -> element.attr("abs:src")
    }

    private fun getNumberFromString(epsStr: String?): Float = epsStr?.substringBefore(" ")?.toFloatOrNull() ?: -1f

    private fun reconstructDate(dateStr: String): Long = runCatching { dateFormat.parse(dateStr)?.time }
        .getOrNull() ?: 0L

    // Popular

    override fun popularMangaFromElement(element: Element): SManga = basicInformationFromElement(element)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?order=popular", headers)

    // Latest

    override fun latestUpdatesFromElement(element: Element): SManga = basicInformationFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?order=update", headers)

    // Element Selectors

    override fun popularMangaSelector(): String = "#archives > div.entries > article"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector(): String = "nav.pagination a.next, nav.pagination li.active + li a, nav.pagination li.current + li a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Search & FIlter

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val agsFilter = filters.firstInstanceOrNull<AuthorGroupSeriesFilter>()
        val agsValueFilter = filters.firstInstanceOrNull<AuthorGroupSeriesValueFilter>()
        val selectedOption = agsFilter?.values?.getOrNull(agsFilter.state)
        val filterValue = agsValueFilter?.state?.trim() ?: ""

        // Author/Group/Series filter handling (Taxonomy browsing)
        if (query.isBlank() && selectedOption != null && selectedOption.key.isNotBlank()) {
            val typePath = selectedOption.key
            val url = if (filterValue.isBlank()) {
                if (page == 1) "$baseUrl/$typePath/" else "$baseUrl/$typePath/page/$page/"
            } else {
                if (page == 1) "$baseUrl/$typePath/$filterValue/" else "$baseUrl/$typePath/$filterValue/page/$page/"
            }
            return GET(url, headers)
        }

        // Global search & filter handling
        val url = "$baseUrl/manga/page/$page/".toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addQueryParameter("title", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is CharacterFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("character", filter.state)
                    }
                }
                is StatusList -> {
                    val status = filter.values[filter.state]
                    url.addQueryParameter("statusx", status.key)
                }
                is CategoryNames -> {
                    val category = filter.values[filter.state]
                    url.addQueryParameter("typex", category.key)
                }
                is OrderBy -> {
                    val order = filter.values[filter.state]
                    url.addQueryParameter("order", order.key)
                }
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .forEach { genre ->
                            url.addQueryParameter("genre[]", genre.id)
                        }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaFromElement(element: Element): SManga = basicInformationFromElement(element)

    override fun getFilterList() = FilterList(
        Filter.Header("Filter ini bisa digunakan bersamaan dengan pencarian teks"),
        AuthorFilter(),
        CharacterFilter(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(genreList()),
        Filter.Separator(),
        Filter.Header("NB: Filter dibawah ini tidak bisa digabungkan dengan pencarian teks/filter diatas, serta harus memasukkan nama secara lengkap!"),
        AuthorGroupSeriesFilter(authorGroupSeriesOptions),
        AuthorGroupSeriesValueFilter(),
    )

    // Detail Parse

    private val chapterListRegex = Regex("""\d+[-–]?\d*\..+<br>""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("<[^>]*>")
    private val chapterPrefixRegex = Regex("""^\d+(-\d+)?\.\s*.*""")

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("section.metadata")!!
        val authorName = if (infoElement.select("td:contains(Author) ~ td").isEmpty()) {
            null
        } else {
            infoElement.select("td:contains(Author) ~ td").joinToString { it.text() }
        }
        val groupName = if (infoElement.select("td:contains(Group) ~ td").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement.select("td:contains(Group) ~ td").joinToString { it.text() }
        }
        val authorParser = if (authorName.isNullOrEmpty()) {
            groupName.takeIf { it != "Tidak Diketahui" }
        } else {
            authorName
        }
        val characterName = if (infoElement.select("td:contains(Character) ~ td").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement.select("td:contains(Character) ~ td").joinToString { it.text() }
        }
        val seriesParser = if (infoElement.select("td:contains(Series) ~ td").text() == "Manhwa") {
            infoElement.select("td:contains(Serialization) ~ td").text()
        } else {
            infoElement.select("td:contains(Series) ~ td").text()
        }
        val alternativeTitle = if (infoElement.select("h1.title > span.alter").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement.select("h1.title > span.alter").joinToString { it.text() }
        }
        val manga = SManga.create()
        manga.description = if (infoElement.select("div.pb-2 > p:nth-child(1)").isEmpty()) {
            """
            Tidak ada deskripsi yang tersedia bosque

            Judul Alternatif : $alternativeTitle
            Grup             : $groupName
            Karakter         : $characterName
            Seri             : $seriesParser
            """.trimIndent()
        } else {
            val pb2Element = infoElement.selectFirst("div.pb-2")

            val showDescription = pb2Element?.let { element ->
                val paragraphs = element.select("p")
                val firstText = paragraphs.firstOrNull()?.text()?.trim()?.lowercase()

                // Fungsi untuk mendekode semua entitas HTML
                val decodeHtmlEntities = { text: String ->
                    Jsoup.parse(text).text().replace('\u00A0', ' ')
                }

                // CASE 1: Gabungan chapter dalam satu paragraf (Manga Style)
                val mergedChapterElement = element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))").firstOrNull {
                    chapterListRegex.containsMatchIn(it.html())
                }
                if (mergedChapterElement != null) {
                    val chapterList = mergedChapterElement.html()
                        .split("<br>")
                        .drop(1)
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                // CASE 2: Dua paragraf: p[0] = "Sinopsis:", p[1] = daftar chapter (Manga Style)
                if (
                    firstText == "sinopsis:" &&
                    paragraphs.size > 1 &&
                    chapterListRegex.containsMatchIn(paragraphs[1].html())
                ) {
                    val chapterList = paragraphs[1].html()
                        .split("<br>")
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                // CASE 3 + 5 Hybrid: Tangani Sinopsis dengan <strong> + <br> + <p> campuran (Manhwa Style + Terkompresi)
                val sinopsisPara = element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))")
                if (sinopsisPara.isNotEmpty()) {
                    val sinopsisStart = sinopsisPara.first()!!
                    val htmlSplit = sinopsisStart.html().split("<br>")

                    val startText = htmlSplit
                        .drop(1)
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }

                    val sinopsisTexts = buildList {
                        addAll(startText)

                        val allP = element.select("p")
                        val startIndex = allP.indexOf(sinopsisStart)

                        for (i in startIndex + 1 until allP.size) {
                            val htmlSplitNext = allP[i].html().split("<br>")
                            val contents = htmlSplitNext
                                .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                                .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }
                            addAll(contents)
                        }
                    }
                    if (sinopsisTexts.isNotEmpty()) {
                        val isChapterList = sinopsisTexts.first().matches(chapterPrefixRegex)
                        val prefix = if (isChapterList) "Daftar Chapter:" else "Sinopsis:"
                        return@let "$prefix\n" + sinopsisTexts.joinToString("\n\n")
                    }
                }

                // CASE 4: Satu paragraf saja dengan <strong> dan <br> (Manhwa Style)
                if (
                    paragraphs.size == 1 &&
                    element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))").isNotEmpty()
                ) {
                    val para = paragraphs[0]
                    val htmlSplit = para.html().split("<br>")

                    val content = htmlSplit.getOrNull(1)?.let {
                        decodeHtmlEntities(it.replace(htmlTagRegex, "").trim())
                    }.orEmpty()

                    if (content.isNotBlank()) {
                        return@let "Sinopsis:\n$content"
                    }
                }

                // CASE 6: Fallback
                if (firstText == "sinopsis:") {
                    val sinopsisLines = paragraphs.drop(1)
                        .map { decodeHtmlEntities(it.text().trim()) }
                        .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }

                    return@let "Sinopsis:\n" + sinopsisLines.joinToString("\n\n")
                }
                return@let ""
            } ?: ""
            """
            |$showDescription
            |
            |Judul Alternatif : $alternativeTitle
            |Seri             : $seriesParser
            """.trimMargin().replace(Regex(" +"), " ")
        }
        val genres = mutableListOf<String>()
        infoElement.select("div.tags > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.author = authorParser
        manga.genre = infoElement.select("div.tags > a").joinToString { it.text() }
        manga.status = parseStatus(
            infoElement.selectFirst("td:contains(Status) ~ td")!!.text(),
        )
        manga.thumbnail_url = document.selectFirst("figure.thumbnail img")?.attr("src")

        return manga
    }

    // Chapter Stuff

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val eps = element.selectFirst("div.epsright chapter")?.text()
        chapter.chapter_number = getNumberFromString(eps)
        chapter.date_upload = reconstructDate(element.select("div.epsleft > span.date").text())
        chapter.name = "Chapter $eps"
        chapter.setUrlWithoutDomain(element.select("div.epsleft > span.lchx > a").attr("href"))

        return chapter
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")
        .setRandomUserAgent()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,*/*")
            .set("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun chapterListSelector(): String = "#chapter_list li"

    // More parser stuff
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val id = document.select("#reader").attr("data-id")
        val body = FormBody.Builder()
            .add("id", id)
            .build()
        return client.newCall(POST("$baseUrl/themes/ajax/ch.php", headers, body)).execute()
            .asJsoup().select("img").mapIndexed { i, element ->
                Page(i, "", element.attr("src"))
            }
    }

    companion object {
        private val PREF_DOMAIN_KEY = "preferred_domain_name_v${AppInfo.getVersionName()}"
        private const val PREF_DOMAIN_TITLE = "Mengganti BaseUrl"
        private const val PREF_DOMAIN_DEFAULT = "https://doujindesu.tv"
        private const val PREF_DOMAIN_SUMMARY = "Mengganti domain default dengan domain yang berbeda"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = PREF_DOMAIN_TITLE
            dialogTitle = PREF_DOMAIN_TITLE
            summary = PREF_DOMAIN_SUMMARY
            dialogMessage = "Default: $PREF_DOMAIN_DEFAULT"
            setDefaultValue(PREF_DOMAIN_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                Toast.makeText(screen.context, "Mulai ulang aplikasi untuk menerapkan pengaturan baru.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
        screen.addRandomUAPreference()
    }
}
