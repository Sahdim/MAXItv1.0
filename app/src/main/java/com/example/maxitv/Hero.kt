package com.example.maxitv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Grand bandeau de l'accueil : un titre mis en avant, image de fond (ou affiche floutée), boutons Lire / Plus d'infos.
 * Change tout seul toutes les 9 secondes, avec un fondu.
 */
class HeroView(
    private val act: MainActivity,
    private val onPlay: (Item) -> Unit,
    private val onMore: (Item) -> Unit,
    private val onFocused: (View) -> Unit
) : FrameLayout(act) {
    private val bg = ImageView(act)
    private val posterBox = ImageView(act)
    private val block = LinearLayout(act)
    private val tag: TextView
    private val title: TextView
    private val meta: TextView
    private val plot: TextView
    private val playBtn: TextView
    private val moreBtn: TextView
    private val dots = LinearLayout(act)
    private var items: List<Item> = emptyList()
    private var idx = 0
    private var token = 0
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { rotate() }

    init {
        val h = (act.resources.displayMetrics.heightPixels * 0.64f).toInt()
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        setBackgroundColor(Th.cur.bg)
        clipChildren = false

        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(bg, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val shade = View(act)
        shade.background = gradient(GradientDrawable.Orientation.LEFT_RIGHT, Color.argb(235, 5, 5, 5), Color.argb(120, 5, 5, 5), Color.TRANSPARENT)
        addView(shade, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val fade = View(act)
        fade.background = gradient(GradientDrawable.Orientation.BOTTOM_TOP, Th.cur.bg, Color.TRANSPARENT)
        val fl = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h / 2)
        fl.gravity = Gravity.BOTTOM
        addView(fade, fl)

        posterBox.scaleType = ImageView.ScaleType.CENTER_CROP
        posterBox.background = rounded(Th.cur.surface2, act.dpf(12f))
        roundClip(posterBox, act.dpf(12f))
        val pl = LayoutParams(act.dp(150), act.dp(225))
        pl.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        pl.rightMargin = act.dp(70)
        posterBox.visibility = GONE
        addView(posterBox, pl)

        block.orientation = LinearLayout.VERTICAL
        tag = label(act, "", 11f, Th.cur.accent2, true)
        tag.letterSpacing = 0.14f
        title = label(act, "", 32f, Color.WHITE, true)
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        meta = label(act, "", 13f, Color.parseColor("#BFFFFFFF"))
        plot = label(act, "", 13f, Color.parseColor("#D1FFFFFF"))
        plot.maxLines = 3
        plot.ellipsize = TextUtils.TruncateAt.END
        plot.setLineSpacing(0f, 1.15f)
        block.addView(tag)
        block.addView(title, lp(top = 8))
        block.addView(meta, lp(top = 10))
        block.addView(plot, lp(top = 10))
        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        playBtn = pill(act, "▶  Lire", true) { onFocused(this) }
        moreBtn = pill(act, "Plus d'infos") { onFocused(this) }
        btns.addView(playBtn)
        val ml = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ml.leftMargin = act.dp(10)
        btns.addView(moreBtn, ml)
        block.addView(btns, lp(top = 18))
        val bl = LayoutParams(act.dp(380), ViewGroup.LayoutParams.WRAP_CONTENT)
        bl.gravity = Gravity.BOTTOM or Gravity.START
        bl.leftMargin = act.dp(28)
        bl.bottomMargin = act.dp(70)
        addView(block, bl)

        dots.orientation = LinearLayout.HORIZONTAL
        val dl = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dl.gravity = Gravity.BOTTOM or Gravity.END
        dl.rightMargin = act.dp(28)
        dl.bottomMargin = act.dp(56)
        addView(dots, dl)

        playBtn.setOnClickListener { items.getOrNull(idx)?.let { onPlay(it) } }
        moreBtn.setOnClickListener { items.getOrNull(idx)?.let { onMore(it) } }
        block.visibility = INVISIBLE
    }

    private fun lp(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = act.dp(top)
        return p
    }

    fun setItems(list: List<Item>) {
        if (list == items && list.isNotEmpty()) return
        items = list
        idx = 0
        if (list.isEmpty()) {
            block.visibility = INVISIBLE
            return
        }
        show(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 9000)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun rotate() {
        handler.postDelayed(tick, 9000)
        if (items.size > 1 && isShown) {
            idx = (idx + 1) % items.size
            show(idx)
        }
    }

    private fun show(i: Int) {
        val item = items[i]
        val my = ++token
        block.visibility = VISIBLE
        block.animate().alpha(0f).setDuration(120).withEndAction {
            if (my == token) {
                tag.text = if (item.kind == Kind.MOVIES) "FILM À DÉCOUVRIR" else "SÉRIE À DÉCOUVRIR"
                title.text = item.name
                meta.text = if (item.rating.isNotEmpty()) "★ " + item.rating else ""
                plot.text = ""
                block.animate().alpha(1f).setDuration(260).start()
            }
        }.start()
        playBtn.text = if (item.kind == Kind.MOVIES) "▶  Lire" else "▶  Voir les épisodes"
        drawDots(i)
        loadBackdrop(item.poster, true, 0)
        act.ui.launch(Dispatchers.Main) {
            val info = try { Session.info(item) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (info != null && my == token) {
                val m = listOf(info.year, if (info.rating.isNotEmpty()) "★ " + info.rating else "", info.genre, info.duration)
                    .filter { it.isNotEmpty() }.joinToString("  ·  ")
                meta.text = m
                plot.text = info.plot
                if (info.backdrop.isNotEmpty()) loadBackdrop(info.backdrop, false, my)
            }
        }
    }

    /** Sans image de fond du serveur, on étire une toute petite version de l'affiche : elle ressort floutée (et ça ne coûte presque rien). */
    private fun loadBackdrop(url: String, blurred: Boolean, check: Int) {
        if (url.isEmpty()) return
        val app = act.applicationContext
        val prev = bg.drawable
        val req = Glide.with(app).load(url).format(DecodeFormat.PREFER_RGB_565)
        if (blurred) {
            posterBox.visibility = VISIBLE
            Glide.with(app).load(url).format(DecodeFormat.PREFER_RGB_565).override(act.dp(150), act.dp(225)).centerCrop().into(posterBox)
            req.override(24, 14).centerCrop().placeholder(prev).transition(DrawableTransitionOptions.withCrossFade(500)).into(bg)
            bg.alpha = 0.75f
        } else {
            posterBox.visibility = GONE
            bg.alpha = 1f
            req.override(act.dp(960), act.dp(420)).centerCrop().placeholder(prev).transition(DrawableTransitionOptions.withCrossFade(600)).into(bg)
        }
    }

    private fun drawDots(sel: Int) {
        dots.removeAllViews()
        for (n in items.indices) {
            val d = View(act)
            val on = n == sel
            d.background = rounded(if (on) Th.cur.accent else Color.parseColor("#55FFFFFF"), act.dpf(4f))
            val p = LinearLayout.LayoutParams(act.dp(if (on) 18 else 6), act.dp(6))
            p.leftMargin = act.dp(5)
            dots.addView(d, p)
        }
    }

    fun focusPlay(): Boolean = playBtn.requestFocus()
}
