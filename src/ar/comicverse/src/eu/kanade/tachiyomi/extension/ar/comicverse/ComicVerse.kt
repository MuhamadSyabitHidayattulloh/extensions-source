package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.GenreList
import eu.kanade.tachiyomi.multisrc.zeistmanga.StatusList
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document

class ComicVerse : ZeistManga("Comic Verse", "https://arcomixverse.blogspot.com", "ar") {

    override val chapterFeedRegex = """(?:nPL2?\.run|fetchPosts)\(["'](.*?)["']\)""".toRegex()

    override val scriptSelector = "div.flex.aic.jcsb.mt-15[id^=nPL] > script, .check-box + script"

    override val popularMangaSelector = "div.PopularPosts article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "h3 > a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.use { it.asJsoup() }
        val chapterLabel = getChapterFeedUrl(document)

        val feedUrl = apiUrl(chapterLabel)
            .addQueryParameter("max-results", MAX_CHAPTER_RESULTS.toString())
            .build()

        val res = client.newCall(GET(feedUrl.toString(), headers)).execute()
        return json.decodeFromString<ZeistMangaDto>(res.body.string()).feed?.entry
            ?.filter { it.category.orEmpty().any { category -> category.term == chapterCategory } }
            ?.map { it.toSChapter(baseUrl) }
            ?: emptyList()
    }

    override fun getChapterFeedUrl(doc: Document): String {
        val script = doc.select(scriptSelector).firstOrNull {
            it.html().contains(chapterFeedRegex)
        }

        return script?.let {
            chapterFeedRegex.find(it.html())?.groupValues?.get(1)
        } ?: throw Exception("Failed to find chapter feed")
    }

    override val hasFilters = true

    override fun getFilterList() = FilterList(
        StatusList("Status", getStatusList()),
        GenreList("Genre", getGenreList()),
    )

    override fun getGenreList(): List<Genre> = listOf(
        Genre("أكشن", "أكشن"),
        Genre("مغامرة", "مغامرة"),
        Genre("خيال علمي", "خيال علمي"),
        Genre("غموض", "غموض"),
        Genre("فانتازيا", "فانتازيا"),
        Genre("دراما", "دراما"),
        Genre("رعب", "رعب"),
        Genre("سحر", "سحر"),
        Genre("جريمة", "جريمة"),
        Genre("تحقيق", "تحقيق"),
        Genre("كوميديا", "كوميديا"),
        Genre("زومبي", "زومبي"),
        Genre("نهاية العالم", "نهاية العالم"),
    )
}
