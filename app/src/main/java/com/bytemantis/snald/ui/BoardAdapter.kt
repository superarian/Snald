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
        view.layoutParams.height = parent.measuredWidth / 10
        return SquareViewHolder(view)
    }

    override fun getItemCount(): Int = BoardConfig.BOARD_SIZE

    override fun onBindViewHolder(holder: SquareViewHolder, position: Int) {
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
        private val imagePlayer: ImageView = itemView.findViewById(R.id.image_player)

        fun bind(number: Int, players: List<Player>) {
            textNumber.visibility = View.GONE

            // Find if ANY player is on this square
            val playerOnSquare = players.find { it.currentPosition == number && !it.isFinished }

            if (playerOnSquare != null) {
                imagePlayer.visibility = View.VISIBLE

                // Set Color (Red, Blue, etc.)
                imagePlayer.setColorFilter(playerOnSquare.color)

                if (playerOnSquare.hasStar) {
                    val pulse = AnimationUtils.loadAnimation(itemView.context, R.anim.aura_pulse)
                    imagePlayer.startAnimation(pulse)
                } else {
                    imagePlayer.clearAnimation()
                }
            } else {
                imagePlayer.visibility = View.GONE
                imagePlayer.clearAnimation()
            }
        }
    }
}