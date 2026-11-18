package com.example.maxitv

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Volet de gauche : toutes les catégories d'un type (films, séries ou chaînes). */
class CategoriesScreen(act: MainActivity, private val kind: Kind, private val cats: List<Cat>) : Screen(act) {
    override val overlay: Boolean = true
    override val view: View by lazy { build() }
    private var firstItem: View? = null

    private fun build(): View {
        val root = FrameLayout(act)
        root.setBackgroundColor(Color.parseColor("#73000000"))
        root.isClickable = true
        root.setOnClickListener { act.pop() }

        val panel = LinearLayout(act)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.parseColor("#F00E0E11"))
        panel.setPadding(act.dp(10), act.dp(22), act.dp(10), act.dp(10))
        root.addView(panel, FrameLayout.LayoutParams(act.dp(300), ViewGroup.LayoutParams.MATCH_PARENT))

        val t = label(act, kind.title + " · catégories", 18f, Color.WHITE, true)
        t.setPadding(act.dp(14), 0, 0, act.dp(2))
        panel.addView(t)
        val s = label(act, cats.size.toString() + " au total", 12f, Th.muted)
        s.setPadding(act.dp(14), 0, 0, act.dp(12))
        panel.addView(s)

        val scroll = ScrollView(act)
        val list = LinearLayout(act)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        for (c in cats) {
            val r = label(act, c.name, 15f, Color.WHITE)
            r.maxLines = 1
            r.ellipsize = android.text.TextUtils.TruncateAt.END
            r.setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(10))
            r.isFocusable = true
            r.isClickable = true
            r.setOnFocusChangeListener { v, f ->
                r.background = if (f) rounded(Th.cur.accent, act.dpf(12f)) else null
                r.setTextColor(if (f) Th.cur.onAccent else Color.WHITE)
                v.animate().scaleX(if (f) 1.02f else 1f).scaleY(if (f) 1.02f else 1f).setDuration(120).start()
            }
            r.setOnClickListener {
                act.pop()
                act.push(CategoryScreen(act, kind, c))
            }
            list.addView(r)
            if (firstItem == null) firstItem = r
        }
        return root
    }

    override fun onShown() {
        firstItem?.requestFocus()
    }
}

/** Toute une catégorie en grille. */
class CategoryScreen(act: MainActivity, private val kind: Kind, private val cat: Cat) : Screen(act) {
    override val view: View by lazy { build() }
    private lateinit var grid: RecyclerView
    private lateinit var sub: TextView
    private lateinit var sortBtn: TextView
    private var all: List<Item> = emptyList()
    private var recentFirst = false
    private lateinit var cards: CardAdapter

    private fun build(): View {
        val root = FrameLayout(act)
        root.setBackgroundColor(Th.cur.bg)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(act.dp(32), act.dp(22), act.dp(32), act.dp(6))
        val titles = LinearLayout(act)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label(act, cat.name, 26f, Color.WHITE, true))
        sub = label(act, "Chargement…", 13f, Th.muted)
        titles.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(4) })
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sortBtn = pill(act, "Récents d'abord")
        sortBtn.setOnClickListener {
            recentFirst = !recentFirst
            sortBtn.text = if (recentFirst) "✓  Récents d'abord" else "Récents d'abord"
            show()
        }
        head.addView(sortBtn)
        col.addView(head)

        grid = RecyclerView(act)
        val span = if (kind == Kind.LIVE) 4 else 6
        grid.layoutManager = GridLayoutManager(act, span)
        grid.clipChildren = false
        grid.clipToPadding = false
        grid.setPadding(act.dp(27), act.dp(16), act.dp(27), act.dp(40))
        grid.itemAnimator = null
        grid.isFocusable = false
        cards = CardAdapter(
            { item, list -> act.open(item, list) },
            { item -> act.toast(if (Store.toggleFav(act, item)) "Ajouté aux favoris" else "Retiré des favoris") },
            { _ -> }
        )
        grid.adapter = cards
        col.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        act.ui.launch(Dispatchers.Main) {
            val l = try { Session.itemsFull(kind, cat) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (l == null) {
                sub.text = "Impossible de charger cette catégorie."
            } else {
                all = l
                show()
                grid.post { grid.getChildAt(0)?.requestFocus() }
            }
        }
        return root
    }

    private fun show() {
        val l = if (recentFirst) all.sortedByDescending { it.added } else all
        sub.text = l.size.toString() + (if (kind == Kind.LIVE) " chaînes" else " titres")
        cards.set(l, kind == Kind.LIVE)
    }

    override fun onShown() {
        sortBtn.requestFocus()
    }
}
