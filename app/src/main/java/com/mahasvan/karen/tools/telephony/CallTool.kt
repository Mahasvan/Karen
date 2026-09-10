package com.mahasvan.karen.tools.telephony

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolParameter
import com.mahasvan.karen.tools.ToolResult

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

    override fun execute(context: Context, arguments: Map<String, String>): ToolResult {
        val target = arguments["target"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ToolResult(false, "Invalid target")
        val digits = target.filter { it.isDigit() || it == '+' }
        val intent = if (digits.length >= MIN_DIALABLE_DIGITS) {
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
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Dial doesn't produce a card — the dialer UI is the visual confirmation.
            ToolResult(true, "Opening dialer for $target")
        } catch (_: ActivityNotFoundException) {
            ToolResult(false, "No dialer app available")
        }
    }
}
