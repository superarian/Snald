package com.bytemantis.snald.ludogame

class LudoRuleEngine {

    // Possible outcomes of a move
    sealed class MoveResult {
        object Invalid : MoveResult()
        data class MoveOnly(val newPosIndex: Int) : MoveResult()
        data class Kill(val newPosIndex: Int, val victimPlayerIdx: Int, val victimTokenIdx: Int) : MoveResult()
        data class SafeStack(val newPosIndex: Int) : MoveResult() // Landed on enemy in safe zone

        // OWNER ADDITION: New Result for landing on an empty Safe Zone
        data class SafeZoneLanded(val newPosIndex: Int) : MoveResult()

        data class Win(val newPosIndex: Int) : MoveResult()
    }

    fun calculateMove(
        activePlayerIdx: Int,
        tokenIndex: Int,
        currentPos: Int,
        diceRoll: Int,
        allPlayers: List<LudoPlayer>
    ): MoveResult {

        if (currentPos == -1) {
            if (diceRoll == 6) return MoveResult.MoveOnly(0)
            else return MoveResult.Invalid
        }

        val targetPos = currentPos + diceRoll
        if (targetPos > 56) return MoveResult.Invalid
        if (targetPos == 57) return MoveResult.Win(57)

        val targetGlobalCoord = LudoBoardConfig.getGlobalCoord(activePlayerIdx, targetPos)
            ?: return MoveResult.MoveOnly(targetPos)

        // Check Collisions
        for (enemy in allPlayers) {
            if (enemy.id == (activePlayerIdx + 1)) continue

            for (i in 0 until 4) {
                val enemyPos = enemy.tokenPositions[i]
                if (enemyPos < 0 || enemyPos > 50) continue

                val enemyGlobalCoord = LudoBoardConfig.getGlobalCoord(enemy.id - 1, enemyPos)

                if (targetGlobalCoord == enemyGlobalCoord) {
                    if (LudoBoardConfig.SAFE_ZONES.contains(targetGlobalCoord)) {
                        return MoveResult.SafeStack(targetPos)
                    } else {
                        return MoveResult.Kill(targetPos, enemy.id - 1, i)
                    }
                }
            }
        }

        // OWNER ADDITION: Check if landing on Safe Zone (without collision)
        if (LudoBoardConfig.SAFE_ZONES.contains(targetGlobalCoord)) {
            return MoveResult.SafeZoneLanded(targetPos)
        }

        return MoveResult.MoveOnly(targetPos)
    }
}