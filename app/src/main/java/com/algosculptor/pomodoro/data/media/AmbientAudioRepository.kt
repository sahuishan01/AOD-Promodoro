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
        BundledTrack("campfire", R.string.track_campfire, R.raw.campfire),
        BundledTrack("clock_ticking", R.string.track_clock_ticking, R.raw.clock_ticking),
        BundledTrack("brown_noise", R.string.track_brown_noise, R.raw.brown_noise),
        BundledTrack("forest_birds", R.string.track_forest_birds, R.raw.forest_birds),
        BundledTrack("zen_garden", R.string.track_zen_garden, R.raw.zen_garden),
        BundledTrack("soft_chime", R.string.track_soft_chime, R.raw.soft_chime),
    )

    fun byId(id: String): BundledTrack? = tracks.find { it.id == id }

    fun completionChimeUri(): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.soft_chime}")

    fun uriFor(track: BundledTrack): Uri =
        Uri.parse("android.resource://${context.packageName}/${track.rawRes}")
}
