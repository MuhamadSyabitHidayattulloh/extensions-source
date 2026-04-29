package eu.kanade.tachiyomi.extension.es.ragnarokscanlation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class RagnarokScanlation :
    Madara(
        "Ragnarok Scanlation",
        "https://ragnarokscanlation.org",
        "es",
        SimpleDateFormat("MMMM d, yyyy", Locale("en")),
    ) {
    override val versionId = 2

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaSubString = "series"

    override fun pageListParse(document: Document): List<Page> {
        val button = document.selectFirst("#miBoton")
            ?: return super.pageListParse(document)

        launchIO { countViews(document) }

        val mangaId = document.selectFirst("#manga-reading-nav-foot, #manga-reading-nav-head")?.attr("data-id")
        val chapterId = document.selectFirst("#wp-manga-current-chap")?.attr("data-id")
        val rkDataStr = document.selectFirst("script:containsData(var RK =)")?.data()
            ?.substringAfter("var RK = ")
            ?.substringBefore(";")

        if (mangaId == null || chapterId == null || rkDataStr == null) {
            return super.pageListParse(document)
        }

        val rkData = json.parseToJsonElement(rkDataStr).jsonObject
        val ajaxUrl = rkData["ajaxUrl"]!!.jsonPrimitive.content
        val nonce = rkData["nonce"]!!.jsonPrimitive.content

        val formBody = FormBody.Builder()
            .add("action", "rk_get_token")
            .add("nonce", nonce)
            .add("chapter_id", chapterId)
            .add("manga_id", mangaId)
            .build()

        val ajaxJson = client.newCall(POST(ajaxUrl, headers, formBody)).execute().use {
            json.parseToJsonElement(it.body.string()).jsonObject
        }

        if (!ajaxJson["success"]!!.jsonPrimitive.boolean) {
            return emptyList()
        }

        val data = ajaxJson["data"]!!.jsonObject
        val token = data["token"]!!.jsonPrimitive.content
        val readerUrl = data["reader_url"]!!.jsonPrimitive.content

        val readerFormBody = FormBody.Builder()
            .add("rt", token)
            .add("chapter_id", chapterId)
            .add("manga_id", mangaId)
            .build()

        val readerDoc = client.newCall(POST(readerUrl, headers, readerFormBody)).execute().use { it.asJsoup() }

        return readerDoc.select("figure.wp-block-image img[alt='Context image']")
            .mapIndexed { index, element ->
                Page(index, document.location(), imageFromElement(element))
            }
    }
}
