package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class ComicVerse : ZeistManga("Comic Verse", "https://arcomixverse.blogspot.com", "ar") {

    override val chapterFeedRegex = """(?:nPL2?\.run|fetchPosts)\(["'](.*?)["']\)""".toRegex()

    override val scriptSelector = "div.flex.aic.jcsb.mt-15[id^=nPL] > script, .check-box + script"
}
