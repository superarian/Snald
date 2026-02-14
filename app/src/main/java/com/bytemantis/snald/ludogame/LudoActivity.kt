package com.bytemantis.snald.ludogame

import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bytemantis.snald.R
import com.bytemantis.snald.core.SoundManager
import kotlin.math.min

class LudoActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager
    private lateinit var boardImage: ImageView
    private lateinit var statusText: TextView
    private lateinit var diceViews: Map<Int, ImageView>
    private lateinit var tokenOverlay: FrameLayout

    // Store tokens to move them later: Map<Color, List<ImageView>>
    private val tokenViews = mutableMapOf<String, MutableList<ImageView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ludo)

        soundManager = SoundManager(this)

        // Initialize Map
        tokenViews["RED"] = mutableListOf()
        tokenViews["GREEN"] = mutableListOf()
        tokenViews["YELLOW"] = mutableListOf()
        tokenViews["BLUE"] = mutableListOf()

        setupUI()
        setupBackPressLogic()
    }

    private fun setupUI() {
        boardImage = findViewById(R.id.img_ludo_board)
        statusText = findViewById(R.id.text_ludo_status)
        tokenOverlay = findViewById(R.id.overlay_ludo_tokens)

        // Map the dice
        diceViews = mapOf(
            1 to findViewById(R.id.dice_p1),
            2 to findViewById(R.id.dice_p2),
            3 to findViewById(R.id.dice_p3),
            4 to findViewById(R.id.dice_p4)
        )

        // Set the board image
        boardImage.setImageResource(R.drawable.ludo_board) // Ensure this matches your file name

        // WAIT for the layout to measure the board before placing tokens
        boardImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                boardImage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                placeTokensOnBoard()
            }
        })
    }

    private fun placeTokensOnBoard() {
        val boardWidth = boardImage.width
        val boardHeight = boardImage.height

        // The board is 15x15. Calculate single cell size.
        val cellW = boardWidth / 15f
        val cellH = boardHeight / 15f

        // Clear any existing tokens (in case of restart)
        tokenOverlay.removeAllViews()
        tokenViews.values.forEach { it.clear() }

        // --- SPAWN RED TOKENS ---
        LudoBoardConfig.RED_BASE.forEach { pos ->
            val token = spawnToken(R.drawable.red_token, pos, cellW, cellH)
            tokenViews["RED"]?.add(token)
        }

        // --- SPAWN GREEN TOKENS ---
        LudoBoardConfig.GREEN_BASE.forEach { pos ->
            val token = spawnToken(R.drawable.green_token, pos, cellW, cellH)
            tokenViews["GREEN"]?.add(token)
        }

        // --- SPAWN YELLOW TOKENS ---
        LudoBoardConfig.YELLOW_BASE.forEach { pos ->
            val token = spawnToken(R.drawable.yellow_token, pos, cellW, cellH) // Ensure filename matches
            tokenViews["YELLOW"]?.add(token)
        }

        // --- SPAWN BLUE TOKENS ---
        LudoBoardConfig.BLUE_BASE.forEach { pos ->
            val token = spawnToken(R.drawable.blue_token, pos, cellW, cellH)
            tokenViews["BLUE"]?.add(token)
        }

        statusText.text = "Board Ready!"
    }

    private fun spawnToken(resId: Int, gridPos: Pair<Int, Int>, cellW: Float, cellH: Float): ImageView {
        val tokenView = ImageView(this)
        tokenView.setImageResource(resId)

        // Scale Token: 80% of the cell size so it doesn't touch edges
        val tokenSize = (min(cellW, cellH) * 0.8f).toInt()
        val params = FrameLayout.LayoutParams(tokenSize, tokenSize)
        tokenView.layoutParams = params

        // Calculate Pixel Position (Center the token in the cell)
        val col = gridPos.first
        val row = gridPos.second

        // X = Column * Width + Padding to Center
        val padX = (cellW - tokenSize) / 2
        val padY = (cellH - tokenSize) / 2

        tokenView.x = (col * cellW) + padX
        tokenView.y = (row * cellH) + padY

        // Add to the Overlay Container
        tokenOverlay.addView(tokenView)
        return tokenView
    }

    private fun setupBackPressLogic() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish() // Return to Hub
            }
        })
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
    }
}