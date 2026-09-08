package com.mahasvan.karen.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import com.mahasvan.karen.R

/**
 * Hands a [ToolAction] to the platform. Karen never places a call or rings an alarm itself.
 *
 * Every intent is built by the owning tool from arguments that tool validated. Karen never
 * receives an intent from another app and never forwards one, so there is no redirection
 * surface to guard here.
 *
 * No FLAG_ACTIVITY_NEW_TASK: the caller is the Activity, so the target belongs in Karen's
 * task and Back returns here rather than stranding the user in a detached task.
 */
fun Context.perform(action: ToolAction) {
    try {
        startActivity(action.intent(this))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_handler_app, Toast.LENGTH_SHORT).show()
    }
}
