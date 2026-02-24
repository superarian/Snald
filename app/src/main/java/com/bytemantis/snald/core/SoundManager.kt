package com.bytemantis.snald.core

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

    // Background Music Players
    private var menuMusicPlayer: MediaPlayer? = null
    private var neonMusicPlayer: MediaPlayer? = null
    private var woodMusicPlayer: MediaPlayer? = null
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
        soundMap.put(R.raw.sfx_safe_zone, soundPool.load(context, R.raw.sfx_safe_zone, 1))
    }

    // --- Theme Music Control ---
    fun playMusicForTheme(themeResId: Int) {
        when (themeResId) {
            R.drawable.ludo_board -> {
                stopNeonMusic()
                stopWoodMusic()
                startMenuMusic()
            }
            R.drawable.ludo_board_neon -> {
                stopMenuMusic()
                stopWoodMusic()
                startNeonMusic()
            }
            R.drawable.ludo_board_wood -> {
                stopMenuMusic()
                stopNeonMusic()
                startWoodMusic()
            }
            else -> {
                stopMenuMusic()
                stopNeonMusic()
                stopWoodMusic()
            }
        }
    }

    fun startMenuMusic() {
        if (!isMusicEnabled) return
        if (menuMusicPlayer == null) {
            try {
                menuMusicPlayer = MediaPlayer.create(context, R.raw.bgm_menu)
                menuMusicPlayer?.isLooping = true
                menuMusicPlayer?.setVolume(0.5f, 0.5f)
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (menuMusicPlayer?.isPlaying == false) menuMusicPlayer?.start()
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
        if (menuMusicPlayer?.isPlaying == true) menuMusicPlayer?.pause()
    }

    fun resumeMusic() {
        if (menuMusicPlayer != null && !menuMusicPlayer!!.isPlaying) menuMusicPlayer?.start()
    }

    fun startNeonMusic() {
        if (!isMusicEnabled) return
        if (neonMusicPlayer == null) {
            try {
                neonMusicPlayer = MediaPlayer.create(context, R.raw.bgm_neon)
                neonMusicPlayer?.isLooping = true
                neonMusicPlayer?.setVolume(0.4f, 0.4f)
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (neonMusicPlayer?.isPlaying == false) neonMusicPlayer?.start()
    }

    fun stopNeonMusic() {
        try {
            if (neonMusicPlayer?.isPlaying == true) {
                neonMusicPlayer?.pause()
                neonMusicPlayer?.seekTo(0)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun pauseNeonMusic() {
        if (neonMusicPlayer?.isPlaying == true) neonMusicPlayer?.pause()
    }

    fun resumeNeonMusic() {
        if (neonMusicPlayer != null && !neonMusicPlayer!!.isPlaying) neonMusicPlayer?.start()
    }

    fun startWoodMusic() {
        if (!isMusicEnabled) return
        if (woodMusicPlayer == null) {
            try {
                woodMusicPlayer = MediaPlayer.create(context, R.raw.bgm_wood)
                woodMusicPlayer?.isLooping = true
                woodMusicPlayer?.setVolume(0.4f, 0.4f)
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (woodMusicPlayer?.isPlaying == false) woodMusicPlayer?.start()
    }

    fun stopWoodMusic() {
        try {
            if (woodMusicPlayer?.isPlaying == true) {
                woodMusicPlayer?.pause()
                woodMusicPlayer?.seekTo(0)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun pauseWoodMusic() {
        if (woodMusicPlayer?.isPlaying == true) woodMusicPlayer?.pause()
    }

    fun resumeWoodMusic() {
        if (woodMusicPlayer != null && !woodMusicPlayer!!.isPlaying) woodMusicPlayer?.start()
    }

    // --- SFX Methods with Centralized Volume Mixing ---
    fun playDiceRoll(): Int = play(R.raw.sfx_dice_roll, 0.5f) // -50% Volume
    fun playHop(): Int = play(R.raw.sfx_hop, 0.7f)            // -30% Volume
    fun playSafeZone(): Int = play(R.raw.sfx_safe_zone, 0.8f) // -20% Volume

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

    // Modified for -20% starting volume
    fun playStarCollect() {
        val soundId = soundMap.get(R.raw.sfx_star_collect)
        if (soundId == 0) return
        val streamId = soundPool.play(soundId, 0.8f, 0.8f, 1, 0, 1.0f)
        val fadeAnim = ValueAnimator.ofFloat(0.8f, 0.0f)
        fadeAnim.duration = 1500L
        fadeAnim.interpolator = LinearInterpolator()
        fadeAnim.addUpdateListener { animation ->
            val volume = animation.animatedValue as Float
            soundPool.setVolume(streamId, volume, volume)
        }
        fadeAnim.startDelay = 500
        fadeAnim.start()
    }

    // Upgraded internal play method to handle volume scaling
    private fun play(resId: Int, volume: Float = 1.0f): Int {
        val soundId = soundMap.get(resId)
        if (soundId == 0) return 0
        return soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
    }
}