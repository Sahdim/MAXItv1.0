package com.example.maxitv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Un écran de l'appli (accueil, fiche, lecteur…). Ils s'empilent les uns sur les autres avec un fondu. */
abstract class Screen(val act: MainActivity) {
    abstract val view: View

    /** Écran translucide posé par-dessus (le précédent reste visible dessous). */
    open val overlay: Boolean = false
    var lastFocus: View? = null

    open fun onKey(e: KeyEvent): Boolean = false

    /** Renvoie true si l'écran a géré le bouton Retour lui-même. */
    open fun onBack(): Boolean = false

    open fun onShown() {}

    open fun onStop() {}

    open fun onDestroy() {}

    open fun restoreFocus() {
        val f = lastFocus
        if (f != null && f.isAttachedToWindow && f.isShown) f.requestFocus() else view.requestFocus()
    }
}

/** Une seule activité. Aucun identifiant ne passe par un Intent. */
class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private val stack = ArrayList<Screen>()

    /** Tâches de l'interface : s'arrêtent toutes quand l'appli se ferme. */
    val ui: CoroutineScope get() = lifecycleScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Th.load(this)
        root = FrameLayout(this)
        root.setBackgroundColor(Th.cur.bg)
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                back()
            }
        })
        val ok = Session.ready || Session.begin(this)
        push(if (ok) HomeScreen(this) else LoginScreen(this))
    }

    fun top(): Screen? = stack.lastOrNull()

    fun push(s: Screen) {
        val prev = stack.lastOrNull()
        prev?.lastFocus = prev?.view?.findFocus()
        stack.add(s)
        val v = s.view
        v.alpha = 0f
        root.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        v.animate().alpha(1f).setDuration(200).withEndAction {
            if (!s.overlay && prev != null && stack.contains(s)) prev.view.visibility = View.GONE
        }.start()
        v.post { s.onShown() }
    }

    fun pop() {
        if (stack.size <= 1) return
        val s = stack.removeAt(stack.size - 1)
        val prev = stack.lastOrNull()
        if (prev != null) prev.view.visibility = View.VISIBLE
        s.view.animate().cancel()
        s.view.animate().alpha(0f).setDuration(160).withEndAction {
            root.removeView(s.view)
            s.onDestroy()
        }.start()
        if (prev != null) prev.view.post { prev.restoreFocus() }
    }

    /** Remplace tous les écrans par un seul (connexion, changement de profil…). */
    fun reset(s: Screen) {
        for (old in stack) {
            root.removeView(old.view)
            old.onDestroy()
        }
        stack.clear()
        push(s)
    }

    fun showHome() = reset(if (Session.begin(this)) HomeScreen(this) else LoginScreen(this))

    fun showLogin() = push(LoginScreen(this))

    private fun back() {
        val t = stack.lastOrNull()
        if (t != null && t.onBack()) return
        if (stack.size > 1) pop() else finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val t = stack.lastOrNull()
        if (t != null && t.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        stack.lastOrNull()?.onStop()
    }

    /** Ouvre un titre : une chaîne se lit tout de suite, un film ou une série ouvre sa fiche. */
    fun open(item: Item, siblings: List<Item>) {
        if (item.kind == Kind.LIVE) playLive(item, siblings) else push(DetailScreen(this, item))
    }

    fun playLive(item: Item, list: List<Item>) {
        Store.addRecent(this, item)
        push(PlayerScreen(this, Session.liveRequest(item, list)))
    }

    /** Lecture directe d'un film (bouton « Lire » du bandeau). */
    fun playMovie(item: Item) {
        ui.launch(Dispatchers.Main) {
            val info = try { Session.info(item) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            Store.addRecent(this@MainActivity, item)
            val key = "m:" + item.id
            val pos = Store.pos(this@MainActivity, key)
            push(PlayerScreen(this@MainActivity, Session.movieRequest(item, info, if (pos > 30_000L) pos else 0L)))
        }
    }

    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
