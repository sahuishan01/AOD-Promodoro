package com.algosculptor.pomodoro.ui.screens.timer

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.algosculptor.pomodoro.R
import com.algosculptor.pomodoro.core.util.TimeFormatter
import com.algosculptor.pomodoro.data.background.BackgroundCatalog
import com.algosculptor.pomodoro.timer.TimerPhase
import com.algosculptor.pomodoro.ui.components.AodClock
import com.algosculptor.pomodoro.ui.components.BackgroundModel
import com.algosculptor.pomodoro.ui.components.BackgroundSurface
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

@Composable
fun TimerScreen(
    onOpenSettings: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Screen stays on while this screen is composed and keepScreenOn is enabled.
    DisposableEffect(ui.settings.keepScreenOn) {
        view.keepScreenOn = ui.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Active mode while timer is idle (allows toggling preview/duration before starting)
    var idleSelectedPhase by remember { mutableStateOf(TimerPhase.WORKING) }
    var showTimeAdjustDialog by remember { mutableStateOf(false) }

    // Haptic phase cue (opt-in).
    var lastPhase by remember { mutableStateOf(ui.snapshot.phase) }
    if (ui.settings.hapticsEnabled && ui.snapshot.phase != lastPhase) {
        LaunchedEffect(ui.snapshot.phase) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastPhase = ui.snapshot.phase
    }

    // Auto-dim: after 5 minutes in the same phase, the clock dims to 35%.
    var phaseStartMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (ui.snapshot.phase != lastPhase) {
        phaseStartMillis = System.currentTimeMillis()
        lastPhase = ui.snapshot.phase
    }
    val dimmed = ui.settings.autoDimEnabled &&
        ui.snapshot.phase.isRunning &&
        System.currentTimeMillis() - phaseStartMillis > 5 * 60_000

    val backgroundModel = remember(ui.settings.backgroundId) {
        resolveBackground(ui.settings.backgroundId)
    }

    var lastTouchMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isIdleRunning = ui.snapshot.phase.isRunning && (System.currentTimeMillis() - lastTouchMillis > 8_000)
    val controlsAlpha by animateFloatAsState(
        targetValue = if (isIdleRunning) 0.18f else 1.0f,
        animationSpec = tween(durationMillis = 600),
        label = "controlsAlpha"
    )

    val isIdle = !ui.snapshot.phase.isRunning
    val activePhase = if (isIdle) idleSelectedPhase else ui.snapshot.phase
    val isFocus = activePhase == TimerPhase.WORKING

    // Slide/Swipe gesture detection across clock area
    var dragAccumulatorX by remember { mutableFloatStateOf(0f) }
    val swipeModifier = Modifier.pointerInput(isIdle, idleSelectedPhase, ui.snapshot.phase) {
        detectHorizontalDragGestures(
            onDragStart = { dragAccumulatorX = 0f },
            onDragEnd = {
                if (abs(dragAccumulatorX) > 35f) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    lastTouchMillis = System.currentTimeMillis()
                    if (isIdle) {
                        idleSelectedPhase = if (idleSelectedPhase == TimerPhase.WORKING) TimerPhase.RESTING else TimerPhase.WORKING
                    } else {
                        viewModel.skip()
                    }
                }
                dragAccumulatorX = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                dragAccumulatorX += dragAmount
            }
        )
    }

    // Quick Time Adjustment Dialog
    if (showTimeAdjustDialog) {
        QuickTimeAdjustDialog(
            isFocus = isFocus,
            currentMinutes = if (isFocus) ui.settings.workMinutes else ui.settings.restMinutes,
            onDismiss = { showTimeAdjustDialog = false },
            onConfirm = { newMinutes ->
                viewModel.updateDuration(isFocus = isFocus, minutes = newMinutes)
            },
            onToggleMode = {
                if (isIdle) {
                    idleSelectedPhase = if (idleSelectedPhase == TimerPhase.WORKING) TimerPhase.RESTING else TimerPhase.WORKING
                }
            }
        )
    }

    BackgroundSurface(model = backgroundModel) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { lastTouchMillis = System.currentTimeMillis() })
                }
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1.1f)
                            .then(swipeModifier),
                    ) {
                        InteractivePhaseBadge(
                            currentPhase = ui.snapshot.phase,
                            isIdle = isIdle,
                            idleSelectedPhase = idleSelectedPhase,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                lastTouchMillis = System.currentTimeMillis()
                                if (isIdle) {
                                    idleSelectedPhase = if (idleSelectedPhase == TimerPhase.WORKING) TimerPhase.RESTING else TimerPhase.WORKING
                                } else {
                                    viewModel.skip()
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        AodClock(
                            text = TimeFormatter.formatClock(
                                if (isIdle) {
                                    if (idleSelectedPhase == TimerPhase.WORKING) ui.settings.workMinutes.minutes
                                    else ui.settings.restMinutes.minutes
                                } else {
                                    ui.snapshot.remaining
                                }
                            ),
                            contentDescription = stringResource(R.string.cd_timer_clock),
                            dimmed = dimmed,
                            fontSize = 100.sp,
                            onClick = {
                                lastTouchMillis = System.currentTimeMillis()
                                showTimeAdjustDialog = true
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        SoundSliderRow(
                            currentSelection = ui.settings.audioSelection,
                            tracks = viewModel.audioRepository.tracks,
                            onSelectTrack = { trackId ->
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.selectAudioTrack(trackId)
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        BrightnessSliderRow(
                            brightness = ui.settings.screenBrightness,
                            onBrightnessChange = { b ->
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.setBrightness(b)
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        if (isIdle) {
                            Text(
                                text = stringResource(R.string.quick_edit_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        } else {
                            Text(
                                text = "Cycle ${ui.snapshot.completedCycles + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(0.9f)
                            .alpha(controlsAlpha),
                    ) {
                        ControlsRow(
                            phase = ui.snapshot.phase,
                            isPaused = ui.snapshot.isPaused,
                            onStart = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.start(if (isIdle) idleSelectedPhase else TimerPhase.WORKING)
                            },
                            onPause = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.pause()
                            },
                            onResume = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.resume()
                            },
                            onReset = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.reset()
                            },
                            onSkip = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.skip()
                            },
                        )
                        Spacer(Modifier.height(20.dp))
                        VolumeRow(
                            playing = ui.audioPlaying,
                            volume = ui.settings.volume,
                            onToggle = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.toggleAudio()
                            },
                            onVolume = { vol: Float ->
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.setVolume(vol)
                            },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = swipeModifier,
                    ) {
                        InteractivePhaseBadge(
                            currentPhase = ui.snapshot.phase,
                            isIdle = isIdle,
                            idleSelectedPhase = idleSelectedPhase,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                lastTouchMillis = System.currentTimeMillis()
                                if (isIdle) {
                                    idleSelectedPhase = if (idleSelectedPhase == TimerPhase.WORKING) TimerPhase.RESTING else TimerPhase.WORKING
                                } else {
                                    viewModel.skip()
                                }
                            }
                        )
                        Spacer(Modifier.height(16.dp))

                        AodClock(
                            text = TimeFormatter.formatClock(
                                if (isIdle) {
                                    if (idleSelectedPhase == TimerPhase.WORKING) ui.settings.workMinutes.minutes
                                    else ui.settings.restMinutes.minutes
                                } else {
                                    ui.snapshot.remaining
                                }
                            ),
                            contentDescription = stringResource(R.string.cd_timer_clock),
                            dimmed = dimmed,
                            fontSize = 120.sp,
                            onClick = {
                                lastTouchMillis = System.currentTimeMillis()
                                showTimeAdjustDialog = true
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        SoundSliderRow(
                            currentSelection = ui.settings.audioSelection,
                            tracks = viewModel.audioRepository.tracks,
                            onSelectTrack = { trackId ->
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.selectAudioTrack(trackId)
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        BrightnessSliderRow(
                            brightness = ui.settings.screenBrightness,
                            onBrightnessChange = { b ->
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.setBrightness(b)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        if (isIdle) {
                            Text(
                                text = stringResource(R.string.quick_edit_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        } else {
                            Text(
                                text = "Cycle ${ui.snapshot.completedCycles + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(48.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha(controlsAlpha)
                    ) {
                        ControlsRow(
                            phase = ui.snapshot.phase,
                            isPaused = ui.snapshot.isPaused,
                            onStart = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.start(if (isIdle) idleSelectedPhase else TimerPhase.WORKING)
                            },
                            onPause = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.pause()
                            },
                            onResume = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.resume()
                            },
                            onReset = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.reset()
                            },
                            onSkip = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.skip()
                            },
                        )
                        Spacer(Modifier.height(32.dp))

                        VolumeRow(
                            playing = ui.audioPlaying,
                            volume = ui.settings.volume,
                            onToggle = {
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.toggleAudio()
                            },
                            onVolume = { vol: Float ->
                                lastTouchMillis = System.currentTimeMillis()
                                viewModel.setVolume(vol)
                            },
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(48.dp)
                    .alpha(controlsAlpha),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun resolveBackground(id: String): BackgroundModel {
    return when {
        id.startsWith("bundled:") -> {
            val bundled = BackgroundCatalog.byId(id.removePrefix("bundled:"))
                ?: BackgroundCatalog.default
            BackgroundModel.Bundled(bundled)
        }
        id.startsWith("image:") -> BackgroundModel.UserImage(Uri.parse(id.removePrefix("image:")))
        else -> BackgroundModel.Bundled(BackgroundCatalog.default)
    }
}

@Composable
private fun InteractivePhaseBadge(
    currentPhase: TimerPhase,
    isIdle: Boolean,
    idleSelectedPhase: TimerPhase,
    onToggle: () -> Unit,
) {
    val isFocus = if (isIdle) idleSelectedPhase == TimerPhase.WORKING else currentPhase == TimerPhase.WORKING
    val label = if (isFocus) stringResource(R.string.phase_working) else stringResource(R.string.phase_resting)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "‹ ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = " ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun QuickTimeAdjustDialog(
    isFocus: Boolean,
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onToggleMode: () -> Unit,
) {
    var selectedMinutes by remember(currentMinutes) { mutableIntStateOf(currentMinutes) }
    val maxMinutes = if (isFocus) 120 else 60
    val minMinutes = 1
    val presets = if (isFocus) listOf(15, 20, 25, 30, 45, 50, 60, 90) else listOf(3, 5, 10, 15, 20, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isFocus) stringResource(R.string.quick_edit_focus_title)
                           else stringResource(R.string.quick_edit_rest_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FilterChip(
                    selected = false,
                    onClick = onToggleMode,
                    label = {
                        Text(if (isFocus) "Rest" else "Focus")
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalIconButton(
                        onClick = { selectedMinutes = (selectedMinutes - 5).coerceAtLeast(minMinutes) },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Text("-5", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = { selectedMinutes = (selectedMinutes - 1).coerceAtLeast(minMinutes) },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Text("-1", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "$selectedMinutes min",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconButton(
                        onClick = { selectedMinutes = (selectedMinutes + 1).coerceAtMost(maxMinutes) },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Text("+1", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = { selectedMinutes = (selectedMinutes + 5).coerceAtMost(maxMinutes) },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Text("+5", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(16.dp))

                Slider(
                    value = selectedMinutes.toFloat(),
                    onValueChange = { selectedMinutes = it.toInt() },
                    valueRange = minMinutes.toFloat()..maxMinutes.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(presets) { preset ->
                        val isSelected = selectedMinutes == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMinutes = preset },
                            label = { Text("${preset}m") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedMinutes)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.quick_edit_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ControlsRow(
    phase: TimerPhase,
    isPaused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!phase.isRunning) {
            BigIconButton(Icons.Filled.PlayArrow, stringResource(R.string.action_start), onStart)
        } else {
            BigIconButton(
                if (isPaused) Icons.Filled.PlayArrow else Icons.Outlined.Pause,
                stringResource(if (isPaused) R.string.action_resume else R.string.action_pause),
                if (isPaused) onResume else onPause,
            )
        }
        Spacer(Modifier.width(24.dp))
        BigIconButton(Icons.Outlined.SkipNext, stringResource(R.string.action_skip), onSkip)
        Spacer(Modifier.width(24.dp))
        BigIconButton(Icons.Outlined.Stop, stringResource(R.string.action_reset), onReset)
    }
}

@Composable
private fun BigIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun VolumeRow(
    playing: Boolean,
    volume: Float,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                contentDescription = stringResource(
                    if (playing) R.string.action_pause else R.string.action_start
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolume,
            modifier = Modifier.width(180.dp),
        )
    }
}

@Composable
private fun SoundSliderRow(
    currentSelection: String,
    tracks: List<com.algosculptor.pomodoro.data.media.BundledTrack>,
    onSelectTrack: (String) -> Unit,
) {
    val currentTrackId = when {
        currentSelection == "off" -> "off"
        currentSelection.startsWith("bundled:") -> currentSelection.removePrefix("bundled:")
        else -> "off"
    }

    val availableTracks = listOf("off") + tracks.filter { it.id != "soft_chime" }.map { it.id }
    val currentIndex = availableTracks.indexOf(currentTrackId).let { if (it < 0) 0 else it }

    val label = if (currentTrackId == "off") {
        "Sound: Off"
    } else {
        val t = tracks.find { it.id == currentTrackId }
        if (t != null) "♫ ${stringResource(t.titleRes)}" else "Sound: Off"
    }

    FilterChip(
        selected = currentTrackId != "off",
        onClick = {
            val nextIndex = (currentIndex + 1) % availableTracks.size
            onSelectTrack(availableTracks[nextIndex])
        },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

@Composable
private fun BrightnessSliderRow(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(220.dp)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = "☼",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = 0.05f..1.0f,
            modifier = Modifier.weight(1f),
        )
    }
}


