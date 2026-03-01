package eu.kanade.tachiyomi.extension.id.roseveil

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val data: List<MangaItemDto>,
    val page: Int,
    val total_pages: Int,
)

@Serializable
data class MangaItemDto(
    val title: String,
    val slug: String,
    val poster_image_url: String? = null,
)

@Serializable
data class MangaDetailDto(
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    val poster_image_url: String? = null,
    val author_name: String? = null,
    val artist_name: String? = null,
    val comic_status: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val units: List<ChapterUnitDto> = emptyList(),
)

@Serializable
data class GenreDto(
    val name: String,
)

@Serializable
data class ChapterUnitDto(
    val title: String,
    val slug: String,
    val number: String,
    val created_at: String? = null,
)

@Serializable
data class PageListDto(
    val chapter: ChapterDetailDto,
)

@Serializable
data class ChapterDetailDto(
    val pages: List<PageDto>,
)

@Serializable
data class PageDto(
    val page_number: Int,
    val image_url: String,
)
