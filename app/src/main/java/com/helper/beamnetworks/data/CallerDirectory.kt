package com.helper.beamnetworks.data

import android.content.Context
import com.helper.beamnetworks.util.normalizePhone
import com.helper.beamnetworks.util.whenLabel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** What the caller ID popup shows for a number. */
@Serializable
data class CallerMatch(val name: String, val phone: String, val label: String, val location: String)

/**
 * Phone numbers of customers and scheduled installations, saved on the device so an incoming
 * call can be matched instantly — even offline or when the app process was not running.
 * Rewritten whenever the customer or installation lists load; cleared on sign out.
 */
class CallerDirectory(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "caller_directory.json"))

    private val serializer = ListSerializer(Entry.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(val key: String, val match: CallerMatch, val priority: Int)

    @Volatile
    private var entries: List<Entry>? = null

    fun lookup(number: String): CallerMatch? {
        val key = normalizePhone(number)
        if (key.length < 6) return null
        return load().filter { it.key == key }.minByOrNull { it.priority }?.match
    }

    fun update(customers: List<Customer>, jobs: List<Job>) {
        val list = customers.filter { it.phone.isNotBlank() }.map { c ->
            val label = listOf("${c.status.label} customer", c.planName).filter { it.isNotBlank() }.joinToString(" · ")
            Entry(normalizePhone(c.phone), CallerMatch(c.name, c.phone, label, c.location), priority = 0)
        } + jobs.filter { !it.phone.isNullOrBlank() && it.status.isUpcoming }.map { j ->
            Entry(normalizePhone(j.phone!!), CallerMatch(j.customerName, j.phone, "Installation · ${whenLabel(j.scheduled)}", j.location), priority = 1)
        }
        entries = list
        runCatching { file.writeText(json.encodeToString(serializer, list)) }
    }

    fun clear() {
        entries = emptyList()
        file.delete()
    }

    private fun load(): List<Entry> = entries ?: (
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList()).also { entries = it }
        )
}
