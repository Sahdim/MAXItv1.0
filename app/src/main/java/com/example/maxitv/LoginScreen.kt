package com.example.maxitv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Écran de connexion : adresse du serveur, identifiant, mot de passe. Rien n'est mémorisé ailleurs que dans le coffre chiffré. */
class LoginScreen(act: MainActivity) : Screen(act) {
    override val view: View by lazy { build() }
    private lateinit var host: EditText
    private lateinit var status: TextView
    private lateinit var connect: TextView

    private fun field(hint: String, type: Int, ime: Int): EditText {
        val e = EditText(act)
        e.hint = hint
        e.setHintTextColor(Th.muted)
        e.setTextColor(Color.WHITE)
        e.textSize = 15f
        e.setSingleLine(true)
        e.inputType = type
        e.imeOptions = ime
        e.setPadding(act.dp(16), act.dp(12), act.dp(16), act.dp(12))
        val normal = rounded(Color.parseColor("#1AFFFFFF"), act.dpf(14f), Color.parseColor("#22FFFFFF"), act.dp(1))
        val focused = rounded(Color.parseColor("#26FFFFFF"), act.dpf(14f), Th.cur.accent, act.dp(2))
        e.background = normal
        e.setOnFocusChangeListener { _, f -> e.background = if (f) focused else normal }
        return e
    }

    private fun build(): View {
        val root = FrameLayout(act)
        root.background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, Color.parseColor("#1D1D21"), Th.cur.bg, Th.cur.bg)
        val scroll = ScrollView(act)
        scroll.isFillViewport = true
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(act.dp(24), act.dp(20), act.dp(24), act.dp(20))
        scroll.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val logo = ImageView(act)
        logo.setImageResource(R.drawable.logo)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        col.addView(logo, LinearLayout.LayoutParams(act.dp(220), act.dp(90)))

        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(act.dp(26), act.dp(22), act.dp(26), act.dp(22))
        box.background = rounded(Color.parseColor("#1FFFFFFF"), act.dpf(24f), Color.parseColor("#22FFFFFF"), act.dp(1))
        col.addView(box, LinearLayout.LayoutParams(act.dp(400), ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(10) })

        host = field("Adresse du serveur (http://…)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_NEXT)
        val user = field("Identifiant", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, EditorInfo.IME_ACTION_NEXT)
        val pass = field("Mot de passe", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE)
        for (e in listOf(host, user, pass)) {
            box.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = act.dp(10) })
        }
        status = label(act, "", 13f, Th.muted)
        status.minHeight = act.dp(20)
        box.addView(status)

        connect = pill(act, "Se connecter", true)
        box.addView(connect, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(10) })

        if (Profiles.all(act).isNotEmpty()) {
            val cancel = pill(act, "Annuler")
            cancel.setOnClickListener { act.pop() }
            box.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(8) })
        }

        fun go() {
            if (!connect.isEnabled) return
            val h = host.text.toString().trim()
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (h.isEmpty() || u.isEmpty() || p.isEmpty()) {
                status.text = "Remplis les trois champs."
                return
            }
            connect.isEnabled = false
            status.text = "Connexion…"
            act.ui.launch(Dispatchers.Main) {
                try {
                    XtreamApi(h, u, p).login()
                    Profiles.add(act, h, u, p)
                    act.showHome()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiError) {
                    status.text = e.message ?: "Connexion impossible."
                    connect.isEnabled = true
                } catch (e: Exception) {
                    status.text = "Connexion impossible."
                    connect.isEnabled = true
                }
            }
        }
        connect.setOnClickListener { go() }
        pass.setOnEditorActionListener { _, _, _ ->
            go()
            true
        }
        return root
    }

    override fun onShown() {
        host.requestFocus()
    }
}
