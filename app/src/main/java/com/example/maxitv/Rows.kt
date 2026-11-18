package com.example.maxitv

import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Row {
    class Hero : Row() {
        var items: List<Item> = emptyList()
    }

    class Bar(val kind: Kind) : Row()

    class Shelf(val title: String, val kind: Kind, val cat: Cat?, var items: List<Item>? = null) : Row() {
        var loading = false
        var failed = false
    }

    class Heading(val text: String, val sub: String = "", val lead: Boolean = false) : Row()

    class Note(val text: String, val action: (() -> Unit)? = null) : Row()
}

/** Liste verticale : le bandeau, la barre « Catégories » et les étagères. Une étagère ne se charge que quand elle arrive à l'écran. */
class RowsAdapter(
    private val act: MainActivity,
    private val rv: RecyclerView,
    private val onOpen: (Item, List<Item>) -> Unit,
    private val onCats: (Kind) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val rows = ArrayList<Row>()
    private val pool = RecyclerView.RecycledViewPool()
    private val topAlign = act.dp(84)

    fun setRows(list: List<Row>) {
        rows.clear()
        rows.addAll(list)
        notifyDataSetChanged()
    }

    fun indexOfRow(r: Row): Int = rows.indexOf(r)

    fun changed(r: Row) {
        val i = rows.indexOf(r)
        if (i >= 0) notifyItemChanged(i)
    }

    fun removeRow(r: Row) {
        val i = rows.indexOf(r)
        if (i >= 0) {
            rows.removeAt(i)
            notifyItemRemoved(i)
        }
    }

    /** Fait défiler la liste pour que la ligne sélectionnée se place toujours au même endroit (comme Apple TV). */
    private fun align(row: View, target: Int) {
        val d = row.top - target
        if (Math.abs(d) > act.dp(6)) rv.smoothScrollBy(0, d, DecelerateInterpolator(), 240)
    }

    private fun alignOf(v: View, target: Int) {
        var p: View? = v
        while (p != null && p.parent !== rv) p = p.parent as? View
        if (p != null) align(p, target)
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Hero -> 0
        is Row.Bar -> 1
        is Row.Shelf -> 2
        is Row.Heading -> 3
        is Row.Note -> 4
    }

    class HeroH(val hero: HeroView) : RecyclerView.ViewHolder(hero)

    class BarH(val root: LinearLayout, val btn: TextView) : RecyclerView.ViewHolder(root)

    class ShelfH(
        val root: LinearLayout,
        val title: TextView,
        val rail: RecyclerView,
        val skel: LinearLayout,
        val retry: TextView,
        val cards: CardAdapter
    ) : RecyclerView.ViewHolder(root)

    class HeadingH(val root: LinearLayout, val title: TextView, val sub: TextView) : RecyclerView.ViewHolder(root)

    class NoteH(val btn: TextView) : RecyclerView.ViewHolder(btn)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val full = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        when (viewType) {
            0 -> return HeroH(HeroView(act, { onOpen2(it) }, { onOpen(it, emptyList()) }, { v -> alignOf(v, 0) }))
            1 -> {
                val root = LinearLayout(ctx)
                root.layoutParams = RecyclerView.LayoutParams(full, wrap)
                root.setPadding(ctx.dp(28), ctx.dp(8), ctx.dp(28), ctx.dp(4))
                val btn = pill(ctx, "☰  Catégories") { v -> alignOf(v, topAlign) }
                root.addView(btn)
                return BarH(root, btn)
            }
            2 -> {
                val root = LinearLayout(ctx)
                root.orientation = LinearLayout.VERTICAL
                root.clipChildren = false
                root.clipToPadding = false
                root.layoutParams = RecyclerView.LayoutParams(full, wrap)
                val title = label(ctx, "", 17f, Color.WHITE, true)
                title.setPadding(ctx.dp(28), ctx.dp(12), ctx.dp(28), 0)
                root.addView(title)
                val rail = RecyclerView(ctx)
                rail.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
                rail.clipChildren = false
                rail.clipToPadding = false
                rail.setPadding(ctx.dp(23), ctx.dp(14), ctx.dp(23), ctx.dp(20))
                rail.isFocusable = false
                rail.itemAnimator = null
                rail.setRecycledViewPool(pool)
                rail.setItemViewCacheSize(6)
                val cards = CardAdapter(
                    { item, list -> onOpen(item, list) },
                    { item -> toggleFav(item) },
                    { v -> alignOf(v, topAlign) }
                )
                rail.adapter = cards
                root.addView(rail, LinearLayout.LayoutParams(full, wrap))
                val skel = LinearLayout(ctx)
                skel.orientation = LinearLayout.HORIZONTAL
                skel.setPadding(ctx.dp(28), ctx.dp(14), 0, ctx.dp(20))
                for (n in 0 until 8) {
                    val b = View(ctx)
                    b.background = rounded(Th.cur.surface, ctx.dpf(10f))
                    val bp = LinearLayout.LayoutParams(ctx.dp(118), ctx.dp(177))
                    bp.rightMargin = ctx.dp(10)
                    skel.addView(b, bp)
                }
                root.addView(skel)
                val retry = pill(ctx, "Impossible de charger — appuie sur OK pour réessayer") { v -> alignOf(v, topAlign) }
                val rp = LinearLayout.LayoutParams(wrap, wrap)
                rp.setMargins(ctx.dp(28), ctx.dp(14), 0, ctx.dp(30))
                root.addView(retry, rp)
                return ShelfH(root, title, rail, skel, retry, cards)
            }
            3 -> {
                val root = LinearLayout(ctx)
                root.orientation = LinearLayout.VERTICAL
                root.layoutParams = RecyclerView.LayoutParams(full, wrap)
                val t = label(ctx, "", 30f, Color.WHITE, true)
                val s = label(ctx, "", 13f, Th.muted)
                root.addView(t)
                root.addView(s, LinearLayout.LayoutParams(wrap, wrap).also { it.topMargin = ctx.dp(6) })
                return HeadingH(root, t, s)
            }
            else -> {
                val b = pill(ctx, "") { v -> alignOf(v, topAlign) }
                val lp = RecyclerView.LayoutParams(wrap, wrap)
                lp.setMargins(ctx.dp(28), ctx.dp(90), 0, ctx.dp(20))
                b.layoutParams = lp
                return NoteH(b)
            }
        }
    }

    private fun onOpen2(item: Item) {
        if (item.kind == Kind.MOVIES) act.playMovie(item) else onOpen(item, emptyList())
    }

    private fun toggleFav(item: Item) {
        val now = Store.toggleFav(act, item)
        Toast.makeText(act, if (now) "Ajouté aux favoris" else "Retiré des favoris", Toast.LENGTH_SHORT).show()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        when (holder) {
            is HeroH -> holder.hero.setItems((row as Row.Hero).items)
            is BarH -> {
                val k = (row as Row.Bar).kind
                holder.btn.setOnClickListener { onCats(k) }
            }
            is ShelfH -> bindShelf(holder, row as Row.Shelf)
            is HeadingH -> {
                val h = row as Row.Heading
                holder.title.text = h.text
                holder.sub.text = h.sub
                holder.sub.visibility = if (h.sub.isEmpty()) View.GONE else View.VISIBLE
                holder.root.setPadding(act.dp(28), act.dp(if (h.lead) 84 else 18), act.dp(28), act.dp(4))
            }
            is NoteH -> {
                val n = row as Row.Note
                holder.btn.text = n.text
                holder.btn.setOnClickListener { n.action?.invoke() }
                holder.btn.isFocusable = n.action != null
            }
        }
    }

    private fun bindShelf(h: ShelfH, s: Row.Shelf) {
        h.title.text = s.title
        val items = s.items
        if (items != null) {
            h.skel.visibility = View.GONE
            h.retry.visibility = View.GONE
            h.rail.visibility = View.VISIBLE
            h.cards.set(items, s.kind == Kind.LIVE)
        } else if (s.failed) {
            h.skel.visibility = View.GONE
            h.rail.visibility = View.GONE
            h.retry.visibility = View.VISIBLE
            h.retry.setOnClickListener {
                s.failed = false
                changed(s)
            }
        } else {
            h.rail.visibility = View.GONE
            h.retry.visibility = View.GONE
            h.skel.visibility = View.VISIBLE
            if (!s.loading) load(s)
        }
    }

    private fun load(s: Row.Shelf) {
        val c = s.cat ?: return
        s.loading = true
        act.ui.launch(Dispatchers.Main) {
            val res = try { Session.items(s.kind, c) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            s.loading = false
            if (indexOfRow(s) < 0) return@launch
            if (res == null) {
                s.failed = true
                changed(s)
            } else if (res.isEmpty()) {
                removeRow(s)
            } else {
                s.items = res
                changed(s)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ShelfH) holder.rail.stopScroll()
    }

    override fun getItemCount(): Int = rows.size
}

/** Liste verticale qui ne fait pas défiler toute seule (c'est l'adaptateur qui place chaque ligne, avec une animation douce). */
class RowsLayoutManager(ctx: android.content.Context) : LinearLayoutManager(ctx) {
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean = false
}
