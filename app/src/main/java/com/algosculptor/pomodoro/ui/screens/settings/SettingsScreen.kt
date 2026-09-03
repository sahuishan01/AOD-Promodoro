package com.algosculptor.pomodoro.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.algosculptor.pomodoro.R
import com.algosculptor.pomodoro.data.background.BackgroundCatalog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onImagePicked(uri) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onAudioPicked(uri) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val msg = when (event) {
                SettingsViewModel.Event.MediaRejected -> R.string.settings_media_rejected
                SettingsViewModel.Event.MediaRevoked -> R.string.settings_media_revoked
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.settings_work_duration))
            DurationSlider(
                value = settings.workMinutes,
                range = 1..120,
                onChange = viewModel::setWorkMinutes,
            )
            SectionTitle(stringResource(R.string.settings_rest_duration))
            DurationSlider(
                value = settings.restMinutes,
                range = 1..60,
                onChange = viewModel::setRestMinutes,
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_background))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(BackgroundCatalog.all) { bg ->
                    val selected = settings.backgroundId == "bundled:${bg.id}"
                    val accentColor = Color(bg.accentColor)
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.selectBundledBackground(bg.id) },
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(72.dp)
                                .background(bg.toBrush(), shape = MaterialTheme.shapes.medium)
                                .then(
                                    if (selected) Modifier.border(
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                        shape = MaterialTheme.shapes.medium,
                                    ) else Modifier.border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "25:00",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = accentColor,
                                )
                                Text(
                                    text = "FOCUS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor.copy(alpha = 0.7f),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            bg.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }) {
                Text(
                    stringResource(R.string.settings_pick_image),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_audio))
            AudioOption(
                label = stringResource(R.string.settings_audio_none),
                selected = settings.audioSelection == "off",
                onSelect = viewModel::selectAudioOff,
            )
            viewModel.bundledTracks.forEach { track ->
                AudioOption(
                    label = stringResource(track.titleRes),
                    selected = settings.audioSelection == "bundled:${track.id}",
                    onSelect = { viewModel.selectBundledTrack(track) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                Text(
                    stringResource(R.string.settings_pick_audio),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_volume))
            Slider(
                value = settings.volume,
                onValueChange = viewModel::setVolume,
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_phase_end_sound))
            AudioOption(
                label = stringResource(R.string.settings_phase_end_sound_none),
                selected = settings.phaseEndSound == "off",
                onSelect = { viewModel.setPhaseEndSound("off") },
            )
            AudioOption(
                label = stringResource(R.string.settings_phase_end_sound_chime),
                selected = settings.phaseEndSound == "chime",
                onSelect = { viewModel.setPhaseEndSound("chime") },
            )

            Spacer(Modifier.height(24.dp))
            ToggleRow(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = settings.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
            ToggleRow(
                label = stringResource(R.string.settings_show_when_locked),
                checked = settings.showWhenLocked,
                onChange = viewModel::setShowWhenLocked,
            )
            ToggleRow(
                label = stringResource(R.string.settings_auto_dim),
                checked = settings.autoDimEnabled,
                onChange = viewModel::setAutoDim,
            )
            ToggleRow(
                label = stringResource(R.string.settings_haptics),
                checked = settings.hapticsEnabled,
                onChange = viewModel::setHaptics,
            )

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_DREAM_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                            context.startActivity(fallbackIntent)
                        } catch (ignored: Exception) { }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Configure System Screen Saver / AOD",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DurationSlider(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.settings_minutes_format, value),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun AudioOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
