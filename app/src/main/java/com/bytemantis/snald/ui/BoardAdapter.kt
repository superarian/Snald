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

    private var currentPlayer: Player? = null

    fun updatePlayerState(player: Player) {
        this.currentPlayer = player
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SquareViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_square, parent, false)

        // OWNER FIX: Force square height to 1/10th of width for perfect grid alignment
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
            // Hide debug numbers
            textNumber.visibility = View.GONE

            if (player != null && player.currentPosition == number) {
                imagePlayer.visibility = View.VISIBLE

                if (player.hasStar) {
                    // 1. Turn Gold
                    imagePlayer.setColorFilter(0xFFFFD700.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)

                    // 2. NEW: Play "Aura Pulse" Animation
                    val pulse = AnimationUtils.loadAnimation(itemView.context, R.anim.aura_pulse)
                    imagePlayer.startAnimation(pulse)
                } else {
                    // Reset to normal
                    imagePlayer.clearColorFilter()
                    imagePlayer.clearAnimation()
                }
            } else {
                imagePlayer.visibility = View.GONE
                imagePlayer.clearAnimation()
            }
        }
    }
}