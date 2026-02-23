package com.bytemantis.snald.ludogame

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.bytemantis.snald.R
import com.bytemantis.snald.core.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

class LudoActivity : AppCompatActivity() {

    private val viewModel: LudoViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    private lateinit var boardImage: ImageView
    private lateinit var neonOverlay: LottieAnimationView
    private lateinit var statusText: TextView
    private lateinit var tokenOverlay: FrameLayout
    private lateinit var starOverlay: FrameLayout
    private lateinit var victoryPopText: TextView
    private lateinit var setupLayout: LinearLayout
    private lateinit var setupTitle: TextView
    private lateinit var groupTheme: LinearLayout
    private lateinit var groupPlayers: LinearLayout
    private lateinit var groupTokens: LinearLayout

    private lateinit var layoutDashboard: LinearLayout
    private lateinit var textDashP1: TextView
    private lateinit var textDashP2: TextView
    private lateinit var textDashP3: TextView
    private lateinit var textDashP4: TextView
    private lateinit var textDashTimer: TextView

    private lateinit var videoSetupTop: VideoView
    private lateinit var videoSetupBottom: VideoView

    // TODO: Replace these with your actual 9 video file names!
    private val setupVideoResources = listOf(
        R.raw.random_bgm_1, R.raw.random_bgm_2, R.raw.random_bgm_3,
        R.raw.random_bgm_4, R.raw.random_bgm_5, R.raw.random_bgm_6,
        R.raw.random_bgm_7, R.raw.random_bgm_8, R.raw.random_bgm_9
    )

    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var playerLayouts: Map<Int, View>
    private lateinit var progressBars: Map<Int, ProgressBar>

    private val allTokenViews = mutableListOf<MutableList<ImageView>>()
    private val activeBadges = mutableListOf<TextView>()

    private var cellW = 0f
    private var cellH = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f
    private var isUiInitialized = false

    private val PREFS_NAME = "LudoPrefs"
    private val KEY_THEME = "SelectedTheme"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ludo)
        soundManager = SoundManager(this)

        allTokenViews.clear()
        repeat(4) { allTokenViews.add(mutableListOf()) }

        setupUI()
        setupObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = viewModel.gameState.value
                if (state == LudoViewModel.State.SETUP_THEME ||
                    state == LudoViewModel.State.SETUP_PLAYERS || state == LudoViewModel.State.SETUP_TOKENS) {
                    if (!viewModel.navigateBackInSetup()) {
                        finish()
                    }
                } else if (state == LudoViewModel.State.GAME_OVER) {
                    viewModel.quitGame()
                    finish()
                } else {
                    AlertDialog.Builder(this@LudoActivity)
                        .setTitle("Leave Match")
                        .setMessage("Do you want to save your progress or quit the match completely?")
                        .setPositiveButton("Save & Exit") { _, _ -> viewModel.saveCurrentState(); finish() }
                        .setNegativeButton("Quit Match") { _, _ -> viewModel.quitGame(); finish() }
                        .setNeutralButton("Cancel", null)
                        .show()
                }
            }
        })
    }

    private fun setupUI() {
        boardImage = findViewById(R.id.img_ludo_board)
        neonOverlay = findViewById(R.id.anim_ludo_neon_overlay)
        statusText = findViewById(R.id.text_ludo_status)
        tokenOverlay = findViewById(R.id.overlay_ludo_tokens)
        starOverlay = findViewById(R.id.overlay_ludo_stars)
        victoryPopText = findViewById(R.id.text_ludo_victory_pop)
        setupLayout = findViewById(R.id.layout_ludo_setup)
        setupTitle = findViewById(R.id.text_setup_title)
        groupTheme = findViewById(R.id.group_setup_theme)
        groupPlayers = findViewById(R.id.group_setup_players)
        groupTokens = findViewById(R.id.group_setup_tokens)

        videoSetupTop = findViewById(R.id.video_setup_top)
        videoSetupBottom = findViewById(R.id.video_setup_bottom)

        layoutDashboard = findViewById(R.id.layout_dashboard)
        textDashP1 = findViewById(R.id.text_dash_p1)
        textDashP2 = findViewById(R.id.text_dash_p2)
        textDashP3 = findViewById(R.id.text_dash_p3)
        textDashP4 = findViewById(R.id.text_dash_p4)
        textDashTimer = findViewById(R.id.text_dash_timer)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt(KEY_THEME, R.drawable.ludo_board)
        boardImage.setImageResource(savedTheme)

        if (savedTheme == R.drawable.ludo_board_neon) {
            neonOverlay.visibility = View.VISIBLE
            neonOverlay.playAnimation()
        } else {
            neonOverlay.visibility = View.GONE
            neonOverlay.cancelAnimation()
        }

        diceViews = mapOf(1 to findViewById(R.id.dice_p1), 2 to findViewById(R.id.dice_p2), 3 to findViewById(R.id.dice_p3), 4 to findViewById(R.id.dice_p4))
        playerLayouts = mapOf(3 to findViewById(R.id.layout_ludo_p3), 4 to findViewById(R.id.layout_ludo_p4))
        progressBars = mapOf(0 to findViewById(R.id.progress_p1), 1 to findViewById(R.id.progress_p2), 2 to findViewById(R.id.progress_p3), 3 to findViewById(R.id.progress_p4))

        findViewById<Button>(R.id.btn_theme_classic).setOnClickListener { applyAndSaveTheme(R.drawable.ludo_board) }
        findViewById<Button>(R.id.btn_theme_wood).setOnClickListener { applyAndSaveTheme(R.drawable.ludo_board_wood) }
        findViewById<Button>(R.id.btn_theme_neon).setOnClickListener { applyAndSaveTheme(R.drawable.ludo_board_neon) }

        findViewById<Button>(R.id.btn_ludo_2p).setOnClickListener { viewModel.selectPlayerCount(2) }
        findViewById<Button>(R.id.btn_ludo_3p).setOnClickListener { viewModel.selectPlayerCount(3) }
        findViewById<Button>(R.id.btn_ludo_4p).setOnClickListener { viewModel.selectPlayerCount(4) }
        findViewById<Button>(R.id.btn_tokens_1).setOnClickListener { viewModel.startGame(1) }
        findViewById<Button>(R.id.btn_tokens_2).setOnClickListener { viewModel.startGame(2) }
        findViewById<Button>(R.id.btn_tokens_4).setOnClickListener { viewModel.startGame(4) }

        boardImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (boardImage.width > 0 && !isUiInitialized) {
                    calculateBoardMetrics()
                    val currentPlayers = viewModel.players.value
                    if (currentPlayers != null && currentPlayers.isNotEmpty()) {
                        spawnAllTokensInitial(currentPlayers.size)
                        isUiInitialized = true
                        renderBoardState()
                        renderDynamicSafeZone(viewModel.dynamicSafeZone.value)
                    }
                    boardImage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })

        diceViews.forEach { (id, view) ->
            view.setOnClickListener {
                val activeIdx = viewModel.activePlayerIndex.value ?: 0
                if (viewModel.gameState.value == LudoViewModel.State.WAITING_FOR_ROLL && id == (activeIdx + 1)) {
                    viewModel.rollDice()
                }
            }
        }
    }

    private fun playRandomSetupVideos() {
        if (videoSetupTop.isPlaying || videoSetupBottom.isPlaying) return

        val randomVids = setupVideoResources.shuffled().take(2)
        if (randomVids.size == 2) {
            setupVideoLoop(videoSetupTop, randomVids[0])
            setupVideoLoop(videoSetupBottom, randomVids[1])
        }
    }

    private fun setupVideoLoop(videoView: VideoView, resId: Int) {
        try {
            val uri = Uri.parse("android.resource://$packageName/$resId")
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                videoView.start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopSetupVideos() {
        if (videoSetupTop.isPlaying) videoSetupTop.stopPlayback()
        if (videoSetupBottom.isPlaying) videoSetupBottom.stopPlayback()
    }

    private fun applyAndSaveTheme(themeResId: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_THEME, themeResId).apply()
        boardImage.setImageResource(themeResId)
        if (themeResId == R.drawable.ludo_board_neon) { neonOverlay.visibility = View.VISIBLE; neonOverlay.playAnimation() }
        else { neonOverlay.visibility = View.GONE; neonOverlay.cancelAnimation() }
        viewModel.selectTheme()
    }

    // --- NEW: Centralized Audio Logic for Game State ---
    private fun handleGameStateMusic(state: LudoViewModel.State, themeResId: Int) {
        when (state) {
            LudoViewModel.State.SETUP_THEME,
            LudoViewModel.State.SETUP_PLAYERS,
            LudoViewModel.State.SETUP_TOKENS -> {
                soundManager.stopLudoMusic()
                soundManager.startMenuMusic()
            }
            LudoViewModel.State.WAITING_FOR_ROLL,
            LudoViewModel.State.WAITING_FOR_MOVE,
            LudoViewModel.State.ANIMATING -> {
                soundManager.playMusicForTheme(themeResId)
            }
            LudoViewModel.State.GAME_OVER -> {
                soundManager.stopMenuMusic()
                soundManager.stopLudoMusic()
            }
        }
    }

    private fun setupObservers() {
        viewModel.gameState.observe(this) { state ->
            layoutDashboard.visibility = if (state == LudoViewModel.State.SETUP_THEME || state == LudoViewModel.State.SETUP_PLAYERS || state == LudoViewModel.State.SETUP_TOKENS) View.GONE else View.VISIBLE

            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentTheme = prefs.getInt(KEY_THEME, R.drawable.ludo_board)

            // Execute audio changes based strictly on game state and current theme
            handleGameStateMusic(state, currentTheme)

            // Execute UI changes ONLY. No audio commands belong inside this block anymore.
            when(state) {
                LudoViewModel.State.SETUP_THEME -> {
                    setupLayout.visibility = View.VISIBLE
                    playRandomSetupVideos()
                    groupTheme.visibility = View.VISIBLE; groupPlayers.visibility = View.GONE; groupTokens.visibility = View.GONE; setupTitle.text = "SELECT BOARD"
                }
                LudoViewModel.State.SETUP_PLAYERS -> {
                    setupLayout.visibility = View.VISIBLE
                    playRandomSetupVideos()
                    groupTheme.visibility = View.GONE; groupPlayers.visibility = View.VISIBLE; groupTokens.visibility = View.GONE; setupTitle.text = "LUDO MATCH"
                }
                LudoViewModel.State.SETUP_TOKENS -> {
                    setupLayout.visibility = View.VISIBLE
                    playRandomSetupVideos()
                    groupTheme.visibility = View.GONE; groupPlayers.visibility = View.GONE; groupTokens.visibility = View.VISIBLE; setupTitle.text = "GAME LENGTH"
                }
                LudoViewModel.State.GAME_OVER -> {
                    setupLayout.visibility = View.GONE
                    stopSetupVideos()
                    showGameOverDialog()
                }
                else -> {
                    setupLayout.visibility = View.GONE
                    stopSetupVideos()
                }
            }
        }

        viewModel.players.observe(this) { players ->
            if (players.isNotEmpty()) {
                playerLayouts.get(3)?.visibility = if (players.size >= 3) View.VISIBLE else View.GONE
                playerLayouts.get(4)?.visibility = if (players.size >= 4) View.VISIBLE else View.GONE
                progressBars.get(2)?.visibility = if (players.size >= 3) View.VISIBLE else View.GONE
                progressBars.get(3)?.visibility = if (players.size >= 4) View.VISIBLE else View.GONE
                if (cellW > 0 && !isUiInitialized) { spawnAllTokensInitial(players.size); isUiInitialized = true }
                if (isUiInitialized) renderBoardState()
                updateDashboardStats()
            }
        }

        viewModel.activePlayerIndex.observe(this) { idx ->
            diceViews.forEach { (id, v) -> v.alpha = if (id == idx + 1) 1f else 0.5f; v.scaleX = if (id == idx + 1) 1.2f else 1f; v.scaleY = if (id == idx + 1) 1.2f else 1f }
        }

        viewModel.statusMessage.observe(this) { statusText.text = it }
        viewModel.announcement.observe(this) { ann -> if (ann != null) { showDynamicAnnouncement(ann); viewModel.clearAnnouncement() } }
        viewModel.turnUpdate.observe(this) { if (it != null && isUiInitialized) playTurnSequence(it) }
        viewModel.statsUpdate.observe(this) { updateDashboardStats() }

        viewModel.diceValue.observe(this) { dice ->
            val dv = diceViews.get(viewModel.activePlayerIndex.value!! + 1)
            if (dv != null) { soundManager.playDiceRoll(); updateDiceImage(dv, dice); dv.animate().rotationBy(360f).setDuration(300).start() }
        }

        viewModel.timerSeconds.observe(this) { secs ->
            textDashTimer.text = "00:${secs.toString().padStart(2, '0')}"
            if (secs <= 5) textDashTimer.setTextColor(Color.RED) else textDashTimer.setTextColor(Color.WHITE)
        }

        viewModel.dynamicSafeZone.observe(this) { zone ->
            if (isUiInitialized) renderDynamicSafeZone(zone)
        }
    }

    private fun updateDashboardStats() {
        val players = viewModel.players.value ?: return

        if (players.size > 0) { textDashP1.visibility = View.VISIBLE; textDashP1.text = "P1 ⚔️${players[0].kills}  💀${players[0].deaths}  🎲${players[0].sixesRolled}" } else { textDashP1.visibility = View.GONE }
        if (players.size > 1) { textDashP2.visibility = View.VISIBLE; textDashP2.text = "P2 ⚔️${players[1].kills}  💀${players[1].deaths}  🎲${players[1].sixesRolled}" } else { textDashP2.visibility = View.GONE }
        if (players.size > 2) { textDashP3.visibility = View.VISIBLE; textDashP3.text = "P3 ⚔️${players[2].kills}  💀${players[2].deaths}  🎲${players[2].sixesRolled}" } else { textDashP3.visibility = View.GONE }
        if (players.size > 3) { textDashP4.visibility = View.VISIBLE; textDashP4.text = "P4 ⚔️${players[3].kills}  💀${players[3].deaths}  🎲${players[3].sixesRolled}" } else { textDashP4.visibility = View.GONE }
    }

    private fun renderDynamicSafeZone(coord: Pair<Int, Int>?) {
        if (cellW <= 0) return
        starOverlay.removeAllViews()
        if (coord != null) {
            val star = ImageView(this).apply {
                setImageResource(android.R.drawable.btn_star_big_on)
                layoutParams = FrameLayout.LayoutParams((cellW * 0.7f).toInt(), (cellW * 0.7f).toInt())
                alpha = 0f
            }
            starOverlay.addView(star)
            moveViewToGrid(star, coord.first, coord.second)
            star.animate().alpha(0.85f).setDuration(500).start()
        }
    }

    private fun showDynamicAnnouncement(ann: LudoViewModel.Announcement) {
        victoryPopText.text = ann.message
        victoryPopText.visibility = View.VISIBLE
        victoryPopText.alpha = 0f

        if (ann.type == LudoViewModel.AnnouncementType.PLAYER_VICTORY) {
            soundManager.playWin()
            victoryPopText.animate().alpha(1f).scaleX(1.2f).scaleY(1.2f).setDuration(500).withEndAction {
                lifecycleScope.launch { delay(3000); victoryPopText.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(500).withEndAction { victoryPopText.visibility = View.GONE }.start() }
            }.start()
        } else {
            soundManager.playSafeZone()
            victoryPopText.animate().alpha(1f).setDuration(300).withEndAction {
                lifecycleScope.launch { delay(1200); victoryPopText.animate().alpha(0f).setDuration(300).withEndAction { victoryPopText.visibility = View.GONE }.start() }
            }.start()
        }
    }

    private fun playTurnSequence(u: LudoViewModel.TurnUpdate) {
        if (!isUiInitialized || allTokenViews.get(u.playerIdx).isEmpty()) return
        val view = allTokenViews.get(u.playerIdx).get(u.tokenIdx)
        view.visibility = View.VISIBLE; view.bringToFront()

        if (u.isSpawn) {
            soundManager.playStarCollect()
            val coord = LudoBoardConfig.getGlobalCoord(u.playerIdx, 0)
            if (coord != null) moveViewToGrid(view, coord.first, coord.second)
            view.postDelayed({ finishTurnSequence(u) }, 450)
        } else {
            val players = viewModel.players.value!!
            val currentPos = players.get(u.playerIdx).tokenPositions.get(u.tokenIdx)
            val start = kotlin.math.max(0, currentPos - u.visualSteps)
            runMoveLoop(view, u.playerIdx, start, 1, u.visualSteps, u)
        }
    }

    private fun runMoveLoop(v: View, p: Int, s: Int, step: Int, total: Int, u: LudoViewModel.TurnUpdate) {
        if (step > total) { finishTurnSequence(u); return }
        val coord = LudoBoardConfig.getGlobalCoord(p, s + step)
        if (coord != null) {
            soundManager.playHop()
            val tx = boardOffsetX + (coord.first + 0.5f) * cellW - (v.width / 2)
            val ty = boardOffsetY + (coord.second + 0.5f) * cellH - (v.height / 2)
            v.animate().x(tx).y(ty).setDuration(150).withEndAction { runMoveLoop(v, p, s, step + 1, total, u) }.start()
        } else finishTurnSequence(u)
    }

    private fun finishTurnSequence(u: LudoViewModel.TurnUpdate) {
        if (!u.isSpawn) when(u.soundToPlay) {
            LudoViewModel.SoundType.SAFE -> soundManager.playSafeZone()
            LudoViewModel.SoundType.WIN -> soundManager.playWin()
            LudoViewModel.SoundType.KILL -> soundManager.playSlideBack()
            LudoViewModel.SoundType.STAR_COLLECT -> soundManager.playStarCollect()
            LudoViewModel.SoundType.SHIELD_BREAK -> soundManager.playStarUsed()
            else -> {}
        }
        if (u.killInfo != null) {
            val victim = allTokenViews.get(u.killInfo.victimPlayerIdx).get(u.killInfo.victimTokenIdx)
            val base = getBaseCoord(u.killInfo.victimPlayerIdx, u.killInfo.victimTokenIdx)
            victim.animate().x(boardOffsetX + base.first * cellW - victim.width/2)
                .y(boardOffsetY + base.second * cellH - victim.height/2)
                .setDuration(500).withEndAction { renderBoardState(); viewModel.onTurnAnimationsFinished() }.start()
        } else {
            renderBoardState()
            viewModel.onTurnAnimationsFinished()
        }
    }

    private fun renderBoardState() {
        val players = viewModel.players.value ?: return
        if (!isUiInitialized || allTokenViews.get(0).isEmpty()) return
        clearBadges()
        val occMap = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()

        players.forEachIndexed { pIdx, player ->
            val max = player.tokenCount * 56
            val current = player.tokenPositions.filter { it > -1 }.sumOf { it }
            progressBars.get(pIdx)?.progress = (current.toDouble() / max * 100).toInt()

            for (tIdx in 0 until player.tokenPositions.size) {
                val pos = player.tokenPositions.get(tIdx)
                val v = allTokenViews.get(pIdx).get(tIdx)
                val hasShield = player.tokenShields.get(tIdx)

                if (hasShield) {
                    v.setBackgroundResource(R.drawable.bg_ludo_halo)
                    val padding = (cellW * 0.20f).toInt()
                    v.setPadding(padding, padding, padding, padding)
                } else {
                    v.background = null
                    v.setPadding(0, 0, 0, 0)
                }

                if (pos == -1) {
                    val b = getBaseCoord(pIdx, tIdx)
                    moveViewToPrecise(v, b.first, b.second); v.scaleX = 1f; v.scaleY = 1f; v.visibility = View.VISIBLE
                } else if (pos == 56) v.visibility = View.GONE
                else {
                    val c = LudoBoardConfig.getGlobalCoord(pIdx, pos)
                    if (c != null) occMap.getOrPut(c) { mutableListOf() }.add(Pair(pIdx, tIdx))
                }
            }
        }

        occMap.forEach { (c, tokens) ->
            val groupedByPlayer = tokens.groupBy { it.first }
            if (groupedByPlayer.size == 1) {
                val playerTokens = groupedByPlayer.values.first()
                val mainToken = allTokenViews[playerTokens[0].first][playerTokens[0].second]
                mainToken.visibility = View.VISIBLE; mainToken.scaleX = 1f; mainToken.scaleY = 1f; moveViewToGrid(mainToken, c.first, c.second)
                for (i in 1 until playerTokens.size) allTokenViews[playerTokens[i].first][playerTokens[i].second].visibility = View.GONE
                if (playerTokens.size > 1) addBadge(mainToken, playerTokens.size)
            } else {
                var colorIndex = 0
                groupedByPlayer.forEach { (playerId, playerTokens) ->
                    val mainToken = allTokenViews[playerId][playerTokens[0].second]
                    mainToken.visibility = View.VISIBLE; mainToken.scaleX = 0.6f; mainToken.scaleY = 0.6f
                    val ox = if (colorIndex % 2 == 0) -0.25f else 0.25f; val oy = if (colorIndex < 2) -0.25f else 0.25f
                    moveViewToPrecise(mainToken, c.first + 0.5f + ox, c.second + 0.5f + oy)
                    for (i in 1 until playerTokens.size) allTokenViews[playerId][playerTokens[i].second].visibility = View.GONE
                    if (playerTokens.size > 1) addBadge(mainToken, playerTokens.size)
                    colorIndex++
                }
            }
        }
    }

    private fun calculateBoardMetrics() {
        val d = boardImage.drawable ?: return
        val scale = min(boardImage.width.toFloat() / d.intrinsicWidth, boardImage.height.toFloat() / d.intrinsicHeight)
        val dw = d.intrinsicWidth * scale; val dh = d.intrinsicHeight * scale
        boardOffsetX = (boardImage.width - dw) / 2; boardOffsetY = (boardImage.height - dh) / 2
        cellW = dw / 15f; cellH = dh / 15f
    }

    private fun spawnAllTokensInitial(playerCount: Int) {
        if (cellW <= 0) return
        tokenOverlay.removeAllViews(); allTokenViews.forEach { it.clear() }
        val players = viewModel.players.value ?: return
        val tokensPerPlayer = players.get(0).tokenCount
        val res = listOf(R.drawable.red_token, R.drawable.green_token, R.drawable.blue_token, R.drawable.yellow_token)
        for (i in 0 until playerCount) {
            for (tIdx in 0 until tokensPerPlayer) {
                val t = ImageView(this).apply {
                    setImageResource(res.get(i)); layoutParams = FrameLayout.LayoutParams((cellW * 0.8f).toInt(), (cellW * 0.8f).toInt())
                    setOnClickListener { if (viewModel.activePlayerIndex.value == i) viewModel.onTokenClicked(tIdx) }
                }
                allTokenViews.get(i).add(t); tokenOverlay.addView(t)
                val baseCoord = LudoBoardConfig.getBasePreciseCoord(i, tIdx)
                moveViewToPrecise(t, baseCoord.first, baseCoord.second)
            }
        }
    }

    private fun moveViewToGrid(v: View, c: Int, r: Int) = moveViewToPrecise(v, c + 0.5f, r + 0.5f)
    private fun moveViewToPrecise(v: View, cx: Float, cy: Float) { v.x = boardOffsetX + (cx * cellW) - (v.layoutParams.width / 2); v.y = boardOffsetY + (cy * cellH) - (v.layoutParams.width / 2) }
    private fun getBaseCoord(p: Int, t: Int) = LudoBoardConfig.getBasePreciseCoord(p, t)
    private fun updateDiceImage(v: ImageView, d: Int) = v.setImageResource(when(d) { 1 -> R.drawable.dice_1; 2 -> R.drawable.dice_2; 3 -> R.drawable.dice_3; 4 -> R.drawable.dice_4; 5 -> R.drawable.dice_5; else -> R.drawable.dice_6 })

    private fun addBadge(t: View, c: Int) {
        val b = TextView(this).apply { text = c.toString(); setTextColor(Color.WHITE); setBackgroundResource(R.drawable.bg_badge_circle); gravity = Gravity.CENTER; textSize = 10f; val s = (cellW * 0.4f).toInt(); layoutParams = FrameLayout.LayoutParams(s, s); x = t.x + (t.width / 2); y = t.y - (s / 4) }
        tokenOverlay.addView(b); activeBadges.add(b)
    }

    private fun clearBadges() { activeBadges.forEach { tokenOverlay.removeView(it) }; activeBadges.clear() }

    private fun showGameOverDialog() {
        AlertDialog.Builder(this)
            .setTitle("GAME OVER")
            .setMessage("All winners have taken their spots!")
            .setCancelable(false)
            .setPositiveButton("EXIT TO HUB") { _, _ -> viewModel.quitGame(); finish() }
            .show()
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
        soundManager.pauseLudoMusic()
        stopSetupVideos()
        val state = viewModel.gameState.value
        if (state != LudoViewModel.State.SETUP_THEME && state != LudoViewModel.State.SETUP_PLAYERS && state != LudoViewModel.State.SETUP_TOKENS && state != LudoViewModel.State.GAME_OVER) {
            viewModel.saveCurrentState()
        }
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.gameState.value ?: return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTheme = prefs.getInt(KEY_THEME, R.drawable.ludo_board)

        if (state == LudoViewModel.State.WAITING_FOR_ROLL || state == LudoViewModel.State.WAITING_FOR_MOVE || state == LudoViewModel.State.ANIMATING) {
            if (currentTheme == R.drawable.ludo_board_neon) {
                soundManager.resumeLudoMusic()
            } else if (currentTheme == R.drawable.ludo_board) {
                soundManager.resumeMusic()
            }
        } else if (state == LudoViewModel.State.SETUP_THEME || state == LudoViewModel.State.SETUP_PLAYERS || state == LudoViewModel.State.SETUP_TOKENS) {
            playRandomSetupVideos()
            soundManager.resumeMusic()
        }
    }
}