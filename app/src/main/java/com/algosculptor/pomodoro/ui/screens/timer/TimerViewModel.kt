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
import com.algosculptor.pomodoro.timer.TimerPhase
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

    private var previousPhase: TimerPhase? = null

    init {
        viewModelScope.launch {
            engine.snapshot.collect { snap ->
                val prev = previousPhase
                val curr = snap.phase
                previousPhase = curr

                if (prev != null && prev.isRunning && prev != curr) {
                    val settings = settingsRepository.settings.first()
                    if (settings.phaseEndSound == "chime") {
                        playback.playOnce(audioRepository.completionChimeUri(), settings.volume)
                    }
                }
            }
        }
    }

    val uiState: StateFlow<TimerUiState> = combine(
        engine.snapshot,
        settingsRepository.settings,
        playback.state,
    ) { snap, settings, playbackState ->
        // Keep engine config in sync with persisted durations.
        engine.configure(settings.workMinutes.minutes, settings.restMinutes.minutes)
        TimerUiState(snap, settings, playbackState.isPlaying)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun start(phase: TimerPhase = TimerPhase.WORKING) {
        engine.start(phase)
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (s.audioSelection != "off") {
                applyAudioSelection(s)
            }
        }
    }

    fun pause() {
        engine.pause()
        playback.pause()
    }

    fun resume() {
        engine.resume()
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (s.audioSelection != "off") {
                applyAudioSelection(s)
            }
        }
    }

    fun reset() {
        engine.reset()
        playback.stop()
    }

    fun skip() = engine.skipPhase()

    fun switchToPhase(phase: TimerPhase) = engine.switchToPhase(phase)

    fun updateDuration(isFocus: Boolean, minutes: Int) {
        viewModelScope.launch {
            if (isFocus) {
                settingsRepository.setWorkMinutes(minutes)
            } else {
                settingsRepository.setRestMinutes(minutes)
            }
        }
    }

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
        if (playback.state.value.isPlaying) {
            playback.pause()
        } else {
            viewModelScope.launch {
                val s = settingsRepository.settings.first()
                if (s.audioSelection == "off") {
                    val firstTrack = audioRepository.tracks.firstOrNull()
                    if (firstTrack != null) {
                        val tag = "bundled:${firstTrack.id}"
                        settingsRepository.setAudioSelection(tag)
                        playback.play(audioRepository.uriFor(firstTrack), tag, s.volume)
                    }
                } else {
                    applyAudioSelection(s)
                }
            }
        }
    }

    fun setVolume(v: Float) {
        playback.setVolume(v)
        viewModelScope.launch { settingsRepository.setVolume(v) }
    }
}
