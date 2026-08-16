package com.phicomm.gemini.audio

import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File

object AudioPlayer {
    private const val TAG = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null

    fun playFile(file: File, onCompletion: (() -> Unit)? = null) {
        if (!file.exists()) {
            Log.e(TAG, "Audio file not found: ${file.absolutePath}")
            return
        }
        playUrl(file.absolutePath, onCompletion)
    }

    fun playUrl(url: String, onCompletion: (() -> Unit)? = null) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(url)
                setOnCompletionListener {
                    Log.d(TAG, "Audio playback completed")
                    stop()
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
                prepareAsync()
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared, starting playback")
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }
}
