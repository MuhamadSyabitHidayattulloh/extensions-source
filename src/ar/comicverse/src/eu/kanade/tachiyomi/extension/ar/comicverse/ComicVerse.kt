package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class ComicVerse : ZeistManga("Comic Verse", "https://arcomixverse.blogspot.com", "ar") {

    override val supportsLatest = false

    override val chapterFeedRegex = """(?:nPL2?\.run|fetchPosts)\(["'](.*?)["']\)""".toRegex()

    override val scriptSelector = "script"
}
