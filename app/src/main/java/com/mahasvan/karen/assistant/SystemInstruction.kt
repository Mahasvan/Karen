package com.mahasvan.karen.assistant

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The assistant's persona and its wall-clock reference, shared by every backend
 * so they describe the same assistant. Without the clock line a model cannot turn
 * "set an alarm for three hours from now" into an hour/minute pair.
 *
 * Returned as lines because backends frame them differently — LiteRT wants one
 * `Content.Text` per line, an OpenAI-compatible endpoint wants a single system
 * message. Built per call so a backend that rebuilds its prompt each turn gets a
 * fresh clock rather than one frozen at session start.
 */
fun systemInstructionLines(): List<String> {
    val now = LocalDateTime.now()
    val dateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    val dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE"))
    return listOf(
        "You are a helpful, concise assistant. You can control the device's clock, " +
                "calendar, and dialer through function calls.",
        "Current date and time: $dateTime ($dayOfWeek).",
    )
}
