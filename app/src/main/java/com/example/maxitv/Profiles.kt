package com.example.maxitv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Profile(val id: String, val name: String, val host: String, val user: String, val pass: String)

/** Liste des profils (un par code Xtream), stockée dans le coffre chiffré. */
object Profiles {
    private fun read(ctx: Context): MutableList<Profile> {
        val raw = Secure.get(ctx, "profiles")
        val out = mutableListOf<Profile>()
        if (raw.isEmpty()) return out
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Profile(o.getString("id"), o.getString("name"), o.getString("host"), o.getString("user"), o.getString("pass")))
            }
        } catch (e: Exception) {
            return mutableListOf()
        }
        return out
    }

    private fun write(ctx: Context, list: List<Profile>) {
        val a = JSONArray()
        for (p in list) {
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("host", p.host).put("user", p.user).put("pass", p.pass))
        }
        Secure.set(ctx, "profiles", if (list.isEmpty()) "" else a.toString())
    }

    fun all(ctx: Context): List<Profile> = read(ctx)

    fun active(ctx: Context): Profile? {
        val id = Secure.get(ctx, "active")
        val list = read(ctx)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    /** Ajoute un profil (ou remplace celui qui a le même serveur et le même identifiant) et le rend actif. */
    fun add(ctx: Context, host: String, user: String, pass: String): Profile {
        val list = read(ctx)
        list.removeAll { it.host.equals(host, ignoreCase = true) && it.user == user }
        val p = Profile(UUID.randomUUID().toString(), user, host, user, pass)
        list.add(p)
        write(ctx, list)
        Secure.set(ctx, "active", p.id)
        return p
    }

    fun setActive(ctx: Context, id: String) {
        if (read(ctx).any { it.id == id }) Secure.set(ctx, "active", id)
    }

    fun remove(ctx: Context, id: String) {
        val list = read(ctx)
        list.removeAll { it.id == id }
        write(ctx, list)
        val still = Secure.get(ctx, "active")
        if (still == id || list.none { it.id == still }) Secure.set(ctx, "active", list.firstOrNull()?.id ?: "")
    }
}
