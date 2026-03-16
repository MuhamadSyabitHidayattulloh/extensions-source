package eu.kanade.tachiyomi.extension.all.lunaranime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val manga: List<MangaDto> = emptyList(),
    val page: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
)

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: String? = null,
    @SerialName("publication_status") val status: String? = null,
)

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter_number") val number: Float,
    @SerialName("chapter_title") val title: String? = null,
    val language: String,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
)

@Serializable
class PageListResponse(
    val data: PageListData,
)

@Serializable
class PageListData(
    val images: List<String> = emptyList(),
)
