package com.example.maxitv

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ApiError(message: String) : Exception(message)

private fun JSONObject.s(key: String): String = if (isNull(key)) "" else optString(key, "").trim()

class XtreamApi(rawHost: String, private val user: String, private val pass: String) {
    private val base: HttpUrl

    init {
        var s = rawHost.trim()
        if (!s.lowercase().startsWith("http")) s = "http://$s"
        s = s.trimEnd('/')
        base = s.toHttpUrlOrNull() ?: throw ApiError("Adresse du serveur invalide.")
        Net.trust(base.host)
    }

    private fun url(action: String?, vararg extra: Pair<String, String>): HttpUrl {
        val b = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", user)
            .addQueryParameter("password", pass)
        if (action != null) b.addQueryParameter("action", action)
        for ((k, v) in extra) b.addQueryParameter(k, v)
        return b.build()
    }

    /** Adresse d'un flux (film, épisode ou chaîne). Gardée en mémoire seulement, jamais écrite ni affichée. */
    fun streamUrl(type: String, id: String, ext: String): String =
        base.newBuilder()
            .addPathSegment(type)
            .addPathSegment(user)
            .addPathSegment(pass)
            .addPathSegment("$id.$ext")
            .build()
            .toString()

    /**
     * Toutes les requêtes passent par une file : 2 à la fois au maximum, une courte pause entre chaque et
     * jusqu'à 3 tentatives. Les serveurs IPTV refusent (erreur HTTP) quand l'appli les inonde de requêtes.
     * Les messages d'erreur ne contiennent jamais d'adresse ni d'identifiant.
     */
    private suspend fun <T> call(block: () -> T): T = gate.withPermit {
        var last: Exception = ApiError("Connexion impossible.")
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(attempt * 900L)
            try {
                val res = withContext(Dispatchers.IO) { block() }
                delay(120)
                return@withPermit res
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpErr) {
                last = ApiError("Le serveur a répondu avec une erreur (HTTP ${e.code}).")
                if (e.code == 401 || e.code == 404) break
            } catch (e: IOException) {
                last = ApiError("Connexion impossible.")
            } catch (e: Exception) {
                last = ApiError("Réponse du serveur invalide.")
            }
        }
        throw last
    }

    private suspend fun get(u: HttpUrl): String = call { Net.fetchString(u) }

    /** Vérifie les identifiants. */
    suspend fun login() {
        val body = get(url(null))
        val j = try { JSONObject(body) } catch (e: Exception) { throw ApiError("Réponse du serveur invalide.") }
        val info = j.optJSONObject("user_info") ?: throw ApiError("Identifiants refusés par le serveur.")
        val auth = info.opt("auth")?.toString() ?: "0"
        if (auth != "1" && auth != "true") throw ApiError("Identifiants refusés par le serveur.")
    }

    suspend fun categories(kind: Kind): List<Cat> {
        val arr = try { JSONArray(get(url(kind.categoriesAction))) } catch (e: CancellationException) { throw e } catch (e: ApiError) { throw e } catch (e: Exception) { return emptyList() }
        val out = ArrayList<Cat>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.s("category_id")
            val name = o.s("category_name")
            if (id.isNotEmpty() && name.isNotEmpty()) out.add(Cat(id, name))
        }
        return out
    }

    /** Titres d'une catégorie (ou de tout le catalogue si catId est null). Lus au fil de l'eau pour ménager la mémoire. */
    suspend fun items(kind: Kind, catId: String?): List<Item> {
        val u = if (catId == null) url(kind.itemsAction) else url(kind.itemsAction, "category_id" to catId)
        return call {
            Net.open(u).use { r ->
                val body = r.body ?: throw IOException("vide")
                JsonReader(body.charStream().buffered()).use { readItems(kind, it) }
            }
        }
    }

    private fun str(jr: JsonReader): String = when (jr.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> jr.nextString().trim()
        JsonToken.BOOLEAN -> jr.nextBoolean().toString()
        JsonToken.NULL -> {
            jr.nextNull()
            ""
        }
        else -> {
            jr.skipValue()
            ""
        }
    }

    private fun readItems(kind: Kind, jr: JsonReader): List<Item> {
        val out = ArrayList<Item>()
        if (jr.peek() != JsonToken.BEGIN_ARRAY) return out
        jr.beginArray()
        while (jr.hasNext()) {
            if (jr.peek() != JsonToken.BEGIN_OBJECT) {
                jr.skipValue()
                continue
            }
            var id = ""
            var name = ""
            var icon = ""
            var cover = ""
            var rating = ""
            var added = ""
            var ext = ""
            jr.beginObject()
            while (jr.hasNext()) {
                when (jr.nextName()) {
                    "stream_id" -> if (kind != Kind.SERIES) id = str(jr) else jr.skipValue()
                    "series_id" -> if (kind == Kind.SERIES) id = str(jr) else jr.skipValue()
                    "name" -> name = str(jr)
                    "stream_icon" -> icon = str(jr)
                    "cover" -> cover = str(jr)
                    "rating" -> rating = str(jr)
                    "added" -> if (kind != Kind.SERIES) added = str(jr) else jr.skipValue()
                    "last_modified" -> if (kind == Kind.SERIES) added = str(jr) else jr.skipValue()
                    "container_extension" -> ext = str(jr)
                    else -> jr.skipValue()
                }
            }
            jr.endObject()
            if (id.isEmpty()) continue
            val rawPoster = if (kind == Kind.SERIES) cover.ifEmpty { icon } else icon
            val poster = if (rawPoster.startsWith("http://") || rawPoster.startsWith("https://")) rawPoster else ""
            val r = if ((rating.toFloatOrNull() ?: 0f) > 0f) rating else ""
            out.add(Item(id, kind, name, poster, r, added.toLongOrNull() ?: 0L, ext))
        }
        jr.endArray()
        return out
    }

    private fun firstString(v: Any?): String = when (v) {
        is JSONArray -> if (v.length() > 0) v.optString(0, "") else ""
        is String -> v
        else -> ""
    }

    /** Fiche détaillée d'un film ou d'une série (synopsis, image de fond, épisodes…). */
    suspend fun info(item: Item): Info {
        val u = if (item.kind == Kind.SERIES) url("get_series_info", "series_id" to item.id) else url("get_vod_info", "vod_id" to item.id)
        val j = try { JSONObject(get(u)) } catch (e: CancellationException) { throw e } catch (e: ApiError) { throw e } catch (e: Exception) { throw ApiError("Réponse du serveur invalide.") }
        val i = j.optJSONObject("info") ?: JSONObject()
        val backdrop = firstString(i.opt("backdrop_path")).let { if (it.startsWith("http")) it else "" }
        val year = (i.s("releasedate").ifEmpty { i.s("releaseDate").ifEmpty { i.s("release_date") } }).take(4)
        val rating = i.s("rating").let { r -> if ((r.toFloatOrNull() ?: 0f) > 0f) r else "" }
        val tmdb = i.s("tmdb_id").ifEmpty { i.s("tmdb") }
        val trailer = i.s("youtube_trailer")
        val plot = i.s("plot").ifEmpty { i.s("description") }
        val seasons = LinkedHashMap<Int, List<Episode>>()
        var ext = ""
        if (item.kind == Kind.SERIES) {
            val eps = j.opt("episodes")
            val byKey = ArrayList<Pair<Int, JSONArray>>()
            if (eps is JSONObject) {
                val keys = eps.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val a = eps.optJSONArray(k) ?: continue
                    byKey.add((k.toIntOrNull() ?: 0) to a)
                }
            } else if (eps is JSONArray) {
                for (n in 0 until eps.length()) {
                    val a = eps.optJSONArray(n) ?: continue
                    byKey.add((n + 1) to a)
                }
            }
            byKey.sortBy { it.first }
            for ((season, arr) in byKey) {
                val list = ArrayList<Episode>()
                for (n in 0 until arr.length()) {
                    val e = arr.optJSONObject(n) ?: continue
                    val id = e.s("id")
                    if (id.isEmpty()) continue
                    val ei = e.optJSONObject("info") ?: JSONObject()
                    val num = e.s("episode_num").toIntOrNull() ?: (n + 1)
                    val img = ei.s("movie_image").let { if (it.startsWith("http")) it else "" }
                    list.add(Episode(id, num, e.s("title").ifEmpty { "Épisode $num" }, e.s("container_extension").ifEmpty { "mp4" }, img, ei.s("duration")))
                }
                if (list.isNotEmpty()) seasons[season] = list
            }
        } else {
            ext = (j.optJSONObject("movie_data") ?: JSONObject()).s("container_extension")
        }
        return Info(plot, backdrop, year, rating, i.s("genre"), i.s("duration"), ext, tmdb, trailer, seasons)
    }

    companion object {
        private val gate = Semaphore(2)
    }
}
