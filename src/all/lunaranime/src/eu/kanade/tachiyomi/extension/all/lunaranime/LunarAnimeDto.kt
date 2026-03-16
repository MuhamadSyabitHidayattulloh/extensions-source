package eu.kanade.tachiyomi.extension.all.lunaranime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class SearchPayload(
    val query: String,
    val page: Int,
    val filters: SearchFilters = SearchFilters(),
)

@Serializable
class SearchFilters(
    val genres: List<String>? = null,
    val year: Int? = null,
    val sort: String? = null,
    val status: String? = null,
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val author: String? = null,
    val artist: String? = null,
    @SerialName("translated_languages") val translatedLanguages: List<String>? = null,
    @SerialName("scan_group") val scanGroup: String? = null,
    val isAdult: Boolean = false,
)

@Serializable
class AniSearchResponse(
    val data: AniSearchData? = null,
    val fallback: Boolean? = null,
    val query: String? = null,
    val variables: JsonObject? = null,
)

@Serializable
class AniListRequest(
    val query: String,
    val variables: JsonObject,
)

@Serializable
class AniSearchData(@SerialName("Page") val page: AniPage)

@Serializable
class AniPage(
    val pageInfo: PageInfo,
    val media: List<AniMedia>,
)

@Serializable
class PageInfo(
    val hasNextPage: Boolean,
)

@Serializable
class AniMedia(
    val id: Int,
    val title: AniTitle,
    val coverImage: AniCoverImage,
)

@Serializable
class AniInfoResponse(val data: AniInfo)

@Serializable
class AniInfo(
    val title: AniTitle,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val coverImage: AniCoverImage,
    val author: String? = null,
    val artist: String? = null,
)

@Serializable
class AniTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
class AniCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
class VermillionChapterRequest(val id: String, val refresh: Boolean)

@Serializable
class VermillionSource(
    val providerId: String,
    val chapters: List<VermillionChapter> = emptyList(),
)

@Serializable
class VermillionChapter(
    val title: String? = null,
    val number: Float,
)

@Serializable
class VermillionImagesRequest(val id: String, val chapter: String)

@Serializable
class VermillionImagesResponse(val images: List<VermillionImage> = emptyList())

@Serializable
class VermillionImage(val index: Int, val url: String)
