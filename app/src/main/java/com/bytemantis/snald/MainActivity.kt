package com.bytemantis.snald

import android.os.Bundle
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
    private lateinit var imgDice: ImageView
    private lateinit var textStatus: TextView
    private lateinit var btnRoll: Button

    // FLAG: Prevents the Board from updating instantly when ViewModel changes
    private var isAnimatingMove = false
    private var currentVisualPosition = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        soundManager = SoundManager(this)

        val recycler = findViewById<RecyclerView>(R.id.recycler_board)
        recycler.layoutManager = GridLayoutManager(this, 10)
        recycler.adapter = adapter

        btnRoll = findViewById(R.id.btn_roll)
        val textDice = findViewById<TextView>(R.id.text_dice_result)
        textStatus = findViewById(R.id.text_status)
        imgDice = findViewById(R.id.img_dice)

        // 1. Observe Dice (Trigger Animation)
        viewModel.diceValue.observe(this) { dice ->
            btnRoll.isEnabled = false
            textStatus.text = "Rolling..."
            soundManager.playDiceRoll()

            // FIX 1: Set the image IMMEDIATELY so it spins *as* the correct number
            // This prevents the "snap/lag" effect at the end.
            updateDiceImage(dice)

            imgDice.animate()
                .rotationBy(720f) // Spin 2 full times
                .setDuration(500)
                .withEndAction {
                    textDice.text = "Rolled: $dice 🎲"

                    // START HOPPING SEQUENCE
                    animateHoppingMovement(dice)
                }
                .start()
        }

        // 2. Observe Player (Sync Board)
        viewModel.playerState.observe(this) { player ->
            // Only update board instantly if we are NOT in the middle of an animation sequence
            if (!isAnimatingMove) {
                adapter.updatePlayerState(player)
                updateStatusText(player)
                currentVisualPosition = player.currentPosition
            }
        }

        btnRoll.setOnClickListener {
            if (!isAnimatingMove) {
                // Capture start position before logic updates it
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

    // --- THE HOPPING LOGIC ---
    private fun animateHoppingMovement(stepsToMove: Int) {
        lifecycleScope.launch {
            val player = viewModel.playerState.value ?: return@launch
            val moveResult = viewModel.lastMoveResult.value

            // FIX 2: Check for OVERSHOOT before hopping.
            // If the engine says "Stay" (because 98 + 5 > 100), we STOP here.
            if (moveResult is GameEngine.MoveResult.Stay) {
                Toast.makeText(this@MainActivity, "🚫 Overshot! Need exact roll.", Toast.LENGTH_SHORT).show()
                delay(500) // Small pause so user sees why they didn't move

                // Reset Flags
                isAnimatingMove = false
                btnRoll.isEnabled = true
                adapter.updatePlayerState(player) // Ensure visual sync
                return@launch
            }

            // If valid move, start the Hop Loop
            for (i in 1..stepsToMove) {
                currentVisualPosition++

                // Create a temporary player for visual update
                val tempPlayer = player.copy(currentPosition = currentVisualPosition)
                adapter.updatePlayerState(tempPlayer)

                soundManager.playHop()

                // Delay for "Smooth but Quick" effect
                delay(200)
            }

            // We have finished walking. Now handle Snakes/Ladders/Stars

            // Short pause before Snake/Ladder action
            delay(300)
            handleMoveResult(moveResult)

            // 3. Final Sync & Unlock
            isAnimatingMove = false
            adapter.updatePlayerState(player) // Ensure we match ViewModel exactly
            updateStatusText(player)
            btnRoll.isEnabled = true
        }
    }

    private fun handleMoveResult(result: GameEngine.MoveResult?) {
        if (result == null) return

        when (result) {
            is GameEngine.MoveResult.SnakeBite -> {
                soundManager.playSnakeBite()
                Toast.makeText(this, "🐍 Ouch! Bit by snake!", Toast.LENGTH_SHORT).show()
                // The final sync will move the token to the tail
            }
            is GameEngine.MoveResult.LadderClimb -> {
                soundManager.playLadderClimb()
                Toast.makeText(this, "🪜 Yahoo! Ladder!", Toast.LENGTH_SHORT).show()
                // The final sync will move the token to the top
            }
            is GameEngine.MoveResult.StarCollected -> {
                soundManager.playStarCollect()
                Toast.makeText(this, "⭐ STAR POWER ACQUIRED!", Toast.LENGTH_SHORT).show()
            }
            is GameEngine.MoveResult.StarUsed -> {
                soundManager.playStarUsed()
                Toast.makeText(this, "🛡️ STAR SAVED YOU!", Toast.LENGTH_LONG).show()
            }
            is GameEngine.MoveResult.Win -> {
                soundManager.playWin()
                Toast.makeText(this, "🏆 YOU WIN!", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
}