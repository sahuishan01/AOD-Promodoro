package com.algosculptor.pomodoro.data.media

import android.content.Context
import android.net.Uri
import com.algosculptor.pomodoro.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BundledTrack(
    val id: String,
    val titleRes: Int,
    val rawRes: Int,
)

/** Bundled ambient tracks shipped in res/raw (locally synthesized, license-clean). */
@Singleton
class AmbientAudioRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val tracks: List<BundledTrack> = listOf(
        BundledTrack("ocean_waves", R.string.track_ocean_waves, R.raw.ocean_waves),
        BundledTrack("soft_rainfall", R.string.track_soft_rainfall, R.raw.soft_rainfall),
        BundledTrack("gentle_stream", R.string.track_gentle_stream, R.raw.gentle_stream),
        BundledTrack("zen_garden", R.string.track_zen_garden, R.raw.zen_garden),
        BundledTrack("rain_drift", R.string.track_rain_drift, R.raw.rain_drift),
        BundledTrack("night_pad", R.string.track_night_pad, R.raw.night_pad),
        BundledTrack("forest_stream", R.string.track_forest_stream, R.raw.forest_stream),
        BundledTrack("deep_focus", R.string.track_deep_focus, R.raw.deep_focus),
        BundledTrack("cosmic_synth", R.string.track_cosmic_synth, R.raw.cosmic_synth),
        BundledTrack("soft_chime", R.string.track_soft_chime, R.raw.soft_chime),
    )

    fun byId(id: String): BundledTrack? = tracks.find { it.id == id }

    fun completionChimeUri(): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.soft_chime}")

    fun uriFor(track: BundledTrack): Uri =
        Uri.parse("android.resource://${context.packageName}/${track.rawRes}")
}
