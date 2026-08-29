package com.aod.pomodromo.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aod.pomodromo.MainActivity
import com.aod.pomodromo.PomodoroApp
import com.aod.pomodromo.R
import com.aod.pomodromo.core.util.TimeFormatter
import com.aod.pomodromo.timer.TimerEngine
import com.aod.pomodromo.timer.TimerPhase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keep-alive service for background timer execution (DEVELOPMENT-PLAN.md §Phase 6).
 *
 * The [TimerEngine] is an app-scoped singleton; this service only keeps the process
 * alive while a phase is running and mirrors engine state into a LOW-priority
 * notification, throttled to phase changes or 5s cadence.
 */
@AndroidEntryPoint
class TimerForegroundService : Service() {

    @Inject lateinit var engine: TimerEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotifiedBucket = -1L
    private var lastNotifiedPhase: TimerPhase? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            ACTION_SKIP -> engine.skipPhase()
            ACTION_RESET -> { engine.reset(); stopSelf() }
            else -> startObserving()
        }
        return START_STICKY
    }

    private fun startObserving() {
        startForegroundWithType(buildNotification(engine.snapshot.value))
        serviceScope.launch {
            engine.snapshot.collect { snap ->
                if (!snap.phase.isRunning) {
                    stopSelf()
                    return@collect
                }
                // Throttle: notify on phase change or when the 5-second bucket flips.
                val bucket = snap.remaining.inWholeSeconds / 5
                if (snap.phase != lastNotifiedPhase || bucket != lastNotifiedBucket) {
                    lastNotifiedPhase = snap.phase
                    lastNotifiedBucket = bucket
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(snap))
                }
            }
        }
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(snap: com.aod.pomodromo.timer.EngineSnapshot): Notification {
        val phaseLabel = getString(
            when (snap.phase) {
                TimerPhase.WORKING -> R.string.phase_working
                TimerPhase.RESTING -> R.string.phase_resting
                TimerPhase.COMPLETE -> R.string.phase_complete
                TimerPhase.IDLE -> R.string.phase_idle
            }
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, PomodoroApp.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notif_title_format, phaseLabel, TimeFormatter.formatClock(snap.remaining)))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)

        if (snap.isPaused) {
            builder.addAction(0, getString(R.string.action_resume), actionPendingIntent(ACTION_RESUME, 1))
        } else {
            builder.addAction(0, getString(R.string.action_pause), actionPendingIntent(ACTION_PAUSE, 2))
        }
        builder.addAction(0, getString(R.string.action_skip), actionPendingIntent(ACTION_SKIP, 3))
        builder.addAction(0, getString(R.string.action_reset), actionPendingIntent(ACTION_RESET, 4))
        return builder.build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, TimerForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_PAUSE = "com.aod.pomodromo.action.PAUSE"
        const val ACTION_RESUME = "com.aod.pomodromo.action.RESUME"
        const val ACTION_SKIP = "com.aod.pomodromo.action.SKIP"
        const val ACTION_RESET = "com.aod.pomodromo.action.RESET"

        fun start(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerForegroundService::class.java))
        }
    }
}
