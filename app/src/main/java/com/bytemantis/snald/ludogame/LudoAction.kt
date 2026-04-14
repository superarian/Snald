package com.bytemantis.snald.ludogame

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class LudoAction(
    val actionId: String = "",
    val type: String = "", // e.g., "ROLL_DICE", "MOVE_TOKEN"
    val playerId: Int = -1,
    val value: Int = -1, // Dice roll value or token index
    @ServerTimestamp val timestamp: Date? = null
)