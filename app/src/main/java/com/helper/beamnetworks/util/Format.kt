package com.helper.beamnetworks.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

private val symbols = DecimalFormatSymbols(Locale.US)
private val twoDecimals = DecimalFormat("#,##0.00", symbols)
private val whole = DecimalFormat("#,##0", symbols)

/** "Ksh 8,000.00", "-Ksh 334.00". */
fun ksh(amount: Double): String = (if (amount < -0.004) "-" else "") + "Ksh " + twoDecimals.format(abs(amount))

/** "Ksh 8,000", rounded. */
fun kshShort(amount: Double): String = (if (amount <= -0.5) "-" else "") + "Ksh " + whole.format(Math.round(abs(amount)))

fun parseMoney(value: String?): Double = value?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

/** Amount typed by a person; null when it isn't a positive number. */
fun parseAmountInput(value: String): Double? = value.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 }

fun moneyString(amount: Double): String = String.format(Locale.US, "%.2f", amount)

val zone: ZoneId get() = ZoneId.systemDefault()

/** Parses API datetimes (ISO with offset) and plain dates. */
fun parseDateTime(value: String?): ZonedDateTime? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).atZoneSameInstant(zone)
    } catch (e: DateTimeParseException) {
        try {
            LocalDate.parse(value.take(10)).atStartOfDay(zone)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val hourMinute = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val legacyDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

/** "Today · 14:30" or "26 Oct · 10:00". */
fun whenLabel(time: ZonedDateTime?, today: LocalDate = LocalDate.now(zone)): String {
    if (time == null) return "Not scheduled"
    val local = time.withZoneSameInstant(zone)
    val day = when (local.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> local.format(dayMonth)
    }
    return "$day · ${local.format(hourMinute)}"
}

/** "Today", "Yesterday" or "23 Oct". */
fun shortDate(date: LocalDate?, today: LocalDate = LocalDate.now(zone)): String = when (date) {
    null -> ""
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayMonth)
}

fun longDate(date: LocalDate): String = date.format(dayMonthYear)

/** "Just now", "2h ago", "Yesterday", "20 Oct". */
fun relativeAge(time: ZonedDateTime?, now: ZonedDateTime = ZonedDateTime.now(zone)): String {
    if (time == null) return ""
    val minutes = Duration.between(time, now).toMinutes()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 && time.toLocalDate() == now.toLocalDate() -> "${minutes / 60}h ago"
        else -> shortDate(time.withZoneSameInstant(zone).toLocalDate(), now.toLocalDate())
    }
}

/** "Now" when online, otherwise when the customer was last seen. */
fun lastOnlineLabel(online: Boolean, lastSeen: ZonedDateTime?): String = when {
    online -> "Now"
    lastSeen == null -> "Never"
    else -> relativeAge(lastSeen)
}

/** Expense dates in Firebase are stored as dd/MM/yyyy. */
fun parseLegacyDate(value: String?): LocalDate? = try {
    if (value.isNullOrBlank()) null else LocalDate.parse(value.trim(), legacyDate)
} catch (e: DateTimeParseException) {
    null
}

fun formatLegacyDate(date: LocalDate): String = date.format(legacyDate)

fun isoOffset(date: LocalDate, hour: Int, minute: Int): String =
    LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toOffsetDateTime().toString()

/** Kenyan numbers: makes 07…, +254… and 254… comparable. */
fun normalizePhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.startsWith("254") -> digits.drop(3)
        digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }
}
