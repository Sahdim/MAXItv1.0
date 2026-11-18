package com.example.maxitv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Lecteur vidéo natif (Media3 / ExoPlayer).
 * Télécommande : OK = pause + barre, gauche/droite = reculer/avancer (plus longtemps = plus vite),
 * haut/bas = menus (Audio, Sous-titres, Image, Vitesse). Sur une chaîne : haut/bas = changer de chaîne.
 * Si un format ne passe pas, les suivants sont essayés tout seuls (extension du serveur, m3u8, mp4, mkv).
 */
class PlayerScreen(act: MainActivity, private var req: PlayRequest) : Screen(act) {
    override val view: View by lazy { build() }
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private lateinit var frame: AspectRatioFrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var subs: SubtitleView
    private lateinit var online: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var errorTv: TextView
    private lateinit var controls: FrameLayout
    private lateinit var titleTv: TextView
    private lateinit var curTv: TextView
    private lateinit var endTv: TextView
    private lateinit var progress: ProgressBar
    private lateinit var playBtn: TextView
    private lateinit var seekRow: LinearLayout
    private lateinit var menuPanel: LinearLayout
    private lateinit var menuTitle: TextView
    private lateinit var menuList: LinearLayout
    private lateinit var menuScroll: ScrollView

    private var urlIndex = 0
    private var retries = 0
    private var everPlayed = false
    private var resizeIdx = 0
    private var speed = 1f
    private var overlayOn = false
    private var menuOn = false
    private var cues: List<Cue> = emptyList()
    private var cueOffset = 0L
    private var cueName = ""
    private var onlineList: List<OnlineSub> = emptyList()
    private var onlineState = ""
    private var lastSave = 0L
    private var gen = 0

    private val hideTask = Runnable { hideOverlay() }
    private val tickTask = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 300)
        }
    }

    private fun build(): View {
        val root = FrameLayout(act)
        root.setBackgroundColor(Color.BLACK)
        root.isFocusable = true
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        frame = AspectRatioFrameLayout(act)
        frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        surface = SurfaceView(act)
        frame.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        fp.gravity = Gravity.CENTER
        root.addView(frame, fp)

        subs = SubtitleView(act)
        subs.setUserDefaultStyle()
        subs.setUserDefaultTextSize()
        root.addView(subs, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        online = label(act, "", 20f, Color.WHITE, true)
        online.gravity = Gravity.CENTER
        online.setShadowLayer(6f, 0f, 0f, Color.BLACK)
        online.setPadding(act.dp(60), 0, act.dp(60), 0)
        online.visibility = View.GONE
        val op = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        op.gravity = Gravity.BOTTOM
        op.bottomMargin = act.dp(54)
        root.addView(online, op)

        spinner = ProgressBar(act)
        spinner.indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        val sp = FrameLayout.LayoutParams(act.dp(48), act.dp(48))
        sp.gravity = Gravity.CENTER
        root.addView(spinner, sp)

        errorTv = label(act, "", 16f, Color.WHITE, true)
        errorTv.gravity = Gravity.CENTER
        errorTv.visibility = View.GONE
        val ep = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ep.gravity = Gravity.CENTER
        root.addView(errorTv, ep)

        controls = FrameLayout(act)
        controls.visibility = View.GONE
        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val topShade = View(act)
        topShade.background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, Color.argb(190, 0, 0, 0), Color.TRANSPARENT)
        controls.addView(topShade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, act.dp(110)))
        titleTv = label(act, req.title, 20f, Color.WHITE, true)
        titleTv.maxLines = 1
        titleTv.ellipsize = TextUtils.TruncateAt.END
        val tp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tp.setMargins(act.dp(36), act.dp(28), act.dp(36), 0)
        controls.addView(titleTv, tp)

        val bottom = LinearLayout(act)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.background = gradient(GradientDrawable.Orientation.BOTTOM_TOP, Color.argb(220, 0, 0, 0), Color.TRANSPARENT)
        bottom.setPadding(act.dp(36), act.dp(40), act.dp(36), act.dp(22))
        val bp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bp.gravity = Gravity.BOTTOM
        controls.addView(bottom, bp)

        seekRow = LinearLayout(act)
        seekRow.orientation = LinearLayout.HORIZONTAL
        seekRow.gravity = Gravity.CENTER_VERTICAL
        curTv = label(act, "0:00", 12f, Color.WHITE)
        endTv = label(act, "0:00", 12f, Color.WHITE)
        progress = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 1000
        progress.progressTintList = ColorStateList.valueOf(Th.cur.accent)
        progress.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
        seekRow.addView(curTv, LinearLayout.LayoutParams(act.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        seekRow.addView(progress, LinearLayout.LayoutParams(0, act.dp(6), 1f))
        val ep2 = LinearLayout.LayoutParams(act.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
        ep2.leftMargin = act.dp(8)
        seekRow.addView(endTv, ep2)
        bottom.addView(seekRow)

        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER_HORIZONTAL
        bottom.addView(btns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(12) })
        fun add(t: TextView) {
            t.setOnKeyListener { _, _, _ ->
                scheduleHide()
                false
            }
            btns.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.leftMargin = act.dp(5); it.rightMargin = act.dp(5) })
        }
        val back = pill(act, "−10 s")
        back.setOnClickListener { seek(-10_000L) }
        playBtn = pill(act, "Pause", true)
        playBtn.setOnClickListener { toggle() }
        val fwd = pill(act, "+10 s")
        fwd.setOnClickListener { seek(10_000L) }
        if (!req.live) {
            add(back)
            add(playBtn)
            add(fwd)
        } else {
            add(playBtn)
        }
        val audio = pill(act, "Audio")
        audio.setOnClickListener { openMenu("Audio") }
        val sub = pill(act, "Sous-titres")
        sub.setOnClickListener { openMenu("Sous-titres") }
        val img = pill(act, "Image")
        img.setOnClickListener { openMenu("Image") }
        add(audio)
        add(sub)
        add(img)
        if (!req.live) {
            val sp2 = pill(act, "Vitesse")
            sp2.setOnClickListener { openMenu("Vitesse") }
            add(sp2)
        }

        menuPanel = LinearLayout(act)
        menuPanel.orientation = LinearLayout.VERTICAL
        menuPanel.setBackgroundColor(Color.parseColor("#F2101012"))
        menuPanel.setPadding(act.dp(12), act.dp(20), act.dp(12), act.dp(12))
        menuPanel.visibility = View.GONE
        menuTitle = label(act, "", 17f, Color.WHITE, true)
        menuTitle.setPadding(act.dp(10), 0, 0, act.dp(10))
        menuPanel.addView(menuTitle)
        menuScroll = ScrollView(act)
        menuList = LinearLayout(act)
        menuList.orientation = LinearLayout.VERTICAL
        menuScroll.addView(menuList)
        menuPanel.addView(menuScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val mp = FrameLayout.LayoutParams(act.dp(300), ViewGroup.LayoutParams.MATCH_PARENT)
        mp.gravity = Gravity.END
        root.addView(menuPanel, mp)

        makePlayer()
        return root
    }

    // ---------- lecteur ----------

    private fun makePlayer() {
        val ds = OkHttpDataSource.Factory(Net.playerClient()).setUserAgent(Net.UA)
        val attrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        val p = ExoPlayer.Builder(act)
            .setRenderersFactory(DefaultRenderersFactory(act).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(20_000, 45_000, 1_500, 3_000).build())
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.setVideoSurfaceView(surface)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                spinner.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) {
                    everPlayed = true
                    retries = 0
                    errorTv.visibility = View.GONE
                }
                if (state == Player.STATE_ENDED) ended()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.text = if (isPlaying) "Pause" else "Lecture"
            }

            override fun onPlayerError(error: PlaybackException) {
                failed(error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val h = videoSize.height
                frame.setAspectRatio(if (h == 0) 0f else videoSize.width * videoSize.pixelWidthHeightRatio / h)
            }

            override fun onCues(cueGroup: CueGroup) {
                subs.setCues(cueGroup.cues)
            }
        })
        player = p
    }

    private fun start() {
        startRequest(req)
    }

    private fun startRequest(r: PlayRequest) {
        req = r
        val p = player ?: return
        gen++
        urlIndex = 0
        retries = 0
        everPlayed = false
        cues = emptyList()
        cueName = ""
        onlineList = emptyList()
        onlineState = ""
        online.visibility = View.GONE
        errorTv.visibility = View.GONE
        titleTv.text = r.title
        seekRow.visibility = if (r.live) View.GONE else View.VISIBLE
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        load(0, r.startMs)
        if (!r.live) findOnline(r)
    }

    private fun load(index: Int, startMs: Long) {
        val p = player ?: return
        urlIndex = index
        p.setMediaItem(MediaItem.fromUri(req.urls[index]), startMs)
        p.prepare()
        p.playWhenReady = true
    }

    private fun failed(e: PlaybackException) {
        val p = player ?: return
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        if (everPlayed && retries < 3) {
            retries++
            load(urlIndex, p.currentPosition)
            return
        }
        if (urlIndex + 1 < req.urls.size) {
            load(urlIndex + 1, if (everPlayed) p.currentPosition else req.startMs)
            return
        }
        spinner.visibility = View.GONE
        errorTv.text = "Impossible de lire ce contenu.\nAppuie sur Retour pour revenir."
        errorTv.visibility = View.VISIBLE
        showOverlay(false)
    }

    private fun ended() {
        saveNow(true)
        val n = req.next?.invoke()
        if (n != null) startRequest(n) else act.pop()
    }

    private fun findOnline(r: PlayRequest) {
        val key = Secure.get(act, "subkey")
        if (r.tmdb.isEmpty() || (r.tmdb.toLongOrNull() ?: 0L) <= 0L) {
            onlineState = "none"
            return
        }
        if (key.isEmpty()) {
            onlineState = "nokey"
            return
        }
        onlineState = "loading"
        val my = gen
        act.ui.launch(Dispatchers.Main) {
            val l = try { Subs.find(key, r.tmdb, r.season, r.episode) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (my != gen) return@launch
            if (l == null) {
                onlineState = "err"
            } else {
                onlineList = l
                onlineState = if (l.isEmpty()) "none" else "ready"
                if (l.isNotEmpty()) act.toast("Sous-titres trouvés en ligne")
            }
        }
    }

    // ---------- touches ----------

    private fun controlsFocused(): Boolean = controls.findFocus() != null

    override fun onKey(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return false
        val p = player ?: return false
        val k = e.keyCode
        if (menuOn) return false
        when (k) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                toggle()
                showOverlay(false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                p.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                p.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seek(30_000L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seek(-30_000L)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                zap(-1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                zap(1)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                showOverlay(true)
                return true
            }
        }
        if (controlsFocused()) {
            scheduleHide()
            return false
        }
        when (k) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (req.live) {
                    showOverlay(true)
                } else {
                    toggle()
                    showOverlay(false)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!req.live) {
                    val step = 10_000L * (1 + minOf(5, e.repeatCount / 3))
                    seek(if (k == KeyEvent.KEYCODE_DPAD_LEFT) -step else step)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (req.live) zap(-1) else showOverlay(true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (req.live) zap(1) else showOverlay(true)
                return true
            }
        }
        return false
    }

    override fun onBack(): Boolean {
        if (menuOn) {
            closeMenu()
            return true
        }
        if (overlayOn && controlsFocused()) {
            hideOverlay()
            return true
        }
        return false
    }

    private fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun seek(delta: Long) {
        val p = player ?: return
        val dur = p.duration
        var t = p.currentPosition + delta
        if (t < 0) t = 0
        if (dur != C.TIME_UNSET && t > dur - 500) t = maxOf(0L, dur - 500)
        p.seekTo(t)
        showOverlay(false)
        tick()
    }

    private fun zap(dir: Int) {
        val list = req.channels
        if (!req.live || list.size < 2) return
        val i = ((req.index + dir) % list.size + list.size) % list.size
        val ch = list[i]
        Store.addRecent(act, ch)
        startRequest(Session.liveRequest(ch, list))
        showOverlay(false)
    }

    // ---------- barre de commandes ----------

    private fun showOverlay(focusButtons: Boolean) {
        overlayOn = true
        controls.visibility = View.VISIBLE
        tick()
        if (focusButtons || controlsFocused()) {
            if (!controlsFocused()) playBtn.requestFocus()
        }
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideTask)
        handler.postDelayed(hideTask, 4500)
    }

    private fun hideOverlay() {
        if (menuOn) return
        handler.removeCallbacks(hideTask)
        overlayOn = false
        controls.visibility = View.GONE
        view.requestFocus()
    }

    private fun fmt(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, ss) else String.format("%d:%02d", m, ss)
    }

    private fun tick() {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (overlayOn && !req.live) {
            curTv.text = fmt(pos)
            if (dur != C.TIME_UNSET && dur > 0) {
                endTv.text = fmt(dur)
                progress.progress = (pos * 1000L / dur).toInt().coerceIn(0, 1000)
            }
        }
        if (cues.isNotEmpty()) {
            val t = pos - cueOffset
            val c = cues.firstOrNull { it.start <= t && t < it.end }
            if (c == null) {
                online.visibility = View.GONE
            } else {
                if (online.text.toString() != c.text) online.text = c.text
                online.visibility = View.VISIBLE
            }
        }
        val now = System.currentTimeMillis()
        if (!req.live && req.key.isNotEmpty() && p.isPlaying && now - lastSave > 5000L) {
            lastSave = now
            saveNow(false)
        }
    }

    private fun saveNow(finished: Boolean) {
        val p = player ?: return
        if (req.live || req.key.isEmpty()) return
        val dur = p.duration
        val pos = p.currentPosition
        if (finished || (dur != C.TIME_UNSET && dur - pos < 60_000L)) Store.setPos(act, req.key, 0L) else if (everPlayed) Store.setPos(act, req.key, pos)
    }

    // ---------- menus ----------

    private class Entry(val label: String, val selected: Boolean, val keepOpen: Boolean = false, val action: () -> Unit)

    private fun trackLabel(f: Format, n: Int): String {
        val lang = f.language
        val name = f.label ?: if (!lang.isNullOrEmpty() && lang != "und") {
            Locale.forLanguageTag(lang).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { it.uppercase() }
        } else {
            "Piste " + n
        }
        val extra = if (f.channelCount > 2) " · " + (if (f.channelCount == 6) "5.1" else f.channelCount.toString() + " canaux") else ""
        return name + extra
    }

    private fun trackEntries(type: Int): List<Entry> {
        val p = player ?: return emptyList()
        val out = ArrayList<Entry>()
        var n = 0
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                n++
                val label = trackLabel(g.getTrackFormat(i), n)
                val group = g
                val idx = i
                out.add(Entry(label, g.isTrackSelected(i) && (type != C.TRACK_TYPE_TEXT || cues.isEmpty())) {
                    cues = emptyList()
                    online.visibility = View.GONE
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, idx))
                        .build()
                })
            }
        }
        return out
    }

    private fun openMenu(which: String) {
        val p = player ?: return
        val items = ArrayList<Entry>()
        when (which) {
            "Audio" -> {
                items.addAll(trackEntries(C.TRACK_TYPE_AUDIO))
                if (items.isEmpty()) items.add(Entry("Une seule piste audio", true) {})
            }
            "Sous-titres" -> {
                val off = cues.isEmpty() && p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                items.add(Entry("Désactivés", off) {
                    cues = emptyList()
                    online.visibility = View.GONE
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                })
                items.addAll(trackEntries(C.TRACK_TYPE_TEXT))
                for (o in onlineList) {
                    items.add(Entry(o.label, cueName == o.url && cues.isNotEmpty()) { loadOnline(o) })
                }
                if (onlineList.isEmpty() && !req.live) {
                    val msg = when (onlineState) {
                        "loading" -> "Recherche en ligne en cours…"
                        "nokey" -> "En ligne : clé à saisir dans Profil"
                        "err" -> "Recherche en ligne impossible"
                        else -> "Aucun sous-titre en ligne"
                    }
                    items.add(Entry(msg, false) {})
                }
                if (cues.isNotEmpty()) {
                    items.add(Entry("Retarder de 0,5 s   (" + offsetText() + ")", false, true) {
                        cueOffset += 500L
                        refreshOffsetLabel()
                    })
                    items.add(Entry("Avancer de 0,5 s   (" + offsetText() + ")", false, true) {
                        cueOffset -= 500L
                        refreshOffsetLabel()
                    })
                }
            }
            "Image" -> {
                val names = listOf("Ajustée (bandes noires)", "Zoom (remplit l'écran)", "Étirée")
                val modes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
                for (i in names.indices) {
                    items.add(Entry(names[i], resizeIdx == i) {
                        resizeIdx = i
                        frame.setResizeMode(modes[i])
                    })
                }
            }
            else -> {
                val vals = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                for (v in vals) {
                    items.add(Entry((if (v == 1f) "Normale" else v.toString() + "×"), speed == v) {
                        speed = v
                        p.setPlaybackSpeed(v)
                    })
                }
            }
        }
        showMenu(which, items)
    }

    private fun offsetText(): String = (if (cueOffset <= 0L) "+" else "−") + String.format("%.1f", Math.abs(cueOffset) / 1000f) + " s"

    private fun refreshOffsetLabel() {
        act.toast("Décalage : " + offsetText())
    }

    private fun loadOnline(o: OnlineSub) {
        act.toast("Chargement des sous-titres…")
        val my = gen
        act.ui.launch(Dispatchers.Main) {
            val c = try { Subs.download(o.url) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            if (my != gen) return@launch
            if (c.isEmpty()) {
                act.toast("Sous-titres indisponibles")
            } else {
                cues = c
                cueName = o.url
                cueOffset = 0L
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                }
                act.toast("Sous-titres : " + o.label)
            }
        }
    }

    private fun showMenu(title: String, items: List<Entry>) {
        menuOn = true
        handler.removeCallbacks(hideTask)
        menuTitle.text = title
        menuList.removeAllViews()
        var focus: View? = null
        for (e in items) {
            val r = label(act, (if (e.selected) "✓  " else "     ") + e.label, 14f, Color.WHITE)
            r.setPadding(act.dp(12), act.dp(10), act.dp(12), act.dp(10))
            r.isFocusable = true
            r.isClickable = true
            r.setOnFocusChangeListener { _, f ->
                r.background = if (f) rounded(Th.cur.accent, act.dpf(10f)) else null
                r.setTextColor(if (f) Th.cur.onAccent else Color.WHITE)
            }
            r.setOnClickListener {
                e.action()
                if (!e.keepOpen) closeMenu()
            }
            menuList.addView(r)
            if (focus == null || e.selected) focus = r
        }
        menuPanel.visibility = View.VISIBLE
        focus?.requestFocus()
    }

    private fun closeMenu() {
        menuOn = false
        menuPanel.visibility = View.GONE
        if (overlayOn) {
            playBtn.requestFocus()
            scheduleHide()
        } else {
            view.requestFocus()
        }
    }

    // ---------- cycle de vie ----------

    override fun onShown() {
        start()
        handler.post(tickTask)
        showOverlay(false)
        view.requestFocus()
    }

    override fun onStop() {
        player?.pause()
        saveNow(false)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveNow(false)
        gen++
        player?.release()
        player = null
        act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
