package com.algosculptor.pomodoro.ui.screens.timer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algosculptor.pomodoro.data.background.BackgroundCatalog
import com.algosculptor.pomodoro.data.settings.AppSettings
import com.algosculptor.pomodoro.data.settings.SettingsRepository
import com.algosculptor.pomodoro.media.PlaybackController
import com.algosculptor.pomodoro.data.media.AmbientAudioRepository
import com.algosculptor.pomodoro.timer.EngineSnapshot
import com.algosculptor.pomodoro.timer.TimerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

data class TimerUiState(
    val snapshot: EngineSnapshot = EngineSnapshot(),
    val settings: AppSettings = AppSettings(),
    val audioPlaying: Boolean = false,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val engine: TimerEngine,
    private val settingsRepository: SettingsRepository,
    private val audioRepository: AmbientAudioRepository,
    val playback: PlaybackController,
) : ViewModel() {

    val uiState: StateFlow<TimerUiState> = combine(
        engine.snapshot,
        settingsRepository.settings,
        playback.state,
    ) { snap, settings, playbackState ->
        // Keep engine config in sync with persisted durations.
        engine.configure(settings.workMinutes.minutes, settings.restMinutes.minutes)
        TimerUiState(snap, settings, playbackState.isPlaying)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun start() = engine.start()
    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun reset() = engine.reset()
    fun skip() = engine.skipPhase()

    fun applyAudioSelection(settings: AppSettings) {
        val sel = settings.audioSelection
        when {
            sel == "off" -> playback.stop()
            sel.startsWith("bundled:") -> {
                val track = audioRepository.byId(sel.removePrefix("bundled:")) ?: return
                playback.play(audioRepository.uriFor(track), sel, settings.volume)
            }
            sel.startsWith("picked:") -> {
                playback.play(Uri.parse(sel.removePrefix("picked:")), "picked", settings.volume)
            }
        }
    }

    fun toggleAudio() {
        if (playback.state.value.isPlaying) playback.pause()
        else viewModelScope.launch {
            // Resume the persisted selection.
            applyAudioSelection(settingsRepository.settings.first())
        }
    }

    fun setVolume(v: Float) {
        playback.setVolume(v)
        viewModelScope.launch { settingsRepository.setVolume(v) }
    }
}
