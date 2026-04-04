package eu.kanade.tachiyomi.extension.id.komikucc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListResponseDto(
    val manga: MangaListDataDto,
)

@Serializable
class MangaListDataDto(
    val data: List<MangaDto>,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("current_page") val currentPage: Int,
)

@Serializable
class MangaDetailsResponseDto(
    val manga: MangaDto? = null,
    val chapters: List<ChapterDto>? = null,
)

@Serializable
class ChapterResponseDto(
    val data: ChapterDataDto,
)

@Serializable
class ChapterDataDto(
    val chapter: ChapterDto,
)

@Serializable
class MangaDto(
    val title: String,
    val link: String,
    val img: String? = null,
    val author: String? = null,
    val status: String? = null,
    val des: String? = null,
    val genre: List<GenreDto>? = null,
)

@Serializable
class ChapterDto(
    val title: String,
    val link: String,
    val images: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class GenreDto(
    val title: String,
)
