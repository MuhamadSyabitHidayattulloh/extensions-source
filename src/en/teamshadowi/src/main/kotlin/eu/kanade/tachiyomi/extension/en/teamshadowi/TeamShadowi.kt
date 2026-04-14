package eu.kanade.tachiyomi.extension.en.teamshadowi

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.SChapter

class TeamShadowi :
    Iken(
        "Team Shadowi",
        "en",
        "https://www.team-shadowi.com",
    ) {

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.replace("/series/", "/read/").substringBeforeLast("#")
}
