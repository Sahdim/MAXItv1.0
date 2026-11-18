package com.example.maxitv

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Onglet Profil : profils (un par code Xtream), thème de couleurs, clé des sous-titres en ligne, déconnexion. */
class ProfileView(private val act: MainActivity, private val home: HomeScreen) : ScrollView(act) {
    private val col = LinearLayout(act)

    init {
        clipChildren = false
        isFillViewport = true
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(act.dp(32), act.dp(86), act.dp(32), act.dp(60))
        addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        draw()
    }

    private fun heading(t: String, topGap: Int) {
        val l = label(act, t, 18f, Color.WHITE, true)
        col.addView(l, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(topGap); it.bottomMargin = act.dp(10) })
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        r.clipChildren = false
        col.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return r
    }

    private fun gap(r: LinearLayout, v: TextView) {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.rightMargin = act.dp(10)
        r.addView(v, p)
    }

    private fun draw() {
        col.removeAllViews()
        col.addView(label(act, "Profil", 30f, Color.WHITE, true))

        heading("Profils", 22)
        val active = Profiles.active(act)?.id
        val pr = row()
        for (p in Profiles.all(act)) {
            val b = pill(act, (if (p.id == active) "● " else "") + p.name, p.id == active)
            b.setOnClickListener {
                Profiles.setActive(act, p.id)
                act.showHome()
            }
            gap(pr, b)
        }
        val ar = row()
        val add = pill(act, "Ajouter un profil")
        add.setOnClickListener { act.showLogin() }
        gap(ar, add)
        val out = pill(act, "Se déconnecter")
        out.setOnClickListener {
            Profiles.active(act)?.let { Profiles.remove(act, it.id) }
            Session.end()
            act.showHome()
        }
        gap(ar, out)

        heading("Thème", 26)
        val tr = row()
        val cur = Store.themeIndex(act)
        for ((i, t) in Th.all.withIndex()) {
            val b = pill(act, (if (i == cur) "✓ " else "") + t.name, i == cur)
            b.setOnClickListener {
                Store.setTheme(act, i)
                act.recreate()
            }
            gap(tr, b)
        }

        heading("Sous-titres en ligne", 26)
        val sr = row()
        val has = Secure.get(act, "subkey").isNotEmpty()
        val key = pill(act, if (has) "Clé enregistrée — modifier" else "Saisir la clé wyzie.io")
        key.setOnClickListener { askKey() }
        gap(sr, key)
        if (has) {
            val rm = pill(act, "Supprimer la clé")
            rm.setOnClickListener {
                Secure.set(act, "subkey", "")
                draw()
            }
            gap(sr, rm)
        }
        val hint = label(act, "La clé est gratuite sur wyzie.io. Elle est gardée chiffrée sur cet appareil.", 12f, Th.muted)
        col.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(8) })

        col.addView(label(act, "MAXI tv 2.0 · lecteur natif", 12f, Th.muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(34) })
    }

    private fun askKey() {
        val e = EditText(act)
        e.setSingleLine(true)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        e.hint = "Clé wyzie.io"
        AlertDialog.Builder(act)
            .setTitle("Clé des sous-titres en ligne")
            .setView(e)
            .setPositiveButton("Enregistrer") { _, _ ->
                Secure.set(act, "subkey", e.text.toString().trim())
                draw()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
