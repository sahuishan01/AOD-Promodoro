package com.aod.pomodromo.core.util

import kotlin.time.Duration

/** Formats a duration as fixed-width "MM:SS" (hours collapse into minutes). */
object TimeFormatter {
    fun formatClock(d: Duration): String {
        val totalSeconds = d.inWholeSeconds.coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
