package com.algosculptor.pomodoro.service

import android.content.Intent
import android.service.dreams.DreamService
import com.algosculptor.pomodoro.MainActivity

/**
 * System Screensaver / DreamService integration allowing AOD Pomodoro
 * to be selected in Android Settings -> Display -> Screen saver.
 */
class AodDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = false
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }
}
