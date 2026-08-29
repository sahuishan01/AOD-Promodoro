package com.aod.pomodromo.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private fun TestScope.engine(): TimerEngine =
        TimerEngine(this, TickClock { currentTime })

    @Test
    fun `start enters WORKING with full work duration`() = runTest {
        val e = engine()
        e.configure(25.minutes, 5.minutes)
        e.start()
        assertEquals(TimerPhase.WORKING, e.snapshot.value.phase)
        assertEquals(25.minutes, e.snapshot.value.phaseTotal)
    }

    @Test
    fun `full cycle auto-advances WORK then REST`() = runTest {
        val e = engine()
        e.configure(1.minutes, 1.minutes)
        e.start()
        // Virtual time drives the monotonic fake clock — no wall-clock dependency.
        kotlinx.coroutines.test.advanceTimeBy(61.seconds)
        assertEquals(TimerPhase.RESTING, e.snapshot.value.phase)
    }

    @Test
    fun `pause freezes remaining and resume continues`() = runTest {
        val e = engine()
        e.configure(10.minutes, 5.minutes)
        e.start()
        kotlinx.coroutines.test.advanceTimeBy(30.seconds)
        e.pause()
        val frozenRemaining = e.snapshot.value.remaining
        kotlinx.coroutines.test.advanceTimeBy(60.seconds)
        assertEquals(frozenRemaining, e.snapshot.value.remaining)
        assertTrue(e.snapshot.value.isPaused)
        e.resume()
        assertFalse(e.snapshot.value.isPaused)
    }

    @Test
    fun `reset returns to IDLE`() = runTest {
        val e = engine()
        e.start()
        e.reset()
        assertEquals(TimerPhase.IDLE, e.snapshot.value.phase)
        assertFalse(e.snapshot.value.isPaused)
    }

    @Test
    fun `skip moves WORK to REST and REST to WORK, counting completed cycles`() = runTest {
        val e = engine()
        e.configure(25.minutes, 5.minutes)
        e.start()
        e.skipPhase()
        assertEquals(TimerPhase.RESTING, e.snapshot.value.phase)
        e.skipPhase()
        assertEquals(TimerPhase.WORKING, e.snapshot.value.phase)
        assertEquals(1, e.snapshot.value.completedCycles)
    }

    @Test
    fun `config validation rejects out of range durations`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TimerConfig(121.minutes, 5.minutes)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TimerConfig(25.minutes, 61.minutes)
        }
    }

    @Test
    fun `drifted ticks never accumulate error`() = runTest {
        // Even if a tick fires late, remaining is recomputed from the absolute
        // phase-end timestamp — error cannot compound.
        val e = engine()
        e.configure(2.minutes, 1.minutes)
        e.start()
        kotlinx.coroutines.test.advanceTimeBy(90.seconds)
        val remaining = e.snapshot.value.remaining
        assertEquals(30.seconds, remaining)
    }
}
