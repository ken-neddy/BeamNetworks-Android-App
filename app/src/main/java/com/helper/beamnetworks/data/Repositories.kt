package com.helper.beamnetworks.data

import com.helper.beamnetworks.data.api.AdjustRequest
import com.helper.beamnetworks.data.api.AssignReceiptRequest
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.BeamApi
import com.helper.beamnetworks.data.api.CashPaymentRequest
import com.helper.beamnetworks.data.api.CustomerCreate
import com.helper.beamnetworks.data.api.CustomerDto
import com.helper.beamnetworks.data.api.ExtendRequest
import com.helper.beamnetworks.data.api.InventoryCreate
import com.helper.beamnetworks.data.api.InventoryItemDto
import com.helper.beamnetworks.data.api.InventoryUpdate
import com.helper.beamnetworks.data.api.InventoryUse
import com.helper.beamnetworks.data.api.JobAssign
import com.helper.beamnetworks.data.api.JobCompleteFull
import com.helper.beamnetworks.data.api.InventoryMoveRequest
import com.helper.beamnetworks.data.api.ExtendServiceRequest
import com.helper.beamnetworks.data.api.JobCreate
import com.helper.beamnetworks.data.api.JobDto
import com.helper.beamnetworks.data.api.PaymentDto
import com.helper.beamnetworks.data.api.TicketCreate
import com.helper.beamnetworks.data.api.TicketDto
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.api.fetchAllPages
import com.helper.beamnetworks.util.isoOffset
import com.helper.beamnetworks.util.moneyString
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.parseMoney
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.LocalTime

class CustomerRepository(private val api: BeamApi, scope: CoroutineScope) {
    val customers = RemoteList(scope) { loadCustomers() }
    val plans = RemoteList(scope) {
        apiCall { api.plans().results }.filter { it.isActive }.map { Plan(it.id, it.name, it.networkScope) }
    }
    val networks = RemoteList(scope) { apiCall { api.networks().results }.map { Network(it.id, it.name) } }

    private suspend fun loadCustomers(): List<Customer> = coroutineScope {
        val list = async { apiCall { fetchAllPages { api.customers(it) } } }
        // Sessions and enrolments need extra permissions; the list still works without them
        val online = async { runCatching { apiCall { api.pppoeSessions() } }.getOrNull()?.mapNotNull { it.customer }?.toSet() }
        val identities = async {
            runCatching { apiCall { fetchAllPages { api.enrollments(it) } } }.getOrNull()
                ?.filter { it.pppoeCustomer != null && it.identityId != null }
                ?.associate { it.pppoeCustomer!! to it.identityId!! }
        }
        val onlineIds = online.await().orEmpty()
        val identityIds = identities.await().orEmpty()
        list.await()
            .filter { it.status != "archived" }
            .map { it.toCustomer(it.id in onlineIds, identityIds[it.id]) }
            .sortedBy { it.name.lowercase() }
    }

    fun byId(id: Int): Customer? = customers.items.firstOrNull { it.id == id }

    suspend fun create(request: CustomerCreate): Customer {
        val dto = apiCall { api.createCustomer(request) }
        val customer = dto.toCustomer(online = false, identityId = null)
        customers.update { (listOf(customer) + it).sortedBy { c -> c.name.lowercase() } }
        // The server links an identity on create; reload in the background to pick it up
        customers.refreshAsync()
        return customer
    }

    suspend fun suspend(customer: Customer) = replace(customer) { api.suspendCustomer(customer.id) }

    suspend fun reactivate(customer: Customer) = replace(customer) { api.reactivateCustomer(customer.id) }

    suspend fun extend(customer: Customer, days: Int, reason: String) = replace(customer) { api.extendCustomer(customer.id, ExtendRequest(reason, days)) }

    /** Extends by [weeks]/[days], or until [resumeAt] (ISO datetime) when given. */
    suspend fun extendService(customer: Customer, reason: String, weeks: Int? = null, days: Int? = null, resumeAt: String? = null) = replace(customer) {
        api.extendService(customer.id, if (resumeAt != null) ExtendServiceRequest(reason, resumeAt = resumeAt) else ExtendServiceRequest(reason, weeks, days))
    }

    /** Archiving is terminal, so the customer is dropped from the list. */
    suspend fun archive(customer: Customer) {
        apiCall { api.archiveCustomer(customer.id) }
        customers.update { list -> list.filterNot { it.id == customer.id } }
    }

    suspend fun sendPayLink(customer: Customer) {
        apiCall { api.sendPayLink(customer.id) }
    }

    private suspend fun replace(customer: Customer, call: suspend () -> CustomerDto): Customer {
        val dto = apiCall { call() }
        // Suspending disconnects the session, so only an active customer can still be online
        val updated = dto.toCustomer(customer.online && dto.status == "active", customer.identityId)
        customers.update { list -> list.map { if (it.id == customer.id) updated else it } }
        return updated
    }

    private fun CustomerDto.toCustomer(online: Boolean, identityId: Int?): Customer {
        val status = CustomerStatus.from(status)
        return Customer(
            id = id,
            name = fullName.ifBlank { "Customer $id" },
            phone = phone,
            email = email?.takeIf { it.isNotBlank() },
            planId = plan,
            planName = planName.orEmpty(),
            networkName = networkName?.takeIf { it.isNotBlank() },
            pppoeUser = radiusUsername?.takeIf { it.isNotBlank() },
            accountCode = accountCode,
            status = status,
            amountDue = parseMoney(balance),
            lastSeen = parseDateTime(lastSeenAt),
            online = online && status != CustomerStatus.Suspended,
            identityId = identityId,
            location = listOfNotNull(estate, road, building, houseNumber).filter { it.isNotBlank() }.joinToString(", ")
                .ifBlank { subCounty.orEmpty() },
            latitude = latitude?.toDoubleOrNull(),
            longitude = longitude?.toDoubleOrNull(),
        )
    }
}

/**
 * Confirmed payments and invoices from [since] onwards. Lists are assumed newest-first, so
 * paging stops once a page is entirely older than [since].
 */
class PaymentRepository(private val api: BeamApi, scope: CoroutineScope) {
    @Volatile
    var since: LocalDate = defaultSince()
        private set

    val payments = RemoteList(scope) {
        val cutoff = since
        apiCall {
            fetchAllPages(maxPages = 20, stop = { page -> page.isNotEmpty() && page.all { (it.paymentDate()?.toLocalDate() ?: LocalDate.MAX) < cutoff } }) {
                api.payments(it)
            }
        }.map { it.toPayment() }.filter { (it.date?.toLocalDate() ?: LocalDate.MIN) >= cutoff }.sortedByDescending { it.date }
    }

    val invoices = RemoteList(scope) {
        val cutoff = since
        apiCall {
            fetchAllPages(maxPages = 20, stop = { page -> page.isNotEmpty() && page.all { (parseDateTime(it.createdAt)?.toLocalDate() ?: LocalDate.MAX) < cutoff } }) {
                api.invoices(it)
            }
        }
            .filter { it.status != "cancelled" && it.status != "waived" }
            .map { inv ->
                // Billed = subtotal + tax (matches the web's "billed_net"); total is net of credit
                val billed = inv.subtotal?.let { parseMoney(it) + parseMoney(inv.taxAmount) } ?: parseMoney(inv.total)
                Invoice(inv.id, inv.customer, billed, (parseDateTime(inv.periodStart) ?: parseDateTime(inv.createdAt))?.toLocalDate())
            }
            .filter { (it.date ?: LocalDate.MIN) >= cutoff }
    }

    /** Makes sure payments back to [date] are loaded (for the Accounts month picker). */
    fun ensureSince(date: LocalDate) {
        if (date < since) {
            since = date
            payments.refreshAsync()
        } else {
            payments.ensureLoaded()
        }
    }

    suspend fun recordCash(customer: Customer, amount: Double, reference: String?): Payment {
        val code = reference?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        if (code != null) {
            // A paybill payment is credited by Beam itself; recording it again would double-credit
            val existing = apiCall { api.paymentRecords(search = code, pageSize = 10) }.results
                .firstOrNull { it.mpesaTransactionId.equals(code, ignoreCase = true) && it.status != "failed" }
            if (existing != null) {
                throw ApiException("M‑Pesa $code is already in Beam (${existing.customerName?.ifBlank { null } ?: "another customer"}, Ksh ${existing.amount}). Not recorded again.")
            }
            // Paid to the paybill with a wrong account number: attach that deposit instead
            val receipt = runCatching { apiCall { api.findReceipts(code) }.results }.getOrNull()
                ?.firstOrNull { it.transId.equals(code, ignoreCase = true) }
            if (receipt != null) {
                apiCall { api.assignReceipt(receipt.id, AssignReceiptRequest(customerId = customer.id)) }
                val payment = Payment(receipt.id, customer.id, customer.name, parseMoney(receipt.amount), "mpesa", code, java.time.ZonedDateTime.now(zone))
                payments.refreshAsync()
                return payment
            }
        }
        val note = buildString {
            append("Recorded in Beam app")
            if (!reference.isNullOrBlank()) append(" · M-Pesa ").append(reference)
        }
        val dto = apiCall {
            api.recordCashPayment(CashPaymentRequest("pppoe", customer.id, moneyString(amount), note, reference?.takeIf { it.isNotBlank() }))
        }
        val payment = dto.toPayment().let { if (it.customerName.isBlank()) it.copy(customerName = customer.name) else it }
        payments.update { listOf(payment) + it }
        return payment
    }

    fun reset() {
        since = defaultSince()
        payments.reset()
        invoices.reset()
    }

    private fun PaymentDto.paymentDate() = parseDateTime(confirmedAt) ?: parseDateTime(createdAt)

    private fun PaymentDto.toPayment() = Payment(
        id = id,
        customerId = pppoeCustomer,
        customerName = customerName.orEmpty(),
        amount = parseMoney(amount),
        method = method.orEmpty(),
        reference = mpesaTransactionId?.takeIf { it.isNotBlank() } ?: reference?.takeIf { it.isNotBlank() },
        date = paymentDate(),
    )

    companion object {
        /** Start of this month, or 14 days back for the chart, whichever is earlier. */
        fun defaultSince(today: LocalDate = LocalDate.now(zone)): LocalDate = minOf(today.withDayOfMonth(1), today.minusDays(13))
    }
}

class TicketRepository(private val api: BeamApi, scope: CoroutineScope) {
    val tickets = RemoteList(scope) {
        apiCall { fetchAllPages(maxPages = 5) { api.tickets(it) } }.map { it.toTicket() }.sortedByDescending { it.created }
    }

    suspend fun raise(issue: String, priority: String, queue: String?, assignee: Int?, identityId: Int?, customerName: String): Ticket {
        val subject = issue.lineSequence().first().take(120)
        val description = if (identityId == null && customerName.isNotBlank()) "$issue\n\nCustomer: $customerName" else issue
        val dto = apiCall { api.createTicket(TicketCreate(subject, description, priority, queue, assignee, identityId)) }
        val ticket = dto.toTicket().let { if (it.customerName.isBlank()) it.copy(customerName = customerName) else it }
        tickets.update { listOf(ticket) + it }
        return ticket
    }

    suspend fun resolve(ticket: Ticket) {
        val updated = apiCall { api.resolveTicket(ticket.id) }.toTicket()
        tickets.update { list -> list.map { if (it.id == ticket.id) updated.copy(customerName = updated.customerName.ifBlank { ticket.customerName }) else it } }
    }

    private fun TicketDto.toTicket() = Ticket(
        id = id,
        subject = subject,
        description = description.ifBlank { subject },
        status = TicketStatus.from(status),
        high = priority == "high" || priority == "urgent",
        identityId = customerIdentity,
        customerName = customerName?.takeIf { it.isNotBlank() }
            ?: description.lineSequence().firstOrNull { it.startsWith("Customer: ") }?.removePrefix("Customer: ").orEmpty(),
        created = parseDateTime(createdAt),
        slaStatus = slaStatus,
        priority = priority,
    )
}

/** Details the app needs for an installation that the field-job model has no fields for. */
data class JobDraft(
    val customerName: String,
    val identityId: Int?,
    val phone: String,
    val location: String,
    val notes: String,
    val hasRouter: Boolean,
    val hasPoe: Boolean = false,
    val date: LocalDate,
    val time: LocalTime,
    val technicianId: Int?,
    val type: JobType = JobType.Installation,
    val priority: String = "medium",
)

class JobRepository(private val api: BeamApi, scope: CoroutineScope) {
    val jobs = RemoteList(scope) { apiCall { fetchAllPages(maxPages = 10) { api.jobs(it) } }.map { it.toJob() } }
    val staff = RemoteList(scope) {
        apiCall { fetchAllPages { api.staff(it) } }.filter { it.isActive && it.user != null }.map { StaffMember(it.user!!, it.name.ifBlank { it.email }) }
    }

    fun byId(id: Int): Job? = jobs.items.firstOrNull { it.id == id }

    suspend fun schedule(draft: JobDraft): Job = save(null, draft)

    suspend fun update(id: Int, draft: JobDraft): Job = save(id, draft)

    private suspend fun save(id: Int?, draft: JobDraft): Job {
        val scheduledFor = isoOffset(draft.date, draft.time.hour, draft.time.minute)
        val body = JobCreate(
            title = draft.customerName.trim(),
            description = encodeDescription(draft),
            scheduledFor = scheduledFor,
            customerIdentity = draft.identityId,
            jobType = draft.type.key,
            priority = draft.priority,
        )
        var dto = apiCall { if (id == null) api.createJob(body) else api.updateJob(id, body) }
        if (draft.technicianId != null && draft.technicianId != dto.assignedTo) {
            dto = apiCall { api.assignJob(dto.id, JobAssign(draft.technicianId, scheduledFor)) }
        }
        val job = dto.toJob()
        jobs.update { list -> if (id == null) listOf(job) + list else list.map { if (it.id == id) job else it } }
        return job
    }

    /** On-site check-in (moves the job to in progress), with the phone's location when known. */
    suspend fun checkIn(job: Job, lat: Double?, lng: Double?) {
        val dto = apiCall {
            api.checkInJobAt(job.id, com.helper.beamnetworks.data.api.JobCheckInRequest(lat?.let { "%.6f".format(java.util.Locale.US, it) }, lng?.let { "%.6f".format(java.util.Locale.US, it) }, "Checked in from Beam app"))
        }
        jobs.update { list -> list.map { if (it.id == job.id) dto.toJob() else it } }
    }

    suspend fun cancel(job: Job) {
        val dto = apiCall { api.cancelJob(job.id, com.helper.beamnetworks.data.api.NoteRequest("Cancelled in Beam app")) }
        jobs.update { list -> list.map { if (it.id == job.id) dto.toJob() else it } }
    }

    /** Completion only works from in_progress (API.md §16), so check in first when needed. */
    suspend fun complete(job: Job, used: Map<Int, Int>, code: String = "", confirmed: Boolean = false, technicianNotes: String = "") {
        if (job.status != JobStatus.InProgress) apiCall { api.checkInJob(job.id) }
        val dto = apiCall {
            api.completeJobFull(
                job.id,
                JobCompleteFull(
                    completionNotes = "Marked done in Beam app",
                    technicianNotes = technicianNotes.trim().ifBlank { null },
                    completionCode = code.trim().ifBlank { null },
                    customerConfirmed = confirmed,
                    inventoryUsed = used.filterValues { it > 0 }.map { (item, qty) -> InventoryUse(item, qty) },
                ),
            )
        }
        jobs.update { list -> list.map { if (it.id == job.id) dto.toJob() else it } }
    }

    private fun JobDto.toJob(): Job {
        val lines = description.orEmpty().lines()
        fun field(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
        // Jobs made in the app always carry a "Router:" line; anything else (web, escalated tickets)
        // has a plain description, which is the job's notes rather than a location
        val appFormat = lines.any { it.startsWith(ROUTER) }
        val location = if (appFormat) lines.firstOrNull { line -> line.isNotBlank() && PREFIXES.none { line.startsWith(it) } }.orEmpty() else ""
        val notes = if (appFormat) field(NOTES).orEmpty() else description.orEmpty().trim()
        return Job(
            id = id,
            customerName = customerName?.takeIf { it.isNotBlank() } ?: title,
            identityId = customerIdentity,
            phone = customerPhone?.takeIf { it.isNotBlank() } ?: field(PHONE),
            location = location,
            notes = notes,
            hasRouter = field(ROUTER) == "yes",
            hasPoe = field(POE) == "yes",
            scheduled = parseDateTime(scheduledFor),
            status = JobStatus.from(status),
            technicianId = assignedTo,
            technicianName = assignedToName?.takeIf { it.isNotBlank() },
            completedAt = parseDateTime(completedAt),
            type = JobType.from(jobType),
            high = priority == "high" || priority == "urgent",
            checkedInAt = parseDateTime(checkInAt),
            sourceTicket = sourceTicketSubject?.takeIf { it.isNotBlank() },
            priority = priority ?: "medium",
        )
    }

    private fun encodeDescription(draft: JobDraft) = buildList {
        add(draft.location.trim())
        if (draft.phone.isNotBlank()) add(PHONE + draft.phone.trim())
        add(ROUTER + if (draft.hasRouter) "yes" else "no")
        add(POE + if (draft.hasPoe) "yes" else "no")
        if (draft.notes.isNotBlank()) add(NOTES + draft.notes.trim().replace('\n', ' '))
    }.joinToString("\n")

    private companion object {
        const val PHONE = "Phone: "
        const val ROUTER = "Router: "
        const val POE = "PoE: "
        const val NOTES = "Notes: "
        val PREFIXES = listOf(PHONE, ROUTER, POE, NOTES)
    }
}

class InventoryRepository(private val api: BeamApi, scope: CoroutineScope) {
    val items = RemoteList(scope) { apiCall { fetchAllPages { api.inventory(it) } }.map { it.toItem() }.sortedBy { it.name.lowercase() } }

    /** Adds a product, or adds [quantity] to an existing one with the same name. */
    suspend fun addOrRestock(name: String, quantity: Int, reorderAt: Int, itemType: String = "consumable", sku: String? = null, unitCost: Double? = null, unitPrice: Double? = null): StockItem {
        val existing = items.items.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        val dto = if (existing != null) {
            var updated = if (quantity != 0) apiCall { api.adjustItem(existing.id, AdjustRequest(quantity, "Restocked in Beam app")) } else null
            if (reorderAt != existing.reorderAt) updated = apiCall { api.updateItem(existing.id, InventoryUpdate(reorderAt)) }
            updated ?: return existing
        } else {
            apiCall { api.createItem(InventoryCreate(name.trim(), sku?.trim()?.takeIf { it.isNotEmpty() } ?: skuFor(name), itemType = itemType, unitCost = unitCost?.let { moneyString(it) }, unitPrice = unitPrice?.let { moneyString(it) }, quantity = quantity, reorderThreshold = reorderAt)) }
        }
        val item = dto.toItem()
        items.update { list -> (list.filterNot { it.id == item.id } + item).sortedBy { it.name.lowercase() } }
        return item
    }

    suspend fun setReorderAt(item: StockItem, reorderAt: Int) {
        val updated = apiCall { api.updateItem(item.id, InventoryUpdate(reorderAt)) }.toItem()
        items.update { list -> list.map { if (it.id == item.id) updated else it } }
    }

    /** Shows the new quantity straight away; [commitAdjust] sends the accumulated change. */
    fun adjustLocally(id: Int, delta: Int) {
        items.update { list -> list.map { if (it.id == id) it.copy(quantity = (it.quantity + delta).coerceAtLeast(0)) else it } }
    }

    suspend fun commitAdjust(id: Int, delta: Int) {
        if (delta == 0) return
        try {
            val updated = apiCall { api.adjustItem(id, AdjustRequest(delta, "Stock count in Beam app")) }.toItem()
            items.update { list -> list.map { if (it.id == id) updated else it } }
        } catch (e: ApiException) {
            adjustLocally(id, -delta)
            throw e
        }
    }

    /** A stock movement (issue to a technician, receive, return…); the server updates the quantity. */
    suspend fun move(item: StockItem, type: String, quantity: Int, staffUserId: Int?, notes: String?) {
        val updated = apiCall { api.moveItem(item.id, InventoryMoveRequest(type, quantity, assignedToUser = staffUserId, notes = notes?.ifBlank { null })) }.toItem()
        items.update { list -> list.map { if (it.id == item.id) updated else it } }
    }

    /** Archives (soft-deletes) the product; its movement history is kept. */
    suspend fun archive(item: StockItem) {
        val response = apiCall { api.archiveItem(item.id) }
        if (!response.isSuccessful) throw ApiException("Couldn't archive ${item.name} (error ${response.code()})", response.code())
        items.update { list -> list.filterNot { it.id == item.id } }
    }

    private fun InventoryItemDto.toItem() = StockItem(id, name, quantity, reorderThreshold, itemType)

    private fun skuFor(name: String): String {
        val base = name.uppercase().filter { it.isLetterOrDigit() }.take(10).ifEmpty { "ITEM" }
        return "$base-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}"
    }
}
