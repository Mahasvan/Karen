package com.mahasvan.karen.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import com.mahasvan.karen.R

/**
 * Turns an [AssistantAction] into a system intent. Karen never places a call or rings an
 * alarm itself; it hands off and the platform app takes over.
 *
 * Dialling uses ACTION_DIAL rather than ACTION_CALL on purpose: it needs no runtime
 * permission and leaves the final press to the user.
 *
 * Every intent here is implicit and built from our own data. Karen never receives an intent
 * from another app and never forwards one, so there is no redirection surface to guard.
 */
fun Context.perform(action: AssistantAction) {
    val intent = when (action) {
        is AssistantAction.Dial -> dialIntent(action.query)

        is AssistantAction.SetAlarm -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

        is AssistantAction.StartTimer -> Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

        is AssistantAction.CreateEvent -> Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.endMillis)
        }
    }

    // No FLAG_ACTIVITY_NEW_TASK: the caller is the Activity, so the target belongs in Karen's
    // task and Back returns here rather than stranding the user in a detached task.
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_handler_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A dialable string goes straight to the dialer pre-filled. A contact name instead opens
 * the contacts app filtered to that name, which needs no READ_CONTACTS on our side.
 */
private fun dialIntent(query: String): Intent {
    val digits = query.filter { it.isDigit() || it == '+' }
    return if (digits.length >= 3) {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
    } else {
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(query),
            )
        }
    }
}
