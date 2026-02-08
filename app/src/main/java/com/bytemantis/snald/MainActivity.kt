package com.bytemantis.snald

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

class MainActivity : AppCompatActivity() {

    private val viewModel: GameViewModel by viewModels()
    private val adapter = BoardAdapter()
    private lateinit var soundManager: SoundManager

    // Dice Views Map
    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var statusText: TextView

    // Overlays
    private lateinit var textOverlayPop: TextView
    private lateinit var imgOverlayPop: ImageView
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
        val recycler = findViewById<RecyclerView>(R.id.recycler_board)
        recycler.layoutManager = GridLayoutManager(this, 10)
        recycler.adapter = adapter

        diceViews = mapOf(
            1 to findViewById(R.id.dice_p1),
            2 to findViewById(R.id.dice_p2),
            3 to findViewById(R.id.dice_p3),
            4 to findViewById(R.id.dice_p4)
        )

        statusText = findViewById(R.id.text_main_status)
        textOverlayPop = findViewById(R.id.text_overlay_pop)
        imgOverlayPop = findViewById(R.id.img_overlay_pop)
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
                if (!isAnimatingMove && viewModel.activePlayerId.value == id) {
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
        // 1. Dice Roll
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

        // 2. Turn Change - FIXED: Removed isAnimatingMove check so text updates instantly
        viewModel.activePlayerId.observe(this) { id ->
            updateStatusText(id)
            highlightActiveDice(id)
        }

        // 3. Collision (Kill)
        viewModel.collisionEvent.observe(this) { killedPlayer ->
            if (killedPlayer != null) {
                soundManager.playSnakeBite()
                triggerPopImage(R.drawable.img_snake_pop)
                Toast.makeText(this, "P${killedPlayer.id} KILLED! Back to Start!", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Game Over
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

    private fun updateStatusText(playerId: Int) {
        statusText.text = "Player $playerId's Turn"
        val color = when(playerId) {
            1 -> 0xFFFF0000.toInt() // Red
            2 -> 0xFF0000FF.toInt() // Blue
            3 -> 0xFF00FF00.toInt() // Green
            4 -> 0xFFFFFF00.toInt() // Yellow
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

            // 1. Check Overshot
            if (moveResult is GameEngine.MoveResult.Stay) {
                Toast.makeText(this@MainActivity, "🚫 Overshot!", Toast.LENGTH_SHORT).show()
                delay(500)
                isAnimatingMove = false
                viewModel.updatePositionAndNextTurn(activePlayer.currentPosition)
                return@launch
            }

            // 2. Hop Loop
            for (i in 1..stepsToMove) {
                currentVisualPosition++
                activePlayer.currentPosition = currentVisualPosition
                adapter.updatePlayers(players)
                soundManager.playHop()
                delay(200)
            }

            delay(300)
            handleMoveResult(moveResult)

            // 3. Logic: Check for Snakes/Ladders Destination
            var finalPos = currentVisualPosition
            if (moveResult is GameEngine.MoveResult.SnakeBite) finalPos = moveResult.tail
            if (moveResult is GameEngine.MoveResult.LadderClimb) finalPos = moveResult.top

            // 4. FIX: Visually move the token THERE first, BEFORE ending turn
            if (finalPos != currentVisualPosition) {
                activePlayer.currentPosition = finalPos
                adapter.updatePlayers(players)
                delay(500)
            }

            // 5. Update and Unlock
            // NOTE: We unlock the animation flag BEFORE telling ViewModel to switch turns,
            // so the Observer can trigger the UI update correctly.
            isAnimatingMove = false
            viewModel.updatePositionAndNextTurn(finalPos)
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
                soundManager.playStarCollect() // Uses new fade function
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

    private fun triggerPopText(text: String, color: Int, animRes: Int) {
        textOverlayPop.text = text
        textOverlayPop.setTextColor(color)
        textOverlayPop.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, animRes)
        anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                textOverlayPop.visibility = View.GONE
            }
        })
        textOverlayPop.startAnimation(anim)
    }

    private fun triggerPopImage(drawableRes: Int) {
        imgOverlayPop.setImageResource(drawableRes)
        imgOverlayPop.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, R.anim.pop_image_fade)
        anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                imgOverlayPop.visibility = View.GONE
            }
        })
        imgOverlayPop.startAnimation(anim)
    }
}