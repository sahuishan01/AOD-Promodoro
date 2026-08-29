package com.aod.pomodromo.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Immutable snapshot consumed by UI and the foreground-service notification. */
data class EngineSnapshot(
    val phase: TimerPhase = TimerPhase.IDLE,
    val remaining: Duration = Duration.ZERO,
    val phaseTotal: Duration = Duration.ZERO,
    val isPaused: Boolean = false,
    val completedCycles: Int = 0,
) {
    val progress: Float
        get() = if (phaseTotal <= Duration.ZERO) 0f
        else (1f - (remaining / phaseTotal).toFloat()).coerceIn(0f, 1f)
}

/** Validated timer configuration. Rejects out-of-bounds values at the boundary. */
data class TimerConfig(
    val work: Duration = 25.minutes,
    val rest: Duration = 5.minutes,
) {
    init {
        require(work in MIN_WORK..MAX_WORK) { "work duration out of range" }
        require(rest in MIN_REST..MAX_REST) { "rest duration out of range" }
    }

    companion object {
        val MIN_WORK = 1.minutes
        val MAX_WORK = 120.minutes
        val MIN_REST = 1.minutes
        val MAX_REST = 60.minutes
    }
}

/**
 * JVM-pure pomodoro state machine: IDLE -> WORKING -> RESTING -> WORKING ... -> COMPLETE (on reset).
 *
 * Ticking uses a monotonic [TickClock] with drift correction: each phase computes an absolute
 * end timestamp once, and every tick recomputes remaining time from the clock rather than
 * decrementing a counter — so delayed or coalesced ticks never accumulate error.
 */
class TimerEngine(
    private val scope: CoroutineScope,
    private val clock: TickClock = TickClock.SYSTEM,
) {
    private val _snapshot = MutableStateFlow(EngineSnapshot())
    val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()

    private var config = TimerConfig()
    private var tickJob: Job? = null
    private var phaseEndAtMillis = 0L

    fun configure(work: Duration, rest: Duration) {
        val newConfig = TimerConfig(work, rest)
        config = newConfig
        if (!_snapshot.value.phase.isRunning) {
            _snapshot.update { it.copy(phase = TimerPhase.IDLE, remaining = Duration.ZERO, phaseTotal = Duration.ZERO, isPaused = false) }
        }
    }

    fun start() {
        val s = _snapshot.value
        if (s.phase.isRunning && !s.isPaused) return
        if (s.isPaused) { resume(); return }
        enterPhase(TimerPhase.WORKING, config.work)
    }

    fun pause() {
        val s = _snapshot.value
        if (!s.phase.isRunning || s.isPaused) return
        tickJob?.cancel()
        _snapshot.update { it.copy(isPaused = true, remaining = remainingFromClock()) }
    }

    fun resume() {
        val s = _snapshot.value
        if (!s.phase.isRunning || !s.isPaused) return
        phaseEndAtMillis = clock.nowMillis() + s.remaining.inWholeMilliseconds
        _snapshot.update { it.copy(isPaused = false) }
        launchTicker()
    }

    fun reset() {
        tickJob?.cancel()
        _snapshot.value = EngineSnapshot()
    }

    /** Skips to the next phase (WORKING -> RESTING, RESTING -> WORKING). */
    fun skipPhase() {
        when (_snapshot.value.phase) {
            TimerPhase.WORKING -> enterPhase(TimerPhase.RESTING, config.rest)
            TimerPhase.RESTING -> enterPhase(TimerPhase.WORKING, config.work)
            else -> Unit
        }
    }

    private fun enterPhase(phase: TimerPhase, duration: Duration) {
        tickJob?.cancel()
        phaseEndAtMillis = clock.nowMillis() + duration.inWholeMilliseconds
        _snapshot.update {
            it.copy(
                phase = phase,
                remaining = duration,
                phaseTotal = duration,
                isPaused = false,
                completedCycles = if (phase == TimerPhase.WORKING && it.phase == TimerPhase.RESTING)
                    it.completedCycles + 1 else it.completedCycles,
            )
        }
        launchTicker()
    }

    private fun remainingFromClock(): Duration =
        (phaseEndAtMillis - clock.nowMillis()).coerceAtLeast(0L).milliseconds

    private fun launchTicker() {
        tickJob = scope.launch {
            while (true) {
                val remaining = remainingFromClock()
                if (remaining <= Duration.ZERO) {
                    advancePhase()
                    return@launch
                }
                _snapshot.update { it.copy(remaining = remaining) }
                // Tick at ~1s cadence, but never sleep past the phase end.
                delay(minOf(1_000L, remaining.inWholeMilliseconds))
            }
        }
    }

    private fun advancePhase() {
        when (_snapshot.value.phase) {
            TimerPhase.WORKING -> enterPhase(TimerPhase.RESTING, config.rest)
            TimerPhase.RESTING -> enterPhase(TimerPhase.WORKING, config.work)
            else -> Unit
        }
    }
}
