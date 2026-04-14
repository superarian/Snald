package com.bytemantis.snald.ludogame

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.UUID

class LudoNetworkManager {
    private val db = FirebaseFirestore.getInstance()
    private var actionListener: ListenerRegistration? = null
    var currentRoomId: String? = null
        private set

    // Simple data class for the Room Document
    data class RoomInfo(
        val hostId: Int = 1,
        val status: String = "WAITING",
        val playerCount: Int = 4,
        val tokenCount: Int = 4
    )

    fun createPrivateRoom(playerCount: Int, tokenCount: Int, onComplete: (String?) -> Unit) {
        val roomId = generateRoomCode()
        val roomData = RoomInfo(
            hostId = 1, // Host is always player 1 internally
            status = "WAITING",
            playerCount = playerCount,
            tokenCount = tokenCount
        )

        db.collection("rooms").document(roomId).set(roomData)
            .addOnSuccessListener {
                currentRoomId = roomId
                onComplete(roomId)
            }
            .addOnFailureListener { e ->
                Log.e("LudoNetworkManager", "Error creating room", e)
                onComplete(null)
            }
    }

    fun joinPrivateRoom(roomId: String, onComplete: (Boolean, RoomInfo?) -> Unit) {
        val upperCaseRoomId = roomId.uppercase()
        db.collection("rooms").document(upperCaseRoomId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val roomInfo = document.toObject(RoomInfo::class.java)
                    currentRoomId = upperCaseRoomId
                    onComplete(true, roomInfo)
                } else {
                    onComplete(false, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("LudoNetworkManager", "Error joining room", e)
                onComplete(false, null)
            }
    }

    fun getJoinedPlayersCount(onComplete: (Int) -> Unit) {
        val roomId = currentRoomId ?: return onComplete(0)
        db.collection("rooms").document(roomId)
            .collection("actions")
            .whereEqualTo("type", "PLAYER_JOINED")
            .get()
            .addOnSuccessListener { snapshots ->
                onComplete(snapshots.size())
            }
            .addOnFailureListener {
                onComplete(0)
            }
    }

    fun pushAction(type: String, playerId: Int, value: Int) {
        val roomId = currentRoomId ?: return
        val actionId = UUID.randomUUID().toString()

        val action = LudoAction(
            actionId = actionId,
            type = type,
            playerId = playerId,
            value = value
            // timestamp is auto-handled by @ServerTimestamp in the cloud
        )

        db.collection("rooms").document(roomId)
            .collection("actions")
            .document(actionId)
            .set(action)
            .addOnFailureListener { e ->
                Log.e("LudoNetworkManager", "Error pushing action", e)
            }
    }

    fun startListeningForActions(onNewAction: (LudoAction) -> Unit) {
        val roomId = currentRoomId ?: return

        actionListener?.remove() // Clear any existing listener just in case

        // Listen to the actions subcollection, ordered by the time they happened
        actionListener = db.collection("rooms").document(roomId)
            .collection("actions")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("LudoNetworkManager", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    for (dc in snapshots.documentChanges) {
                        if (dc.type == DocumentChange.Type.ADDED) {
                            val action = dc.document.toObject(LudoAction::class.java)

                            // We trigger the UI ONLY when the action is confirmed by the server
                            // or if it's our own pending local write.
                            if (action.timestamp != null || dc.document.metadata.hasPendingWrites()) {
                                onNewAction(action)
                            }
                        }
                    }
                }
            }
    }

    fun stopListening() {
        actionListener?.remove()
        actionListener = null
        currentRoomId = null
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}