package com.bytemantis.snald.logic

import com.bytemantis.snald.model.Player

class GameEngine {

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

            // NEW: Spawn Pac-Man if 2nd star and not already active
            if (player.starCount >= 2 && player.pacmanPosition == 0) {
                player.pacmanPosition = 99 // Spawn at 99
                return MoveResult.PacmanSpawned(newPosition)
            }
            return MoveResult.StarCollected(newPosition)
        }

        // 2. Check for Snakes
        if (BoardConfig.SNAKES.containsKey(newPosition)) {
            val snakeTail = BoardConfig.SNAKES[newPosition]!!
            if (player.hasShield) {
                player.starCount-- // Consume 1 shield
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

    // =================================================================
    // NEW: Centralized Circular Logic (Owner Approach)
    // =================================================================

    // Helper to generate the exact list of squares Pac-Man visits
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

    // Calculates the move and returns the full path for the UI
    fun calculatePacmanMove(player: Player, diceValue: Int): PacmanResult {
        if (player.pacmanPosition == 0) return PacmanResult.NoMove

        // Speed is double the dice value
        val steps = diceValue * 2
        val path = calculatePacmanPath(player.pacmanPosition, steps)
        val finalPos = path.last()

        return PacmanResult.Move(path, finalPos)
    }

    // Checks if ANY player is on the path Pac-Man traveled
    fun checkPacmanPathKills(hunter: Player, allPlayers: List<Player>, path: List<Int>): Player? {
        if (hunter.pacmanPosition == 0) return null

        // Check every step in the path for an enemy
        for (step in path) {
            for (enemy in allPlayers) {
                // Can't eat self, finished players, or players at start/end
                if (enemy.id != hunter.id && !enemy.isFinished && enemy.currentPosition != 1 && enemy.currentPosition != 100) {
                    if (enemy.currentPosition == step) {
                        return enemy // Found a victim on the path!
                    }
                }
            }
        }
        return null
    }

    // Standard Collision (Player vs Player) - Same as before
    fun checkCollisions(activePlayer: Player, allPlayers: List<Player>): Player? {
        if (activePlayer.currentPosition == 1 || activePlayer.currentPosition == 100) return null

        for (enemy in allPlayers) {
            if (enemy.id != activePlayer.id && !enemy.isFinished && enemy.currentPosition == activePlayer.currentPosition) {
                return enemy
            }
        }
        return null
    }

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

    sealed class PacmanResult {
        object NoMove : PacmanResult()
        // Changed: Now carries the path list for animation
        data class Move(val path: List<Int>, val finalPos: Int) : PacmanResult()
    }
}