package com.example.maxitv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import java.nio.charset.Charset

class Cue(val start: Long, val end: Long, val text: String)

class OnlineSub(val label: String, val url: String)

/**
 * Sous-titres en ligne (wyzie.io) : trouvés grâce à l'identifiant TMDB du film. Il faut une clé gratuite,
 * saisie dans Profil et gardée dans le coffre chiffré. Seules des adresses https de wyzie.io sont acceptées.
 */
object Subs {
    private val tm = Regex("(?:(\\d+):)?(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(?:(\\d+):)?(\\d+):(\\d+)[,.](\\d+)")
    private val tags = Regex("<[^>]+>|\\{[^}]*\\}")

    private fun ms(h: String, m: String, s: String, f: String): Long {
        val frac = f.padEnd(3, '0').take(3).toLong()
        return ((h.toLongOrNull() ?: 0L) * 3600L + m.toLong() * 60L + s.toLong()) * 1000L + frac
    }

    fun parse(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        val blocks = text.replace("﻿", "").replace("\r", "").split(Regex("\n\n+"))
        for (b in blocks) {
            val lines = b.split("\n")
            val i = lines.indexOfFirst { tm.containsMatchIn(it) }
            if (i < 0) continue
            val g = tm.find(lines[i])?.groupValues ?: continue
            val body = lines.drop(i + 1).joinToString("\n").replace(tags, "").trim()
            if (body.isEmpty()) continue
            out.add(Cue(ms(g[1], g[2], g[3], g[4]), ms(g[5], g[6], g[7], g[8]), body))
        }
        return out
    }

    private fun allowed(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        return u.isHttps && (u.host == "wyzie.io" || u.host.endsWith(".wyzie.io"))
    }

    suspend fun find(key: String, tmdb: String, season: Int, episode: Int): List<OnlineSub> = withContext(Dispatchers.IO) {
        val b = "https://sub.wyzie.io/search".toHttpUrl().newBuilder()
            .addQueryParameter("id", tmdb)
            .addQueryParameter("language", "fr,en")
            .addQueryParameter("format", "srt")
            .addQueryParameter("key", key)
        if (season > 0 && episode > 0) {
            b.addQueryParameter("season", season.toString())
            b.addQueryParameter("episode", episode.toString())
        }
        val arr = JSONArray(Net.fetchString(b.build()))
        class Cand(val lang: String, val url: String, val hi: Boolean, val dl: Long)
        val cands = ArrayList<Cand>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("url", "")
            if (!allowed(u)) continue
            cands.add(Cand(o.optString("language", "").take(2).lowercase(), u, o.optBoolean("isHearingImpaired", false), o.optLong("downloadCount", 0L)))
        }
        cands.sortWith(compareBy<Cand> { it.hi }.thenByDescending { it.dl })
        val out = ArrayList<OnlineSub>()
        for ((code, name) in listOf("fr" to "Français", "en" to "Anglais")) {
            cands.filter { it.lang == code }.take(3).forEachIndexed { n, c ->
                out.add(OnlineSub(name + (if (n > 0) " " + (n + 1) else "") + " (en ligne)", c.url))
            }
        }
        out
    }

    suspend fun download(url: String): List<Cue> = withContext(Dispatchers.IO) {
        if (!allowed(url)) return@withContext emptyList<Cue>()
        val bytes = Net.fetchBytes(url.toHttpUrl())
        var t = String(bytes, Charsets.UTF_8)
        if (t.contains('�')) t = String(bytes, Charset.forName("windows-1252"))
        parse(t)
    }
}
