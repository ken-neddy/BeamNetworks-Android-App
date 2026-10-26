package com.helper.beamnetworks.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What an M-Pesa confirmation SMS says about a payment the phone made. */
data class MpesaMessage(val code: String?, val amount: String?, val paidTo: String?, val date: LocalDate?)

private val codeRegex = Regex("""^\s*([A-Z0-9]{10})\b""")
private val amountRegex = Regex("""Ksh\.?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

// "sent to FAIBA OPTICS 0712345678 on", "paid to FAIBA OPTICS LTD. on", "sent to KPLC PREPAID for account 123 on"
private val paidToRegex = Regex(
    """\b(?:sent|paid)\s+to\s+(.+?)(?:\s+for\s+account\b|\s+(?:\+?254|0)\d{9}\b|\.?\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}|\.\s|\.?$)""",
    RegexOption.IGNORE_CASE,
)
private val dateRegex = Regex("""\bon\s+(\d{1,2}/\d{1,2}/\d{2,4})""", RegexOption.IGNORE_CASE)

/** Reads the code, amount, who was paid and the date from an M-Pesa SMS; parts it can't find are null. */
fun parseMpesaMessage(body: String): MpesaMessage {
    // The first Ksh figure after "sent"/"paid" is the payment; balances and charges come later
    val amount = amountRegex.find(body)?.groupValues?.get(1)?.replace(",", "")
    val paidTo = paidToRegex.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.let(::titleCase)
    val date = dateRegex.find(body)?.groupValues?.get(1)?.let { raw ->
        val pattern = if (raw.substringAfterLast('/').length == 2) "d/M/yy" else "d/M/yyyy"
        runCatching { LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
    }
    return MpesaMessage(codeRegex.find(body)?.groupValues?.get(1), amount, paidTo, date)
}

/** "FAIBA OPTICS LTD" → "Faiba Optics Ltd"; names already in mixed case are left alone. */
private fun titleCase(name: String): String =
    if (name != name.uppercase()) name
    else name.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
