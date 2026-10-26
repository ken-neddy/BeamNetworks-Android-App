package com.helper.beamnetworks.ui.sheets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.Job
import com.helper.beamnetworks.data.JobDraft
import com.helper.beamnetworks.data.JobType
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.CustomerCreate
import com.helper.beamnetworks.data.api.LocationOptionsDto
import com.helper.beamnetworks.data.api.apiCall
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

/** What the website's "Create Customer" form asks for beyond name, phone, email, plan and network. */
data class CustomerForm(
    val status: String = "active",
    val country: String = "Kenya",
    val county: String = "",
    val subCounty: String = "",
    val ward: String = "",
    val road: String = "",
    val estate: String = "",
    val estateSameAsRoad: Boolean = false,
    val street: String = "",
    val building: String = "",
    val house: String = "",
    val landmark: String = "",
    val details: String = "",
    val billed: Boolean = true,
    /** Day of the month (1–28) the first bill falls on; null bills on today's day of the month. */
    val billingDay: Int? = null,
    val referred: Boolean = false,
    val referrerId: Int? = null,
    val referrerText: String = "",
    val rewardMethod: String = "credit",
    val referralLeadId: Int? = null,
    val leadNote: String? = null,
)

private fun String.orNull(): String? = trim().ifBlank { null }

/** Reads "lat,lng" as produced by the map picker; null if it isn't a valid coordinate pair. */
private fun parsePin(text: String): Pair<Double, Double>? {
    val match = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$""").matchEntire(text) ?: return null
    val lat = match.groupValues[1].toDouble()
    val lng = match.groupValues[2].toDouble()
    return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
}

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
    val hasPoe: Boolean = false,
    val date: LocalDate = LocalDate.now(zone).plusDays(1),
    val time: LocalTime = LocalTime.of(10, 0),
    val technicianId: Int? = null,
    val issue: String = "",
    val priority: String = "medium",
    val queue: String = "general",
    val sku: String = "",
    val unitCost: String = "",
    val unitPrice: String = "",
    val title: String = "",
    val shop: String = "",
    val category: String = ExpenseCategories.first(),
    val qty: String = "",
    val min: String = "",
    val used: Map<Int, Int> = emptyMap(),
    val days: String = "7",
    val jobType: JobType = JobType.Installation,
    val code: String = "",
    val confirmed: Boolean = false,
    val itemType: String = "consumable",
    val cust: CustomerForm = CustomerForm(),
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
            is SheetType.Payment -> app.customers.customers.ensureLoaded()
            is SheetType.Ticket -> {
                app.customers.customers.ensureLoaded()
                app.jobs.staff.ensureLoaded()
            }
            SheetType.NewCustomer -> {
                app.customers.plans.ensureLoaded()
                app.customers.networks.ensureLoaded()
                app.customers.customers.ensureLoaded()
                loadCustomerDefaults()
            }
            is SheetType.Job -> {
                app.customers.customers.ensureLoaded()
                app.jobs.staff.ensureLoaded()
            }
            is SheetType.CompleteJob, is SheetType.Product -> app.inventory.items.ensureLoaded()
            else -> Unit
        }
    }

    /** County, sub-county and ward suggestions for the add-customer form. */
    var locations by mutableStateOf(LocationOptionsDto())
        private set

    /** Pre-fills the location with the business's defaults (admins only), then loads place suggestions. */
    private fun loadCustomerDefaults() {
        locations = LocationOptionsDto()
        viewModelScope.launch {
            val settings = runCatching { apiCall { app.api.settings() } }.getOrNull()
            fun setting(key: String) = (settings?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (sheet == SheetType.NewCustomer) {
                val c = draft.cust
                draft = draft.copy(
                    cust = c.copy(
                        county = c.county.ifBlank { setting("default_customer_city") },
                        subCounty = c.subCounty.ifBlank { setting("default_customer_sub_county") },
                        ward = c.ward.ifBlank { setting("default_customer_ward") },
                        road = c.road.ifBlank { setting("default_customer_road") },
                    ),
                )
            }
            refreshLocations()
        }
    }

    /** Reloads the sub-county and ward suggestions for the county / sub-county now in the form. */
    fun refreshLocations() {
        val c = draft.cust
        viewModelScope.launch {
            runCatching { apiCall { app.api.locationOptions(c.county.orNull(), c.subCounty.orNull()) } }.getOrNull()?.let { locations = it }
        }
    }

    /** Looks for a pending referral lead with this phone, so the referrer is filled in for the admin. */
    fun lookupReferralLead(phone: String) {
        if (normalizePhone(phone).length < 9) return
        viewModelScope.launch {
            val lead = runCatching { apiCall { app.api.matchReferralLead(phone.trim()) } }.getOrNull()
                ?.results?.firstOrNull { it.status == "pending" && it.referrer != null }
            val c = draft.cust
            draft = when {
                lead != null -> draft.copy(
                    cust = c.copy(
                        referred = true,
                        referrerId = lead.referrer,
                        referrerText = lead.referrerName.orEmpty(),
                        referralLeadId = lead.id,
                        leadNote = "Matches a referral from ${lead.referrerName ?: "an existing customer"}",
                    ),
                )
                // The number no longer matches the lead we filled in earlier
                c.referralLeadId != null -> draft.copy(cust = c.copy(referred = false, referrerId = null, referrerText = "", referralLeadId = null, leadNote = null))
                else -> draft
            }
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
        results["picked_code"]?.let { draft = draft.copy(reference = it) }
        if (parked is SheetType.Expense) {
            results["picked_paid_to"]?.let { draft = draft.copy(shop = it) }
            results["picked_date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { draft = draft.copy(date = it) }
        }
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
                    hasPoe = job.hasPoe,
                    date = local?.toLocalDate() ?: LocalDate.now(zone),
                    time = local?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(10, 0),
                    technicianId = job.technicianId,
                    jobType = job.type,
                    priority = job.priority,
                )
            } ?: Draft()
            is SheetType.Expense -> type.editId?.let { id -> app.expenses.expenses.value.items.firstOrNull { it.id == id } }?.let {
                Draft(title = it.title, shop = it.shop, amount = com.helper.beamnetworks.util.moneyString(it.amount), category = it.category, date = it.date ?: LocalDate.now(zone), notes = it.notes)
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
                val c = d.cust
                val pin = parsePin(d.location)
                if (d.location.isNotBlank() && pin == null) return fail("Pick the pin on the map, or clear it")
                if (c.referred && c.referrerId == null) return fail("Choose who referred this customer")
                val request = CustomerCreate(
                    fullName = d.name.trim(),
                    phone = d.phone.trim(),
                    plan = plan.id,
                    network = networkId,
                    email = d.email.orNull(),
                    status = c.status,
                    country = c.country.orNull(),
                    city = c.county.orNull(),
                    subCounty = c.subCounty.orNull(),
                    ward = c.ward.orNull(),
                    road = c.road.orNull(),
                    estate = (if (c.estateSameAsRoad) c.road else c.estate).orNull(),
                    street = c.street.orNull(),
                    building = c.building.orNull(),
                    houseNumber = c.house.orNull(),
                    commonMarker = c.landmark.orNull(),
                    moreDetails = c.details.orNull(),
                    latitude = pin?.let { "%.6f".format(java.util.Locale.US, it.first) },
                    longitude = pin?.let { "%.6f".format(java.util.Locale.US, it.second) },
                    isBilled = c.billed,
                    billingCycleDay = c.billingDay,
                    referredBy = c.referrerId.takeIf { c.referred },
                    referralRewardMethod = c.rewardMethod.takeIf { c.referred },
                    referralLead = c.referralLeadId.takeIf { c.referred },
                )
                run {
                    app.customers.create(request)
                    done("${d.name.trim()} added", SheetDestination.Customers)
                }
            }
            is SheetType.Job -> {
                val name = d.name.trim().ifBlank { d.customerText.trim() }
                if (name.isBlank()) return fail("Add the customer name")
                val customer = d.customerId?.let { app.customers.byId(it) }?.takeIf { it.name == name }
                val jobDraft = JobDraft(
                    customerName = name, identityId = customer?.identityId, phone = d.phone, location = d.location, notes = d.notes,
                    hasRouter = d.hasRouter, hasPoe = d.hasPoe, date = d.date, time = d.time, technicianId = d.technicianId,
                    type = d.jobType, priority = d.priority,
                )
                run {
                    val previousTechnician = type.editId?.let { app.jobs.byId(it)?.technicianId }
                    val job = if (type.editId == null) app.jobs.schedule(jobDraft) else app.jobs.update(type.editId, jobDraft)
                    val texted = textTechnician(job, previousTechnician)
                    if (type.editId == null) done("${d.jobType.label} scheduled$texted", SheetDestination.Installs) else done("Job updated$texted")
                }
            }
            is SheetType.Ticket -> {
                if (d.issue.isBlank()) return fail("Describe the issue")
                val customer = d.customerId?.let { app.customers.byId(it) }?.takeIf { it.name == d.customerText }
                val customerName = customer?.name ?: d.customerText.trim()
                if (customerName.isBlank()) return fail("Choose a customer")
                run {
                    val withPhone = if (customer == null && d.phone.isNotBlank()) "${d.issue.trim()}\nPhone: ${d.phone.trim()}" else d.issue.trim()
                    app.tickets.raise(withPhone, d.priority, d.queue, d.technicianId, customer?.identityId, customerName)
                    app.issues.remember(d.issue)
                    done("Ticket raised", SheetDestination.Tickets)
                }
            }
            is SheetType.Expense -> {
                val amount = parseAmountInput(d.amount)
                if (d.title.isBlank() || amount == null) return fail("Add the item and amount")
                run {
                    app.expenses.save(type.editId, d.title, amount, d.category, d.date, d.notes, d.shop)
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
                        app.inventory.addOrRestock(d.name, qty, min, d.itemType, d.sku, parseAmountInput(d.unitCost), parseAmountInput(d.unitPrice))
                        done("${d.name.trim()} added", SheetDestination.Stock)
                    }
                }
            }
            is SheetType.CompleteJob -> {
                val job = app.jobs.byId(type.jobId) ?: return fail("That installation no longer exists")
                run {
                    app.jobs.complete(job, d.used, d.code, d.confirmed, d.notes)
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

    /** Archives the product being edited (Beam keeps its movement history). */
    fun archiveProduct(id: Int) {
        val item = app.inventory.items.items.firstOrNull { it.id == id } ?: return
        run {
            app.inventory.archive(item)
            done("${item.name} archived")
        }
    }

    /**
     * Texts a newly assigned technician the job's details; the technician's own app also raises a
     * notification when it sees the job. Returns a suffix for the confirmation toast.
     */
    private fun textTechnician(job: Job, previousTechnician: Int?): String {
        val technicianId = job.technicianId ?: return ""
        if (technicianId == previousTechnician || technicianId == app.sessions.current?.userId) return ""
        val technician = app.jobs.staff.items.firstOrNull { it.userId == technicianId }
        val phone = technician?.phone.orEmpty()
        return when {
            phone.isBlank() -> ". ${technician?.name ?: "The technician"} has no phone number, so no SMS was sent"
            !app.technicianAlerts.canText() -> ". Allow SMS in Settings › Phone permissions to text technicians"
            app.technicianAlerts.text(phone, app.technicianAlerts.message(job)) -> ". ${technician?.name ?: "Technician"} was texted"
            else -> ". The SMS to ${technician?.name ?: "the technician"} could not be sent"
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
