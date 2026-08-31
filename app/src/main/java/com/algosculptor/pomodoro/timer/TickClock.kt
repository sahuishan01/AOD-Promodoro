package com.algosculptor.pomodoro.timer

/**
 * Monotonic clock in milliseconds. Production uses [System.nanoTime];
 * tests inject a fake driven by the virtual-time scheduler so the engine
 * is immune to wall-clock edits and fully deterministic under test.
 */
fun interface TickClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = TickClock { System.nanoTime() / 1_000_000L }
    }
}
