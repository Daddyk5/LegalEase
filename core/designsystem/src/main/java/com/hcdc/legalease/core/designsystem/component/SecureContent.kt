package com.hcdc.legalease.core.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Marks the window secure (no screenshots, screen recording, or recents thumbnail) while the calling
 * screen is shown. Use it on every screen that displays contract content. Nested and overlapping
 * screens are counted, so leaving one secure screen never unprotects another.
 */
@Composable
fun SecureContent() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        SecureWindows.acquire(activity)
        onDispose { SecureWindows.release(activity) }
    }
}

private object SecureWindows {
    private val holders = mutableMapOf<Activity, Int>()

    fun acquire(activity: Activity) {
        val count = holders.getOrDefault(activity, 0)
        if (count == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        holders[activity] = count + 1
    }

    fun release(activity: Activity) {
        val count = holders.getOrDefault(activity, 0) - 1
        if (count <= 0) {
            holders.remove(activity)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holders[activity] = count
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
