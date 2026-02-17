package com.bytemantis.snald.ludogame

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bytemantis.snald.R
import com.bytemantis.snald.core.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

class LudoActivity : AppCompatActivity() {

    private val viewModel: LudoViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    private lateinit var boardImage: ImageView
    private lateinit var statusText: TextView
    private lateinit var tokenOverlay: FrameLayout
    private lateinit var victoryPopText: TextView
    private lateinit var setupLayout: LinearLayout

    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var playerLayouts: Map<Int, View>
    private lateinit var progressBars: Map<Int, ProgressBar>

    private val allTokenViews = mutableListOf<MutableList<ImageView>>()
    private val activeBadges = mutableListOf<TextView>()

    private var cellW = 0f
    private var cellH = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ludo)
        soundManager = SoundManager(this)

        repeat(4) { allTokenViews.add(mutableListOf()) }

        setupUI()
        setupObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.gameState.value != LudoViewModel.State.SETUP) viewModel.saveCurrentState()
                finish()
            }
        })
    }

    private fun setupUI() {
        boardImage = findViewById(R.id.img_ludo_board)
        statusText = findViewById(R.id.text_ludo_status)
        tokenOverlay = findViewById(R.id.overlay_ludo_tokens)
        victoryPopText = findViewById(R.id.text_ludo_victory_pop)
        setupLayout = findViewById(R.id.layout_ludo_setup)

        boardImage.setImageResource(R.drawable.ludo_board)

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

        findViewById<Button>(R.id.btn_ludo_2p).setOnClickListener { viewModel.startGame(2) }
        findViewById<Button>(R.id.btn_ludo_3p).setOnClickListener { viewModel.startGame(3) }
        findViewById<Button>(R.id.btn_ludo_4p).setOnClickListener { viewModel.startGame(4) }

        boardImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (boardImage.width > 0) {
                    boardImage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    calculateBoardMetrics()
                    // FIX: Re-trigger spawning if players were already loaded
                    viewModel.players.value?.let { players ->
                        if (players.isNotEmpty() && allTokenViews[0].isEmpty()) {
                            spawnAllTokensInitial(players.size)
                            renderBoardState()
                        }
                    }
                }
            }
        })

        diceViews.forEach { (id, view) ->
            view.setOnClickListener {
                val currentIdx = viewModel.activePlayerIndex.value ?: 0
                if (viewModel.gameState.value == LudoViewModel.State.WAITING_FOR_ROLL && id == (currentIdx + 1)) {
                    viewModel.rollDice()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.gameState.observe(this) { state ->
            setupLayout.visibility = if (state == LudoViewModel.State.SETUP) View.VISIBLE else View.GONE
            if (state == LudoViewModel.State.GAME_OVER) showGameOverDialog()
        }

        viewModel.players.observe(this) { players ->
            if (players.isNotEmpty()) {
                playerLayouts[3]?.visibility = if (players.size >= 3) View.VISIBLE else View.GONE
                playerLayouts[4]?.visibility = if (players.size >= 4) View.VISIBLE else View.GONE
                progressBars[2]?.visibility = if (players.size >= 3) View.VISIBLE else View.GONE
                progressBars[3]?.visibility = if (players.size >= 4) View.VISIBLE else View.GONE

                // FIX: Only spawn if metrics are ready
                if (cellW > 0 && allTokenViews[0].isEmpty()) {
                    spawnAllTokensInitial(players.size)
                }

                // FIX: Only render if tokens were spawned to prevent crash
                if (allTokenViews[0].isNotEmpty()) {
                    renderBoardState()
                }
            }
        }

        viewModel.statusMessage.observe(this) { statusText.text = it }
        viewModel.victoryAnnouncement.observe(this) { if (it != null) showVictoryPopUp(it) }
        viewModel.turnUpdate.observe(this) { if (it != null) playTurnSequence(it) }

        viewModel.diceValue.observe(this) { dice ->
            val dv = diceViews[viewModel.activePlayerIndex.value!! + 1]
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

    private fun playTurnSequence(u: LudoViewModel.TurnUpdate) {
        if (allTokenViews[0].isEmpty()) return // Safety guard
        val view = allTokenViews[u.playerIdx][u.tokenIdx]
        view.visibility = View.VISIBLE
        view.bringToFront()

        if (u.isSpawn) {
            soundManager.playStarCollect()
            val coord = LudoBoardConfig.getGlobalCoord(u.playerIdx, 0)
            if (coord != null) moveViewToGrid(view, coord.first, coord.second)
            view.postDelayed({ finishTurnSequence(u) }, 450)
        } else {
            val players = viewModel.players.value!!
            val start = kotlin.math.max(0, players[u.playerIdx].tokenPositions[u.tokenIdx] - u.visualSteps)
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
            val victim = allTokenViews[u.killInfo.victimPlayerIdx][u.killInfo.victimTokenIdx]
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
        if (allTokenViews[0].isEmpty()) return // FIX: Crash prevention

        clearBadges()
        val occMap = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()

        players.forEachIndexed { pIdx, player ->
            progressBars[pIdx]?.progress = (player.tokenPositions.filter { it > -1 }.sumOf { it } / 228.0 * 100).toInt()
            for (tIdx in 0 until 4) {
                val pos = player.tokenPositions[tIdx]
                val v = allTokenViews[pIdx][tIdx]
                if (pos == -1) {
                    val b = getBaseCoord(pIdx, tIdx)
                    moveViewToPrecise(v, b.first, b.second)
                    v.scaleX = 1f; v.scaleY = 1f; v.visibility = View.VISIBLE
                } else if (pos == 57) v.visibility = View.GONE
                else {
                    val c = LudoBoardConfig.getGlobalCoord(pIdx, pos)
                    if (c != null) occMap.getOrPut(c) { mutableListOf() }.add(Pair(pIdx, tIdx))
                }
            }
        }

        occMap.forEach { (c, tokens) ->
            if (tokens.size == 1) {
                val v = allTokenViews[tokens[0].first][tokens[0].second]
                v.visibility = View.VISIBLE; v.scaleX = 1f; v.scaleY = 1f; moveViewToGrid(v, c.first, c.second)
            } else {
                val p1 = tokens[0].first
                if (tokens.all { it.first == p1 }) {
                    val v = allTokenViews[p1][tokens[0].second]
                    v.visibility = View.VISIBLE; moveViewToGrid(v, c.first, c.second)
                    for (i in 1 until tokens.size) allTokenViews[tokens[i].first][tokens[i].second].visibility = View.GONE
                    addBadge(v, tokens.size)
                } else {
                    tokens.forEachIndexed { i, (p, t) ->
                        val v = allTokenViews[p][t]; v.visibility = View.VISIBLE; v.scaleX = 0.6f; v.scaleY = 0.6f
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
        val dw = d.intrinsicWidth * scale
        val dh = d.intrinsicHeight * scale
        boardOffsetX = (boardImage.width - dw) / 2
        boardOffsetY = (boardImage.height - dh) / 2
        cellW = dw / 15f; cellH = dh / 15f
    }

    private fun spawnAllTokensInitial(count: Int) {
        if (cellW <= 0) return // Safety
        tokenOverlay.removeAllViews(); allTokenViews.forEach { it.clear() }
        val res = listOf(R.drawable.red_token, R.drawable.green_token, R.drawable.blue_token, R.drawable.yellow_token)
        for (i in 0 until count) {
            val base = when(i) { 0 -> LudoBoardConfig.RED_BASE_PRECISE; 1 -> LudoBoardConfig.GREEN_BASE_PRECISE; 2 -> LudoBoardConfig.BLUE_BASE_PRECISE; else -> LudoBoardConfig.YELLOW_BASE_PRECISE }
            base.forEachIndexed { tIdx, p ->
                val t = ImageView(this).apply {
                    setImageResource(res[i]); layoutParams = FrameLayout.LayoutParams((cellW * 0.8f).toInt(), (cellW * 0.8f).toInt())
                    setOnClickListener { if (viewModel.activePlayerIndex.value == i) viewModel.onTokenClicked(tIdx) }
                }
                allTokenViews[i].add(t); tokenOverlay.addView(t); moveViewToPrecise(t, p.first, p.second)
            }
        }
    }

    private fun moveViewToGrid(v: View, c: Int, r: Int) = moveViewToPrecise(v, c + 0.5f, r + 0.5f)
    private fun moveViewToPrecise(v: View, cx: Float, cy: Float) {
        val lp = v.layoutParams ?: return
        v.x = boardOffsetX + (cx * cellW) - (lp.width / 2)
        v.y = boardOffsetY + (cy * cellH) - (lp.width / 2)
    }
    private fun getBaseCoord(p: Int, t: Int) = when(p) { 0 -> LudoBoardConfig.RED_BASE_PRECISE[t]; 1 -> LudoBoardConfig.GREEN_BASE_PRECISE[t]; 2 -> LudoBoardConfig.BLUE_BASE_PRECISE[t]; else -> LudoBoardConfig.YELLOW_BASE_PRECISE[t] }
    private fun updateDiceImage(v: ImageView, d: Int) = v.setImageResource(when(d) { 1 -> R.drawable.dice_1; 2 -> R.drawable.dice_2; 3 -> R.drawable.dice_3; 4 -> R.drawable.dice_4; 5 -> R.drawable.dice_5; else -> R.drawable.dice_6 })
    private fun addBadge(t: View, c: Int) {
        val b = TextView(this).apply {
            text = c.toString(); setTextColor(Color.WHITE); setBackgroundResource(R.drawable.bg_badge_circle); gravity = Gravity.CENTER; textSize = 10f
            val s = (cellW * 0.4f).toInt(); layoutParams = FrameLayout.LayoutParams(s, s); x = t.x + (t.width / 2); y = t.y - (s / 4)
        }
        tokenOverlay.addView(b); activeBadges.add(b)
    }
    private fun clearBadges() { activeBadges.forEach { tokenOverlay.removeView(it) }; activeBadges.clear() }
    private fun showVictoryPopUp(m: String) {
        soundManager.playWin(); victoryPopText.text = m; victoryPopText.visibility = View.VISIBLE; victoryPopText.alpha = 0f
        victoryPopText.animate().alpha(1f).setDuration(500).withEndAction { lifecycleScope.launch { delay(3000); victoryPopText.animate().alpha(0f).withEndAction { victoryPopText.visibility = View.GONE } } }
    }
    private fun showGameOverDialog() {
        AlertDialog.Builder(this).setTitle("GAME OVER").setMessage(viewModel.statusMessage.value).setCancelable(false).setPositiveButton("Hub") { _, _ -> viewModel.quitGame(); finish() }.show()
    }
    override fun onPause() { super.onPause(); soundManager.pauseMusic(); if (viewModel.gameState.value != LudoViewModel.State.SETUP) viewModel.saveCurrentState() }
}