package com.helper.beamnetworks.data

import java.time.LocalDate
import java.time.ZonedDateTime

enum class CustomerStatus(val label: String) {
    Active("Active"), Suspended("Suspended"), Overdue("Overdue"), Paused("Paused"), Archived("Archived");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Active
    }
}

data class Customer(
    val id: Int,
    val name: String,
    val phone: String,
    val email: String?,
    val planId: Int?,
    val planName: String,
    val networkName: String?,
    val pppoeUser: String?,
    val accountCode: String?,
    val status: CustomerStatus,
    /** Amount the customer owes (the API's balance = invoices − payments). Negative = credit. */
    val amountDue: Double,
    val lastSeen: ZonedDateTime?,
    val online: Boolean,
    val identityId: Int?,
    val location: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val owing: Boolean get() = amountDue > 0.005

    /** Balance as the design shows it: what the customer has in hand, negative when owing. */
    val displayBalance: Double get() = -amountDue
}

data class Payment(
    val id: Int,
    val customerId: Int?,
    val customerName: String,
    val amount: Double,
    val method: String,
    val reference: String?,
    val date: ZonedDateTime?,
)

data class Invoice(val id: Int, val customerId: Int?, val total: Double, val date: LocalDate?)

enum class TicketStatus { Open, InProgress, Resolved, Closed;
    val isOpen: Boolean get() = this == Open || this == InProgress

    companion object {
        fun from(value: String) = when (value) {
            "in_progress" -> InProgress
            "resolved" -> Resolved
            "closed" -> Closed
            else -> Open
        }
    }
}

data class Ticket(
    val id: Int,
    val subject: String,
    val description: String,
    val status: TicketStatus,
    val high: Boolean,
    val identityId: Int?,
    val customerName: String,
    val created: ZonedDateTime?,
    val slaStatus: String? = null,
    val priority: String = "medium",
) {
    val number: String get() = "#T-$id"
}

enum class JobStatus { Open, Scheduled, InProgress, Completed, Cancelled;
    val isUpcoming: Boolean get() = this == Open || this == Scheduled || this == InProgress

    companion object {
        fun from(value: String) = when (value) {
            "scheduled" -> Scheduled
            "in_progress" -> InProgress
            "completed" -> Completed
            "cancelled" -> Cancelled
            else -> Open
        }
    }
}

enum class JobType(val key: String, val label: String) {
    Installation("installation", "Installation"),
    Repair("repair", "Repair"),
    Maintenance("maintenance", "Maintenance"),
    Survey("survey", "Site survey"),
    Support("support", "Support visit"),
    Other("other", "Other");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.key == value } ?: Other
    }
}

/** A field job: installation, repair, maintenance visit… */
data class Job(
    val id: Int,
    val customerName: String,
    val identityId: Int?,
    val phone: String?,
    val location: String,
    val notes: String,
    val hasRouter: Boolean,
    val scheduled: ZonedDateTime?,
    val status: JobStatus,
    val technicianId: Int?,
    val technicianName: String?,
    val completedAt: ZonedDateTime?,
    val type: JobType = JobType.Installation,
    val high: Boolean = false,
    val checkedInAt: ZonedDateTime? = null,
    val sourceTicket: String? = null,
    val priority: String = "medium",
)

data class StockItem(val id: Int, val name: String, val quantity: Int, val reorderAt: Int, val itemType: String = "consumable") {
    val isLow: Boolean get() = reorderAt > 0 && quantity <= reorderAt
}

data class Expense(
    val id: String,
    val title: String,
    val category: String,
    val amount: Double,
    val date: LocalDate?,
    val notes: String,
    /** Who was paid (shop or person), often read from the M-Pesa SMS. */
    val shop: String = "",
)

data class StaffMember(val userId: Int, val name: String)

data class Plan(val id: Int, val name: String, val networkScope: Int?)

data class Network(val id: Int, val name: String)
