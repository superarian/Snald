package com.bytemantis.snald

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bytemantis.snald.logic.GameEngine
import com.bytemantis.snald.model.Player
import com.bytemantis.snald.ui.BoardAdapter
import com.bytemantis.snald.ui.GameViewModel
import com.bytemantis.snald.ui.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {

    private val viewModel: GameViewModel by viewModels()
    private val adapter = BoardAdapter()
    private lateinit var soundManager: SoundManager

    private lateinit var recyclerBoard: RecyclerView
    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var statusText: TextView

    // Overlays
    private lateinit var textOverlayPop: TextView
    private lateinit var imgOverlayPop: ImageView
    private lateinit var videoOverlayPop: VideoView
    private lateinit var floatingToken: ImageView
    private lateinit var setupLayout: LinearLayout
    private lateinit var gameOverLayout: LinearLayout

    private var isAnimatingMove = false
    private var currentVisualPosition = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        soundManager = SoundManager(this)

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        recyclerBoard = findViewById(R.id.recycler_board)
        recyclerBoard.layoutManager = GridLayoutManager(this, 10)
        recyclerBoard.adapter = adapter

        diceViews = mapOf(
            1 to findViewById(R.id.dice_p1),
            2 to findViewById(R.id.dice_p2),
            3 to findViewById(R.id.dice_p3),
            4 to findViewById(R.id.dice_p4)
        )

        statusText = findViewById(R.id.text_main_status)
        textOverlayPop = findViewById(R.id.text_overlay_pop)
        imgOverlayPop = findViewById(R.id.img_overlay_pop)
        videoOverlayPop = findViewById(R.id.video_overlay_pop)
        floatingToken = findViewById(R.id.floating_token)
        setupLayout = findViewById(R.id.layout_setup)
        gameOverLayout = findViewById(R.id.layout_game_over)

        findViewById<Button>(R.id.btn_2_players).setOnClickListener { startGame(2) }
        findViewById<Button>(R.id.btn_3_players).setOnClickListener { startGame(3) }
        findViewById<Button>(R.id.btn_4_players).setOnClickListener { startGame(4) }
        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            gameOverLayout.visibility = View.GONE
            setupLayout.visibility = View.VISIBLE
        }

        diceViews.forEach { (id, view) ->
            view.setOnClickListener {
                if (!isAnimatingMove && !viewModel.isAnimationLocked && viewModel.activePlayerId.value == id) {
                    val player = viewModel.players.value?.find { it.id == id } ?: return@setOnClickListener
                    currentVisualPosition = player.currentPosition
                    isAnimatingMove = true
                    viewModel.rollDiceForActivePlayer()
                }
            }
        }
    }

    private fun startGame(count: Int) {
        setupLayout.visibility = View.GONE
        findViewById<View>(R.id.layout_p3).visibility = if (count >= 3) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layout_p4).visibility = if (count >= 4) View.VISIBLE else View.GONE
        viewModel.startGame(count)
    }

    private fun setupObservers() {
        viewModel.players.observe(this) { players ->
            if (!isAnimatingMove && !viewModel.isAnimationLocked) {
                adapter.updatePlayers(players)
            }
        }

        viewModel.diceValue.observe(this) { dice ->
            val activeId = viewModel.activePlayerId.value ?: return@observe
            val activeDiceView = diceViews[activeId] ?: return@observe

            soundManager.playDiceRoll()
            updateDiceImage(activeDiceView, dice)
            resetDiceOpacity()
            activeDiceView.alpha = 1.0f

            activeDiceView.animate().rotationBy(720f).setDuration(500).withEndAction {
                animateHoppingMovement(dice)
            }.start()
        }

        viewModel.activePlayerId.observe(this) { id ->
            updateStatusText(id)
            highlightActiveDice(id)
        }

        viewModel.collisionEvent.observe(this) { event ->
            if (event != null) {
                val (killedPlayer, fatalPos) = event

                Toast.makeText(this, "P${killedPlayer.id} CRUSHED!", Toast.LENGTH_SHORT).show()

                // Play Video
                triggerPopVideo(R.raw.hammer_kill) {
                    // Video Done -> Start Slide
                    animateDeathSlideSmooth(killedPlayer, fatalPos)
                }
            }
        }

        viewModel.gameOver.observe(this) { isOver ->
            if (isOver) {
                soundManager.playWin()
                triggerPopText("GAME OVER", 0xFFFFFFFF.toInt(), R.anim.pop_zoom_fade)
                lifecycleScope.launch {
                    delay(2000)
                    gameOverLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    // --- FINAL UPDATED: 100ms SLOW SLIDE + STOP SFX ---
    private fun animateDeathSlideSmooth(player: Player, startPos: Int) {
        lifecycleScope.launch {

            // 1. Play Sound and KEEP the ID
            val streamId = soundManager.playSlideBack()

            // 2. Settings: 100ms delay (Very deliberate slow slide)
            val stepSize = 2
            val frameDelay = 100L

            var currentPos = startPos

            // 3. Loop
            while (currentPos > 1) {
                val oldPos = currentPos

                currentPos -= stepSize
                if (currentPos < 1) currentPos = 1

                player.currentPosition = currentPos

                val oldIndex = getAdapterPositionForSquare(oldPos)
                val newIndex = getAdapterPositionForSquare(currentPos)

                adapter.notifyItemChanged(oldIndex)
                adapter.notifyItemChanged(newIndex)

                delay(frameDelay)
            }

            // 4. STOP SOUND EXACTLY HERE
            soundManager.stop(streamId)

            // 5. Final Sync
            player.currentPosition = 1
            adapter.notifyDataSetChanged()

            // 6. Resume Game
            viewModel.finalizeKill(player.id)
        }
    }

    private fun updateStatusText(playerId: Int) {
        statusText.text = "Player $playerId's Turn"
        val color = when(playerId) {
            1 -> 0xFFFF0000.toInt()
            2 -> 0xFF0000FF.toInt()
            3 -> 0xFF00FF00.toInt()
            4 -> 0xFFFFFF00.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        statusText.setTextColor(color)
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

    private fun resetDiceOpacity() {
        diceViews.values.forEach { it.alpha = 0.5f }
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

    private fun animateHoppingMovement(stepsToMove: Int) {
        lifecycleScope.launch {
            val players = viewModel.players.value ?: return@launch
            val activeId = viewModel.activePlayerId.value ?: return@launch
            val activePlayer = players.find { it.id == activeId } ?: return@launch
            val moveResult = viewModel.lastMoveResult.value

            if (moveResult is GameEngine.MoveResult.Stay) {
                Toast.makeText(this@MainActivity, "🚫 Overshot!", Toast.LENGTH_SHORT).show()
                delay(500)
                isAnimatingMove = false
                viewModel.updatePositionAndNextTurn(activePlayer.currentPosition)
                return@launch
            }

            for (i in 1..stepsToMove) {
                currentVisualPosition++
                activePlayer.currentPosition = currentVisualPosition
                adapter.updatePlayers(players)
                soundManager.playHop()
                delay(200)
            }

            var finalPos = currentVisualPosition

            if (moveResult != null) {
                if (moveResult is GameEngine.MoveResult.SnakeBite || moveResult is GameEngine.MoveResult.LadderClimb) {
                    delay(300)
                    handleMoveResult(moveResult)

                    delay(1000)

                    if (moveResult is GameEngine.MoveResult.SnakeBite) finalPos = moveResult.tail
                    if (moveResult is GameEngine.MoveResult.LadderClimb) finalPos = moveResult.top

                    animateSlide(currentVisualPosition, finalPos, activePlayer.color)
                } else {
                    handleMoveResult(moveResult)

                    if (moveResult is GameEngine.MoveResult.StarUsed) {
                        delay(3600)
                    } else {
                        delay(500)
                    }
                }
            }

            activePlayer.currentPosition = finalPos
            adapter.updatePlayers(players)

            isAnimatingMove = false
            viewModel.updatePositionAndNextTurn(finalPos)
        }
    }

    private fun getAdapterPositionForSquare(squareNumber: Int): Int {
        if (squareNumber < 1 || squareNumber > 100) return 0
        val bottomUpRow = (squareNumber - 1) / 10
        val col = if (bottomUpRow % 2 == 0) {
            (squareNumber - 1) % 10
        } else {
            9 - ((squareNumber - 1) % 10)
        }
        val row = 9 - bottomUpRow
        return (row * 10) + col
    }

    private suspend fun animateSlide(startSquare: Int, endSquare: Int, color: Int) = suspendCancellableCoroutine<Unit> { continuation ->
        val startIndex = getAdapterPositionForSquare(startSquare)
        val endIndex = getAdapterPositionForSquare(endSquare)
        val startView = recyclerBoard.layoutManager?.findViewByPosition(startIndex)
        val endView = recyclerBoard.layoutManager?.findViewByPosition(endIndex)

        if (startView != null && endView != null) {
            val startCoords = IntArray(2)
            val endCoords = IntArray(2)
            val parentCoords = IntArray(2)

            startView.getLocationInWindow(startCoords)
            endView.getLocationInWindow(endCoords)
            findViewById<View>(R.id.overlay_container).getLocationInWindow(parentCoords)

            val startX = startCoords[0] - parentCoords[0]
            val startY = startCoords[1] - parentCoords[1]
            val endX = endCoords[0] - parentCoords[0]
            val endY = endCoords[1] - parentCoords[1]

            floatingToken.setColorFilter(color)
            floatingToken.translationX = startX.toFloat()
            floatingToken.translationY = startY.toFloat()
            floatingToken.visibility = View.VISIBLE

            floatingToken.animate()
                .translationX(endX.toFloat())
                .translationY(endY.toFloat())
                .setDuration(1200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        floatingToken.visibility = View.GONE
                        continuation.resume(Unit)
                    }
                })
                .start()
        } else {
            continuation.resume(Unit)
        }
    }

    private fun handleMoveResult(result: GameEngine.MoveResult?) {
        if (result == null) return
        when (result) {
            is GameEngine.MoveResult.SnakeBite -> {
                soundManager.playSnakeBite()
                triggerPopImage(R.drawable.img_snake_pop)
            }
            is GameEngine.MoveResult.LadderClimb -> {
                soundManager.playLadderClimb()
                triggerPopText("YAY!", 0xFF4CAF50.toInt(), R.anim.pop_zoom_fade)
            }
            is GameEngine.MoveResult.StarCollected -> {
                soundManager.playStarCollect()
                triggerPopText("POWER!", 0xFFFFD700.toInt(), R.anim.pop_flash_3x)
            }
            is GameEngine.MoveResult.StarUsed -> {
                soundManager.playStarUsed()
                triggerPopImage(R.drawable.img_devil_smile)
            }
            is GameEngine.MoveResult.Win -> {
                soundManager.playWin()
                triggerPopText("WINNER!", 0xFFFFD700.toInt(), R.anim.pop_zoom_fade)
            }
            else -> {}
        }
    }

    private fun triggerPopVideo(videoResId: Int, onComplete: () -> Unit) {
        try {
            val uri = Uri.parse("android.resource://$packageName/$videoResId")
            videoOverlayPop.setVideoURI(uri)
            videoOverlayPop.setZOrderOnTop(true)
            videoOverlayPop.visibility = View.VISIBLE

            videoOverlayPop.setOnCompletionListener {
                videoOverlayPop.visibility = View.GONE
                onComplete()
            }

            videoOverlayPop.start()
        } catch (e: Exception) {
            e.printStackTrace()
            videoOverlayPop.visibility = View.GONE
            onComplete()
        }
    }

    private fun triggerPopText(text: String, color: Int, animRes: Int) {
        textOverlayPop.text = text
        textOverlayPop.setTextColor(color)
        textOverlayPop.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, animRes)
        anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                textOverlayPop.visibility = View.GONE
            }
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        textOverlayPop.startAnimation(anim)
    }

    private fun triggerPopImage(drawableRes: Int) {
        imgOverlayPop.setImageResource(drawableRes)
        imgOverlayPop.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, R.anim.pop_image_fade)
        anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                imgOverlayPop.visibility = View.GONE
            }
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        imgOverlayPop.startAnimation(anim)
    }
}