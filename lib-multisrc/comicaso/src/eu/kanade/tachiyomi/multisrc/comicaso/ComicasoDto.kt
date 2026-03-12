package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    @Serializable(with = StringOrBooleanSerializer::class)
    val thumbnail: String = "",
    val status: String? = null,
    val type: String? = null,
    val updated_at: Long? = null,
    val manga_date: Long? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/komik/$slug/"
        title = this@MangaDto.title
        thumbnail_url = thumbnail
        description = synopsis
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        status = when (this@MangaDto.status) {
            "on-going" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString()
    }
}

object StringOrBooleanSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrBoolean", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String = if (decoder is JsonDecoder) {
        val element = decoder.decodeJsonElement()
        if (element is JsonPrimitive) {
            element.content.takeUnless { element.booleanOrNull != null } ?: ""
        } else {
            ""
        }
    } else {
        decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
class ChapterDto(
    val slug: String,
    val title: String,
    val date: Long? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/komik/$mangaSlug/$slug/"
        name = title
        date_upload = date?.let { it * 1000 } ?: 0L
    }
}
