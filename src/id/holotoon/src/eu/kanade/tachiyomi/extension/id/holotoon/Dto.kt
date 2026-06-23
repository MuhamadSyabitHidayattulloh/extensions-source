package eu.kanade.tachiyomi.extension.id.holotoon

import kotlinx.serialization.Serializable

@Serializable
class PageListDto(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val url: String,
)
