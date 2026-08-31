package com.algosculptor.pomodoro.data.media

import android.content.Context
import android.net.Uri
import com.algosculptor.pomodoro.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import androidx.media3.datasource.RawResourceDataSource

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
        BundledTrack("rain_drift", R.string.track_rain_drift, R.raw.rain_drift),
        BundledTrack("night_pad", R.string.track_night_pad, R.raw.night_pad),
    )

    fun byId(id: String): BundledTrack? = tracks.find { it.id == id }

    fun uriFor(track: BundledTrack): Uri =
        RawResourceDataSource.buildRawResourceUri(track.rawRes)
}
