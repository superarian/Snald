package com.bytemantis.snald.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bytemantis.snald.R
import com.bytemantis.snald.logic.BoardConfig
import com.bytemantis.snald.model.Player

class BoardAdapter : RecyclerView.Adapter<BoardAdapter.SquareViewHolder>() {

    private var playerList: List<Player> = emptyList()

    fun updatePlayers(players: List<Player>) {
        this.playerList = players
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SquareViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_square, parent, false)
        // Ensure square is perfect grid size
        view.layoutParams.height = parent.measuredWidth / 10
        return SquareViewHolder(view)
    }

    override fun getItemCount(): Int = BoardConfig.BOARD_SIZE

    override fun onBindViewHolder(holder: SquareViewHolder, position: Int) {
        // Calculate Zig-Zag Position
        val row = position / 10
        val col = position % 10
        val bottomUpRow = 9 - row
        val squareNumber = if (bottomUpRow % 2 == 0) {
            (bottomUpRow * 10) + col + 1
        } else {
            (bottomUpRow * 10) + (10 - col)
        }

        holder.bind(squareNumber, playerList)
    }

    class SquareViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textNumber: TextView = itemView.findViewById(R.id.text_square_number)

        // Single Token View
        private val tokenCenter: ImageView = itemView.findViewById(R.id.token_center)

        // Multi Token Views
        private val smallTokens = listOf<ImageView>(
            itemView.findViewById(R.id.token_1),
            itemView.findViewById(R.id.token_2),
            itemView.findViewById(R.id.token_3),
            itemView.findViewById(R.id.token_4)
        )

        fun bind(number: Int, players: List<Player>) {
            textNumber.visibility = View.GONE

            // RESET ALL VISIBILITY
            tokenCenter.visibility = View.GONE
            smallTokens.forEach { it.visibility = View.GONE; it.clearAnimation() }

            // 1. Players on this square
            val playersHere = players.filter { it.currentPosition == number && !it.isFinished }

            // 2. Pac-Men on this square
            val pacmenHere = players.filter { it.pacmanPosition == number }

            // LOGIC: If a Pac-Man is here, we must show it.
            // If both Player and Pac-Man are here, we might need clustered view.

            val totalItems = playersHere + pacmenHere

            if (totalItems.isEmpty()) return

            if (totalItems.size == 1) {
                // Single Item Mode
                val item = totalItems[0]
                tokenCenter.visibility = View.VISIBLE
                tokenCenter.setColorFilter(item.color)

                // Distinguish Pac-Man from Player visually
                if (pacmenHere.contains(item)) {
                    // It's a Pac-Man! Rotate it or Scale it
                    tokenCenter.rotation = 180f // Upside down logic?
                    tokenCenter.scaleX = 0.7f   // Make it look meaner/smaller?
                } else {
                    // Normal Player
                    tokenCenter.rotation = 0f
                    tokenCenter.scaleX = 1.0f

                    if (item.hasShield) {
                        val pulse = AnimationUtils.loadAnimation(itemView.context, R.anim.aura_pulse)
                        tokenCenter.startAnimation(pulse)
                    }
                }
            } else {
                // Multi Mode (Cluster)
                for (i in totalItems.indices) {
                    if (i < smallTokens.size) {
                        val tokenView = smallTokens[i]
                        val item = totalItems[i]

                        tokenView.visibility = View.VISIBLE
                        tokenView.setColorFilter(item.color)

                        if (pacmenHere.contains(item)) {
                            // Visual cue for Pac-Man in small view
                            tokenView.rotation = 180f
                        } else {
                            tokenView.rotation = 0f
                            if (item.hasShield) {
                                val pulse = AnimationUtils.loadAnimation(itemView.context, R.anim.aura_pulse)
                                tokenView.startAnimation(pulse)
                            }
                        }
                    }
                }
            }
        }
    }
}