package com.mahasvan.karen.tools.telephony

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolAction
import com.mahasvan.karen.tools.ToolParameter

const val CALL_TOOL = "dial"

/** Shortest thing worth treating as a phone number rather than a contact name. */
private const val MIN_DIALABLE_DIGITS = 3

object CallTool : Tool {

    override val name = CALL_TOOL

    override val description =
        "Open the dialer pre-filled with a phone number, or look up a contact by name. " +
            "Never places the call; the user presses dial."

    override val parameters = listOf(
        ToolParameter("target", "A phone number or a contact name.", required = true),
    )

    override fun parse(arguments: Map<String, String>): ToolAction? {
        val target = arguments["target"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return CallAction(target)
    }
}

/**
 * Dialling uses ACTION_DIAL rather than ACTION_CALL on purpose: it needs no runtime
 * permission and leaves the final press to the user. That matters more now that the target
 * can originate from a model — a prompt-injected model still cannot place a call.
 */
@Immutable
data class CallAction(val target: String) : ToolAction {

    override fun intent(context: Context): Intent {
        val digits = target.filter { it.isDigit() || it == '+' }
        return if (digits.length >= MIN_DIALABLE_DIGITS) {
            // Rebuilt from the filtered digits, never from the raw model string, so nothing
            // else can ride along in the tel: URI.
            Intent(Intent.ACTION_DIAL, "tel:$digits".toUri())
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.withAppendedPath(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                    Uri.encode(target),
                )
            }
        }
    }
}
