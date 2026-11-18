package com.example.maxitv

import android.content.Context
import android.graphics.Color
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

/** Une affiche (ou une chaîne) : image arrondie, légende dessous, zoom + bague de couleur quand elle est sélectionnée. */
class CardView(ctx: Context, val live: Boolean) : LinearLayout(ctx) {
    private val art = FrameLayout(ctx)
    private val img = ImageView(ctx)
    private val ph: TextView
    private val badge: TextView
    private val cap: TextView
    private val imgW = ctx.dp(if (live) 176 else 118)
    private val imgH = ctx.dp(if (live) 99 else 177)
    var onFocus: ((View) -> Unit)? = null

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        val lp = RecyclerView.LayoutParams(imgW, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(ctx.dp(5), 0, ctx.dp(5), 0)
        layoutParams = lp
        isFocusable = true
        isClickable = true

        art.layoutParams = LayoutParams(imgW, imgH)
        art.background = if (live) gradient(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, Color.parseColor("#1B1B1F"), Color.parseColor("#0A0A0C")) else rounded(Th.cur.surface2, 0f)
        roundClip(art, ctx.dpf(10f))
        addView(art)

        ph = label(ctx, "", if (live) 26f else 30f, Color.parseColor("#38FFFFFF"), true)
        ph.gravity = Gravity.CENTER
        art.addView(ph, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        img.scaleType = if (live) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        if (live) img.setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
        art.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        badge = label(ctx, "NOUVEAU", 8f, Th.cur.onAccent, true)
        badge.setPadding(ctx.dp(6), ctx.dp(3), ctx.dp(6), ctx.dp(3))
        badge.background = rounded(Th.cur.accent, ctx.dpf(8f))
        val bl = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bl.setMargins(ctx.dp(6), ctx.dp(6), 0, 0)
        art.addView(badge, bl)

        cap = label(ctx, "", 11f, Th.muted)
        cap.maxLines = 1
        cap.ellipsize = TextUtils.TruncateAt.END
        cap.setPadding(ctx.dp(2), ctx.dp(8), ctx.dp(2), 0)
        addView(cap)

        zoomOnFocus(1.1f) { f ->
            art.foreground = if (f) rounded(Color.TRANSPARENT, ctx.dpf(10f), Th.cur.accent, ctx.dp(3)) else null
            cap.setTextColor(if (f) Color.WHITE else Th.muted)
            if (f) onFocus?.invoke(this)
        }
    }

    fun bind(item: Item) {
        ph.text = initials(item.name)
        cap.text = item.name
        val now = System.currentTimeMillis() / 1000
        badge.visibility = if (!live && item.added > 0 && now - item.added < 14L * 86400L) VISIBLE else GONE
        val app = context.applicationContext
        if (item.poster.isEmpty()) {
            Glide.with(app).clear(img)
            img.setImageDrawable(null)
        } else {
            val req = Glide.with(app).load(item.poster)
                .format(DecodeFormat.PREFER_RGB_565)
                .override(imgW, imgH)
                .transition(DrawableTransitionOptions.withCrossFade(120))
            (if (live) req.fitCenter() else req.centerCrop()).into(img)
        }
    }

    fun recycle() {
        Glide.with(context.applicationContext).clear(img)
        img.setImageDrawable(null)
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
    }
}

/** Liste horizontale de cartes (une étagère, ou une ligne de résultats). */
class CardAdapter(
    private val onClick: (Item, List<Item>) -> Unit,
    private val onLong: (Item) -> Unit,
    private val onFocused: (View) -> Unit
) : RecyclerView.Adapter<CardAdapter.VH>() {
    var items: List<Item> = emptyList()
        private set
    private var live = false

    class VH(val card: CardView) : RecyclerView.ViewHolder(card)

    fun set(list: List<Item>, isLive: Boolean) {
        items = list
        live = isLive
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = if (live) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val c = CardView(parent.context, viewType == 1)
        c.onFocus = { v -> onFocused(v) }
        return VH(c)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.card.bind(item)
        holder.card.setOnClickListener { onClick(item, items) }
        holder.card.setOnLongClickListener {
            onLong(item)
            true
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.card.recycle()
    }

    override fun getItemCount(): Int = items.size
}
