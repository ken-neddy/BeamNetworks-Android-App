package com.helper.beamnetworks.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.helper.beamnetworks.util.formatLegacyDate
import com.helper.beamnetworks.util.parseLegacyDate
import com.helper.beamnetworks.util.parseMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** Emits [map] of the node's snapshot every time it changes. */
private fun <T> DatabaseReference.observe(map: (DataSnapshot) -> T) = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(map(snapshot))
        }

        override fun onCancelled(error: DatabaseError) {
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

/** Expenses are not part of the billing API, so they stay in Firebase (node "expenses"). */
class ExpenseRepository(scope: CoroutineScope) {
    private val ref = FirebaseDatabase.getInstance().getReference("expenses")

    val expenses: StateFlow<Loadable<Expense>> = ref.observe { snapshot ->
        val items = snapshot.children.mapNotNull { child ->
            val title = child.child("item").getValue(String::class.java) ?: return@mapNotNull null
            Expense(
                id = child.key ?: return@mapNotNull null,
                title = title,
                category = child.child("category").getValue(String::class.java).orEmpty().ifBlank { "Other" },
                amount = parseMoney(child.child("amount").value?.toString()),
                date = parseLegacyDate(child.child("date").getValue(String::class.java)),
                notes = child.child("moreNotes").getValue(String::class.java).orEmpty(),
            )
        }.sortedByDescending { it.date }
        Loadable(items, loaded = true)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), Loadable(loading = true))

    /** Writes the same fields the old screens used (amount as a string, dd/MM/yyyy date) plus category. */
    suspend fun save(id: String?, title: String, amount: Double, category: String, date: LocalDate, notes: String) {
        val node = if (id == null) ref.push() else ref.child(id)
        node.setValue(
            mapOf(
                "id" to node.key,
                "item" to title.trim(),
                "amount" to com.helper.beamnetworks.util.moneyString(amount),
                "date" to formatLegacyDate(date),
                "moreNotes" to notes,
                "category" to category,
            ),
        ).await()
    }

    suspend fun delete(id: String) {
        ref.child(id).removeValue().await()
    }
}

/** Previously used ticket issues, for suggestions (Firebase node "issues"). */
class IssueRepository(scope: CoroutineScope) {
    private val ref = FirebaseDatabase.getInstance().getReference("issues")

    val issues: StateFlow<List<String>> = ref.observe { snapshot ->
        snapshot.children.mapNotNull { it.getValue(String::class.java) }.distinct()
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remember(issue: String) {
        val text = issue.trim()
        if (text.isNotEmpty() && issues.value.none { it.equals(text, ignoreCase = true) }) ref.push().setValue(text)
    }
}
