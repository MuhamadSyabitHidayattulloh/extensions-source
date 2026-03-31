package eu.kanade.tachiyomi.extension.all.onisaga

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
    val unchecked get() = state.filter { !it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class TypeFilter :
    Filter.Select<String>(
        "Type",
        arrayOf(
            "Any",
            "Manga",
            "Manhwa",
            "Manhua",
            "Novel",
            "One-Shot",
            "Doujinshi",
        ),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "MANGA"
        2 -> "MANHWA"
        3 -> "MANHUA"
        4 -> "NOVEL"
        5 -> "ONE-SHOT"
        6 -> "DOUJINSHI"
        else -> ""
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf(
            "Any",
            "Ongoing",
            "Completed",
            "Hiatus",
            "Releasing",
        ),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        3 -> "hiatus"
        4 -> "releasing"
        else -> ""
    }
}

class GenreFilter :
    CheckBoxGroup(
        "Genres",
        listOf(
            Pair("Action", "1"),
            Pair("Adventure", "6"),
            Pair("Avant Garde", "43"),
            Pair("Boys Love", "31"),
            Pair("Comedy", "2"),
            Pair("Demons", "5"),
            Pair("Drama", "15"),
            Pair("Ecchi", "29"),
            Pair("Fantasy", "7"),
            Pair("Girls Love", "28"),
            Pair("Gourmet", "42"),
            Pair("Harem", "37"),
            Pair("Horror", "16"),
            Pair("Isekai", "3"),
            Pair("Iyashikei", "34"),
            Pair("Josei", "35"),
            Pair("Kids", "38"),
            Pair("Magic", "8"),
            Pair("Mahou Shoujo", "41"),
            Pair("Martial Arts", "11"),
            Pair("Mecha", "36"),
            Pair("Military", "17"),
            Pair("Music", "30"),
            Pair("Mystery", "19"),
            Pair("Parody", "12"),
            Pair("Psychological", "18"),
            Pair("Reverse Harem", "44"),
            Pair("Romance", "20"),
            Pair("School", "21"),
            Pair("School Life", "24"),
            Pair("Sci-Fi", "13"),
            Pair("Seinen", "14"),
            Pair("Shoujo", "27"),
            Pair("Shounen", "4"),
            Pair("Slice of Life", "26"),
            Pair("Space", "22"),
            Pair("Sports", "32"),
            Pair("Super Power", "9"),
            Pair("Supernatural", "10"),
            Pair("Suspense", "39"),
            Pair("Thriller", "40"),
            Pair("Time Travel", "23"),
            Pair("Tragedy", "25"),
            Pair("Vampire", "33"),
        ),
    ) {
    fun getSelectedGenres(): List<String> = checked ?: emptyList()
    fun getExcludedGenres(): List<String> = unchecked ?: emptyList()
}

class MinChapterFilter :
    Filter.Select<String>(
        "Chapters",
        arrayOf(
            "Any",
            "10+",
            "50+",
            "100+",
            "200+",
        ),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "10"
        2 -> "50"
        3 -> "100"
        4 -> "200"
        else -> ""
    }
}

class TextField(name: String, default: String = "") : Filter.Text(name, default)

class ReleaseDateFilter :
    Filter.Group<TextField>(
        "Release date",
        listOf(
            TextField("Start Date", ""),
            TextField("End Date", ""),
        ),
    ) {
    val startDate get() = state.getOrNull(0)
    val endDate get() = state.getOrNull(1)
}

class SortFilter :
    Filter.Select<String>(
        "Sort",
        arrayOf(
            "Newest",
            "Most viewed",
            "Release date",
            "Top rated",
            "Name A-Z",
            "Fan Favorites",
        ),
    ) {
    fun toUriPart(): String = when (state) {
        0 -> "created_at"
        1 -> "view"
        2 -> "release_date"
        3 -> "vote_average"
        4 -> "title"
        5 -> "fan_favorites"
        else -> "created_at"
    }
}
