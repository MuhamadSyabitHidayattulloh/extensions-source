package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

class SortFilter : Filter.Select<String>("Sort", SORT_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = SORT_OPTIONS.getOrNull(state)?.second
}

class StatusFilter : Filter.Select<String>("Publication Status", STATUS_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = STATUS_OPTIONS.getOrNull(state)?.second
}

class FormatFilter : Filter.Select<String>("Format", FORMAT_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = FORMAT_OPTIONS.getOrNull(state)?.second
}

class CountryFilter : Filter.Select<String>("Country of Origin", COUNTRY_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = COUNTRY_OPTIONS.getOrNull(state)?.second
}

class YearFilter : Filter.Select<String>("Release Year", YEAR_LABELS) {
    fun toValue(): Int? = YEAR_VALUES.getOrNull(state)?.toIntOrNull()
}

class ScanGroupFilter : Filter.Text("Scan Group")
class AuthorFilter : Filter.Text("Author")
class ArtistFilter : Filter.Text("Artist")

class LanguageFilter :
    Filter.Group<LanguageOption>(
        "Target Language",
        LANGUAGE_OPTIONS.map { LanguageOption(it.first, it.second) },
    ) {
    fun toLanguages(): List<String> = state.filter { it.state }.map { it.value }
}

class LanguageOption(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<GenreOption>(
        "Genres",
        GENRE_OPTIONS.map { GenreOption(it) },
    ) {
    fun toGenres(): List<String> = state.filter { it.state }.map { it.name }
}

class GenreOption(name: String) : Filter.CheckBox(name)

fun FilterList.toSearchFilters(): SearchFilters {
    val sort = filterIsInstance<SortFilter>().firstOrNull()?.toValue()
    val status = filterIsInstance<StatusFilter>().firstOrNull()?.toValue()
    val format = filterIsInstance<FormatFilter>().firstOrNull()?.toValue()
    val country = filterIsInstance<CountryFilter>().firstOrNull()?.toValue()
    val year = filterIsInstance<YearFilter>().firstOrNull()?.toValue()
    val genres = filterIsInstance<GenreFilter>().firstOrNull()?.toGenres()
    val scanGroup = filterIsInstance<ScanGroupFilter>().firstOrNull()?.state?.takeIf { it.isNotBlank() }
    val author = filterIsInstance<AuthorFilter>().firstOrNull()?.state?.takeIf { it.isNotBlank() }
    val artist = filterIsInstance<ArtistFilter>().firstOrNull()?.state?.takeIf { it.isNotBlank() }
    val languages = filterIsInstance<LanguageFilter>().firstOrNull()?.toLanguages()

    return SearchFilters(
        genres = genres?.ifEmpty { null },
        year = year,
        sort = sort,
        status = status,
        format = format ?: "manga",
        countryOfOrigin = country,
        author = author,
        artist = artist,
        translatedLanguages = languages?.ifEmpty { null },
        scanGroup = scanGroup,
    )
}

const val SORT_POPULAR = "POPULARITY_DESC"
const val SORT_LATEST = "START_DATE_DESC"

private val SORT_OPTIONS = arrayOf(
    "Popular" to SORT_POPULAR,
    "Top Rated" to "SCORE_DESC",
    "Latest" to SORT_LATEST,
    "A-Z" to "TITLE_ROMAJI",
)

private val STATUS_OPTIONS = arrayOf(
    "Any" to null,
    "Completed" to "completed",
    "Ongoing" to "ongoing",
    "Upcoming" to "upcoming",
    "Cancelled" to "cancelled",
    "Hiatus" to "hiatus",
)

private val FORMAT_OPTIONS = arrayOf(
    "Any" to null,
    "One-shot" to "oneshot",
    "Doujinshi" to "doujinshi",
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
)

private val COUNTRY_OPTIONS = arrayOf(
    "Any" to null,
    "Manga (JP)" to "JP",
    "Manhwa (KR)" to "KR",
    "Manhua (CN)" to "CN",
)

private val GENRE_OPTIONS = listOf(
    "Action",
    "Adventure",
    "Boys' Love",
    "Comedy",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Girls' Love",
    "Horror",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Supernatural",
    "Thriller",
    "Mystery",
    "Psychological",
    "Sports",
    "Music",
    "Josei",
    "Seinen",
    "Shoujo",
    "Shounen",
)

private val LANGUAGE_OPTIONS = arrayOf(
    "English" to "en",
    "Arabic" to "ar",
    "Spanish" to "es",
    "Spanish (LatAm)" to "es-419",
    "French" to "fr",
    "Italian" to "it",
    "Polish" to "pl",
    "Portuguese (Brazil)" to "pt-br",
    "German" to "de",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Chinese" to "zh",
    "Russian" to "ru",
    "Turkish" to "tr",
    "Thai" to "th",
    "Vietnamese" to "vi",
    "Indonesian" to "id",
    "Malay" to "ms",
    "Tagalog" to "tl",
    "Hindi" to "hi",
    "Bengali" to "bn",
    "Urdu" to "ur",
    "Persian" to "fa",
    "Hebrew" to "he",
    "Dutch" to "nl",
    "Swedish" to "sv",
    "Norwegian" to "no",
    "Danish" to "da",
    "Finnish" to "fi",
    "Portuguese" to "pt",
    "Bulgarian" to "bg",
)

private val YEAR_VALUES: Array<String> = run {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (0..24).map { (currentYear - it).toString() }
    arrayOf("") + years
}

private val YEAR_LABELS: Array<String> = run {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (0..24).map { (currentYear - it).toString() }
    arrayOf("Any") + years
}
