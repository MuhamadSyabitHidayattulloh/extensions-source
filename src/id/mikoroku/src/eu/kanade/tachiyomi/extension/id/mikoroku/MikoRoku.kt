package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.intOrNull
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class MikoRoku : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(10, 1.seconds, 300.milliseconds)
        .readTimeout(30, TimeUnit.SECONDS)

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalog().sortedByDescending { it.rating }.toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalog().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalized()
        if (normalizedQuery.isEmpty() && query.isNotBlank()) return MangasPage(emptyList(), false)

        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.state?.let { GENRES[it] } ?: "Semua"

        var entries = fetchCatalog()
        if (normalizedQuery.isNotEmpty()) {
            entries = entries.filter { entry ->
                entry.title.normalized().contains(normalizedQuery) ||
                    entry.altTitle.split(";").any { it.normalized().contains(normalizedQuery) }
            }
        }
        if (genre != "Semua") {
            entries = entries.filter { entry ->
                entry.genres.any { it.equals(genre, ignoreCase = true) }
            }
        }
        entries = when (sort) {
            1 -> entries.sortedBy { it.title.lowercase() }
            2 -> entries.sortedByDescending { it.title.lowercase() }
            3 -> entries
            else -> entries.sortedByDescending { it.rating }
        }
        return entries.toMangasPage()
    }

    private fun List<CatalogEntry>.toMangasPage() = MangasPage(map { it.toSManga() }, false)

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(SortFilter(), GenreFilter())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val slug = when {
            url.pathSegments.getOrNull(0) == "series" -> url.pathSegments.getOrNull(1)
            else -> url.queryParameter("slug")
        } ?: return null

        return buildManga(slug).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = slugFromMangaUrl(manga.url) ?: throw Exception("Invalid manga URL: ${manga.url}")
        val details = if (fetchDetails) buildManga(slug) else manga
        val chapterList = if (fetchChapters) fetchChapterList(slug, details.title) else chapters
        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = slugFromMangaUrl(manga.url) ?: manga.url
        return "$baseUrl/series/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val match = SERIES_CHAPTER_URL_REGEX.find(chapter.url)
        if (match != null) return "$baseUrl/series/${match.groupValues[1]}"
        return baseUrl + chapter.url
    }

    private fun slugFromMangaUrl(url: String): String? {
        if (url.startsWith("/series/")) {
            return url.removePrefix("/series/").substringBefore("/").takeIf { it.isNotBlank() }
        }
        if (url.startsWith("/detail.html")) {
            return "$baseUrl$url".toHttpUrlOrNull()?.queryParameter("slug")
        }
        if (!url.startsWith("/") && !url.contains("/")) {
            return url.takeIf { it.isNotBlank() }
        }
        return null
    }

    private suspend fun buildManga(slug: String): SManga {
        val fields = fetchFirestoreDoc(slug)?.fields.orEmpty()
        val entry = fetchCatalog().find { it.slug == slug }
        return SManga.create().apply {
            url = slug
            title = fields.getString("title").nonBlank()
                ?: entry?.title
                ?: throw Exception("Manga tidak ditemukan")
            author = fields.getString("author").nonBlank() ?: entry?.author.nonBlank()
            artist = fields.getString("artist").nonBlank() ?: entry?.artist.nonBlank()
            description = fields.getString("desc").nonBlank()?.let { Jsoup.parseBodyFragment(it).text() }
                ?: entry?.desc.nonBlank()
            genre = fields.getStringList("genres").takeIf { it.isNotEmpty() }?.joinToString()
                ?: entry?.genres?.takeIf { it.isNotEmpty() }?.joinToString()
            status = (fields.getString("status") ?: entry?.status).toMangaStatus()
            thumbnail_url = fields.getString("img").nonBlank() ?: entry?.img.nonBlank()
        }
    }

    private fun String?.toMangaStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(slug: String, title: String): List<SChapter> {
        val byNum = sortedMapOf<Float, SChapter>(reverseOrder())

        addFromSiteApi(slug, title, byNum)
        if (byNum.isEmpty()) addFromListen(slug, byNum)

        return byNum.values.toList()
    }

    private suspend fun addFromSiteApi(
        slug: String,
        apiTitle: String,
        byNum: MutableMap<Float, SChapter>,
    ) {
        val url = "$baseUrl/api/blogger/chapters?title=${URLEncoder.encode(apiTitle, "UTF-8")}"
        val chapters = try {
            client.get(url, Fetch.XHR).parseAs<ApiChaptersResponse>().chapters
        } catch (e: Exception) {
            return
        }
        for (c in chapters) {
            val num = c.num.toFloatOrNull()
                ?: c.title.chapterNumber().toFloat().takeIf { it >= 0 } ?: continue
            val images = c.images.map { it.replace("/s1600/", "/s0/") }
            if (!byNum.containsKey(num)) {
                byNum[num] = SChapter.create().apply {
                    url = "/series/$slug/chapter/$num"
                    name = c.title.ifBlank { "Chapter ${num.toString().removeSuffix(".0")}" }
                    chapter_number = num
                    date_upload = Instant.tryParse(c.createdAt)
                    if (images.isNotEmpty()) {
                        memo = buildJsonObject {
                            putJsonArray("images") {
                                for (img in images) add(JsonPrimitive(img))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun addFromListen(slug: String, byNum: MutableMap<Float, SChapter>) {
        val parent = "$FIRESTORE_DB/documents/manga/$slug"
        val docs = firestoreListenDocs(parent, "chapters")
        for (doc in docs) {
            val f = doc.fields
            if (f.getBoolean("isDraft") == true) continue
            val docId = doc.name.substringAfterLast("/")
            val numRaw = f.getString("num").orEmpty().ifBlank { f.getString("chapter").orEmpty() }
                .ifBlank { docId }.ifBlank { f.getString("order").orEmpty() }
                .ifBlank { f.getString("title").orEmpty() }
            val num = numRaw.toFloatOrNull()
                ?: f.getString("title").orEmpty().chapterNumber().toFloat().takeIf { it >= 0 } ?: continue
            val images = f.getStringList("images").map { it.replace("/s1600/", "/s0/") }
            if (!byNum.containsKey(num)) {
                val dateMillis = f.getLong("date") ?: 0L
                byNum[num] = SChapter.create().apply {
                    url = "/series/$slug/chapter/$num"
                    name = f.getString("title").nonBlank() ?: "Chapter ${num.toString().removeSuffix(".0")}"
                    chapter_number = num
                    date_upload = dateMillis
                    if (images.isNotEmpty()) {
                        memo = buildJsonObject {
                            putJsonArray("images") {
                                for (img in images) add(JsonPrimitive(img))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun firestoreListenDocs(
        parent: String,
        collectionId: String,
    ): List<FirestoreDoc> {
        val docs = mutableListOf<FirestoreDoc>()
        try {
            val dbEnc = URLEncoder.encode(FIRESTORE_DB, "UTF-8")
            val reqJson = buildJsonObject {
                put("database", FIRESTORE_DB)
                putJsonObject("addTarget") {
                    putJsonObject("query") {
                        putJsonObject("structuredQuery") {
                            putJsonArray("from") {
                                addJsonObject {
                                    put("collectionId", collectionId)
                                }
                            }
                            putJsonArray("orderBy") {
                                addJsonObject {
                                    putJsonObject("field") {
                                        put("fieldPath", "__name__")
                                    }
                                    put("direction", "ASCENDING")
                                }
                            }
                        }
                        put("parent", parent)
                    }
                    put("targetId", 2)
                }
            }.toString()

            val initHeaders =
                "X-Goog-Api-Client:gl-js/ fire/11.0.2\r\n" +
                    "Content-Type:text/plain\r\n" +
                    "X-Firebase-GMPID:1:271920446600:web:974b11c707ebc11a7bf33d\r\n"
            val openBody = "headers=" + URLEncoder.encode(initHeaders, "UTF-8") +
                "&count=1&ofs=0&req0___data__=" + URLEncoder.encode(reqJson, "UTF-8")
            val rid = 10000 + (Math.random() * 90000).toInt()
            val openUrl = "$FIRESTORE_LISTEN?VER=8&database=$dbEnc&RID=$rid" +
                "&CVER=22&X-HTTP-Session-Id=gsessionid&zx=${randomBase36(12)}&t=1"

            val postHeaders = headers.newBuilder()
                .set("X-Client-Protocol", "webchannel")
                .build()
            val session = client.post(
                openUrl,
                postHeaders,
                Fetch.NONE,
                openBody.toRequestBody("text/plain".toMediaType()),
                ensureSuccess = false,
            ).use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val g = resp.header("X-HTTP-Session-Id")
                val b = resp.body.string()
                val m = Regex("\"c\",\"([A-Za-z0-9_\\-]+)\"").find(b)
                Pair(m?.groupValues?.getOrNull(1), g)
            }

            val sid = session.first
            val gsession = session.second
            if (sid.isNullOrBlank() || gsession.isNullOrBlank()) return docs

            val backUrl = "$FIRESTORE_LISTEN?gsessionid=$gsession&VER=8&database=$dbEnc" +
                "&RID=rpc&SID=$sid&AID=0&CI=0&TYPE=xmlhttp&zx=${randomBase36(12)}&t=1"
            val getHeaders = headers.newBuilder()
                .set("X-Client-Protocol", "webchannel")
                .build()

            client.get(
                backUrl,
                getHeaders,
                Fetch.NONE,
                ensureSuccess = false,
            ).use { resp ->
                if (!resp.isSuccessful) return docs
                val src = resp.body.source()
                var done = false
                while (!done) {
                    val lenLine = try {
                        var line = src.readUtf8LineStrict()
                        while (line.isBlank()) line = src.readUtf8LineStrict()
                        line
                    } catch (e: Exception) {
                        break
                    }
                    val len = lenLine.trim().toLongOrNull() ?: break
                    if (len <= 0 || len > 20_000_000) break
                    val frame = try {
                        src.readUtf8(len)
                    } catch (e: Exception) {
                        break
                    }
                    try {
                        val arr = jsonInstance.parseToJsonElement(frame).array
                        for (msg in arr) {
                            val msgArr = msg as? JsonArray ?: continue
                            if (msgArr.size < 2) continue
                            val payload = msgArr[1]
                            val items = when (payload) {
                                is JsonArray -> payload
                                is JsonObject -> listOf(payload)
                                else -> continue
                            }
                            for (item in items) {
                                val obj = item as? JsonObject ?: continue
                                val docChange = obj["documentChange"]?.obj
                                if (docChange != null) {
                                    val doc = docChange["document"]
                                    if (doc != null) {
                                        docs += doc.parseAs<FirestoreDoc>()
                                    }
                                } else if (obj.containsKey("targetChange")) {
                                    val tc = obj["targetChange"]?.obj
                                    if (tc?.get("targetChangeType")?.stringOrNull == "CURRENT") {
                                        val ids = tc["targetIds"] as? JsonArray
                                        if (ids != null && ids.any { it.intOrNull == 2 }) {
                                            done = true
                                        }
                                    }
                                } else if (obj.containsKey("__sm__")) {
                                    done = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Broken frame: skip it and read the next one.
                    }
                }
            }
        } catch (e: Exception) {
            // Listen failed
        }
        return docs
    }

    private fun randomBase36(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder()
        repeat(n) { sb.append(chars[(Math.random() * chars.length).toInt()]) }
        return sb.toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val memoImages = chapter.memo["images"]?.array?.mapNotNull { it.stringOrNull }
        if (!memoImages.isNullOrEmpty()) {
            return memoImages.toPages()
        }

        SERIES_CHAPTER_URL_REGEX.find(chapter.url)?.let { match ->
            val slug = match.groupValues[1]
            val num = match.groupValues[2].toFloatOrNull()
                ?: throw Exception("Unsupported chapter URL: ${chapter.url}")
            return fetchSeriesChapterPages(slug, num)
        }

        POST_URL_REGEX.find(chapter.url)?.let { match ->
            return fetchBloggerPostPages(match.groupValues[2])
        }

        CHAPTER_URL_REGEX.find(chapter.url)?.let { match ->
            val slug = match.groupValues[1]
            val key = match.groupValues[2]
            return fetchListenChapterImages(slug, key)?.toPages() ?: emptyList()
        }

        throw Exception("Unsupported chapter URL: ${chapter.url}")
    }

    private suspend fun fetchSeriesChapterPages(slug: String, num: Float): List<Page> {
        val entry = fetchCatalog().find { it.slug == slug }
        val title = entry?.title ?: slug

        val url = "$baseUrl/api/blogger/chapters?title=${URLEncoder.encode(title, "UTF-8")}"
        try {
            val chapters = client.get(url, Fetch.XHR).parseAs<ApiChaptersResponse>().chapters
            for (c in chapters) {
                val cNum = c.num.toFloatOrNull()
                    ?: c.title.chapterNumber().toFloat().takeIf { it >= 0 } ?: continue
                if (cNum == num && c.images.isNotEmpty()) {
                    return c.images.map { it.replace("/s1600/", "/s0/") }.toPages()
                }
            }
        } catch (e: Exception) {
            // Fall back to Listen
        }

        val parent = "$FIRESTORE_DB/documents/manga/$slug"
        val docs = firestoreListenDocs(parent, "chapters")
        for (doc in docs) {
            val f = doc.fields
            if (f.getBoolean("isDraft") == true) continue
            val docId = doc.name.substringAfterLast("/")
            val numRaw = f.getString("num").orEmpty().ifBlank { f.getString("chapter").orEmpty() }
                .ifBlank { docId }.ifBlank { f.getString("order").orEmpty() }
                .ifBlank { f.getString("title").orEmpty() }
            val docNum = numRaw.toFloatOrNull()
                ?: f.getString("title").orEmpty().chapterNumber().toFloat().takeIf { it >= 0 } ?: continue
            if (docNum == num) {
                val images = f.getStringList("images").map { it.replace("/s1600/", "/s0/") }
                if (images.isNotEmpty()) return images.toPages()
            }
        }

        return emptyList()
    }

    private suspend fun fetchListenChapterImages(slug: String, key: String): List<String>? {
        val parent = "$FIRESTORE_DB/documents/manga/$slug"
        val docs = firestoreListenDocs(parent, "chapters")
        for (doc in docs) {
            if (doc.name.substringAfterLast("/") != key) continue
            val f = doc.fields
            if (f.getBoolean("isDraft") == true) continue
            val images = f.getStringList("images").map { it.replace("/s1600/", "/s0/") }
            if (images.isNotEmpty()) return images
        }
        return null
    }

    private suspend fun fetchBloggerPostPages(postId: String): List<Page> {
        val url = "https://www.mikodrive.my.id/feeds/posts/default/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()

        return client.get(url, Fetch.XHR).parseAs<BloggerEntryResponse>().entry.imageUrls().toPages()
    }

    private fun List<String>.toPages(): List<Page> = mapIndexed { index, url -> Page(index, imageUrl = url) }

    private suspend fun fetchCatalog(): List<CatalogEntry> {
        val site = try {
            client.get("$baseUrl/catalog/manga.json", Fetch.XHR).parseAs<List<CatalogEntry>>()
        } catch (e: Exception) {
            null
        }
        if (site != null) return site
        return client.get(
            "https://raw.githubusercontent.com/moemaomao/mymangadata/main/all-manga.json",
            Fetch.XHR,
        ).parseAs<List<CatalogEntry>>()
    }

    private suspend fun fetchFirestoreDoc(slug: String): FirestoreMap? {
        val url = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/manga/$slug"

        val response = client.get(url, Fetch.XHR, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            return null
        }
        return response.parseAs<FirestoreMap>()
    }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

    private class SortFilter :
        Filter.Select<String>(
            "Order By",
            arrayOf("Populer", "A-Z", "Z-A", "Terbaru"),
        )

    private class GenreFilter : Filter.Select<String>("Genre", GENRES.toTypedArray())

    companion object {
        private val SERIES_CHAPTER_URL_REGEX = """/series/([^/]+)/chapter/([\d.]+)""".toRegex()
        private val CHAPTER_URL_REGEX = """/manga/([^/]+)/chapter/([^/]+)""".toRegex()
        private val POST_URL_REGEX = """/manga/([^/]+)/post/([^/]+)""".toRegex()

        private const val FIRESTORE_DB = "projects/mikoroku/databases/(default)"
        private const val FIRESTORE_LISTEN =
            "https://firestore.googleapis.com/google.firestore.v1.Firestore/Listen/channel"

        private val GENRES = listOf(
            "Semua", "Action", "Adult", "Adventure", "Cheating", "Comedy",
            "Crossdressing", "Doujinshi", "Drama", "Ecchi", "Elf", "Erotica",
            "Fantasy", "Gore", "Harem", "Hentai", "Horror", "Incest", "Isekai",
            "Magic", "Mature", "Mecha", "Milf", "Mystery", "Psychological",
            "Reincarnation", "Romance", "School", "School Life", "Sci-Fi",
            "Seinen", "Sexual Violence", "Shota", "Shounen", "Slice of Life",
            "Smut", "Strategy Game", "Supernatural", "Survival", "Yandere",
            "Zombies",
        )
    }
}
