package eu.kanade.tachiyomi.extension.id.komikucc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListDataDto(
    val data: List<MangaDto>,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("current_page") val currentPage: Int,
)

@Serializable
class MangaListResponseDto(
    val manga: MangaListDataDto,
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
class GenreDto(
    val title: String,
)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val title: String,
    val link: String,
    @SerialName("created_at") val createdAt: String? = null,
    val images: List<String>? = null,
)

@Serializable
class ChapterDataDto(
    val chapter: ChapterDto,
)

@Serializable
class ChapterResponseDto(
    val data: ChapterDataDto,
)
