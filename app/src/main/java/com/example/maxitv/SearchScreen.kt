package com.example.maxitv

import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Recherche dans tout le catalogue (films, séries, chaînes). Le catalogue se télécharge une fois, au fil de l'eau. */
class SearchScreen(act: MainActivity) : Screen(act) {
    override val view: View by lazy { build() }
    private lateinit var input: EditText
    private lateinit var list: RecyclerView
    private lateinit var adapter: RowsAdapter
    private var job: Job? = null

    private fun build(): View {
        val root = FrameLayout(act)
        root.setBackgroundColor(Th.cur.bg)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        input = EditText(act)
        input.hint = "Rechercher un film, une série ou une chaîne"
        input.setHintTextColor(Th.muted)
        input.setTextColor(Color.WHITE)
        input.textSize = 18f
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setPadding(act.dp(20), act.dp(12), act.dp(20), act.dp(12))
        val normal = rounded(Th.glass, act.dpf(30f), Color.parseColor("#22FFFFFF"), act.dp(1))
        val focused = rounded(Color.parseColor("#33FFFFFF"), act.dpf(30f), Th.cur.accent, act.dp(2))
        input.background = normal
        input.setOnFocusChangeListener { _, f -> input.background = if (f) focused else normal }
        val ip = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ip.setMargins(act.dp(32), act.dp(24), act.dp(32), act.dp(4))
        col.addView(input, ip)

        list = RecyclerView(act)
        list.layoutManager = RowsLayoutManager(act)
        list.clipChildren = false
        list.clipToPadding = false
        list.setPadding(0, 0, 0, act.resources.displayMetrics.heightPixels / 2)
        list.itemAnimator = null
        list.isFocusable = false
        adapter = RowsAdapter(act, list, { item, l -> act.open(item, l) }, { })
        list.adapter = adapter
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        adapter.setRows(listOf(Row.Note("Tape un titre puis valide (au moins 2 lettres).")))

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                search(s?.toString() ?: "", true)
            }
        })
        input.setOnEditorActionListener { _, _, _ ->
            search(input.text.toString(), false)
            list.post { list.getChildAt(0)?.requestFocus() }
            true
        }
        return root
    }

    private fun search(q0: String, wait: Boolean) {
        job?.cancel()
        val q = q0.trim()
        if (q.length < 2) return
        job = act.ui.launch(Dispatchers.Main) {
            if (wait) delay(700)
            adapter.setRows(listOf(Row.Note("Recherche en cours…")))
            val res = try {
                coroutineScope {
                    val kinds = listOf(Kind.MOVIES, Kind.SERIES, Kind.LIVE)
                    val ds = kinds.map { k ->
                        async {
                            val all = try { Session.all(k) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                            all.filter { it.name.contains(q, ignoreCase = true) }.take(60)
                        }
                    }
                    ds.map { it.await() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (res == null) {
                adapter.setRows(listOf(Row.Note("Recherche impossible pour le moment.")))
                return@launch
            }
            val rows = ArrayList<Row>()
            if (res[0].isNotEmpty()) rows.add(Row.Shelf("Films", Kind.MOVIES, null, res[0]))
            if (res[1].isNotEmpty()) rows.add(Row.Shelf("Séries", Kind.SERIES, null, res[1]))
            if (res[2].isNotEmpty()) rows.add(Row.Shelf("Chaînes", Kind.LIVE, null, res[2]))
            if (rows.isEmpty()) rows.add(Row.Note("Aucun résultat pour « " + q + " »."))
            adapter.setRows(rows)
        }
    }

    override fun onShown() {
        input.requestFocus()
    }

    override fun onDestroy() {
        job?.cancel()
    }
}
