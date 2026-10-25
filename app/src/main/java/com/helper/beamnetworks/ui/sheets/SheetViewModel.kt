package com.helper.beamnetworks.ui.sheets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.JobDraft
import com.helper.beamnetworks.data.JobType
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.normalizePhone
import com.helper.beamnetworks.util.parseAmountInput
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface SheetType {
    data object Create : SheetType
    data class Payment(val customerId: Int? = null) : SheetType
    data object NewCustomer : SheetType
    data class Job(val editId: Int? = null) : SheetType
    data class Ticket(val customerId: Int? = null) : SheetType
    data class Expense(val editId: String? = null) : SheetType
    data class Product(val editId: Int? = null) : SheetType
    data class CompleteJob(val jobId: Int) : SheetType
    data class Extend(val customerId: Int) : SheetType
}

/** Where to go after a create flow succeeds (the handoff jumps to the matching tab). */
enum class SheetDestination { Installs, Tickets, Accounts, Stock, Customers }

/** Older full-screen pickers a sheet can open; their results land back in the draft. */
enum class Picker { CallLog, Map, Sms }

val ExpenseCategories = listOf("Transport", "Equipment", "Bandwidth", "Other")

data class Draft(
    val customerId: Int? = null,
    val customerText: String = "",
    val amount: String = "",
    val reference: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val planId: Int? = null,
    val networkId: Int? = null,
    val location: String = "",
    val notes: String = "",
    val hasRouter: Boolean = false,
    val date: LocalDate = LocalDate.now(zone).plusDays(1),
    val time: LocalTime = LocalTime.of(10, 0),
    val technicianId: Int? = null,
    val issue: String = "",
    val high: Boolean = false,
    val title: String = "",
    val category: String = ExpenseCategories.first(),
    val qty: String = "",
    val min: String = "",
    val used: Map<Int, Int> = emptyMap(),
    val days: String = "7",
    val jobType: JobType = JobType.Installation,
)

/**
 * Activity-scoped state for the bottom sheets: which sheet is open, the draft being edited, and
 * the submit logic for each create flow (mirrors `submit()` in the prototype).
 */
class SheetViewModel(private val app: AppContainer) : ViewModel() {
    var sheet by mutableStateOf<SheetType?>(null)
        private set
    var draft by mutableStateOf(Draft())
        private set
    var busy by mutableStateOf(false)
        private set

    /** Set while a picker screen is open, so the sheet comes back when it closes. */
    var parkedSheet: SheetType? = null
        private set

    private val _destinations = Channel<SheetDestination>(Channel.BUFFERED)
    val destinations = _destinations.receiveAsFlow()

    private val _pickers = Channel<Picker>(Channel.BUFFERED)
    val pickers = _pickers.receiveAsFlow()

    val customers get() = app.customers
    val jobs get() = app.jobs
    val inventory get() = app.inventory
    val expenses get() = app.expenses
    val issues get() = app.issues
    val toasts get() = app.toasts

    fun openCreateMenu() {
        sheet = SheetType.Create
    }

    fun open(type: SheetType) {
        draft = initialDraft(type)
        sheet = type
        when (type) {
            is SheetType.Payment, is SheetType.Ticket -> app.customers.customers.ensureLoaded()
            SheetType.NewCustomer -> {
                app.customers.plans.ensureLoaded()
                app.customers.networks.ensureLoaded()
            }
            is SheetType.Job -> {
                app.customers.customers.ensureLoaded()
                app.jobs.staff.ensureLoaded()
            }
            is SheetType.CompleteJob, is SheetType.Product -> app.inventory.items.ensureLoaded()
            else -> Unit
        }
    }

    fun close() {
        sheet = null
        busy = false
    }

    fun edit(transform: Draft.() -> Draft) {
        draft = draft.transform()
    }

    fun pickCustomer(customer: Customer) {
        draft = draft.copy(customerId = customer.id, customerText = customer.name, name = customer.name, phone = draft.phone.ifBlank { customer.phone })
    }

    fun openPicker(picker: Picker) {
        parkedSheet = sheet
        sheet = null
        _pickers.trySend(picker)
    }

    /** Called when the screen under the sheet is visible again; [results] come from the picker. */
    fun resumeAfterPicker(results: Map<String, String?>) {
        val parked = parkedSheet ?: return
        parkedSheet = null
        results["selected_phone_number"]?.let { phone -> draft = draft.copy(phone = phone) }
        results["selected_client_name"]?.takeIf { it != "Unknown Number" }?.let { name ->
            val match = app.customers.customers.items.firstOrNull { it.name.equals(name, ignoreCase = true) }
            draft = if (match != null) draft.copy(customerId = match.id, customerText = match.name, name = match.name)
            else draft.copy(name = draft.name.ifBlank { name }, customerText = draft.customerText.ifBlank { name })
        }
        results["picked_location"]?.let { draft = draft.copy(location = it) }
        results["picked_amount"]?.let { draft = draft.copy(amount = it) }
        sheet = parked
    }

    private fun initialDraft(type: SheetType): Draft {
        val customers = app.customers.customers.items
        return when (type) {
            is SheetType.Payment -> customers.firstOrNull { it.id == type.customerId }?.let { Draft(customerId = it.id, customerText = it.name) } ?: Draft()
            is SheetType.Ticket -> customers.firstOrNull { it.id == type.customerId }
                ?.let { Draft(customerId = it.id, customerText = it.name, phone = it.phone) } ?: Draft()
            is SheetType.Job -> type.editId?.let { app.jobs.byId(it) }?.let { job ->
                val local = job.scheduled?.withZoneSameInstant(zone)
                Draft(
                    name = job.customerName,
                    customerText = job.customerName,
                    phone = job.phone.orEmpty(),
                    location = job.location,
                    notes = job.notes,
                    hasRouter = job.hasRouter,
                    date = local?.toLocalDate() ?: LocalDate.now(zone),
                    time = local?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(10, 0),
                    technicianId = job.technicianId,
                    jobType = job.type,
                )
            } ?: Draft()
            is SheetType.Expense -> type.editId?.let { id -> app.expenses.expenses.value.items.firstOrNull { it.id == id } }?.let {
                Draft(title = it.title, amount = com.helper.beamnetworks.util.moneyString(it.amount), category = it.category, date = it.date ?: LocalDate.now(zone), notes = it.notes)
            } ?: Draft(date = LocalDate.now(zone))
            is SheetType.Product -> type.editId?.let { id -> app.inventory.items.items.firstOrNull { it.id == id } }?.let {
                Draft(name = it.name, qty = it.quantity.toString(), min = it.reorderAt.toString())
            } ?: Draft()
            else -> Draft()
        }
    }

    fun submit() {
        val type = sheet ?: return
        if (busy) return
        val d = draft
        when (type) {
            is SheetType.Payment -> {
                val customer = d.customerId?.let { app.customers.byId(it) } ?: return fail("Choose a customer")
                val amount = parseAmountInput(d.amount) ?: return fail("Enter an amount")
                run {
                    app.payments.recordCash(customer, amount, d.reference.trim())
                    // Balance and status are recalculated server-side (a payment can reconnect a suspended customer)
                    app.customers.customers.refreshAsync()
                    done("Payment of ${kshShort(amount)} recorded")
                }
            }
            SheetType.NewCustomer -> {
                if (d.name.isBlank()) return fail("Add a name")
                if (d.phone.isBlank()) return fail("Add a phone number")
                val plan = app.customers.plans.items.firstOrNull { it.id == d.planId } ?: return fail("Choose a plan")
                val networks = app.customers.networks.items
                val networkId = plan.networkScope ?: d.networkId ?: networks.singleOrNull()?.id ?: return fail("Choose a network")
                run {
                    app.customers.create(d.name.trim(), d.phone.trim(), d.email, plan.id, networkId)
                    done("${d.name.trim()} added", SheetDestination.Customers)
                }
            }
            is SheetType.Job -> {
                val name = d.name.trim().ifBlank { d.customerText.trim() }
                if (name.isBlank()) return fail("Add the customer name")
                val customer = d.customerId?.let { app.customers.byId(it) }?.takeIf { it.name == name }
                val jobDraft = JobDraft(name, customer?.identityId, d.phone, d.location, d.notes, d.hasRouter, d.date, d.time, d.technicianId, d.jobType)
                run {
                    if (type.editId == null) {
                        app.jobs.schedule(jobDraft)
                        done("${d.jobType.label} scheduled", SheetDestination.Installs)
                    } else {
                        app.jobs.update(type.editId, jobDraft)
                        done("Job updated")
                    }
                }
            }
            is SheetType.Ticket -> {
                if (d.issue.isBlank()) return fail("Describe the issue")
                val customer = d.customerId?.let { app.customers.byId(it) }?.takeIf { it.name == d.customerText }
                val customerName = customer?.name ?: d.customerText.trim()
                if (customerName.isBlank()) return fail("Choose a customer")
                run {
                    val withPhone = if (customer == null && d.phone.isNotBlank()) "${d.issue.trim()}\nPhone: ${d.phone.trim()}" else d.issue.trim()
                    app.tickets.raise(withPhone, d.high, customer?.identityId, customerName)
                    app.issues.remember(d.issue)
                    done("Ticket raised", SheetDestination.Tickets)
                }
            }
            is SheetType.Expense -> {
                val amount = parseAmountInput(d.amount)
                if (d.title.isBlank() || amount == null) return fail("Add a description and amount")
                run {
                    app.expenses.save(type.editId, d.title, amount, d.category, d.date, d.notes)
                    done(if (type.editId == null) "Expense logged" else "Expense updated", if (type.editId == null) SheetDestination.Accounts else null)
                }
            }
            is SheetType.Product -> {
                if (d.name.isBlank()) return fail("Add a product name")
                val qty = d.qty.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 } ?: return fail("In stock must be a whole number")
                val min = d.min.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 } ?: return fail("Reorder at must be a whole number")
                run {
                    val existing = type.editId?.let { id -> app.inventory.items.items.firstOrNull { it.id == id } }
                    if (existing != null) {
                        if (qty != existing.quantity) {
                            app.inventory.adjustLocally(existing.id, qty - existing.quantity)
                            app.inventory.commitAdjust(existing.id, qty - existing.quantity)
                        }
                        if (min != existing.reorderAt) app.inventory.setReorderAt(existing, min)
                        done("${existing.name} updated")
                    } else {
                        app.inventory.addOrRestock(d.name, qty, min)
                        done("${d.name.trim()} added", SheetDestination.Stock)
                    }
                }
            }
            is SheetType.CompleteJob -> {
                val job = app.jobs.byId(type.jobId) ?: return fail("That installation no longer exists")
                run {
                    app.jobs.complete(job, d.used)
                    if (d.used.values.any { it > 0 }) app.inventory.items.refreshAsync()
                    done("${job.customerName} marked done")
                }
            }
            is SheetType.Extend -> {
                val customer = app.customers.byId(type.customerId) ?: return fail("Customer not found")
                val days = d.days.toIntOrNull()?.takeIf { it in 1..90 } ?: return fail("Days must be between 1 and 90")
                if (d.notes.trim().length < 5) return fail("Give a reason (at least 5 characters)")
                run {
                    app.customers.extend(customer, days, d.notes.trim())
                    done("${customer.name} reconnected for $days days")
                }
            }
            SheetType.Create -> Unit
        }
    }

    private fun fail(message: String) {
        app.toasts.show(message)
    }

    private fun run(block: suspend () -> Unit) {
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Something went wrong")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                app.toasts.show(e.message ?: "Something went wrong")
            } finally {
                busy = false
            }
        }
    }

    private fun done(message: String, destination: SheetDestination? = null) {
        sheet = null
        app.toasts.show(message)
        destination?.let { _destinations.trySend(it) }
    }

    /** Customers matching what's been typed, by name or phone. */
    fun customerMatches(): List<Customer> = app.customers.customers.items

    fun phoneMatches(phone: String): Customer? =
        app.customers.customers.items.firstOrNull { phone.length >= 9 && normalizePhone(it.phone) == normalizePhone(phone) }
}
