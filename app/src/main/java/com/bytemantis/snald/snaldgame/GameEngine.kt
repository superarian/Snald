package com.bytemantis.snald.snaldgame

import com.bytemantis.snald.core.Player

class GameEngine {

    // Helper: Generate circular path (100 -> 1 -> 100)
    fun calculatePacmanPath(startPos: Int, steps: Int): List<Int> {
        val path = mutableListOf<Int>()
        var current = startPos

        for (i in 1..steps) {
            current--
            if (current < 1) {
                current = 100 // Loop back to top
            }
            path.add(current)
        }
        return path
    }

    fun calculateMove(player: Player, diceValue: Int): MoveResult {
        var newPosition = player.currentPosition + diceValue

        if (newPosition > BoardConfig.BOARD_SIZE) {
            return MoveResult.Stay(player.currentPosition, "Overshot!")
        }

        if (newPosition == BoardConfig.BOARD_SIZE) {
            return MoveResult.Win(newPosition)
        }

        // 1. Check for Star
        if (BoardConfig.STARS.contains(newPosition)) {
            player.starCount++

            // SINGLE PAC-MAN RULE:
            // If 2nd star collected, trigger Spawn/Steal immediately.
            if (player.starCount >= 2) {
                return MoveResult.PacmanSpawned(newPosition)
            }
            return MoveResult.StarCollected(newPosition)
        }

        // 2. Check for Snakes (Standard Player)
        if (BoardConfig.SNAKES.containsKey(newPosition)) {
            val snakeTail = BoardConfig.SNAKES[newPosition]!!
            if (player.hasShield) {
                player.starCount-- // Consume shield
                return MoveResult.StarUsed(newPosition, "Star Shield Used!")
            } else {
                return MoveResult.SnakeBite(newPosition, snakeTail)
            }
        }

        // 3. Check for Ladders (Standard Player)
        if (BoardConfig.LADDERS.containsKey(newPosition)) {
            val ladderTop = BoardConfig.LADDERS[newPosition]!!
            return MoveResult.LadderClimb(newPosition, ladderTop)
        }

        return MoveResult.NormalMove(newPosition)
    }

    // PAC-MAN MOVE LOGIC
    fun calculatePacmanMove(player: Player, diceValue: Int): PacmanResult {
        if (player.pacmanPosition == 0) return PacmanResult.NoMove

        val steps = diceValue * 2
        val path = calculatePacmanPath(player.pacmanPosition, steps)
        var finalPos = path.last()

        // CHECK VULNERABILITY
        // 1. Snake
        if (BoardConfig.SNAKES.containsKey(finalPos)) {
            val tail = BoardConfig.SNAKES[finalPos]!!
            return PacmanResult.Move(path, tail, PacmanEvent.SNAKE_BITE)
        }

        // 2. Ladder
        if (BoardConfig.LADDERS.containsKey(finalPos)) {
            val top = BoardConfig.LADDERS[finalPos]!!
            return PacmanResult.Move(path, top, PacmanEvent.LADDER_CLIMB)
        }

        return PacmanResult.Move(path, finalPos, PacmanEvent.NORMAL)
    }

    // Check collisions (Player vs Player)
    fun checkCollisions(activePlayer: Player, allPlayers: List<Player>): Player? {
        if (activePlayer.currentPosition == 1 || activePlayer.currentPosition == 100) return null
        for (enemy in allPlayers) {
            if (enemy.id != activePlayer.id && !enemy.isFinished && enemy.currentPosition == activePlayer.currentPosition) {
                return enemy
            }
        }
        return null
    }

    // RESULT CLASSES
    sealed class MoveResult {
        data class NormalMove(val to: Int) : MoveResult()
        data class SnakeBite(val head: Int, val tail: Int) : MoveResult()
        data class LadderClimb(val bottom: Int, val top: Int) : MoveResult()
        data class StarCollected(val at: Int) : MoveResult()
        data class PacmanSpawned(val at: Int) : MoveResult()
        data class StarUsed(val at: Int, val msg: String) : MoveResult()
        data class Stay(val at: Int, val reason: String) : MoveResult()
        data class Win(val at: Int) : MoveResult()
    }

    enum class PacmanEvent { NORMAL, SNAKE_BITE, LADDER_CLIMB }

    sealed class PacmanResult {
        object NoMove : PacmanResult()
        data class Move(val path: List<Int>, val finalPos: Int, val eventType: PacmanEvent) : PacmanResult()
    }
}