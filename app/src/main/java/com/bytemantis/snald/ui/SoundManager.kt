package com.bytemantis.snald.ui

import android.content.Context
import android.media.MediaPlayer
import com.bytemantis.snald.R

class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun playSnakeBite() {
        playSound(R.raw.sfx_snake_bite)
    }

    fun playLadderClimb() {
        playSound(R.raw.sfx_ladder_climb)
    }

    fun playStarCollect() {
        playSound(R.raw.sfx_star_collect)
    }

    // NEW: Sound when Star Shield protects you
    fun playStarUsed() {
        // Ensure you have a file named 'sfx_star_use' in res/raw
        // If your file is named differently, change it here.
        playSound(R.raw.sfx_star_use)
    }

    // NEW: Sound when reaching 100
    fun playWin() {
        // Ensure you have a file named 'sfx_win' in res/raw
        playSound(R.raw.sfx_win)
    }

    private fun playSound(resId: Int) {
        // 1. Release previous sound to avoid crashing memory
        mediaPlayer?.release()

        // 2. Create and Start new sound
        // We use try-catch to prevent crashes if a sound file is missing
        try {
            mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.start()

            // 3. Clean up when finished
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}