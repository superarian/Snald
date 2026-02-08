package com.bytemantis.snald.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bytemantis.snald.R
import com.bytemantis.snald.logic.BoardConfig
import com.bytemantis.snald.model.Player

class BoardAdapter : RecyclerView.Adapter<BoardAdapter.SquareViewHolder>() {

    private var currentPlayer: Player? = null

    fun updatePlayerState(player: Player) {
        this.currentPlayer = player
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SquareViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_square, parent, false)

        // 🛠️ OWNER FIX: Force every square to be exactly 1/10th of the board width.
        // This prevents the "grid gaps" that make the token look misaligned.
        view.layoutParams.height = parent.measuredWidth / 10

        return SquareViewHolder(view)
    }

    override fun getItemCount(): Int = BoardConfig.BOARD_SIZE

    override fun onBindViewHolder(holder: SquareViewHolder, position: Int) {
        // 1. Calculate row from bottom (0 to 9)
        val row = position / 10
        val col = position % 10
        val bottomUpRow = 9 - row

        // 2. Standard Zig-Zag Logic
        // Even rows (0, 2, 4...): Left to Right
        // Odd rows (1, 3, 5...): Right to Left
        val squareNumber = if (bottomUpRow % 2 == 0) {
            (bottomUpRow * 10) + col + 1
        } else {
            (bottomUpRow * 10) + (10 - col)
        }

        holder.bind(squareNumber, currentPlayer)
    }

    class SquareViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textNumber: TextView = itemView.findViewById(R.id.text_square_number)
        private val imagePlayer: ImageView = itemView.findViewById(R.id.image_player)

        fun bind(number: Int, player: Player?) {
            // Hide debug numbers (since your background has them)
            textNumber.visibility = View.GONE

            if (player != null && player.currentPosition == number) {
                imagePlayer.visibility = View.VISIBLE
                if (player.hasStar) {
                    imagePlayer.setColorFilter(0xFFFFD700.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                } else {
                    imagePlayer.clearColorFilter()
                }
            } else {
                imagePlayer.visibility = View.GONE
            }
        }
    }
}