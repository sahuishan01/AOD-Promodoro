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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(BackgroundCatalog.all) { bg ->
                val selected = settings.backgroundId == "bundled:${bg.id}"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.selectBundledBackground(bg.id) },
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(
                                Brush.verticalGradient(bg.colors.map { Color(it) }),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        bg.displayName,
                        style = MaterialTheme.typography.labelLarge,
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
            Text(stringResource(R.string.settings_pick_image), style = MaterialTheme.typography.labelLarge)
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
        Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
            Text(stringResource(R.string.settings_pick_audio), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.settings_volume))
        Slider(
            value = settings.volume,
            onValueChange = viewModel::setVolume,
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
        Text(
            stringResource(R.string.settings_minutes_format, value),
            style = MaterialTheme.typography.labelLarge,
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
            .clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
