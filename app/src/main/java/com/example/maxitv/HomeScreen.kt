package com.example.maxitv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Accueil : barre d'onglets en verre en haut (Accueil, Films, Séries, Live, Favoris, Profil), page en dessous. */
class HomeScreen(act: MainActivity) : Screen(act) {
    override val view: View by lazy { build() }
    private lateinit var content: FrameLayout
    private val navItems = ArrayList<TextView>()
    private val names = listOf("Accueil", "Films", "Séries", "Live", "Favoris", "Profil")
    private var tab = 0
    private var token = 0
    private var rows: RowsAdapter? = null
    private var searchBtn: TextView? = null

    private fun build(): View {
        val root = FrameLayout(act)
        root.setBackgroundColor(Th.cur.bg)
        content = FrameLayout(act)
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = FrameLayout(act)
        bar.background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, Color.argb(215, 0, 0, 0), Color.TRANSPARENT)
        val bp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, act.dp(70))
        root.addView(bar, bp)

        val logo = ImageView(act)
        logo.setImageResource(R.drawable.logo)
        logo.scaleType = ImageView.ScaleType.FIT_START
        val ll = FrameLayout.LayoutParams(act.dp(120), act.dp(40))
        ll.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        ll.leftMargin = act.dp(28)
        bar.addView(logo, ll)

        val nav = LinearLayout(act)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setPadding(act.dp(4), act.dp(4), act.dp(4), act.dp(4))
        nav.background = rounded(Color.parseColor("#59000000"), act.dpf(40f), Color.parseColor("#26FFFFFF"), act.dp(1))
        for ((i, n) in names.withIndex()) {
            val t = label(act, n, 14f, Th.muted, true)
            t.gravity = Gravity.CENTER
            t.setPadding(act.dp(16), act.dp(8), act.dp(16), act.dp(8))
            t.isFocusable = true
            t.isClickable = true
            t.setOnClickListener { showTab(i) }
            t.zoomOnFocus(1.06f) { paintNav() }
            nav.addView(t)
            navItems.add(t)
        }
        val np = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        np.gravity = Gravity.CENTER
        bar.addView(nav, np)

        val sb = pill(act, "Rechercher")
        sb.setOnClickListener { act.push(SearchScreen(act)) }
        searchBtn = sb
        val sp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        sp.rightMargin = act.dp(28)
        bar.addView(sb, sp)

        showTab(0)
        return root
    }

    private fun paintNav() {
        for ((i, t) in navItems.withIndex()) {
            val f = t.isFocused
            val on = i == tab
            if (f) {
                t.background = rounded(Th.cur.accent, act.dpf(40f))
                t.setTextColor(Th.cur.onAccent)
            } else if (on) {
                t.background = rounded(Th.glassStrong, act.dpf(40f))
                t.setTextColor(Color.WHITE)
            } else {
                t.background = null
                t.setTextColor(Th.muted)
            }
        }
    }

    private fun newList(): RecyclerView {
        val rv = RecyclerView(act)
        rv.layoutManager = RowsLayoutManager(act)
        rv.clipChildren = false
        rv.clipToPadding = false
        rv.setPadding(0, 0, 0, act.resources.displayMetrics.heightPixels / 2)
        rv.itemAnimator = null
        rv.setItemViewCacheSize(4)
        rv.isFocusable = false
        rv.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        return rv
    }

    private fun showTab(i: Int) {
        tab = i
        paintNav()
        val my = ++token
        content.removeAllViews()
        rows = null
        if (i == 5) {
            content.addView(ProfileView(act, this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        val rv = newList()
        val ad = RowsAdapter(act, rv, { item, list -> act.open(item, list) }, { k -> openCategories(k) })
        rv.adapter = ad
        rows = ad
        content.addView(rv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        when (i) {
            0 -> loadHome(ad, my)
            1 -> loadKind(ad, Kind.MOVIES, my)
            2 -> loadKind(ad, Kind.SERIES, my)
            3 -> loadKind(ad, Kind.LIVE, my)
            else -> loadFavs(ad)
        }
    }

    /** Recharge la page affichée (retour d'une fiche : favoris et « reprendre » peuvent avoir changé). */
    fun refresh() {
        if (tab == 4) showTab(4)
    }

    private fun recentShelves(kind: Kind?): List<Row> {
        val l = Store.recents(act)
        val out = ArrayList<Row>()
        val v = l.filter { it.kind != Kind.LIVE && (kind == null || it.kind == kind) }
        if (v.isNotEmpty()) out.add(Row.Shelf("Reprendre", Kind.MOVIES, null, v))
        if (kind == null || kind == Kind.LIVE) {
            val c = l.filter { it.kind == Kind.LIVE }
            if (c.isNotEmpty()) out.add(Row.Shelf("Chaînes récentes", Kind.LIVE, null, c))
        }
        return out
    }

    private fun loadHome(ad: RowsAdapter, my: Int) {
        val hero = Row.Hero()
        val first = ArrayList<Row>()
        first.add(hero)
        first.addAll(recentShelves(null))
        val favs = Store.favs(act).filter { it.kind != Kind.LIVE }
        if (favs.isNotEmpty()) first.add(Row.Shelf("Mes favoris", Kind.MOVIES, null, favs))
        ad.setRows(first)
        act.ui.launch(Dispatchers.Main) {
            val movies = loadCats(Kind.MOVIES)
            val series = loadCats(Kind.SERIES)
            val live = loadCats(Kind.LIVE)
            if (my != token) return@launch
            val more = ArrayList<Row>()
            for (c in movies.take(3)) more.add(Row.Shelf("Films · " + c.name, Kind.MOVIES, c))
            for (c in series.take(3)) more.add(Row.Shelf("Séries · " + c.name, Kind.SERIES, c))
            for (c in live.take(2)) more.add(Row.Shelf("Live · " + c.name, Kind.LIVE, c))
            if (more.isEmpty()) more.add(Row.Note("Aucune catégorie reçue. Appuie sur OK pour réessayer.") { showTab(0) })
            ad.setRows(first + more)
            fillHero(ad, hero, listOf(movies, series), listOf(Kind.MOVIES, Kind.SERIES), my)
        }
    }

    private suspend fun loadCats(kind: Kind): List<Cat> =
        try { Session.categories(kind) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }

    private fun loadKind(ad: RowsAdapter, kind: Kind, my: Int) {
        val initial = ArrayList<Row>()
        val hero = Row.Hero()
        if (kind == Kind.LIVE) initial.add(Row.Heading("Live TV", "", true)) else initial.add(hero)
        ad.setRows(initial)
        act.ui.launch(Dispatchers.Main) {
            val cats = try { Session.categories(kind) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (my != token) return@launch
            if (cats == null) {
                ad.setRows(listOf(Row.Note("Connexion impossible. Appuie sur OK pour réessayer.") { showTab(tab) }))
                return@launch
            }
            val list = ArrayList<Row>()
            if (kind == Kind.LIVE) list.add(Row.Heading("Live TV", cats.size.toString() + " catégories", true)) else list.add(hero)
            list.add(Row.Bar(kind))
            list.addAll(recentShelves(kind))
            for (c in cats) list.add(Row.Shelf(c.name, kind, c))
            ad.setRows(list)
            if (kind != Kind.LIVE) fillHero(ad, hero, listOf(cats), listOf(kind), my)
        }
    }

    private fun loadFavs(ad: RowsAdapter) {
        val l = Store.favs(act)
        val list = ArrayList<Row>()
        list.add(Row.Heading("Mes favoris", "Maintiens OK sur une affiche pour l'ajouter ou la retirer.", true))
        val m = l.filter { it.kind == Kind.MOVIES }
        val s = l.filter { it.kind == Kind.SERIES }
        val c = l.filter { it.kind == Kind.LIVE }
        if (m.isNotEmpty()) list.add(Row.Shelf("Films", Kind.MOVIES, null, m))
        if (s.isNotEmpty()) list.add(Row.Shelf("Séries", Kind.SERIES, null, s))
        if (c.isNotEmpty()) list.add(Row.Shelf("Chaînes", Kind.LIVE, null, c))
        if (l.isEmpty()) list.add(Row.Note("Aucun favori pour l'instant."))
        ad.setRows(list)
    }

    /** Bandeau : on prend le début des deux premières catégories non vides, en alternant films et séries. */
    private suspend fun fillHero(ad: RowsAdapter, hero: Row.Hero, catLists: List<List<Cat>>, kinds: List<Kind>, my: Int) {
        val groups = ArrayList<List<Item>>()
        for ((n, cats) in catLists.withIndex()) {
            var got = 0
            val acc = ArrayList<Item>()
            for (c in cats) {
                if (got >= 2 || my != token) break
                val l = try { Session.items(kinds[n], c) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                val withPoster = l.filter { it.poster.isNotEmpty() }
                if (withPoster.isNotEmpty()) {
                    got++
                    acc.addAll(withPoster.take(4))
                }
            }
            groups.add(acc)
        }
        if (my != token) return
        val pool = ArrayList<Item>()
        var n = 0
        while (pool.size < 8 && groups.any { n < it.size }) {
            for (g in groups) if (n < g.size && pool.size < 8) pool.add(g[n])
            n++
        }
        hero.items = pool
        val i = ad.indexOfRow(hero)
        if (pool.isEmpty() && i >= 0) {
            ad.removeRow(hero)
        } else {
            ad.changed(hero)
            view.post { if (my == token && view.findFocus() == null) focusDefault() }
        }
    }

    private fun focusDefault() {
        val rv = content.getChildAt(0) as? RecyclerView ?: return
        val first = rv.getChildAt(0)
        if (first is HeroView && first.focusPlay()) return
        navItems.getOrNull(tab)?.requestFocus()
    }

    private fun openCategories(kind: Kind) {
        act.ui.launch(Dispatchers.Main) {
            val cats = try { Session.categories(kind) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            act.push(CategoriesScreen(act, kind, cats))
        }
    }

    override fun onShown() {
        if (view.findFocus() == null) navItems.getOrNull(tab)?.requestFocus()
    }

    override fun onBack(): Boolean {
        if (tab != 0) {
            showTab(0)
            navItems[0].requestFocus()
            return true
        }
        return false
    }

    override fun restoreFocus() {
        refresh()
        super.restoreFocus()
    }
}
