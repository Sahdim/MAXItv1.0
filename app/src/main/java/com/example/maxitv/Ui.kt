package com.example.maxitv

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun View.dp(v: Int): Int = context.dp(v)

/** Thèmes de couleurs. Le choix est mémorisé ; l'appli se recrée pour l'appliquer. */
object Th {
    class Palette(
        val name: String,
        val bg: Int,
        val surface: Int,
        val surface2: Int,
        val accent: Int,
        val accent2: Int,
        val onAccent: Int
    )

    private fun c(s: String): Int = Color.parseColor(s)

    val all: List<Palette> by lazy {
        listOf(
            Palette("Apple", c("#050505"), c("#101012"), c("#19191C"), c("#FFFFFF"), c("#AEB2BA"), c("#0A0A0A")),
            Palette("Minuit", c("#04060C"), c("#0C1220"), c("#141C30"), c("#5B9BFF"), c("#9CC0FF"), c("#FFFFFF")),
            Palette("Rubis", c("#0A0405"), c("#1A0D10"), c("#26141A"), c("#FF4D5E"), c("#FF9AA5"), c("#FFFFFF")),
            Palette("Émeraude", c("#030A07"), c("#0A1912"), c("#10261C"), c("#2ECC71"), c("#8FE3B0"), c("#04140B")),
            Palette("Or", c("#0A0803"), c("#181408"), c("#241E0D"), c("#E8B84A"), c("#F3D78F"), c("#1A1200"))
        )
    }

    var cur: Palette = Palette("Apple", Color.parseColor("#050505"), Color.parseColor("#101012"), Color.parseColor("#19191C"), Color.WHITE, Color.parseColor("#AEB2BA"), Color.parseColor("#0A0A0A"))

    val muted: Int = Color.parseColor("#99FFFFFF")
    val glass: Int = Color.parseColor("#2EFFFFFF")
    val glassStrong: Int = Color.parseColor("#47FFFFFF")

    fun load(ctx: Context) {
        val i = Store.themeIndex(ctx)
        cur = all[if (i in all.indices) i else 0]
    }
}

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = radius
    g.setColor(color)
    if (strokeW > 0) g.setStroke(strokeW, stroke)
    return g
}

fun roundClip(v: View, radiusPx: Float) {
    v.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    v.clipToOutline = true
}

fun gradient(orientation: GradientDrawable.Orientation, vararg colors: Int): GradientDrawable =
    GradientDrawable(orientation, colors)

fun label(ctx: Context, text: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = text
    t.textSize = sp
    t.setTextColor(color)
    if (bold) t.typeface = Typeface.DEFAULT_BOLD
    t.includeFontPadding = false
    return t
}

/** Applique l'animation de survol (léger zoom) à une vue qui prend le focus. */
fun View.zoomOnFocus(scale: Float, extra: ((Boolean) -> Unit)? = null) {
    onFocusChangeListener = View.OnFocusChangeListener { v, focused ->
        v.animate().scaleX(if (focused) scale else 1f).scaleY(if (focused) scale else 1f).setDuration(140).start()
        v.translationZ = if (focused) 12f else 0f
        extra?.invoke(focused)
    }
}

/** Bouton « pilule » façon Apple TV : verre translucide, blanc plein quand il est sélectionné. */
fun pill(ctx: Context, text: String, primary: Boolean = false, onFocus: ((View) -> Unit)? = null): TextView {
    val t = label(ctx, text, 14f, Color.WHITE, true)
    t.gravity = Gravity.CENTER
    t.setPadding(ctx.dp(18), ctx.dp(9), ctx.dp(18), ctx.dp(9))
    t.isFocusable = true
    t.isClickable = true
    val r = ctx.dpf(40f)
    fun paint(f: Boolean) {
        val p = Th.cur
        if (f) {
            t.background = rounded(p.accent, r, Color.WHITE, ctx.dp(2))
            t.setTextColor(p.onAccent)
        } else if (primary) {
            t.background = rounded(p.accent2, r)
            t.setTextColor(p.onAccent)
        } else {
            t.background = rounded(Th.glassStrong, r)
            t.setTextColor(Color.WHITE)
        }
    }
    paint(false)
    t.zoomOnFocus(1.07f) { f ->
        paint(f)
        if (f) onFocus?.invoke(t)
    }
    return t
}

fun initials(name: String): String {
    val w = name.trim().split(" ").filter { it.isNotEmpty() }
    if (w.isEmpty()) return "?"
    val a = w[0].take(1)
    val b = if (w.size > 1) w[1].take(1) else ""
    return (a + b).uppercase()
}
