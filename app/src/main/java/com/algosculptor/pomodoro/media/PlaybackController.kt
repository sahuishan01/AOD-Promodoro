package com.algosculptor.pomodoro.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val volume: Float = 0.6f,
    val activeSource: String? = null,
)

/**
 * Dedicated ambient audio engine backed directly by ExoPlayer with audio focus management.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true, // handleAudioFocus
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onVolumeChanged(volume: Float) {
                        _state.update { it.copy(volume = volume) }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Timber.e(error, "ExoPlayer playback error")
                    }
                })
            }
    }

    fun play(uri: Uri, sourceTag: String, volume: Float) {
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.AUDIO_WAV)
                .build()
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.setMediaItem(mediaItem)
            player.volume = volume.coerceIn(0f, 1f)
            player.prepare()
            player.play()
            _state.update { it.copy(activeSource = sourceTag, volume = player.volume, isPlaying = true) }
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio from $uri")
        }
    }

    fun playOnce(uri: Uri, volume: Float) {
        try {
            val sfxPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
                        .setUsage(C.USAGE_ASSISTANCE_SONIFICATION)
                        .build(),
                    false
                )
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.AUDIO_WAV)
                .build()
            sfxPlayer.setMediaItem(mediaItem)
            sfxPlayer.volume = volume.coerceIn(0f, 1f)
            sfxPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        sfxPlayer.release()
                    }
                }
            })
            sfxPlayer.prepare()
            sfxPlayer.play()
        } catch (e: Exception) {
            Timber.e(e, "Error playing one-shot audio from $uri")
        }
    }

    fun pause() {
        try {
            player.pause()
            _state.update { it.copy(isPlaying = false) }
        } catch (e: Exception) {
            Timber.e(e, "Error pausing audio")
        }
    }

    fun stop() {
        try {
            player.stop()
            player.clearMediaItems()
            _state.update { it.copy(isPlaying = false, activeSource = null) }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio")
        }
    }

    fun setVolume(volume: Float) {
        try {
            val v = volume.coerceIn(0f, 1f)
            player.volume = v
            _state.update { it.copy(volume = v) }
        } catch (e: Exception) {
            Timber.e(e, "Error setting volume")
        }
    }

    fun release() {
        try {
            player.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing player")
        }
    }
}
