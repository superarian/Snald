package com.bytemantis.snald.ludogame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bytemantis.snald.R
import com.bytemantis.snald.core.SoundManager
import kotlin.math.min

class LudoActivity : AppCompatActivity() {

    private val viewModel: LudoViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    private lateinit var boardImage: ImageView
    private lateinit var statusText: TextView
    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var tokenOverlay: FrameLayout

    // Store visual tokens: Map<PlayerIndex, List<ImageView>>
    // Red=0, Green=1, Yellow=2, Blue=3
    private val allTokenViews = mutableListOf<MutableList<ImageView>>()

    // Calibration vars
    private var cellW = 0f
    private var cellH = 0f
    private var boardOffsetX = 0f
    private var boardOffsetY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ludo)
        soundManager = SoundManager(this)

        // Initialize List of Lists (4 Players, empty lists initially)
        repeat(4) { allTokenViews.add(mutableListOf()) }

        setupUI()
        setupObservers()
        setupBackPressLogic()
    }

    private fun setupUI() {
        boardImage = findViewById(R.id.img_ludo_board)
        statusText = findViewById(R.id.text_ludo_status)
        tokenOverlay = findViewById(R.id.overlay_ludo_tokens)

        diceViews = mapOf(
            1 to findViewById(R.id.dice_p1),
            2 to findViewById(R.id.dice_p2),
            3 to findViewById(R.id.dice_p3),
            4 to findViewById(R.id.dice_p4)
        )

        boardImage.setImageResource(R.drawable.ludo_board)

        boardImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                boardImage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                calculateBoardMetrics() // Do math once
                spawnAllTokens()        // Then spawn
            }
        })

        diceViews.forEach { (id, view) ->
            view.setOnClickListener {
                val activeIndex = viewModel.activePlayerIndex.value ?: 0
                if (id == (activeIndex + 1)) {
                    viewModel.rollDice()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.statusMessage.observe(this) { statusText.text = it }

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

        // --- THE MOVEMENT ANIMATION HANDLER ---
        viewModel.moveEvent.observe(this) { event ->
            if (event != null) {
                val (playerIdx, tokenIdx, steps) = event
                animateTokenMove(playerIdx, tokenIdx, steps)
            }
        }
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

    private fun spawnAllTokens() {
        tokenOverlay.removeAllViews()
        allTokenViews.forEach { it.clear() }

        // Spawn logic using indices (Red=0, Green=1, Yellow=2, Blue=3)
        spawnSet(0, LudoBoardConfig.RED_BASE_PRECISE, R.drawable.red_token)
        spawnSet(1, LudoBoardConfig.GREEN_BASE_PRECISE, R.drawable.green_token)
        spawnSet(2, LudoBoardConfig.YELLOW_BASE_PRECISE, R.drawable.yellow_token)
        spawnSet(3, LudoBoardConfig.BLUE_BASE_PRECISE, R.drawable.blue_token)
    }

    private fun spawnSet(playerIndex: Int, basePositions: List<Pair<Float, Float>>, resId: Int) {
        basePositions.forEachIndexed { tokenIndex, pos ->
            val token = spawnTokenImage(resId, pos)

            // --- CLICK LISTENER ---
            token.setOnClickListener {
                val activeIdx = viewModel.activePlayerIndex.value ?: -1
                // Only allow clicking your own tokens
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

        // Initial placement
        moveViewToGrid(tokenView, gridPos.first, gridPos.second)
        tokenOverlay.addView(tokenView)
        return tokenView
    }

    private fun moveViewToGrid(view: ImageView, col: Float, row: Float) {
        val tokenSize = view.layoutParams.width
        val centerPadX = (cellW - tokenSize) / 2
        val centerPadY = (cellH - tokenSize) / 2
        view.x = boardOffsetX + (col * cellW) + centerPadX
        view.y = boardOffsetY + (row * cellH) + centerPadY
    }

    private fun animateTokenMove(playerIdx: Int, tokenIdx: Int, steps: Int) {
        val tokenView = allTokenViews[playerIdx][tokenIdx]
        val player = viewModel.players.value!![playerIdx]
        val currentPosIndex = player.tokenPositions[tokenIdx]
        // Note: ViewModel updated the position ALREADY, so 'currentPosIndex' is the DESTINATION.
        // We need to calculate where it *was*.
        // Actually, easiest way: Just animate to the new destination.

        // Get the coordinate list for this player
        val path: List<Pair<Int, Int>> = when(playerIdx) {
            0 -> LudoBoardConfig.PATH_RED
            1 -> LudoBoardConfig.PATH_GREEN
            2 -> LudoBoardConfig.PATH_YELLOW
            else -> LudoBoardConfig.PATH_BLUE
        }

        // Logic: If pos is 0, we are at Start. If pos is 56, we are Home.
        val destCoord = if (currentPosIndex == -1) {
            // Should not happen if we just moved, but fallback to base
            when(playerIdx) {
                0 -> LudoBoardConfig.RED_BASE_PRECISE[tokenIdx]
                1 -> LudoBoardConfig.GREEN_BASE_PRECISE[tokenIdx]
                2 -> LudoBoardConfig.YELLOW_BASE_PRECISE[tokenIdx]
                else -> LudoBoardConfig.BLUE_BASE_PRECISE[tokenIdx]
            }
        } else {
            // Convert Int grid coordinates to Float for our mover
            val intPair = path[currentPosIndex] // Index 0..56
            Pair(intPair.first.toFloat(), intPair.second.toFloat())
        }

        // ANIMATE!
        tokenView.animate()
            .x(boardOffsetX + (destCoord.first * cellW) + (cellW - tokenView.width)/2)
            .y(boardOffsetY + (destCoord.second * cellH) + (cellH - tokenView.height)/2)
            .setDuration(500)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    soundManager.playHop()
                    viewModel.onAnimationFinished()
                }
            })
            .start()
    }

    // --- Helpers ---
    private fun highlightActiveDice(activeId: Int) {
        diceViews.forEach { (id, view) ->
            view.alpha = if (id == activeId) 1.0f else 0.5f
            if (id == activeId) view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).start()
            else view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
        }
    }

    private fun updateDiceImage(view: ImageView, dice: Int) {
        val resId = when (dice) {
            1 -> R.drawable.dice_1
            2 -> R.drawable.dice_2
            3 -> R.drawable.dice_3
            4 -> R.drawable.dice_4
            5 -> R.drawable.dice_5
            else -> R.drawable.dice_6
        }
        view.setImageResource(resId)
    }

    private fun setupBackPressLogic() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
    }
}