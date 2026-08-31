package com.algosculptor.pomodoro

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.os.Build
import android.view.WindowManager
import com.algosculptor.pomodoro.data.settings.SettingsRepository
import com.algosculptor.pomodoro.service.TimerForegroundService
import com.algosculptor.pomodoro.timer.TimerEngine
import com.algosculptor.pomodoro.ui.screens.settings.SettingsScreen
import com.algosculptor.pomodoro.ui.screens.timer.TimerScreen
import com.algosculptor.pomodoro.ui.theme.PomodoroTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engine: TimerEngine
    @Inject lateinit var settingsRepository: SettingsRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* FGS still ticks if denied; fall back silently per plan §Phase 6. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable show over lock screen and turn screen on programmatically
        configureLockScreenAndScreenOn(showWhenLocked = true, turnScreenOn = true)

        // Observe user settings to dynamically control AOD keep-screen-on and lock screen visibility
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                configureLockScreenAndScreenOn(
                    showWhenLocked = settings.showWhenLocked,
                    turnScreenOn = true,
                )
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        // Start/stop the keep-alive FGS in lockstep with engine run state.
        lifecycleScope.launch {
            engine.snapshot
                .map { it.phase }
                .distinctUntilChanged()
                .collect { phase ->
                    if (phase.isRunning) {
                        TimerForegroundService.start(this@MainActivity)
                        // Illuminate / wake the screen on phase start/transition
                        configureLockScreenAndScreenOn(showWhenLocked = true, turnScreenOn = true)
                    }
                }
        }

        setContent {
            PomodoroTheme {
                AppNav()
            }
        }

        // Request notification permission once (rationale: timer status while backgrounded).
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        // Start the keep-alive service immediately if a phase is already running
        // (e.g., activity recreated after process restore).
        if (engine.snapshot.value.phase.isRunning) {
            TimerForegroundService.start(this)
        }
    }

    private fun configureLockScreenAndScreenOn(showWhenLocked: Boolean, turnScreenOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showWhenLocked)
            setTurnScreenOn(turnScreenOn)
        } else {
            @Suppress("DEPRECATION")
            if (showWhenLocked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
            @Suppress("DEPRECATION")
            if (turnScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    override fun onDestroy() {
        if (!engine.snapshot.value.phase.isRunning) {
            TimerForegroundService.stop(this)
        }
        super.onDestroy()
    }
}

private const val ROUTE_TIMER = "timer"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = ROUTE_TIMER) {
        composable(ROUTE_TIMER) {
            TimerScreen(onOpenSettings = { nav.navigate(ROUTE_SETTINGS) })
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
