package com.bytemantis.snald.ludogame

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

    // REMOVED: animManager (The source of the crash)

    private lateinit var boardImage: ImageView
    private lateinit var statusText: TextView
    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var tokenOverlay: FrameLayout
    private lateinit var victoryPopText: TextView
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
        // REMOVED: animManager initialization

        allTokenViews.clear()
        repeat(4) { allTokenViews.add(mutableListOf()) }

        setupUI()
        setupObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.saveCurrentState()
                finish()
            }
        })
    }

    private fun setupUI() {
        boardImage = findViewById(R.id.img_ludo_board)
        statusText = findViewById(R.id.text_ludo_status)
        tokenOverlay = findViewById(R.id.overlay_ludo_tokens)
        victoryPopText = findViewById(R.id.text_ludo_victory_pop)

        diceViews = mapOf(
            1 to findViewById(R.id.dice_p1),
            2 to findViewById(R.id.dice_p2),
            3 to findViewById(R.id.dice_p3),
            4 to findViewById(R.id.dice_p4)
        )

        progressBars = mapOf(
            0 to findViewById(R.id.progress_p1),
            1 to findViewById(R.id.progress_p2),
            2 to findViewById(R.id.progress_p3),
            3 to findViewById(R.id.progress_p4)
        )

        boardImage.setImageResource(R.drawable.ludo_board)

        boardImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                boardImage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                calculateBoardMetrics()
                spawnAllTokensInitial()
                renderBoardState()
            }
        })

        diceViews.forEach { (id, view) ->
            view.setOnClickListener {
                val activeIdx = viewModel.activePlayerIndex.value ?: 0
                if (viewModel.gameState.value == LudoViewModel.State.WAITING_FOR_ROLL &&
                    id == (activeIdx + 1)) {
                    viewModel.rollDice()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.statusMessage.observe(this) { statusText.text = it }

        viewModel.gameState.observe(this) { state ->
            if (state == LudoViewModel.State.GAME_OVER) {
                showGameOverDialog()
            }
        }

        viewModel.victoryAnnouncement.observe(this) { message ->
            if (message != null) showVictoryPopUp(message)
        }

        viewModel.turnUpdate.observe(this) { update ->
            if (update != null) {
                try {
                    playTurnSequence(update)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Safe Mode: ${e.message}", Toast.LENGTH_SHORT).show()
                    viewModel.onTurnAnimationsFinished()
                }
            }
        }

        viewModel.diceValue.observe(this) { dice ->
            val activeIndex = viewModel.activePlayerIndex.value ?: 0
            val diceView = diceViews[activeIndex + 1]
            if (diceView != null) {
                soundManager.playDiceRoll()
                updateDiceImage(diceView, dice)
                diceView.animate().rotationBy(360f).setDuration(300).start()
            }
        }

        viewModel.activePlayerIndex.observe(this) { index ->
            highlightActiveDice(index + 1)
        }
    }

    // --- MANUAL ANIMATION CONTROLLER (No External Manager) ---
    private fun playTurnSequence(update: LudoViewModel.TurnUpdate) {
        val playerIdx = update.playerIdx
        val tokenIdx = update.tokenIdx

        if (playerIdx !in 0..3 || tokenIdx !in 0..3) return

        val tokenView = allTokenViews[playerIdx][tokenIdx]
        if (tokenView.parent == null) {
            viewModel.onTurnAnimationsFinished()
            return
        }

        val currentPlayer = viewModel.players.value!![playerIdx]
        val currentPosIndex = currentPlayer.tokenPositions[tokenIdx]

        clearBadges()
        tokenView.visibility = View.VISIBLE
        tokenView.bringToFront()

        if (update.isSpawn) {
            // === MANUAL SPAWN ===
            soundManager.playStarCollect()
            val targetCoord = LudoBoardConfig.getGlobalCoord(playerIdx, 0)
            if (targetCoord != null) {
                moveViewToGrid(tokenView, targetCoord.first, targetCoord.second)
            }
            // Use Handler, NOT Coroutine
            tokenView.postDelayed({ finishTurnSequence(update) }, 450)
        } else {
            // === MANUAL MOVE ===
            val startPos = kotlin.math.max(0, currentPosIndex - update.visualSteps)
            runMoveLoop(tokenView, playerIdx, startPos, 1, update.visualSteps, update)
        }
    }

    // --- RECURSIVE MOVE LOOP ---
    private fun runMoveLoop(
        view: View, pIdx: Int, startPos: Int,
        currentStep: Int, totalSteps: Int,
        update: LudoViewModel.TurnUpdate
    ) {
        if (currentStep > totalSteps) {
            finishTurnSequence(update)
            return
        }

        val nextIndex = startPos + currentStep
        if (nextIndex > 57) {
            finishTurnSequence(update)
            return
        }

        val coord = LudoBoardConfig.getGlobalCoord(pIdx, nextIndex)
        if (coord != null) {
            soundManager.playHop() // Sound is back

            val targetX = boardOffsetX + (coord.first.toFloat() + 0.5f) * cellW - (view.width / 2)
            val targetY = boardOffsetY + (coord.second.toFloat() + 0.5f) * cellH - (view.height / 2)

            view.animate()
                .x(targetX)
                .y(targetY)
                .setDuration(150)
                .withEndAction {
                    runMoveLoop(view, pIdx, startPos, currentStep + 1, totalSteps, update)
                }
                .start()
        } else {
            // Skip invalid step
            runMoveLoop(view, pIdx, startPos, currentStep + 1, totalSteps, update)
        }
    }

    // --- FINISH SEQUENCE (With Manual Kill Animation) ---
    private fun finishTurnSequence(update: LudoViewModel.TurnUpdate) {
        // 1. Play Land Sound
        if (!update.isSpawn) {
            when (update.soundToPlay) {
                LudoViewModel.SoundType.SAFE -> soundManager.playSafeZone()
                LudoViewModel.SoundType.WIN -> soundManager.playWin()
                else -> {}
            }
        }

        // 2. Handle Kill (Manual Animation, No Coroutines)
        if (update.killInfo != null) {
            val k = update.killInfo
            if (k.victimPlayerIdx in 0..3 && k.victimTokenIdx in 0..3) {
                val victimView = allTokenViews[k.victimPlayerIdx][k.victimTokenIdx]
                val basePos = getBaseCoord(k.victimPlayerIdx, k.victimTokenIdx)

                // Calculate pixel target for base
                val targetX = boardOffsetX + (basePos.first * cellW)
                val targetY = boardOffsetY + (basePos.second * cellH) - (victimView.width / 2)

                soundManager.playSlideBack()
                victimView.bringToFront()

                // Manual Slide Animation
                victimView.animate()
                    .x(targetX)
                    .y(targetY)
                    .setDuration(500)
                    .withEndAction { finalizeTurn() }
                    .start()
                return // Wait for animation
            }
        }

        // 3. Finish
        finalizeTurn()
    }

    private fun finalizeTurn() {
        renderBoardState()
        viewModel.onTurnAnimationsFinished()
    }

    private fun renderBoardState() {
        val players = viewModel.players.value ?: return

        // Strategy Bars
        players.forEachIndexed { index, player ->
            var totalSteps = 0
            player.tokenPositions.forEach { pos ->
                if (pos > -1) totalSteps += pos
            }
            val percentage = (totalSteps / 228.0 * 100).toInt()
            progressBars[index]?.progress = percentage
        }

        clearBadges()
        val occupationMap = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()

        for (pIdx in players.indices) {
            val player = players[pIdx]
            for (tIdx in 0 until 4) {
                val pos = player.tokenPositions[tIdx]

                if (pos == -1) {
                    val basePos = getBaseCoord(pIdx, tIdx)
                    val view = allTokenViews[pIdx][tIdx]
                    moveViewToPrecise(view, basePos.first, basePos.second)
                    view.scaleX = 1.0f; view.scaleY = 1.0f; view.visibility = View.VISIBLE
                } else if (pos == 57) {
                    allTokenViews[pIdx][tIdx].visibility = View.GONE
                } else {
                    val coord = LudoBoardConfig.getGlobalCoord(pIdx, pos)
                    if (coord != null) {
                        if (!occupationMap.containsKey(coord)) occupationMap[coord] = mutableListOf()
                        occupationMap[coord]!!.add(Pair(pIdx, tIdx))
                    }
                }
            }
        }

        occupationMap.forEach { (coord, tokensHere) ->
            if (tokensHere.size == 1) {
                val (pIdx, tIdx) = tokensHere[0]
                val view = allTokenViews[pIdx][tIdx]
                view.visibility = View.VISIBLE
                view.scaleX = 1.0f; view.scaleY = 1.0f
                moveViewToGrid(view, coord.first, coord.second)
            } else {
                val firstPlayer = tokensHere[0].first
                val allSame = tokensHere.all { it.first == firstPlayer }

                if (allSame) {
                    val (pIdx, tIdx) = tokensHere[0]
                    val visibleView = allTokenViews[pIdx][tIdx]
                    visibleView.visibility = View.VISIBLE
                    visibleView.scaleX = 1.0f; visibleView.scaleY = 1.0f
                    moveViewToGrid(visibleView, coord.first, coord.second)
                    for (i in 1 until tokensHere.size) {
                        val (hideP, hideT) = tokensHere[i]
                        allTokenViews[hideP][hideT].visibility = View.GONE
                    }
                    addBadge(visibleView, tokensHere.size)
                } else {
                    val scale = 0.6f
                    val offsetStep = 0.25f
                    tokensHere.forEachIndexed { index, (pIdx, tIdx) ->
                        val view = allTokenViews[pIdx][tIdx]
                        view.visibility = View.VISIBLE
                        view.scaleX = scale; view.scaleY = scale
                        val offsetX = if (index % 2 == 0) -offsetStep else offsetStep
                        val offsetY = if (index < 2) -offsetStep else offsetStep
                        moveViewToPrecise(view, coord.first.toFloat() + 0.5f + offsetX, coord.second.toFloat() + 0.5f + offsetY)
                    }
                }
            }
        }
    }

    private fun addBadge(targetView: View, count: Int) {
        val badge = TextView(this)
        badge.text = count.toString()
        badge.setTextColor(Color.WHITE)
        badge.setBackgroundResource(R.drawable.bg_badge_circle)
        badge.gravity = Gravity.CENTER
        badge.textSize = 10f
        badge.elevation = 20f
        val size = (cellW * 0.4f).toInt()
        val params = FrameLayout.LayoutParams(size, size)
        badge.layoutParams = params
        badge.x = targetView.x + (targetView.width / 2)
        badge.y = targetView.y - (size / 4)
        tokenOverlay.addView(badge)
        activeBadges.add(badge)
    }

    private fun clearBadges() {
        activeBadges.forEach { tokenOverlay.removeView(it) }
        activeBadges.clear()
    }

    private fun calculateBoardMetrics() {
        val drawable = boardImage.drawable ?: return
        val imageW = drawable.intrinsicWidth.toFloat()
        val imageH = drawable.intrinsicHeight.toFloat()
        val viewW = boardImage.width.toFloat()
        val viewH = boardImage.height.toFloat()

        val scale = min(viewW / imageW, viewH / imageH)
        val displayedW = imageW * scale
        val displayedH = imageH * scale

        boardOffsetX = (viewW - displayedW) / 2
        boardOffsetY = (viewH - displayedH) / 2
        cellW = displayedW / 15f
        cellH = displayedH / 15f
    }

    private fun spawnAllTokensInitial() {
        tokenOverlay.removeAllViews()
        allTokenViews.forEach { it.clear() }
        spawnSet(0, LudoBoardConfig.RED_BASE_PRECISE, R.drawable.red_token)
        spawnSet(1, LudoBoardConfig.GREEN_BASE_PRECISE, R.drawable.green_token)
        spawnSet(2, LudoBoardConfig.BLUE_BASE_PRECISE, R.drawable.blue_token)
        spawnSet(3, LudoBoardConfig.YELLOW_BASE_PRECISE, R.drawable.yellow_token)
    }

    private fun spawnSet(playerIndex: Int, basePositions: List<Pair<Float, Float>>, resId: Int) {
        basePositions.forEachIndexed { tokenIndex, pos ->
            val token = spawnTokenImage(resId, pos)
            token.setOnClickListener {
                val activeIdx = viewModel.activePlayerIndex.value ?: -1
                if (activeIdx == playerIndex) {
                    viewModel.onTokenClicked(tokenIndex)
                }
            }
            allTokenViews[playerIndex].add(token)
        }
    }

    private fun spawnTokenImage(resId: Int, gridPos: Pair<Float, Float>): ImageView {
        val tokenView = ImageView(this)
        tokenView.setImageResource(resId)
        val tokenSize = (min(cellW, cellH) * 0.8f).toInt()
        val params = FrameLayout.LayoutParams(tokenSize, tokenSize)
        tokenView.layoutParams = params
        moveViewToPrecise(tokenView, gridPos.first, gridPos.second)
        tokenOverlay.addView(tokenView)
        return tokenView
    }

    private fun moveViewToGrid(view: View, col: Int, row: Int) {
        moveViewToPrecise(view, col.toFloat() + 0.5f, row.toFloat() + 0.5f)
    }

    private fun moveViewToPrecise(view: View, colCenter: Float, rowCenter: Float) {
        val tokenSize = view.layoutParams.width
        val globalX = boardOffsetX + (colCenter * cellW)
        val globalY = boardOffsetY + (rowCenter * cellH)
        view.x = globalX - (tokenSize / 2)
        view.y = globalY - (tokenSize / 2)
    }

    private fun getBaseCoord(pIdx: Int, tIdx: Int): Pair<Float, Float> {
        val list = when(pIdx) {
            0 -> LudoBoardConfig.RED_BASE_PRECISE
            1 -> LudoBoardConfig.GREEN_BASE_PRECISE
            2 -> LudoBoardConfig.BLUE_BASE_PRECISE
            else -> LudoBoardConfig.YELLOW_BASE_PRECISE
        }
        return list[tIdx]
    }

    private fun updateDiceImage(view: ImageView, dice: Int) {
        val resId = when (dice) {
            1 -> R.drawable.dice_1; 2 -> R.drawable.dice_2; 3 -> R.drawable.dice_3
            4 -> R.drawable.dice_4; 5 -> R.drawable.dice_5; else -> R.drawable.dice_6
        }
        view.setImageResource(resId)
    }

    private fun highlightActiveDice(activeId: Int) {
        diceViews.forEach { (id, view) ->
            if (id == activeId) {
                view.alpha = 1.0f
                view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).start()
            } else {
                view.alpha = 0.5f
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
            }
        }
    }

    private fun showVictoryPopUp(message: String) {
        soundManager.playWin()
        victoryPopText.text = message
        victoryPopText.alpha = 0f
        victoryPopText.scaleX = 0.5f; victoryPopText.scaleY = 0.5f
        victoryPopText.visibility = View.VISIBLE
        victoryPopText.animate().alpha(1f).scaleX(1.2f).scaleY(1.2f).setDuration(500)
            .withEndAction {
                victoryPopText.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                lifecycleScope.launch {
                    delay(3000)
                    victoryPopText.animate().alpha(0f).setDuration(500).withEndAction {
                        victoryPopText.visibility = View.GONE
                    }.start()
                }
            }.start()
    }

    private fun showGameOverDialog() {
        soundManager.playWin()
        val message = viewModel.statusMessage.value ?: "Game Over"
        AlertDialog.Builder(this)
            .setTitle("GAME OVER")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Back to Menu") { _, _ ->
                viewModel.quitGame()
                finish()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
        viewModel.saveCurrentState()
    }
}