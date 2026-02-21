package com.bytemantis.snald.ludogame

import android.content.Context
import android.graphics.Color
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
    private lateinit var neonOverlay: LottieAnimationView // NEW
    private lateinit var statusText: TextView
    private lateinit var tokenOverlay: FrameLayout
    private lateinit var victoryPopText: TextView
    private lateinit var setupLayout: LinearLayout
    private lateinit var setupTitle: TextView
    private lateinit var groupTheme: LinearLayout
    private lateinit var groupPlayers: LinearLayout
    private lateinit var groupTokens: LinearLayout

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
                if (state != LudoViewModel.State.SETUP_THEME &&
                    state != LudoViewModel.State.SETUP_PLAYERS &&
                    state != LudoViewModel.State.SETUP_TOKENS &&
                    state != LudoViewModel.State.GAME_OVER) {
                    viewModel.saveCurrentState()
                }
                finish()
            }
        })
    }

    private fun setupUI() {
        boardImage = findViewById(R.id.img_ludo_board)
        neonOverlay = findViewById(R.id.anim_ludo_neon_overlay) // NEW
        statusText = findViewById(R.id.text_ludo_status)
        tokenOverlay = findViewById(R.id.overlay_ludo_tokens)
        victoryPopText = findViewById(R.id.text_ludo_victory_pop)
        setupLayout = findViewById(R.id.layout_ludo_setup)
        setupTitle = findViewById(R.id.text_setup_title)
        groupTheme = findViewById(R.id.group_setup_theme)
        groupPlayers = findViewById(R.id.group_setup_players)
        groupTokens = findViewById(R.id.group_setup_tokens)

        // Load Persistent Theme & apply Lottie states
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

        diceViews = mapOf(
            1 to findViewById(R.id.dice_p1), 2 to findViewById(R.id.dice_p2),
            3 to findViewById(R.id.dice_p3), 4 to findViewById(R.id.dice_p4)
        )
        playerLayouts = mapOf(
            3 to findViewById(R.id.layout_ludo_p3), 4 to findViewById(R.id.layout_ludo_p4)
        )
        progressBars = mapOf(
            0 to findViewById(R.id.progress_p1), 1 to findViewById(R.id.progress_p2),
            2 to findViewById(R.id.progress_p3), 3 to findViewById(R.id.progress_p4)
        )

        // Theme Setup Listeners
        findViewById<Button>(R.id.btn_theme_classic).setOnClickListener { applyAndSaveTheme(R.drawable.ludo_board) }
        findViewById<Button>(R.id.btn_theme_wood).setOnClickListener { applyAndSaveTheme(R.drawable.ludo_board_wood) }
        findViewById<Button>(R.id.btn_theme_neon).setOnClickListener { applyAndSaveTheme(R.drawable.ludo_board_neon) }

        // Existing Setup Listeners
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

    private fun applyAndSaveTheme(themeResId: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_THEME, themeResId).apply()
        boardImage.setImageResource(themeResId)

        // Handle Neon Animation overlay
        if (themeResId == R.drawable.ludo_board_neon) {
            neonOverlay.visibility = View.VISIBLE
            neonOverlay.playAnimation()
        } else {
            neonOverlay.visibility = View.GONE
            neonOverlay.cancelAnimation()
        }

        viewModel.selectTheme()
    }

    private fun setupObservers() {
        viewModel.gameState.observe(this) { state ->
            when(state) {
                LudoViewModel.State.SETUP_THEME -> {
                    setupLayout.visibility = View.VISIBLE
                    groupTheme.visibility = View.VISIBLE
                    groupPlayers.visibility = View.GONE
                    groupTokens.visibility = View.GONE
                    setupTitle.text = "SELECT BOARD"
                }
                LudoViewModel.State.SETUP_PLAYERS -> {
                    setupLayout.visibility = View.VISIBLE
                    groupTheme.visibility = View.GONE
                    groupPlayers.visibility = View.VISIBLE
                    groupTokens.visibility = View.GONE
                    setupTitle.text = "LUDO MATCH"
                }
                LudoViewModel.State.SETUP_TOKENS -> {
                    setupLayout.visibility = View.VISIBLE
                    groupTheme.visibility = View.GONE
                    groupPlayers.visibility = View.GONE
                    groupTokens.visibility = View.VISIBLE
                    setupTitle.text = "GAME LENGTH"
                }
                LudoViewModel.State.GAME_OVER -> {
                    setupLayout.visibility = View.GONE
                    showGameOverDialog()
                }
                else -> setupLayout.visibility = View.GONE
            }
        }

        viewModel.players.observe(this) { players ->
            if (players.isNotEmpty()) {
                playerLayouts.get(3)?.visibility = if (players.size >= 3) View.VISIBLE else View.GONE
                playerLayouts.get(4)?.visibility = if (players.size >= 4) View.VISIBLE else View.GONE
                progressBars.get(2)?.visibility = if (players.size >= 3) View.VISIBLE else View.GONE
                progressBars.get(3)?.visibility = if (players.size >= 4) View.VISIBLE else View.GONE

                if (cellW > 0 && !isUiInitialized) {
                    spawnAllTokensInitial(players.size)
                    isUiInitialized = true
                }
                if (isUiInitialized) renderBoardState()
            }
        }

        viewModel.statusMessage.observe(this) { statusText.text = it }

        viewModel.announcement.observe(this) { ann ->
            if (ann != null) {
                showDynamicAnnouncement(ann)
                viewModel.clearAnnouncement()
            }
        }

        viewModel.turnUpdate.observe(this) { if (it != null && isUiInitialized) playTurnSequence(it) }

        viewModel.diceValue.observe(this) { dice ->
            val dv = diceViews.get(viewModel.activePlayerIndex.value!! + 1)
            if (dv != null) {
                soundManager.playDiceRoll()
                updateDiceImage(dv, dice)
                dv.animate().rotationBy(360f).setDuration(300).start()
            }
        }

        viewModel.activePlayerIndex.observe(this) { idx ->
            diceViews.forEach { (id, v) ->
                v.alpha = if (id == idx + 1) 1f else 0.5f
                v.scaleX = if (id == idx + 1) 1.2f else 1f
                v.scaleY = if (id == idx + 1) 1.2f else 1f
            }
        }
    }

    private fun showDynamicAnnouncement(ann: LudoViewModel.Announcement) {
        victoryPopText.text = ann.message
        victoryPopText.visibility = View.VISIBLE
        victoryPopText.alpha = 0f

        if (ann.type == LudoViewModel.AnnouncementType.PLAYER_VICTORY) {
            soundManager.playWin()
            victoryPopText.animate().alpha(1f).scaleX(1.2f).scaleY(1.2f).setDuration(500).withEndAction {
                lifecycleScope.launch {
                    delay(3000)
                    victoryPopText.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(500).withEndAction {
                        victoryPopText.visibility = View.GONE
                    }.start()
                }
            }.start()
        } else {
            soundManager.playSafeZone()
            victoryPopText.animate().alpha(1f).setDuration(300).withEndAction {
                lifecycleScope.launch {
                    delay(1200)
                    victoryPopText.animate().alpha(0f).setDuration(300).withEndAction {
                        victoryPopText.visibility = View.GONE
                    }.start()
                }
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
            else -> {}
        }
        if (u.killInfo != null) {
            val victim = allTokenViews.get(u.killInfo.victimPlayerIdx).get(u.killInfo.victimTokenIdx)
            val base = getBaseCoord(u.killInfo.victimPlayerIdx, u.killInfo.victimTokenIdx)
            victim.animate().x(boardOffsetX + base.first * cellW - victim.width/2)
                .y(boardOffsetY + base.second * cellH - victim.height/2)
                .setDuration(500).withEndAction {
                    renderBoardState(); viewModel.onTurnAnimationsFinished()
                }.start()
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
            if (tokens.size == 1) {
                val v = allTokenViews.get(tokens.get(0).first).get(tokens.get(0).second)
                v.visibility = View.VISIBLE; v.scaleX = 1f; v.scaleY = 1f; moveViewToGrid(v, c.first, c.second)
            } else {
                val p1 = tokens.get(0).first
                if (tokens.all { it.first == p1 }) {
                    val v = allTokenViews.get(p1).get(tokens.get(0).second)
                    v.visibility = View.VISIBLE; moveViewToGrid(v, c.first, c.second)
                    for (i in 1 until tokens.size) allTokenViews.get(tokens.get(i).first).get(tokens.get(i).second).visibility = View.GONE
                    addBadge(v, tokens.size)
                } else {
                    tokens.forEachIndexed { i, (p, t) ->
                        val v = allTokenViews.get(p).get(t); v.visibility = View.VISIBLE; v.scaleX = 0.6f; v.scaleY = 0.6f
                        val ox = if (i % 2 == 0) -0.25f else 0.25f; val oy = if (i < 2) -0.25f else 0.25f
                        moveViewToPrecise(v, c.first + 0.5f + ox, c.second + 0.5f + oy)
                    }
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
            val base = when(i) {
                0 -> LudoBoardConfig.RED_BASE_PRECISE
                1 -> LudoBoardConfig.GREEN_BASE_PRECISE
                2 -> LudoBoardConfig.BLUE_BASE_PRECISE
                else -> LudoBoardConfig.YELLOW_BASE_PRECISE
            }
            for (tIdx in 0 until tokensPerPlayer) {
                val t = ImageView(this).apply {
                    setImageResource(res.get(i)); layoutParams = FrameLayout.LayoutParams((cellW * 0.8f).toInt(), (cellW * 0.8f).toInt())
                    setOnClickListener { if (viewModel.activePlayerIndex.value == i) viewModel.onTokenClicked(tIdx) }
                }
                allTokenViews.get(i).add(t); tokenOverlay.addView(t); moveViewToPrecise(t, base.get(tIdx).first, base.get(tIdx).second)
            }
        }
    }

    private fun moveViewToGrid(v: View, c: Int, r: Int) = moveViewToPrecise(v, c + 0.5f, r + 0.5f)
    private fun moveViewToPrecise(v: View, cx: Float, cy: Float) {
        v.x = boardOffsetX + (cx * cellW) - (v.layoutParams.width / 2)
        v.y = boardOffsetY + (cy * cellH) - (v.layoutParams.width / 2)
    }
    private fun getBaseCoord(p: Int, t: Int) = when(p) {
        0 -> LudoBoardConfig.RED_BASE_PRECISE.get(t)
        1 -> LudoBoardConfig.GREEN_BASE_PRECISE.get(t)
        2 -> LudoBoardConfig.BLUE_BASE_PRECISE.get(t)
        else -> LudoBoardConfig.YELLOW_BASE_PRECISE.get(t)
    }
    private fun updateDiceImage(v: ImageView, d: Int) = v.setImageResource(when(d) {
        1 -> R.drawable.dice_1; 2 -> R.drawable.dice_2; 3 -> R.drawable.dice_3; 4 -> R.drawable.dice_4; 5 -> R.drawable.dice_5; else -> R.drawable.dice_6
    })
    private fun addBadge(t: View, c: Int) {
        val b = TextView(this).apply {
            text = c.toString(); setTextColor(Color.WHITE); setBackgroundResource(R.drawable.bg_badge_circle); gravity = Gravity.CENTER; textSize = 10f
            val s = (cellW * 0.4f).toInt(); layoutParams = FrameLayout.LayoutParams(s, s); x = t.x + (t.width / 2); y = t.y - (s / 4)
        }
        tokenOverlay.addView(b); activeBadges.add(b)
    }
    private fun clearBadges() { activeBadges.forEach { tokenOverlay.removeView(it) }; activeBadges.clear() }

    private fun showGameOverDialog() {
        AlertDialog.Builder(this)
            .setTitle("GAME OVER")
            .setMessage("All winners have taken their spots!")
            .setCancelable(false)
            .setPositiveButton("EXIT TO HUB") { _, _ ->
                viewModel.quitGame()
                finish()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
        val state = viewModel.gameState.value
        if (state != LudoViewModel.State.SETUP_THEME &&
            state != LudoViewModel.State.SETUP_PLAYERS &&
            state != LudoViewModel.State.SETUP_TOKENS &&
            state != LudoViewModel.State.GAME_OVER) {
            viewModel.saveCurrentState()
        }
    }
}