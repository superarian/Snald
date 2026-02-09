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

            // 1. Find ALL players on this square (who haven't finished)
            val playersHere = players.filter { it.currentPosition == number && !it.isFinished }

            // Reset all visibilities first
            tokenCenter.visibility = View.GONE
            smallTokens.forEach {
                it.visibility = View.GONE
                it.clearAnimation()
            }

            if (playersHere.isEmpty()) {
                return // Nothing to show
            }

            // 2. Decide Layout Mode
            if (playersHere.size == 1) {
                // --- SINGLE MODE ---
                val p = playersHere[0]
                tokenCenter.visibility = View.VISIBLE
                tokenCenter.setColorFilter(p.color)

                // Star Pulse
                if (p.hasStar) {
                    val pulse = AnimationUtils.loadAnimation(itemView.context, R.anim.aura_pulse)
                    tokenCenter.startAnimation(pulse)
                }
            } else {
                // --- MULTI/CLUSTER MODE ---
                // Loop through players present and assign them to corner slots
                for (i in playersHere.indices) {
                    if (i < smallTokens.size) {
                        val tokenView = smallTokens[i]
                        val p = playersHere[i]

                        tokenView.visibility = View.VISIBLE
                        tokenView.setColorFilter(p.color)

                        // Star Pulse for specific small token
                        if (p.hasStar) {
                            val pulse = AnimationUtils.loadAnimation(itemView.context, R.anim.aura_pulse)
                            tokenView.startAnimation(pulse)
                        }
                    }
                }
            }
        }
    }
}