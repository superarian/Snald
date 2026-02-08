package com.bytemantis.snald

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bytemantis.snald.logic.GameEngine
import com.bytemantis.snald.ui.BoardAdapter
import com.bytemantis.snald.ui.GameViewModel
import com.bytemantis.snald.ui.SoundManager

class MainActivity : AppCompatActivity() {

    private val viewModel: GameViewModel by viewModels()
    private val adapter = BoardAdapter()
    private lateinit var soundManager: SoundManager
    private lateinit var imgDice: ImageView
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        soundManager = SoundManager(this)

        // 1. Setup Board
        val recycler = findViewById<RecyclerView>(R.id.recycler_board)
        recycler.layoutManager = GridLayoutManager(this, 10)
        recycler.adapter = adapter

        // 2. Setup Controls
        val btnRoll = findViewById<Button>(R.id.btn_roll)
        val textDice = findViewById<TextView>(R.id.text_dice_result)
        textStatus = findViewById(R.id.text_status)
        imgDice = findViewById(R.id.img_dice)

        // 3. Observe Dice Value (THE MASTER CONTROLLER)
        viewModel.diceValue.observe(this) { dice ->
            // A. Lock UI & Start Feedback
            btnRoll.isEnabled = false
            textStatus.text = "Rolling..."

            // NEW: Play Dice Sound immediately
            soundManager.playDiceRoll()

            // B. Animate Dice (Spin 2x)
            imgDice.animate()
                .rotationBy(720f)
                .setDuration(500)
                .withEndAction {
                    // C. Animation Done: Show Result
                    textDice.text = "Rolled: $dice 🎲"
                    val resId = when (dice) {
                        1 -> R.drawable.dice_1
                        2 -> R.drawable.dice_2
                        3 -> R.drawable.dice_3
                        4 -> R.drawable.dice_4
                        5 -> R.drawable.dice_5
                        else -> R.drawable.dice_6
                    }
                    imgDice.setImageResource(resId)

                    // D. NOW Trigger the Logic Result (Sound/Toast)
                    // We fetch the *current* result from the ViewModel manually
                    val result = viewModel.lastMoveResult.value
                    handleMoveResult(result)

                    // E. Unlock UI
                    btnRoll.isEnabled = true
                }
                .start()
        }

        // 4. Observe Player (Just for Board Update)
        viewModel.playerState.observe(this) { player ->
            adapter.updatePlayerState(player)

            // Update status text only if NOT rolling (avoid overwriting "Rolling...")
            if (btnRoll.isEnabled) {
                if (player.hasStar) {
                    textStatus.text = "Player 1 (IMMUNE!) ⭐"
                } else {
                    textStatus.text = "Player 1's Turn"
                }
            }
        }

        // NOTE: We REMOVED the separate lastMoveResult observer.
        // It is now handled inside the dice animation end action.

        btnRoll.setOnClickListener {
            viewModel.rollDice()
        }
    }

    // Helper to handle sounds and toasts AFTER animation
    private fun handleMoveResult(result: GameEngine.MoveResult?) {
        if (result == null) return

        when (result) {
            is GameEngine.MoveResult.SnakeBite -> {
                soundManager.playSnakeBite()
                Toast.makeText(this, "🐍 Ouch! Bit by snake!", Toast.LENGTH_SHORT).show()
            }
            is GameEngine.MoveResult.LadderClimb -> {
                soundManager.playLadderClimb()
                Toast.makeText(this, "🪜 Yahoo! Ladder!", Toast.LENGTH_SHORT).show()
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