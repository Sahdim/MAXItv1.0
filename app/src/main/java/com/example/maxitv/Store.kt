package com.example.maxitv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Réglages et listes personnelles (thème, favoris, récents, position de lecture).
 * Rien de secret ici : les identifiants restent dans le coffre chiffré (Secure).
 */
object Store {
    private fun p(ctx: Context) = ctx.getSharedPreferences("maxitv_prefs", Context.MODE_PRIVATE)

    private fun pid(ctx: Context): String = Profiles.active(ctx)?.id ?: "none"

    fun themeIndex(ctx: Context): Int = p(ctx).getInt("theme", 0)

    fun setTheme(ctx: Context, i: Int) {
        p(ctx).edit().putInt("theme", i).apply()
    }

    private fun read(ctx: Context, key: String): MutableList<Item> {
        val out = ArrayList<Item>()
        val raw = p(ctx).getString(key, null) ?: return out
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val kind = try { Kind.valueOf(o.getString("k")) } catch (e: Exception) { continue }
                out.add(Item(o.getString("id"), kind, o.optString("n"), o.optString("p"), o.optString("r"), o.optLong("a"), o.optString("e")))
            }
        } catch (e: Exception) {
            return ArrayList()
        }
        return out
    }

    private fun write(ctx: Context, key: String, list: List<Item>) {
        val a = JSONArray()
        for (it in list) {
            a.put(JSONObject().put("id", it.id).put("k", it.kind.name).put("n", it.name).put("p", it.poster).put("r", it.rating).put("a", it.added).put("e", it.ext))
        }
        p(ctx).edit().putString(key, a.toString()).apply()
    }

    fun favs(ctx: Context): List<Item> = read(ctx, "fav:" + pid(ctx))

    fun isFav(ctx: Context, item: Item): Boolean = favs(ctx).any { it.id == item.id && it.kind == item.kind }

    /** Ajoute ou retire des favoris. Renvoie true si le titre est maintenant favori. */
    fun toggleFav(ctx: Context, item: Item): Boolean {
        val l = read(ctx, "fav:" + pid(ctx))
        val had = l.removeAll { it.id == item.id && it.kind == item.kind }
        if (!had) l.add(0, item)
        write(ctx, "fav:" + pid(ctx), l)
        return !had
    }

    fun recents(ctx: Context): List<Item> = read(ctx, "rec:" + pid(ctx))

    fun addRecent(ctx: Context, item: Item) {
        val l = read(ctx, "rec:" + pid(ctx))
        l.removeAll { it.id == item.id && it.kind == item.kind }
        l.add(0, item)
        while (l.size > 24) l.removeAt(l.size - 1)
        write(ctx, "rec:" + pid(ctx), l)
    }

    fun pos(ctx: Context, key: String): Long = if (key.isEmpty()) 0L else p(ctx).getLong("pos:" + pid(ctx) + ":" + key, 0L)

    fun setPos(ctx: Context, key: String, ms: Long) {
        if (key.isEmpty()) return
        val e = p(ctx).edit()
        if (ms <= 0L) e.remove("pos:" + pid(ctx) + ":" + key) else e.putLong("pos:" + pid(ctx) + ":" + key, ms)
        e.apply()
    }
}
