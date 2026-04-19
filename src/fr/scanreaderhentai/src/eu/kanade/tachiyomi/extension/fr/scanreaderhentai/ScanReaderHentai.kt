package eu.kanade.tachiyomi.extension.fr.scanreaderhentai

import eu.kanade.tachiyomi.multisrc.scanreader.getHentaiGenreList
import eu.kanade.tachiyomi.multisrc.scanreader.ScanReader as ScanReaderTheme

class ScanReaderHentai : ScanReaderTheme("Scan Reader Hentai", "fr", "https://hentai.scanreader.net") {
    override val genreList = getHentaiGenreList()
}
