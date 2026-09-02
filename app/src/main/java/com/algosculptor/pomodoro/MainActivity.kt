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
import android.content.pm.ActivityInfo
import android.hardware.SensorManager
import android.os.Build
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.algosculptor.pomodoro.data.settings.SettingsRepository
import com.algosculptor.pomodoro.service.TimerForegroundService
import com.algosculptor.pomodoro.timer.TimerEngine
import com.algosculptor.pomodoro.ui.screens.settings.SettingsScreen
import com.algosculptor.pomodoro.ui.screens.timer.TimerScreen
import com.algosculptor.pomodoro.ui.theme.PomodoroTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engine: TimerEngine
    @Inject lateinit var settingsRepository: SettingsRepository

    private var orientationListener: OrientationEventListener? = null
    private var lastValidOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var isImmersiveActive: Boolean = true

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* FGS still ticks if denied; fall back silently per plan §Phase 6. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Enable show over lock screen and turn screen on programmatically
        configureLockScreenAndScreenOn(showWhenLocked = true, turnScreenOn = true)

        // Setup hardware sensor orientation listener so the desk timer auto-rotates
        // even if system auto-rotate lock is enabled.
        setupAutoRotateListener()

        // Hide system bars immediately whenever window insets change (e.g. keyguard transition)
        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            if (isImmersiveActive) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
            insets
        }

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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    AppNav(setImmersive = ::setFullscreenImmersive)
                }
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

    private fun setupAutoRotateListener() {
        orientationListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val targetOrientation = when {
                    orientation in 45..135 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    orientation in 225..315 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    orientation in 315..360 || orientation in 0..45 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    orientation in 135..225 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    else -> requestedOrientation
                }

                if (targetOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED && requestedOrientation != targetOrientation) {
                    lastValidOrientation = targetOrientation
                    requestedOrientation = targetOrientation
                }
            }
        }
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }
    }

    private fun configureLockScreenAndScreenOn(showWhenLocked: Boolean, turnScreenOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showWhenLocked)
            setTurnScreenOn(turnScreenOn)
        }
        @Suppress("DEPRECATION")
        if (showWhenLocked) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
            window.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }
        @Suppress("DEPRECATION")
        if (turnScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun setFullscreenImmersive(immersive: Boolean) {
        isImmersiveActive = immersive
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (immersive) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isImmersiveActive) setFullscreenImmersive(true)
        if (lastValidOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            requestedOrientation = lastValidOrientation
        }
    }

    override fun onResume() {
        super.onResume()
        if (isImmersiveActive) setFullscreenImmersive(true)
        orientationListener?.enable()
        if (lastValidOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            requestedOrientation = lastValidOrientation
        }
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (isImmersiveActive) {
            setFullscreenImmersive(true)
        }
        orientationListener?.enable()
        if (lastValidOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            requestedOrientation = lastValidOrientation
        }
    }

    override fun onDestroy() {
        orientationListener?.disable()
        if (!engine.snapshot.value.phase.isRunning) {
            TimerForegroundService.stop(this)
        }
        super.onDestroy()
    }
}

private const val ROUTE_TIMER = "timer"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun AppNav(setImmersive: (Boolean) -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = ROUTE_TIMER) {
        composable(ROUTE_TIMER) {
            DisposableEffect(Unit) {
                setImmersive(true)
                onDispose { }
            }
            TimerScreen(onOpenSettings = {
                setImmersive(false)
                nav.navigate(ROUTE_SETTINGS)
            })
        }
        composable(ROUTE_SETTINGS) {
            DisposableEffect(Unit) {
                setImmersive(false)
                onDispose { setImmersive(true) }
            }
            SettingsScreen(onBack = {
                setImmersive(true)
                nav.popBackStack()
            })
        }
    }
}
