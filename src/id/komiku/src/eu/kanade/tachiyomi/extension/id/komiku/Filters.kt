package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class TypeFilter(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class GenreFilter(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class OrderFilter(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class StatusFilter(title: String, val key: String) : Filter.TriState(title) {
    override fun toString(): String = name
}

class Type(types: Array<TypeFilter>) : Filter.Select<TypeFilter>("Tipe", types, 0)
class Order(orders: Array<OrderFilter>) : Filter.Select<OrderFilter>("Order", orders, 0)
class Genre1(genres: Array<GenreFilter>) : Filter.Select<GenreFilter>("Genre 1", genres, 0)
class Genre2(genres: Array<GenreFilter>) : Filter.Select<GenreFilter>("Genre 2", genres, 0)
class Status(statuses: Array<StatusFilter>) : Filter.Select<StatusFilter>("Status", statuses, 0)

fun getFilterList() = FilterList(
    Type(categoryNames),
    Order(orderBy),
    Genre1(genreList),
    Genre2(genreList),
    Status(statusList),
)

private val categoryNames = arrayOf(
    TypeFilter("Semua", ""),
    TypeFilter("Manga", "manga"),
    TypeFilter("Manhua", "manhua"),
    TypeFilter("Manhwa", "manhwa"),
)

private val orderBy = arrayOf(
    OrderFilter("Chapter Terbaru", "modified"),
    OrderFilter("Komik Terbaru", "date"),
    OrderFilter("Peringkat", "meta_value_num"),
    OrderFilter("Acak", "rand"),
)

private val genreList = arrayOf(
    GenreFilter("Semua", ""),
    GenreFilter("Academy", "academy"),
    GenreFilter("Action", "action"),
    GenreFilter("Adaptation", "adaptation"),
    GenreFilter("Adult", "adult"),
    GenreFilter("Adventure", "adventure"),
    GenreFilter("apocalypse", "apocalypse"),
    GenreFilter("Beasts", "beasts"),
    GenreFilter("Blacksmith", "blacksmith"),
    GenreFilter("Comedy", "comedy"),
    GenreFilter("Comic", "comic"),
    GenreFilter("Cooking", "cooking"),
    GenreFilter("Crime", "crime"),
    GenreFilter("Crossdressing", "crossdressing"),
    GenreFilter("Dark Fantasy", "dark-fantasy"),
    GenreFilter("Demons", "demons"),
    GenreFilter("Doujinshi", "doujinshi"),
    GenreFilter("Drama", "drama"),
    GenreFilter("Ecchi", "ecchi"),
    GenreFilter("Entertainment", "entertainment"),
    GenreFilter("Fantasy", "fantasy"),
    GenreFilter("Game", "game"),
    GenreFilter("Gender Bender", "gender-bender"),
    GenreFilter("Genderswap", "genderswap"),
    GenreFilter("Genius", "genius"),
    GenreFilter("Ghosts", "ghosts"),
    GenreFilter("Gore", "gore"),
    GenreFilter("Gyaru", "gyaru"),
    GenreFilter("Harem", "harem"),
    GenreFilter("Hentai", "hentai"),
    GenreFilter("Historical", "historical"),
    GenreFilter("Horror", "horror"),
    GenreFilter("Isekai", "isekai"),
    GenreFilter("Josei", "josei"),
    GenreFilter("Knight", "knight"),
    GenreFilter("Long Strip", "long-strip"),
    GenreFilter("Magic", "magic"),
    GenreFilter("Magical Girls", "magical-girls"),
    GenreFilter("Manga", "manga"),
    GenreFilter("Mangatoon", "mangatoon"),
    GenreFilter("Manhwa", "manhwa"),
    GenreFilter("Martial Art", "martial-art"),
    GenreFilter("Martial Arts", "martial-arts"),
    GenreFilter("Mature", "mature"),
    GenreFilter("MC Rebirth", "mc-rebirth"),
    GenreFilter("Mecha", "mecha"),
    GenreFilter("Medical", "medical"),
    GenreFilter("Military", "military"),
    GenreFilter("Monster", "monster"),
    GenreFilter("Monster girls", "monster-girls"),
    GenreFilter("Monsters", "monsters"),
    GenreFilter("Murim", "murim"),
    GenreFilter("Music", "music"),
    GenreFilter("Mystery", "mystery"),
    GenreFilter("Office Workers", "office-workers"),
    GenreFilter("One Shot", "one-shot"),
    GenreFilter("Oneshot", "oneshot"),
    GenreFilter("Police", "police"),
    GenreFilter("Psychological", "psychological"),
    GenreFilter("Regression", "regression"),
    GenreFilter("Reincarnation", "reincarnation"),
    GenreFilter("Revenge", "revenge"),
    GenreFilter("Romance", "romance"),
    GenreFilter("School", "school"),
    GenreFilter("School life", "school-life"),
    GenreFilter("Sci-fi", "sci-fi"),
    GenreFilter("Seinen", "seinen"),
    GenreFilter("Sexual Violence", "sexual-violence"),
    GenreFilter("Shotacon", "shotacon"),
    GenreFilter("Shoujo", "shoujo"),
    GenreFilter("Shoujo Ai", "shoujo-ai"),
    GenreFilter("Shoujo(G)", "shoujog"),
    GenreFilter("Shounen", "shounen"),
    GenreFilter("Shounen Ai", "shounen-ai"),
    GenreFilter("Slice of Life", "slice-of-life"),
    GenreFilter("Slow Life", "slow-life"),
    GenreFilter("Smut", "smut"),
    GenreFilter("Sport", "sport"),
    GenreFilter("Sports", "sports"),
    GenreFilter("Strategy", "strategy"),
    GenreFilter("Super Power", "super-power"),
    GenreFilter("Supernatural", "supernatural"),
    GenreFilter("Survival", "survival"),
    GenreFilter("Sword Fight", "sword-fight"),
    GenreFilter("Sword Master", "sword-master"),
    GenreFilter("Swormanship", "swormanship"),
    GenreFilter("System", "system"),
    GenreFilter("Thriller", "thriller"),
    GenreFilter("Tragedy", "tragedy"),
    GenreFilter("Trauma", "trauma"),
    GenreFilter("Vampire", "vampire"),
    GenreFilter("Villainess", "villainess"),
    GenreFilter("Violence", "violence"),
    GenreFilter("Web Comic", "web-comic"),
    GenreFilter("Webtoon", "webtoon"),
    GenreFilter("Webtoons", "webtoons"),
    GenreFilter("Xianxia", "xianxia"),
    GenreFilter("Xuanhuan", "xuanhuan"),
    GenreFilter("Yuri", "yuri"),
)

private val statusList = arrayOf(
    StatusFilter("Semua", ""),
    StatusFilter("Ongoing", "ongoing"),
    StatusFilter("Tamat", "end"),
)
