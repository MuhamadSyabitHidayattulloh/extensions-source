package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

class TypeFilter : Filter.Select<String>("Tipe", types.map { it.first }.toTypedArray()) {
    val selected get() = types[state].second

    companion object {
        private val types = arrayOf(
            "Semua" to "",
            "Doujinshi" to "doujinshi",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
        )
    }
}

class StatusFilter : Filter.Select<String>("Status", statuses.map { it.first }.toTypedArray()) {
    val selected get() = statuses[state].second

    companion object {
        private val statuses = arrayOf(
            "Semua" to "",
            "Berlanjut" to "ongoing",
            "Selesai" to "completed",
        )
    }
}

class SortFilter : Filter.Select<String>("Urutkan", sorts.map { it.first }.toTypedArray()) {
    val selected get() = sorts[state].second

    companion object {
        private val sorts = arrayOf(
            "Terbaru" to "newest",
            "Terlama" to "oldest",
            "Update Terbaru" to "latest_chapter",
            "Populer" to "popular",
            "Rating" to "rating",
            "Views" to "views",
            "A-Z" to "alphabetical",
        )
    }
}

class AuthorFilter : Filter.Text("Author")
class GroupFilter : Filter.Text("Group")
class SeriesFilter : Filter.Text("Series")
class CharacterFilter : Filter.Text("Karakter")

val genreList = listOf(
    Genre("Age Progression", "age-progression"),
    Genre("Age Regression", "age-regression"),
    Genre("Ahegao", "ahegao"),
    Genre("All The Way Through", "all-the-way-through"),
    Genre("Amputee", "amputee"),
    Genre("Anal", "anal"),
    Genre("Anorexia", "anorexia"),
    Genre("Apron", "apron"),
    Genre("Artist CG", "artist-cg"),
    Genre("Aunt", "aunt"),
    Genre("Bald", "bald"),
    Genre("Bestiality", "bestiality"),
    Genre("Big Ass", "big-ass"),
    Genre("Big Breast", "big-breast"),
    Genre("Big Penis", "big-penis"),
    Genre("Bike Shorts", "bike-shorts"),
    Genre("Bikini", "bikini"),
    Genre("Birth", "birth"),
    Genre("Bisexual", "bisexual"),
    Genre("Blackmail", "blackmail"),
    Genre("Blindfold", "blindfold"),
    Genre("Bloomers", "bloomers"),
    Genre("Blowjob", "blowjob"),
    Genre("Body Swap", "body-swap"),
    Genre("Bodysuit", "bodysuit"),
    Genre("Bondage", "bondage"),
    Genre("Bowjob", "bowjob"),
    Genre("Business Suit", "business-suit"),
    Genre("Cheating", "cheating"),
    Genre("Collar", "collar"),
    Genre("Condom", "condom"),
    Genre("Cousin", "cousin"),
    Genre("Crossdressing", "crossdressing"),
    Genre("Cunnilingus", "cunnilingus"),
    Genre("Dark Skin", "dark-skin"),
    Genre("Daughter", "daughter"),
    Genre("Defloration", "defloration"),
    Genre("Demon", "demon"),
    Genre("Demon Girl", "demon-girl"),
    Genre("Dick Growth", "dick-growth"),
    Genre("DILF", "dilf"),
    Genre("Double Penetration", "double-penetration"),
    Genre("Drugs", "drugs"),
    Genre("Drunk", "drunk"),
    Genre("Elf", "elf"),
    Genre("Emotionless Sex", "emotionless-sex"),
    Genre("Exhibitionism", "exhibitionism"),
    Genre("Eyepatch", "eyepatch"),
    Genre("Females Only", "females-only"),
    Genre("Femdom", "femdom"),
    Genre("Filming", "filming"),
    Genre("Fingering", "fingering"),
    Genre("Footjob", "footjob"),
    Genre("Full Color", "full-color"),
    Genre("Furry", "furry"),
    Genre("Futanari", "futanari"),
    Genre("Garter Belt", "garter-belt"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Ghost", "ghost"),
    Genre("Glasses", "glasses"),
    Genre("Group", "group"),
    Genre("Guro", "guro"),
    Genre("Gyaru", "gyaru"),
    Genre("Hairy", "hairy"),
    Genre("Handjob", "handjob"),
    Genre("Harem", "harem"),
    Genre("Horns", "horns"),
    Genre("Huge Breast", "huge-breast"),
    Genre("Huge Penis", "huge-penis"),
    Genre("Humiliation", "humiliation"),
    Genre("Impregnation", "impregnation"),
    Genre("Incest", "incest"),
    Genre("Inflation", "inflation"),
    Genre("Insect", "insect"),
    Genre("Inseki", "inseki"),
    Genre("Inverted Nipples", "inverted-nipples"),
    Genre("Invisible", "invisible"),
    Genre("Kemomi", "kemomi"),
    Genre("Kemomimi", "kemomimi"),
    Genre("Kimono", "kimono"),
    Genre("Lactation", "lactation"),
    Genre("Leotard", "leotard"),
    Genre("Lingerie", "lingerie"),
    Genre("Loli", "loli"),
    Genre("Lolipai", "lolipai"),
    Genre("MILF", "milf"),
    Genre("Maid", "maid"),
    Genre("Males Only", "males-only"),
    Genre("Masturbation", "masturbation"),
    Genre("Miko", "miko"),
    Genre("Mind Break", "mind-break"),
    Genre("Mind Control", "mind-control"),
    Genre("Minigirl", "minigirl"),
    Genre("Miniguy", "miniguy"),
    Genre("Monster", "monster"),
    Genre("Monster Girl", "monster-girl"),
    Genre("Mother", "mother"),
    Genre("Multi-work Series", "multi-work-series"),
    Genre("Muscle", "muscle"),
    Genre("Nakadashi", "nakadashi"),
    Genre("Necrophilia", "necrophilia"),
    Genre("Netorare", "netorare"),
    Genre("Niece", "niece"),
    Genre("Nipple Fuck", "nipple-fuck"),
    Genre("Nurse", "nurse"),
    Genre("Old Man", "old-man"),
    Genre("Oyakodon", "oyakodon"),
    Genre("Paizuri", "paizuri"),
    Genre("Pantyhose", "pantyhose"),
    Genre("Possession", "possession"),
    Genre("Pregnant", "pregnant"),
    Genre("Prostitution", "prostitution"),
    Genre("Rape", "rape"),
    Genre("Rimjob", "rimjob"),
    Genre("Scat", "scat"),
    Genre("School Uniform", "school-uniform"),
    Genre("Sex Toys", "sex-toys"),
    Genre("Shemale", "shemale"),
    Genre("Shota", "shota"),
    Genre("Sister", "sister"),
    Genre("Sleeping", "sleeping"),
    Genre("Slime", "slime"),
    Genre("Small Breast", "small-breast"),
    Genre("Snuff", "snuff"),
    Genre("Sole Female", "sole-female"),
    Genre("Sole Male", "sole-male"),
    Genre("Stocking", "stocking"),
    Genre("Story Arc", "story-arc"),
    Genre("Sumata", "sumata"),
    Genre("Sweating", "sweating"),
    Genre("Swimsuit", "swimsuit"),
    Genre("Tanlines", "tanlines"),
    Genre("Teacher", "teacher"),
    Genre("Tentacles", "tentacles"),
    Genre("Tomboy", "tomboy"),
    Genre("Tomgirl", "tomgirl"),
    Genre("Torture", "torture"),
    Genre("Twins", "twins"),
    Genre("Twintails", "twintails"),
    Genre("Uncensored", "uncensored"),
    Genre("Unusual Pupils", "unusual-pupils"),
    Genre("Virginity", "virginity"),
    Genre("Webtoon", "webtoon"),
    Genre("Widow", "widow"),
    Genre("X-Ray", "x-ray"),
    Genre("Yandere", "yandere"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)
