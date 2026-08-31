package com.algosculptor.pomodoro.timer

/** Pomodoro phases. Pausing is a sub-state carried on the snapshot, not a phase. */
enum class TimerPhase {
    IDLE,
    WORKING,
    RESTING,
    COMPLETE;

    val isRunning: Boolean get() = this == WORKING || this == RESTING
}
