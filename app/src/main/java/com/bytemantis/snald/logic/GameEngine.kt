package com.bytemantis.snald.logic

import com.bytemantis.snald.model.Player

class GameEngine {

    fun calculateMove(player: Player, diceValue: Int): MoveResult {
        var newPosition = player.currentPosition + diceValue

        // Rule: Must land exactly on 100
        if (newPosition > BoardConfig.BOARD_SIZE) {
            return MoveResult.Stay(player.currentPosition, "Overshot!")
        }

        // Win Condition
        if (newPosition == BoardConfig.BOARD_SIZE) {
            player.currentPosition = 100
            player.isWinner = true
            return MoveResult.Win
        }

        // 1. Check for Star (Immunity Pickup)
        if (BoardConfig.STARS.contains(newPosition)) {
            player.currentPosition = newPosition
            player.hasStar = true
            return MoveResult.StarCollected(newPosition)
        }

        // 2. Check for Snakes (Danger)
        if (BoardConfig.SNAKES.containsKey(newPosition)) {
            val snakeTail = BoardConfig.SNAKES[newPosition]!!

            if (player.hasStar) {
                // CONSUME STAR - Block the bite!
                player.hasStar = false
                player.currentPosition = newPosition // Stay safe at head
                return MoveResult.StarUsed(newPosition, "Star Shield Used!")
            } else {
                // OUCH - Go down
                player.currentPosition = snakeTail
                return MoveResult.SnakeBite(newPosition, snakeTail)
            }
        }

        // 3. Check for Ladders (Boost)
        if (BoardConfig.LADDERS.containsKey(newPosition)) {
            val ladderTop = BoardConfig.LADDERS[newPosition]!!
            player.currentPosition = ladderTop
            return MoveResult.LadderClimb(newPosition, ladderTop)
        }

        // 4. Normal Move
        player.currentPosition = newPosition
        return MoveResult.NormalMove(newPosition)
    }

    // Tells the UI what to draw/animate
    sealed class MoveResult {
        data class NormalMove(val to: Int) : MoveResult()
        data class SnakeBite(val head: Int, val tail: Int) : MoveResult()
        data class LadderClimb(val bottom: Int, val top: Int) : MoveResult()
        data class StarCollected(val at: Int) : MoveResult()
        data class StarUsed(val at: Int, val msg: String) : MoveResult()
        data class Stay(val at: Int, val reason: String) : MoveResult()
        object Win : MoveResult()
    }
}