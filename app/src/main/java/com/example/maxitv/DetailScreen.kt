package com.example.maxitv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fiche d'un film ou d'une série : affiche, synopsis, boutons, et pour une série les saisons et les épisodes. */
class DetailScreen(act: MainActivity, private val item: Item) : Screen(act) {
    override val view: View by lazy { build() }
    private lateinit var backdrop: ImageView
    private lateinit var title: TextView
    private lateinit var meta: TextView
    private lateinit var plot: TextView
    private lateinit var buttons: LinearLayout
    private lateinit var seasonsBox: LinearLayout
    private lateinit var episodesBox: LinearLayout
    private lateinit var scroll: ScrollView
    private var info: Info? = null
    private var firstFocus: View? = null
    private var curSeason = 0

    private fun build(): View {
        val root = FrameLayout(act)
        root.setBackgroundColor(Th.cur.bg)
        backdrop = ImageView(act)
        backdrop.scaleType = ImageView.ScaleType.CENTER_CROP
        backdrop.alpha = 0.7f
        root.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val app = act.applicationContext
        if (item.poster.isNotEmpty()) {
            Glide.with(app).load(item.poster).format(DecodeFormat.PREFER_RGB_565).override(24, 14).centerCrop().into(backdrop)
        }
        val shade = View(act)
        shade.background = gradient(GradientDrawable.Orientation.LEFT_RIGHT, Color.argb(240, 5, 5, 5), Color.argb(190, 5, 5, 5), Color.argb(90, 5, 5, 5))
        root.addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val fade = View(act)
        fade.background = gradient(GradientDrawable.Orientation.BOTTOM_TOP, Th.cur.bg, Color.TRANSPARENT)
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, act.dp(160))
        fp.gravity = Gravity.BOTTOM
        root.addView(fade, fp)

        scroll = ScrollView(act)
        scroll.isFillViewport = true
        scroll.clipChildren = false
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(act.dp(40), act.dp(40), act.dp(40), act.dp(60))
        scroll.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        col.addView(top)

        val poster = ImageView(act)
        poster.scaleType = ImageView.ScaleType.CENTER_CROP
        poster.background = rounded(Th.cur.surface2, act.dpf(12f))
        roundClip(poster, act.dpf(12f))
        if (item.poster.isNotEmpty()) {
            Glide.with(app).load(item.poster).format(DecodeFormat.PREFER_RGB_565).override(act.dp(150), act.dp(225)).centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150)).into(poster)
        }
        top.addView(poster, LinearLayout.LayoutParams(act.dp(150), act.dp(225)))

        val side = LinearLayout(act)
        side.orientation = LinearLayout.VERTICAL
        val sp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        sp.leftMargin = act.dp(32)
        top.addView(side, sp)

        title = label(act, item.name, 28f, Color.WHITE, true)
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        side.addView(title)
        meta = label(act, if (item.rating.isNotEmpty()) "★ " + item.rating else "", 13f, Color.parseColor("#BFFFFFFF"))
        side.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(10) })
        plot = label(act, "Chargement…", 13f, Color.parseColor("#D1FFFFFF"))
        plot.maxLines = 6
        plot.ellipsize = TextUtils.TruncateAt.END
        plot.setLineSpacing(0f, 1.15f)
        side.addView(plot, LinearLayout.LayoutParams(act.dp(560), ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(10) })

        buttons = LinearLayout(act)
        buttons.orientation = LinearLayout.HORIZONTAL
        side.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(18) })
        drawButtons()

        seasonsBox = LinearLayout(act)
        seasonsBox.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipChildren = false
        hs.addView(seasonsBox)
        col.addView(hs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(26) })

        episodesBox = LinearLayout(act)
        episodesBox.orientation = LinearLayout.VERTICAL
        col.addView(episodesBox, LinearLayout.LayoutParams(act.dp(640), ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(14) })

        act.ui.launch(Dispatchers.Main) {
            val i = try { Session.info(item) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            info = i
            if (i == null) {
                plot.text = "Informations indisponibles pour le moment."
            } else {
                meta.text = listOf(item.kind.title.removeSuffix("s"), i.year, if (i.rating.isNotEmpty()) "★ " + i.rating else "", i.genre, i.duration)
                    .filter { it.isNotEmpty() }.joinToString("  ·  ")
                plot.text = if (i.plot.isEmpty()) "Aucun synopsis." else i.plot
                if (i.backdrop.isNotEmpty()) {
                    backdrop.alpha = 1f
                    Glide.with(app).load(i.backdrop).format(DecodeFormat.PREFER_RGB_565).override(act.dp(960), act.dp(540)).centerCrop()
                        .placeholder(backdrop.drawable).transition(DrawableTransitionOptions.withCrossFade(500)).into(backdrop)
                }
                if (item.kind == Kind.SERIES) drawSeasons(i)
            }
            drawButtons()
            if (view.findFocus() == null) firstFocus?.requestFocus()
        }
        return root
    }

    private fun drawButtons() {
        buttons.removeAllViews()
        firstFocus = null
        val gap = { v: View ->
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = act.dp(10)
            buttons.addView(v, p)
        }
        if (item.kind == Kind.MOVIES) {
            val key = "m:" + item.id
            val pos = Store.pos(act, key)
            if (pos > 30_000L) {
                val resume = pill(act, "▶  Reprendre (" + fmt(pos) + ")", true)
                resume.setOnClickListener { playMovie(pos) }
                gap(resume)
                firstFocus = resume
                val again = pill(act, "Depuis le début")
                again.setOnClickListener { playMovie(0L) }
                gap(again)
            } else {
                val play = pill(act, "▶  Lire", true)
                play.setOnClickListener { playMovie(0L) }
                gap(play)
                firstFocus = play
            }
        }
        val fav = pill(act, if (Store.isFav(act, item)) "Retirer des favoris" else "Ajouter aux favoris")
        fav.setOnClickListener {
            val now = Store.toggleFav(act, item)
            fav.text = if (now) "Retirer des favoris" else "Ajouter aux favoris"
        }
        gap(fav)
        if (firstFocus == null) firstFocus = fav
        val tr = pill(act, "Bande-annonce")
        tr.setOnClickListener { trailer() }
        gap(tr)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, ss) else String.format("%d:%02d", m, ss)
    }

    private fun playMovie(startMs: Long) {
        Store.addRecent(act, item)
        act.push(PlayerScreen(act, Session.movieRequest(item, info, startMs)))
    }

    /** Ouvre la bande-annonce dans l'appli YouTube / le navigateur de la box (aucune page web n'est affichée dans MAXI tv). */
    private fun trailer() {
        val raw = info?.trailer ?: ""
        val id = Regex("([A-Za-z0-9_-]{11})$").find(raw.substringBefore('&').trim())?.groupValues?.get(1)
        val uri = if (id != null) "https://www.youtube.com/watch?v=" + id
        else "https://www.youtube.com/results?search_query=" + Uri.encode(item.name + " bande annonce")
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            act.toast("Aucune appli pour ouvrir la bande-annonce.")
        }
    }

    private fun drawSeasons(i: Info) {
        seasonsBox.removeAllViews()
        episodesBox.removeAllViews()
        if (i.seasons.isEmpty()) {
            plot.text = plot.text.toString() + "\n\nAucun épisode disponible."
            return
        }
        for (s in i.seasons.keys) {
            val b = pill(act, "Saison " + s)
            b.setOnClickListener { showSeason(i, s) }
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = act.dp(8)
            seasonsBox.addView(b, p)
        }
        showSeason(i, i.seasons.keys.first())
    }

    private fun showSeason(i: Info, s: Int) {
        curSeason = s
        episodesBox.removeAllViews()
        val list = i.seasons[s] ?: return
        for ((n, e) in list.withIndex()) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(act.dp(10), act.dp(8), act.dp(14), act.dp(8))
            row.isFocusable = true
            row.isClickable = true
            val normal = rounded(Color.parseColor("#14FFFFFF"), act.dpf(12f))
            row.background = normal
            row.setOnFocusChangeListener { v, f ->
                row.background = if (f) rounded(Th.cur.accent, act.dpf(12f)) else normal
                for (k in 0 until row.childCount) {
                    val c = row.getChildAt(k)
                    if (c is LinearLayout) {
                        for (q in 0 until c.childCount) (c.getChildAt(q) as? TextView)?.setTextColor(if (f) Th.cur.onAccent else if (q == 0) Color.WHITE else Th.muted)
                    } else if (c is TextView) {
                        c.setTextColor(if (f) Th.cur.onAccent else Th.muted)
                    }
                }
                v.animate().scaleX(if (f) 1.02f else 1f).scaleY(if (f) 1.02f else 1f).setDuration(120).start()
            }
            val num = label(act, e.num.toString(), 13f, Th.muted)
            num.gravity = Gravity.CENTER
            row.addView(num, LinearLayout.LayoutParams(act.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT))
            val thumb = ImageView(act)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.background = rounded(Th.cur.surface2, act.dpf(8f))
            roundClip(thumb, act.dpf(8f))
            if (e.image.isNotEmpty()) {
                Glide.with(act.applicationContext).load(e.image).format(DecodeFormat.PREFER_RGB_565).override(act.dp(120), act.dp(68)).centerCrop().into(thumb)
            }
            row.addView(thumb, LinearLayout.LayoutParams(act.dp(120), act.dp(68)))
            val tx = LinearLayout(act)
            tx.orientation = LinearLayout.VERTICAL
            val tt = label(act, e.title, 14f, Color.WHITE, true)
            tt.maxLines = 1
            tt.ellipsize = TextUtils.TruncateAt.END
            tx.addView(tt)
            if (e.duration.isNotEmpty()) {
                tx.addView(label(act, e.duration, 11f, Th.muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(4) })
            }
            val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.leftMargin = act.dp(14)
            row.addView(tx, tp)
            row.setOnClickListener {
                Store.addRecent(act, item)
                val key = "e:" + e.id
                val pos = Store.pos(act, key)
                act.push(PlayerScreen(act, Session.episodeRequest(item, s, list, n, info?.tmdb ?: "", if (pos > 30_000L) pos else 0L)))
            }
            val rp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rp.bottomMargin = act.dp(8)
            episodesBox.addView(row, rp)
        }
    }

    override fun onShown() {
        firstFocus?.requestFocus()
    }

    override fun restoreFocus() {
        drawButtons()
        super.restoreFocus()
    }
}
