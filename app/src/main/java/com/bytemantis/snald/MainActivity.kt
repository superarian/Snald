package com.bytemantis.snald

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
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

    // UI References
    private lateinit var imgDice: ImageView
    private lateinit var textStatus: TextView
    private lateinit var btnRoll: Button

    // Pop-Up Overlay References
    private lateinit var textOverlayPop: TextView
    private lateinit var imgOverlayPop: ImageView

    private var isAnimatingMove = false
    private var currentVisualPosition = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        soundManager = SoundManager(this)

        // 1. Setup Board
        val recycler = findViewById<RecyclerView>(R.id.recycler_board)
        recycler.layoutManager = GridLayoutManager(this, 10)
        recycler.adapter = adapter

        // 2. Setup Controls & Overlays
        btnRoll = findViewById(R.id.btn_roll)
        val textDice = findViewById<TextView>(R.id.text_dice_result)
        textStatus = findViewById(R.id.text_status)
        imgDice = findViewById(R.id.img_dice)

        textOverlayPop = findViewById(R.id.text_overlay_pop)
        imgOverlayPop = findViewById(R.id.img_overlay_pop)

        // 3. Observe Dice
        viewModel.diceValue.observe(this) { dice ->
            btnRoll.isEnabled = false
            textStatus.text = "Rolling..."
            soundManager.playDiceRoll()
            updateDiceImage(dice)

            imgDice.animate()
                .rotationBy(720f)
                .setDuration(500)
                .withEndAction {
                    textDice.text = "Rolled: $dice 🎲"
                    animateHoppingMovement(dice)
                }
                .start()
        }

        // 4. Observe Player
        viewModel.playerState.observe(this) { player ->
            if (!isAnimatingMove) {
                adapter.updatePlayerState(player)
                updateStatusText(player)
                currentVisualPosition = player.currentPosition
            }
        }

        btnRoll.setOnClickListener {
            if (!isAnimatingMove) {
                currentVisualPosition = viewModel.playerState.value?.currentPosition ?: 1
                isAnimatingMove = true
                viewModel.rollDice()
            }
        }
    }

    private fun updateDiceImage(dice: Int) {
        val resId = when (dice) {
            1 -> R.drawable.dice_1
            2 -> R.drawable.dice_2
            3 -> R.drawable.dice_3
            4 -> R.drawable.dice_4
            5 -> R.drawable.dice_5
            else -> R.drawable.dice_6
        }
        imgDice.setImageResource(resId)
    }

    private fun updateStatusText(player: Player) {
        if (player.hasStar) {
            textStatus.text = "Player 1 (IMMUNE!) ⭐"
        } else {
            textStatus.text = "Player 1's Turn"
        }
    }

    private fun animateHoppingMovement(stepsToMove: Int) {
        lifecycleScope.launch {
            val player = viewModel.playerState.value ?: return@launch
            val moveResult = viewModel.lastMoveResult.value

            if (moveResult is GameEngine.MoveResult.Stay) {
                Toast.makeText(this@MainActivity, "🚫 Overshot!", Toast.LENGTH_SHORT).show()
                delay(500)
                isAnimatingMove = false
                btnRoll.isEnabled = true
                adapter.updatePlayerState(player)
                return@launch
            }

            // Hop Loop
            for (i in 1..stepsToMove) {
                currentVisualPosition++
                val tempPlayer = player.copy(currentPosition = currentVisualPosition)
                adapter.updatePlayerState(tempPlayer)
                soundManager.playHop()
                delay(200)
            }

            // Logic Handling with NEW POP ANIMATIONS
            delay(200)
            handleMoveResult(moveResult)

            // Final Sync
            isAnimatingMove = false
            adapter.updatePlayerState(player)
            updateStatusText(player)
            btnRoll.isEnabled = true
        }
    }

    private fun handleMoveResult(result: GameEngine.MoveResult?) {
        if (result == null) return

        when (result) {
            is GameEngine.MoveResult.SnakeBite -> {
                soundManager.playSnakeBite()
                // POP: Snake Image + Fade
                triggerPopImage(R.drawable.img_snake_pop)
            }
            is GameEngine.MoveResult.LadderClimb -> {
                soundManager.playLadderClimb()
                // POP: "YAY!" + Zoom
                triggerPopText("YAY!", 0xFF4CAF50.toInt(), R.anim.pop_zoom_fade)

                // Simulating "Smooth" Ladder Climb by updating visual position
                // (Note: For true xy-translation we need more complex View code,
                // this ensures it snaps cleanly after the YAY)
            }
            is GameEngine.MoveResult.StarCollected -> {
                soundManager.playStarCollect()
                // POP: "POWER!" + Flash 3x
                triggerPopText("POWER!", 0xFFFFD700.toInt(), R.anim.pop_flash_3x)
            }
            is GameEngine.MoveResult.StarUsed -> {
                soundManager.playStarUsed()
                // POP: Devil/Smiley Image + Fade
                triggerPopImage(R.drawable.img_devil_smile)
            }
            is GameEngine.MoveResult.Win -> {
                soundManager.playWin()
                // POP: "YOU WIN" + Zoom
                triggerPopText("YOU WIN!", 0xFFFFD700.toInt(), R.anim.pop_zoom_fade)
            }
            else -> {}
        }
    }

    // --- NEW POP-UP HELPERS ---

    private fun triggerPopText(text: String, color: Int, animRes: Int) {
        textOverlayPop.text = text
        textOverlayPop.setTextColor(color)
        textOverlayPop.visibility = View.VISIBLE

        val anim = AnimationUtils.loadAnimation(this, animRes)
        // Hide again when animation ends
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

        // Always use the fade animation for images as requested
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