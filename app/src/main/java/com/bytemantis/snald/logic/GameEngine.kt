package com.bytemantis.snald.logic

import com.bytemantis.snald.model.Player

class GameEngine {

    fun calculateMove(player: Player, diceValue: Int): MoveResult {
        var newPosition = player.currentPosition + diceValue

        if (newPosition > BoardConfig.BOARD_SIZE) {
            return MoveResult.Stay(player.currentPosition, "Overshot!")
        }

        if (newPosition == BoardConfig.BOARD_SIZE) {
            // Player reached 100
            return MoveResult.Win(newPosition)
        }

        // 1. Check for Star
        if (BoardConfig.STARS.contains(newPosition)) {
            player.hasStar = true
            return MoveResult.StarCollected(newPosition)
        }

        // 2. Check for Snakes
        if (BoardConfig.SNAKES.containsKey(newPosition)) {
            val snakeTail = BoardConfig.SNAKES[newPosition]!!
            if (player.hasStar) {
                player.hasStar = false // Consume Star
                return MoveResult.StarUsed(newPosition, "Star Shield Used!")
            } else {
                return MoveResult.SnakeBite(newPosition, snakeTail)
            }
        }

        // 3. Check for Ladders
        if (BoardConfig.LADDERS.containsKey(newPosition)) {
            val ladderTop = BoardConfig.LADDERS[newPosition]!!
            return MoveResult.LadderClimb(newPosition, ladderTop)
        }

        return MoveResult.NormalMove(newPosition)
    }

    // NEW: Check if current player lands on enemies
    fun checkCollisions(activePlayer: Player, allPlayers: List<Player>): Player? {
        // Find any OTHER player on the same square (except at Start 1 or Goal 100)
        if (activePlayer.currentPosition == 1 || activePlayer.currentPosition == 100) return null

        for (enemy in allPlayers) {
            if (enemy.id != activePlayer.id && !enemy.isFinished && enemy.currentPosition == activePlayer.currentPosition) {
                return enemy // This enemy is in danger!
            }
        }
        return null
    }

    sealed class MoveResult {
        data class NormalMove(val to: Int) : MoveResult()
        data class SnakeBite(val head: Int, val tail: Int) : MoveResult()
        data class LadderClimb(val bottom: Int, val top: Int) : MoveResult()
        data class StarCollected(val at: Int) : MoveResult()
        data class StarUsed(val at: Int, val msg: String) : MoveResult()
        data class Stay(val at: Int, val reason: String) : MoveResult()
        data class Win(val at: Int) : MoveResult()
    }
}