package com.algosculptor.pomodoro.ui.screens.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algosculptor.pomodoro.data.media.AmbientAudioRepository
import com.algosculptor.pomodoro.data.media.BundledTrack
import com.algosculptor.pomodoro.data.media.PickedMediaPolicy
import com.algosculptor.pomodoro.data.settings.AppSettings
import com.algosculptor.pomodoro.data.settings.SettingsRepository
import com.algosculptor.pomodoro.media.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val audioRepository: AmbientAudioRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val bundledTracks: List<BundledTrack> = audioRepository.tracks

    sealed class Event {
        data object MediaRejected : Event()
        data object MediaRevoked : Event()
    }
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events

    fun setWorkMinutes(v: Int) = viewModelScope.launch { settingsRepository.setWorkMinutes(v) }
    fun setRestMinutes(v: Int) = viewModelScope.launch { settingsRepository.setRestMinutes(v) }
    fun setVolume(v: Float) = viewModelScope.launch {
        settingsRepository.setVolume(v)
        playback.setVolume(v)
    }
    fun setAutoDim(v: Boolean) = viewModelScope.launch { settingsRepository.setAutoDim(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsRepository.setHaptics(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepository.setKeepScreenOn(v) }
    fun setShowWhenLocked(v: Boolean) = viewModelScope.launch { settingsRepository.setShowWhenLocked(v) }

    fun selectBundledBackground(id: String) = viewModelScope.launch {
        settingsRepository.setBackground("bundled:$id")
    }

    /** Photo Picker result: validate through the media gate, then persist the URI grant. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val resolver = context.contentResolver
            if (PickedMediaPolicy.validateImage(resolver, uri) !is PickedMediaPolicy.Result.Ok) {
                _events.emit(Event.MediaRejected)
                return@launch
            }
            persistUriPermission(uri)
            settingsRepository.setBackground("image:$uri")
        }
    }

    fun selectAudioOff() = viewModelScope.launch {
        playback.stop()
        settingsRepository.setAudioSelection("off")
    }

    fun selectBundledTrack(track: BundledTrack) = viewModelScope.launch {
        val tag = "bundled:${track.id}"
        settingsRepository.setAudioSelection(tag)
        playback.play(audioRepository.uriFor(track), tag, settings.value.volume)
    }

    /** SAF result: validate through the media gate, then persist the URI grant. */
    fun onAudioPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val resolver = context.contentResolver
            if (PickedMediaPolicy.validateAudio(resolver, uri) !is PickedMediaPolicy.Result.Ok) {
                _events.emit(Event.MediaRejected)
                return@launch
            }
            persistUriPermission(uri)
            val tag = "picked:$uri"
            settingsRepository.setAudioSelection(tag)
            playback.play(uri, "picked", settings.value.volume)
        }
    }

    /**
     * Best-effort persistable grant (DEVELOPMENT-PLAN.md §4.3).
     * Photo Picker URIs are session-scoped; SAF URIs usually accept a persistable grant.
     */
    private fun persistUriPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Timber.d("persistable grant unavailable for %s", uri.toString()) }
    }

    fun onBackgroundRevoked() = viewModelScope.launch {
        settingsRepository.setBackground("bundled:midnight")
        _events.emit(Event.MediaRevoked)
    }
}
