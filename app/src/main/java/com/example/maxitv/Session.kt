package com.example.maxitv

import android.content.Context

/** Connexion en cours : garde catégories et dernières listes en mémoire (peu : la mémoire d'une box est limitée). */
object Session {
    private var api: XtreamApi? = null
    private val cats = HashMap<Kind, List<Cat>>()
    private val lists = LinkedHashMap<String, List<Item>>()
    private val everything = LinkedHashMap<Kind, List<Item>>()
    private val infos = LinkedHashMap<String, Info>()
    private var fullKey = ""
    private var fullList: List<Item> = emptyList()
    private const val ROW_MAX = 40
    private const val CACHE_MAX = 8

    val ready: Boolean get() = api != null

    fun begin(ctx: Context): Boolean {
        val p = Profiles.active(ctx) ?: return false
        return try {
            api = XtreamApi(p.host, p.user, p.pass)
            cats.clear()
            lists.clear()
            everything.clear()
            infos.clear()
            fullKey = ""
            fullList = emptyList()
            true
        } catch (e: ApiError) {
            api = null
            false
        }
    }

    fun end() {
        api = null
        cats.clear()
        lists.clear()
        everything.clear()
        infos.clear()
        fullKey = ""
        fullList = emptyList()
    }

    private fun api(): XtreamApi = api ?: throw ApiError("Non connecté.")

    suspend fun categories(kind: Kind): List<Cat> {
        cats[kind]?.let { return it }
        val l = api().categories(kind)
        cats[kind] = l
        return l
    }

    /** Début d'une catégorie (pour les étagères). */
    suspend fun items(kind: Kind, cat: Cat): List<Item> {
        val key = kind.name + ":" + cat.id
        lists[key]?.let { return it }
        val l = api().items(kind, cat.id).take(ROW_MAX)
        lists[key] = l
        while (lists.size > CACHE_MAX) {
            val it = lists.keys.iterator()
            it.next()
            it.remove()
        }
        return l
    }

    /** Toute une catégorie (page « voir tout »). */
    suspend fun itemsFull(kind: Kind, cat: Cat): List<Item> {
        val key = kind.name + ":" + cat.id
        if (key == fullKey) return fullList
        val l = api().items(kind, cat.id)
        fullKey = key
        fullList = l
        return l
    }

    /** Tout le catalogue d'un type (pour la recherche). */
    suspend fun all(kind: Kind): List<Item> {
        everything[kind]?.let { return it }
        val l = api().items(kind, null)
        everything[kind] = l
        while (everything.size > 3) {
            val it = everything.keys.iterator()
            it.next()
            it.remove()
        }
        return l
    }

    suspend fun info(item: Item): Info {
        val key = item.kind.name + ":" + item.id
        infos[key]?.let { return it }
        val i = api().info(item)
        infos[key] = i
        while (infos.size > 40) {
            val it = infos.keys.iterator()
            it.next()
            it.remove()
        }
        return i
    }

    private fun candidates(first: String, vararg others: String): List<String> =
        (listOf(first) + others.toList()).filter { it.isNotEmpty() }.distinct()

    fun movieRequest(item: Item, info: Info?, startMs: Long): PlayRequest {
        val a = api()
        val first = (info?.ext ?: "").ifEmpty { item.ext }.ifEmpty { "mp4" }
        val urls = candidates(first, "m3u8", "mp4", "mkv").map { a.streamUrl("movie", item.id, it) }
        return PlayRequest(item.name, urls, false, "m:" + item.id, info?.tmdb ?: "", 0, 0, startMs)
    }

    fun episodeRequest(series: Item, season: Int, list: List<Episode>, index: Int, tmdb: String, startMs: Long): PlayRequest {
        val a = api()
        val e = list[index]
        val urls = candidates(e.ext, "m3u8", "mp4", "mkv").map { a.streamUrl("series", e.id, it) }
        val title = series.name + " — S" + season + " E" + e.num
        val next: (() -> PlayRequest?)? =
            if (index + 1 < list.size) ({ episodeRequest(series, season, list, index + 1, tmdb, 0L) }) else null
        return PlayRequest(title, urls, false, "e:" + e.id, tmdb, season, e.num, startMs, emptyList(), 0, next)
    }

    fun liveRequest(item: Item, list: List<Item>): PlayRequest {
        val a = api()
        val urls = listOf("m3u8", "ts").map { a.streamUrl("live", item.id, it) }
        val idx = list.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        return PlayRequest(item.name, urls, true, "", "", 0, 0, 0L, list, idx)
    }
}
