package com.aod.pomodromo

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
import com.aod.pomodromo.service.TimerForegroundService
import com.aod.pomodromo.timer.TimerEngine
import com.aod.pomodromo.ui.screens.settings.SettingsScreen
import com.aod.pomodromo.ui.screens.timer.TimerScreen
import com.aod.pomodromo.ui.theme.PomodromoTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engine: TimerEngine

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* FGS still ticks if denied; fall back silently per plan §Phase 6. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start/stop the keep-alive FGS in lockstep with engine run state.
        lifecycleScope.launch {
            engine.snapshot
                .map { it.phase.isRunning }
                .distinctUntilChanged()
                .collect { running ->
                    if (running) TimerForegroundService.start(this@MainActivity)
                }
        }

        setContent {
            PomodromoTheme {
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
