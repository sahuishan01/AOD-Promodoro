package com.aod.pomodromo.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val volume: Float = 0.6f,
    val activeSource: String? = null, // opaque tag: "bundled:<id>" or "picked"
)

/**
 * Thin facade over a Media3 [MediaController] connected to [AmbientPlaybackService].
 * UI never touches the player directly.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onVolumeChanged(volume: Float) {
            _state.update { it.copy(volume = volume) }
        }
    }

    /** Idempotent connect; safe to call from any screen. */
    fun connect(onReady: (() -> Unit)? = null) {
        if (controller != null) {
            onReady?.invoke()
            return
        }
        val token = SessionToken(context, ComponentName(context, AmbientPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(listener)
                _state.update { it.copy(isPlaying = c.isPlaying, volume = c.volume) }
            }
            onReady?.invoke()
        }, MoreExecutors.directExecutor())
    }

    fun play(uri: Uri, sourceTag: String, volume: Float) {
        connect {
            controller?.let { c ->
                c.setMediaItem(MediaItem.fromUri(uri))
                c.volume = volume.coerceIn(0f, 1f)
                c.prepare()
                c.play()
                _state.update { it.copy(activeSource = sourceTag, volume = c.volume) }
            }
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun stop() {
        controller?.let { c ->
            c.stop()
            c.clearMediaItems()
        }
        _state.update { it.copy(isPlaying = false, activeSource = null) }
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
