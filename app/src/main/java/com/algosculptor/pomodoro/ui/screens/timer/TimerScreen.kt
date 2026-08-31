package com.algosculptor.pomodoro.ui.screens.timer

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.res.Configuration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import kotlin.time.Duration.Companion.minutes
import com.algosculptor.pomodoro.ui.components.BackgroundModel
import com.algosculptor.pomodoro.ui.components.BackgroundSurface

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

    // Haptic phase cue (opt-in).
    var lastPhase by remember { mutableStateOf(ui.snapshot.phase) }
    if (ui.settings.hapticsEnabled && ui.snapshot.phase != lastPhase) {
        LaunchedEffect(ui.snapshot.phase) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastPhase = ui.snapshot.phase
    }

    // Auto-dim: after 5 minutes in the same phase, the clock dims to 35%.
    // Running phases recompose every second, so this stays fresh without a timer.
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

    BackgroundSurface(model = backgroundModel) {
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
                    modifier = Modifier.weight(1.1f),
                ) {
                    PhaseBadge(ui.snapshot.phase)
                    Spacer(Modifier.height(8.dp))
                    AodClock(
                        text = TimeFormatter.formatClock(
                            if (ui.snapshot.phase == TimerPhase.IDLE) ui.settings.workMinutes.minutes
                            else ui.snapshot.remaining
                        ),
                        contentDescription = stringResource(R.string.cd_timer_clock),
                        dimmed = dimmed,
                        fontSize = 72.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Cycle ${ui.snapshot.completedCycles + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(0.9f),
                ) {
                    ControlsRow(
                        phase = ui.snapshot.phase,
                        isPaused = ui.snapshot.isPaused,
                        onStart = viewModel::start,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onReset = viewModel::reset,
                        onSkip = viewModel::skip,
                    )
                    Spacer(Modifier.height(20.dp))
                    VolumeRow(
                        playing = ui.audioPlaying,
                        volume = ui.settings.volume,
                        onToggle = viewModel::toggleAudio,
                        onVolume = viewModel::setVolume,
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
                PhaseBadge(ui.snapshot.phase)
                Spacer(Modifier.height(16.dp))

                AodClock(
                    text = TimeFormatter.formatClock(
                        if (ui.snapshot.phase == TimerPhase.IDLE) ui.settings.workMinutes.minutes
                        else ui.snapshot.remaining
                    ),
                    contentDescription = stringResource(R.string.cd_timer_clock),
                    dimmed = dimmed,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cycle ${ui.snapshot.completedCycles + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(48.dp))

                ControlsRow(
                    phase = ui.snapshot.phase,
                    isPaused = ui.snapshot.isPaused,
                    onStart = viewModel::start,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onReset = viewModel::reset,
                    onSkip = viewModel::skip,
                )
                Spacer(Modifier.height(32.dp))

                VolumeRow(
                    playing = ui.audioPlaying,
                    volume = ui.settings.volume,
                    onToggle = viewModel::toggleAudio,
                    onVolume = viewModel::setVolume,
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(48.dp),
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
private fun PhaseBadge(phase: TimerPhase) {
    val label = when (phase) {
        TimerPhase.WORKING -> stringResource(R.string.phase_working)
        TimerPhase.RESTING -> stringResource(R.string.phase_resting)
        TimerPhase.COMPLETE -> stringResource(R.string.phase_complete)
        TimerPhase.IDLE -> stringResource(R.string.phase_idle)
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics {
            contentDescription = label
        },
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
