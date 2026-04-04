package eu.kanade.tachiyomi.extension.id.komikucc

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected)
        }
    }
}

class StatusFilter : UriPartFilter("Status", "status", statusList)
class TypeFilter : UriPartFilter("Tipe", "type", typeList)
class OrderFilter : UriPartFilter("Urutan", "order", orderList)

class GenreFilter(vals: Array<GenreCheckBox>) :
    Filter.Group<GenreCheckBox>("Genre", vals.toList()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter("genre[]", it.id)
        }
    }
}

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

private val statusList = arrayOf(
    Pair("Semua", ""),
    Pair("Ongoing", "ongoing"),
    Pair("Completed", "completed"),
    Pair("Hiatus", "hiatus"),
)

private val typeList = arrayOf(
    Pair("Semua", ""),
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
    Pair("Manhua", "manhua"),
)

private val orderList = arrayOf(
    Pair("Semua", ""),
    Pair("A-Z", "title"),
    Pair("Z-A", "titlereverse"),
    Pair("Update", "update"),
    Pair("New", "latest"),
    Pair("Popular", "popular"),
)

val genreList = arrayOf(
    GenreCheckBox("4-Koma", "4-koma"),
    GenreCheckBox("Action", "action"),
    GenreCheckBox("Action Adventure", "action-adventure"),
    GenreCheckBox("Adaptation", "adaptation"),
    GenreCheckBox("Adult", "adult"),
    GenreCheckBox("Adventure", "adventure"),
    GenreCheckBox("Animals", "animals"),
    GenreCheckBox("Anthology", "anthology"),
    GenreCheckBox("Antihero", "antihero"),
    GenreCheckBox("apocalypse", "apocalypse"),
    GenreCheckBox("Award Winning", "award-winning"),
    GenreCheckBox("Beasts", "beasts"),
    GenreCheckBox("Bodyswap", "bodyswap"),
    GenreCheckBox("Boys' Love", "boys-love"),
    GenreCheckBox("Bully", "bully"),
    GenreCheckBox("Cartoon", "cartoon"),
    GenreCheckBox("Childhood Friends", "childhood-friends"),
    GenreCheckBox("Comedy", "comedy"),
    GenreCheckBox("Comic", "comic"),
    GenreCheckBox("Cooking", "cooking"),
    GenreCheckBox("Crime", "crime"),
    GenreCheckBox("Crossdressing", "crossdressing"),
    GenreCheckBox("Dance", "dance"),
    GenreCheckBox("Dark Fantasy", "dark-fantasy"),
    GenreCheckBox("Delinquent", "delinquent"),
    GenreCheckBox("Delinquents", "delinquents"),
    GenreCheckBox("Dementia", "dementia"),
    GenreCheckBox("Demon", "demon"),
    GenreCheckBox("Demons", "demons"),
    GenreCheckBox("Doujinshi", "doujinshi"),
    GenreCheckBox("Drama", "drama"),
    GenreCheckBox("Dungeons", "dungeons"),
    GenreCheckBox("Ecchi", "ecchi"),
    GenreCheckBox("Emperor's daughter", "emperors-daughter"),
    GenreCheckBox("Entertainment", "entertainment"),
    GenreCheckBox("Fan-Colored", "fan-colored"),
    GenreCheckBox("Fantas", "fantas"),
    GenreCheckBox("Fantasy", "fantasy"),
    GenreCheckBox("Fetish", "fetish"),
    GenreCheckBox("Full Color", "full-color"),
    GenreCheckBox("Game", "game"),
    GenreCheckBox("Games", "games"),
    GenreCheckBox("Gang", "gang"),
    GenreCheckBox("Gender Bender", "gender-bender"),
    GenreCheckBox("Genderswap", "genderswap"),
    GenreCheckBox("Ghosts", "ghosts"),
    GenreCheckBox("Girls", "girls"),
    GenreCheckBox("Girls' Love", "girls-love"),
    GenreCheckBox("gore", "gore"),
    GenreCheckBox("gorre", "gorre"),
    GenreCheckBox("Gyaru", "gyaru"),
    GenreCheckBox("Harem", "harem"),
    GenreCheckBox("Hentai", "hentai"),
    GenreCheckBox("Hero", "hero"),
    GenreCheckBox("Historical", "historical"),
    GenreCheckBox("Horror", "horror"),
    GenreCheckBox("Imageset", "imageset"),
    GenreCheckBox("Incest", "incest"),
    GenreCheckBox("Isekai", "isekai"),
    GenreCheckBox("Josei", "josei"),
    GenreCheckBox("Josei(W)", "josei-w"),
    GenreCheckBox("Josei(W)", "joseiw"),
    GenreCheckBox("Kids", "kids"),
    GenreCheckBox("Leveling", "leveling"),
    GenreCheckBox("Loli", "loli"),
    GenreCheckBox("Lolicon", "lolicon"),
    GenreCheckBox("Long Strip", "long-strip"),
    GenreCheckBox("Mafia", "mafia"),
    GenreCheckBox("Magi", "magi"),
    GenreCheckBox("Magic", "magic"),
    GenreCheckBox("Magical Girls", "magical-girls"),
    GenreCheckBox("Manga", "manga"),
    GenreCheckBox("Manhua", "manhua"),
    GenreCheckBox("Manhwa", "manhwa"),
    GenreCheckBox("Martial Art", "martial-art"),
    GenreCheckBox("Martial Arts", "martial-arts"),
    GenreCheckBox("Mature", "mature"),
    GenreCheckBox("Mecha", "mecha"),
    GenreCheckBox("Medical", "medical"),
    GenreCheckBox("Military", "military"),
    GenreCheckBox("Mirror", "mirror"),
    GenreCheckBox("Modern", "modern"),
    GenreCheckBox("Monster Girls", "monster-girls"),
    GenreCheckBox("Monsters", "monsters"),
    GenreCheckBox("Murim", "murim"),
    GenreCheckBox("Music", "music"),
    GenreCheckBox("Mystery", "mystery"),
    GenreCheckBox("Necromancer", "necromancer"),
    GenreCheckBox("Ninja", "ninja"),
    GenreCheckBox("Non-human", "non-human"),
    GenreCheckBox("Office Workers", "office-workers"),
    GenreCheckBox("Official Colored", "official-colored"),
    GenreCheckBox("One-Shot", "one-shot"),
    GenreCheckBox("Oneshot", "oneshot"),
    GenreCheckBox("Overpowered", "overpowered"),
    GenreCheckBox("Parody", "parody"),
    GenreCheckBox("Pets", "pets"),
    GenreCheckBox("Philosophical", "philosophical"),
    GenreCheckBox("Police", "police"),
    GenreCheckBox("Post-Apocalyptic", "post-apocalyptic"),
    GenreCheckBox("Project", "project"),
    GenreCheckBox("Psychological", "psychological"),
    GenreCheckBox("Regression", "regression"),
    GenreCheckBox("Reincarnation", "reincarnation"),
    GenreCheckBox("Revenge", "revenge"),
    GenreCheckBox("Reverse Harem", "reverse-harem"),
    GenreCheckBox("Reverse Isekai", "reverse-isekai"),
    GenreCheckBox("Romance", "romance"),
    GenreCheckBox("Royal family", "royal-family"),
    GenreCheckBox("Royalty", "royalty"),
    GenreCheckBox("School", "school"),
    GenreCheckBox("School Life", "school-life"),
    GenreCheckBox("Sci-fi", "sci-fi"),
    GenreCheckBox("Seinen", "seinen"),
    GenreCheckBox("Seinen(M)", "seinenm"),
    GenreCheckBox("Seinin", "seinin"),
    GenreCheckBox("Sexual Violence", "sexual-violence"),
    GenreCheckBox("Shotacon", "shotacon"),
    GenreCheckBox("Shoujo", "shoujo"),
    GenreCheckBox("Shoujo Ai", "shoujo-ai"),
    GenreCheckBox("Shoujo(G)", "shoujo-g"),
    GenreCheckBox("Shoujo(G)", "shoujog"),
    GenreCheckBox("Shounen", "shounen"),
    GenreCheckBox("Shounen Ai", "shounen-ai"),
    GenreCheckBox("Shounen(B)", "shounen-b"),
    GenreCheckBox("Shounen(B)", "shounenb"),
    GenreCheckBox("Shounn", "shounn"),
    GenreCheckBox("Showbiz", "showbiz"),
    GenreCheckBox("Slice of Life", "slice-of-life"),
    GenreCheckBox("Smut", "smut"),
    GenreCheckBox("Space", "space"),
    GenreCheckBox("Sport", "sport"),
    GenreCheckBox("Sports", "sports"),
    GenreCheckBox("Super Power", "super-power"),
    GenreCheckBox("Superhero", "superhero"),
    GenreCheckBox("Supernatural", "supernatural"),
    GenreCheckBox("Supranatural", "supranatural"),
    GenreCheckBox("Survival", "survival"),
    GenreCheckBox("System", "system"),
    GenreCheckBox("Thriller", "thriller"),
    GenreCheckBox("Time Travel", "time-travel"),
    GenreCheckBox("Traditional Games", "traditional-games"),
    GenreCheckBox("Tragedy", "tragedy"),
    GenreCheckBox("Transmigration", "transmigration"),
    GenreCheckBox("Vampire", "vampire"),
    GenreCheckBox("Vampires", "vampires"),
    GenreCheckBox("Video Games", "video-games"),
    GenreCheckBox("Villainess", "villainess"),
    GenreCheckBox("Violence", "violence"),
    GenreCheckBox("Virtual Reality", "virtual-reality"),
    GenreCheckBox("Web Comic", "web-comic"),
    GenreCheckBox("Webtoon", "webtoon"),
    GenreCheckBox("Webtoons", "webtoons"),
    GenreCheckBox("Wuxia", "wuxia"),
    GenreCheckBox("Xianxia", "xianxia"),
    GenreCheckBox("Xuanhuan", "xuanhuan"),
    GenreCheckBox("Yaoi", "yaoi"),
    GenreCheckBox("Yuri", "yuri"),
    GenreCheckBox("Zombies", "zombies"),
)
