package com.bytemantis.snald.ui

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.SparseIntArray
import android.view.animation.LinearInterpolator
import com.bytemantis.snald.R

class SoundManager(private val context: Context) {

    private val soundPool: SoundPool
    private val soundMap = SparseIntArray()

    // --- NEW: Background Music ---
    private var menuMusicPlayer: MediaPlayer? = null
    private var isMusicEnabled = true

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load SFX
        soundMap.put(R.raw.sfx_dice_roll, soundPool.load(context, R.raw.sfx_dice_roll, 1))
        soundMap.put(R.raw.sfx_hop, soundPool.load(context, R.raw.sfx_hop, 1))
        soundMap.put(R.raw.sfx_snake_bite, soundPool.load(context, R.raw.sfx_snake_bite, 1))
        soundMap.put(R.raw.sfx_ladder_climb, soundPool.load(context, R.raw.sfx_ladder_climb, 1))
        soundMap.put(R.raw.sfx_star_collect, soundPool.load(context, R.raw.sfx_star_collect, 1))
        soundMap.put(R.raw.sfx_star_use, soundPool.load(context, R.raw.sfx_star_use, 1))
        soundMap.put(R.raw.sfx_win, soundPool.load(context, R.raw.sfx_win, 1))
        soundMap.put(R.raw.sfx_slide_back, soundPool.load(context, R.raw.sfx_slide_back, 1))
        soundMap.put(R.raw.sfx_pacman_entry, soundPool.load(context, R.raw.sfx_pacman_entry, 1))
        soundMap.put(R.raw.sfx_pacman_move, soundPool.load(context, R.raw.sfx_pacman_move, 1))
        soundMap.put(R.raw.sfx_fast_flash, soundPool.load(context, R.raw.sfx_fast_flash, 1))
    }

    // --- Music Control ---
    fun startMenuMusic() {
        if (!isMusicEnabled) return
        if (menuMusicPlayer == null) {
            try {
                // Ensure 'bgm_menu' exists in res/raw/
                menuMusicPlayer = MediaPlayer.create(context, R.raw.bgm_menu)
                menuMusicPlayer?.isLooping = true
                menuMusicPlayer?.setVolume(0.5f, 0.5f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (menuMusicPlayer?.isPlaying == false) {
            menuMusicPlayer?.start()
        }
    }

    fun stopMenuMusic() {
        try {
            if (menuMusicPlayer?.isPlaying == true) {
                menuMusicPlayer?.pause()
                menuMusicPlayer?.seekTo(0)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun pauseMusic() {
        if (menuMusicPlayer?.isPlaying == true) {
            menuMusicPlayer?.pause()
        }
    }

    fun resumeMusic() {
        // Resume only if it was initialized and we want it playing
        if (menuMusicPlayer != null && !menuMusicPlayer!!.isPlaying) {
            menuMusicPlayer?.start()
        }
    }

    // --- SFX Methods ---
    fun playDiceRoll(): Int = play(R.raw.sfx_dice_roll)
    fun playHop(): Int = play(R.raw.sfx_hop)
    fun playSnakeBite(): Int = play(R.raw.sfx_snake_bite)
    fun playLadderClimb(): Int = play(R.raw.sfx_ladder_climb)
    fun playStarUsed(): Int = play(R.raw.sfx_star_use)
    fun playWin(): Int = play(R.raw.sfx_win)
    fun playSlideBack(): Int = play(R.raw.sfx_slide_back)
    fun playPacmanEntry(): Int = play(R.raw.sfx_pacman_entry)
    fun playPacmanMove(): Int = play(R.raw.sfx_pacman_move)
    fun playFastFlash(): Int = play(R.raw.sfx_fast_flash)

    fun stop(streamId: Int) {
        if (streamId != 0) soundPool.stop(streamId)
    }

    fun playStarCollect() {
        val soundId = soundMap.get(R.raw.sfx_star_collect)
        if (soundId == 0) return
        val streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        val fadeAnim = ValueAnimator.ofFloat(1.0f, 0.0f)
        fadeAnim.duration = 1500L
        fadeAnim.interpolator = LinearInterpolator()
        fadeAnim.addUpdateListener { animation ->
            val volume = animation.animatedValue as Float
            soundPool.setVolume(streamId, volume, volume)
        }
        fadeAnim.startDelay = 500
        fadeAnim.start()
    }

    private fun play(resId: Int): Int {
        val soundId = soundMap.get(resId)
        if (soundId == 0) return 0
        return soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }
}