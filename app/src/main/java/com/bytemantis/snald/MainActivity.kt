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

    // Connect to the ViewModel
    private val viewModel: GameViewModel by viewModels()

    // Connect to the Adapter
    private val adapter = BoardAdapter()
    private lateinit var soundManager: SoundManager
    private lateinit var imgDice: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        soundManager = SoundManager(this)

        // 1. Setup the Board Grid
        val recycler = findViewById<RecyclerView>(R.id.recycler_board)
        recycler.layoutManager = GridLayoutManager(this, 10) // 10 columns
        recycler.adapter = adapter

        // 2. Setup Buttons & Text
        val btnRoll = findViewById<Button>(R.id.btn_roll)
        val textDice = findViewById<TextView>(R.id.text_dice_result)
        val textStatus = findViewById<TextView>(R.id.text_status)
        imgDice = findViewById(R.id.img_dice)

        // 3. Observe the "Dice" with Animation
        viewModel.diceValue.observe(this) { dice ->
            // A. Disable button so they can't spam click while rolling
            btnRoll.isEnabled = false
            textStatus.text = "Rolling..."

            // B. The Animation (Spin 360 degrees fast)
            imgDice.animate()
                .rotationBy(360f * 2) // Spin 2 times
                .setDuration(500)     // Take 0.5 seconds
                .withEndAction {
                    // C. After spinning, SHOW the result
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

                    // Re-enable button
                    btnRoll.isEnabled = true
                }
                .start()
        }

        // 4. Observe the "Player" (Move the piece!)
        viewModel.playerState.observe(this) { player ->
            adapter.updatePlayerState(player)

            if (player.hasStar) {
                textStatus.text = "Player 1 (IMMUNE!) ⭐"
            } else {
                textStatus.text = "Player 1's Turn"
            }
        }

        // 5. Observe Messages (Snakes, Ladders, Wins)
        viewModel.lastMoveResult.observe(this) { result ->
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
                    // NEW: Play the Star Shield Sound
                    soundManager.playStarUsed()
                    Toast.makeText(this, "🛡️ STAR SAVED YOU!", Toast.LENGTH_LONG).show()
                }
                is GameEngine.MoveResult.Win -> {
                    // NEW: Play the Win Sound
                    soundManager.playWin()
                    Toast.makeText(this, "🏆 YOU WIN!", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        // 6. Connect Button Click
        btnRoll.setOnClickListener {
            viewModel.rollDice()
        }
    }
}