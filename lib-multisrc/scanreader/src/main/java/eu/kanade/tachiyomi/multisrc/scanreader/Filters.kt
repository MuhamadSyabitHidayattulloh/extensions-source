package eu.kanade.tachiyomi.multisrc.scanreader

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(name: String, val genres: Array<Pair<String, String>>) : Filter.Select<String>(name, genres.map { it.first }.toTypedArray())

class StatusFilter(name: String, val statuses: Array<Pair<String, String>>) : Filter.Select<String>(name, statuses.map { it.first }.toTypedArray())

class SortFilter(name: String, val sorts: Array<Pair<String, String>>) : Filter.Select<String>(name, sorts.map { it.first }.toTypedArray())

fun getStatusList() = arrayOf(
    "Tous les statuts" to "",
    "En cours" to "En cours",
    "Terminé" to "Terminé",
    "Hiatus" to "Hiatus",
    "Licencié" to "Licencié",
)

fun getSortList() = arrayOf(
    "Plus récent" to "date",
    "Plus populaire" to "views",
    "Titre A-Z" to "title",
)
