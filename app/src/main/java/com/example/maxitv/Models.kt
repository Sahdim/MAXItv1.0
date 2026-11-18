package com.example.maxitv

enum class Kind(val title: String, val categoriesAction: String, val itemsAction: String) {
    LIVE("Live", "get_live_categories", "get_live_streams"),
    MOVIES("Films", "get_vod_categories", "get_vod_streams"),
    SERIES("Séries", "get_series_categories", "get_series")
}

data class Cat(val id: String, val name: String)

data class Item(
    val id: String,
    val kind: Kind,
    val name: String,
    val poster: String,
    val rating: String,
    val added: Long,
    val ext: String
)

data class Episode(val id: String, val num: Int, val title: String, val ext: String, val image: String, val duration: String)

/** Infos détaillées d'un film ou d'une série. */
data class Info(
    val plot: String,
    val backdrop: String,
    val year: String,
    val rating: String,
    val genre: String,
    val duration: String,
    val ext: String,
    val tmdb: String,
    val trailer: String,
    val seasons: Map<Int, List<Episode>>
)

/** Tout ce qu'il faut au lecteur pour démarrer une lecture. */
class PlayRequest(
    val title: String,
    val urls: List<String>,
    val live: Boolean,
    val key: String,
    val tmdb: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val startMs: Long = 0L,
    val channels: List<Item> = emptyList(),
    val index: Int = 0,
    val next: (() -> PlayRequest?)? = null
)
