package com.helper.beamnetworks.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Wire models for the second wave of features: customer 360, money tools, ticket and job
// workflows, outages and outreach. Shapes follow API.md (the generated schema is unreliable for
// custom actions).

// ---- Customer 360 ----

@Serializable
data class CustomerFullDto(
    val id: Int,
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    val email: String? = null,
    @SerialName("plan_name") val planName: String? = null,
    val plan: Int? = null,
    val network: Int? = null,
    @SerialName("network_name") val networkName: String? = null,
    @SerialName("radius_username") val radiusUsername: String? = null,
    @SerialName("radius_password") val radiusPassword: String? = null,
    @SerialName("account_code") val accountCode: String? = null,
    val status: String = "",
    val balance: String? = null,
    val country: String? = null,
    val city: String? = null,
    @SerialName("sub_county") val subCounty: String? = null,
    val ward: String? = null,
    val road: String? = null,
    val estate: String? = null,
    val street: String? = null,
    val building: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    @SerialName("common_marker") val commonMarker: String? = null,
    @SerialName("more_details") val moreDetails: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    @SerialName("static_ip") val staticIp: String? = null,
    @SerialName("service_expires_at") val serviceExpiresAt: String? = null,
    @SerialName("extension_expires_at") val extensionExpiresAt: String? = null,
    @SerialName("paused_until") val pausedUntil: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("is_billed") val isBilled: Boolean? = null,
    @SerialName("billing_cycle_day") val billingCycleDay: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** PATCH body; null fields are left out (ApiJson has explicitNulls = false). */
@Serializable
data class CustomerUpdate(
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val country: String? = null,
    val city: String? = null,
    @SerialName("sub_county") val subCounty: String? = null,
    val ward: String? = null,
    val street: String? = null,
    @SerialName("more_details") val moreDetails: String? = null,
    @SerialName("is_billed") val isBilled: Boolean? = null,
    val estate: String? = null,
    val road: String? = null,
    val building: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    @SerialName("common_marker") val commonMarker: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
)

@Serializable
data class UsagePointDto(val date: String = "", val bytes: Long = 0)

@Serializable
data class WalletDto(val balance: String? = null, val transactions: List<JsonObject> = emptyList())

@Serializable
data class PayLinkDto(val link: String = "")

@Serializable
data class ChangePlanRequest(val plan: Int)

@Serializable
data class PauseRequest(val weeks: Int? = null, val days: Int? = null)

@Serializable
data class SessionDto(
    val id: Int,
    val customer: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("network_name") val networkName: String? = null,
    @SerialName("framed_ip") val framedIp: String? = null,
    @SerialName("nas_id") val nasId: String? = null,
    val status: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("bytes_in") val bytesIn: Long = 0,
    @SerialName("bytes_out") val bytesOut: Long = 0,
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
)

@Serializable
data class DisconnectResponse(
    val session: SessionDto? = null,
    @SerialName("disconnect_sent") val disconnectSent: Boolean = false,
    val detail: String? = null,
)

// ---- Money ----

@Serializable
data class LineItemDto(val description: String = "", val quantity: String? = null, @SerialName("unit_price") val unitPrice: String? = null, val amount: String? = null)

@Serializable
data class InvoiceFullDto(
    val id: Int,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    val customer: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val status: String = "",
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val total: String = "0",
    @SerialName("amount_paid") val amountPaid: String? = null,
    @SerialName("amount_due") val amountDue: String? = null,
    @SerialName("line_items") val lineItems: List<LineItemDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NewLineItem(val description: String, val quantity: String = "1", @SerialName("unit_price") val unitPrice: String)

@Serializable
data class InvoiceCreate(
    val customer: Int,
    @SerialName("line_items") val lineItems: List<NewLineItem>,
    @SerialName("due_in_days") val dueInDays: Int = 7,
)

@Serializable
data class CreditNoteDto(
    val id: Int,
    val customer: Int? = null,
    val amount: String = "0",
    val remaining: String? = null,
    val reason: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreditNoteCreate(val customer: Int, val amount: String, val reason: String)

@Serializable
data class UnmatchedReceiptDto(
    val id: Int,
    @SerialName("trans_id") val transId: String = "",
    @SerialName("bill_ref_number") val billRef: String? = null,
    val amount: String = "0",
    val msisdn: String? = null,
    @SerialName("payer_name") val payerName: String? = null,
    val status: String = "unmatched",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AssignReceiptRequest(@SerialName("customer_type") val customerType: String = "pppoe", @SerialName("customer_id") val customerId: Int)

@Serializable
data class VerifyPaymentRequest(@SerialName("mpesa_code") val mpesaCode: String? = null)

@Serializable
data class PendingPaymentDto(
    val id: Int,
    val method: String? = null,
    val status: String = "",
    val amount: String = "0",
    @SerialName("pppoe_customer") val pppoeCustomer: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("mpesa_phone") val mpesaPhone: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---- Tickets ----

@Serializable
data class TicketCommentDto(
    val id: Int,
    val body: String = "",
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("author_email") val authorEmail: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TicketHistoryDto(
    val id: Int,
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String = "",
    val note: String? = null,
    @SerialName("changed_by_email") val changedByEmail: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TicketDetailDto(
    val id: Int,
    val subject: String = "",
    val description: String = "",
    val source: String? = null,
    val status: String = "open",
    val priority: String = "medium",
    val queue: String? = null,
    @SerialName("customer_identity") val customerIdentity: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("network_name") val networkName: String? = null,
    val assignee: Int? = null,
    @SerialName("assignee_email") val assigneeEmail: String? = null,
    @SerialName("created_by_email") val createdByEmail: String? = null,
    @SerialName("sla_status") val slaStatus: String? = null,
    @SerialName("sla_due_at") val slaDueAt: String? = null,
    @SerialName("escalation_level") val escalationLevel: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val comments: List<TicketCommentDto> = emptyList(),
    val history: List<TicketHistoryDto> = emptyList(),
)

@Serializable
data class CommentRequest(val body: String, @SerialName("is_internal") val isInternal: Boolean)

@Serializable
data class TicketAssignRequest(val assignee: Int? = null, val priority: String? = null, val note: String? = null)

@Serializable
data class EscalateToJobRequest(
    val title: String? = null,
    @SerialName("job_type") val jobType: String = "repair",
    @SerialName("assigned_to") val assignedTo: Int? = null,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    val note: String? = null,
)

@Serializable
data class EscalateToJobResponse(val ticket: TicketDetailDto? = null, val job: JsonElement? = null)

// ---- Field jobs ----

@Serializable
data class JobCheckInRequest(
    @SerialName("check_in_lat") val lat: String? = null,
    @SerialName("check_in_lng") val lng: String? = null,
    @SerialName("check_in_notes") val notes: String? = null,
)

// ---- Outages and outreach ----

@Serializable
data class MaintenanceDto(
    val id: Int,
    val title: String = "",
    val description: String? = null,
    @SerialName("network_name") val networkName: String? = null,
    val estate: String? = null,
    val ward: String? = null,
    val road: String? = null,
    val status: String = "scheduled",
    @SerialName("scheduled_start") val scheduledStart: String? = null,
    @SerialName("scheduled_end") val scheduledEnd: String? = null,
    @SerialName("auto_compensate") val autoCompensate: Boolean = false,
    @SerialName("affected_customer_count") val affectedCustomers: Int = 0,
)

@Serializable
data class MaintenanceCreate(
    val title: String,
    val description: String? = null,
    val network: Int? = null,
    val estate: String? = null,
    val ward: String? = null,
    val road: String? = null,
    @SerialName("auto_compensate") val autoCompensate: Boolean,
    @SerialName("scheduled_start") val scheduledStart: String,
    @SerialName("scheduled_end") val scheduledEnd: String? = null,
)

@Serializable
data class CountResponse(val count: Int = 0)

@Serializable
data class CompensateRequest(
    val mode: String,
    val reason: String,
    val durations: Map<String, Long>? = null,
    @SerialName("customer_ids") val customerIds: List<Int>? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
)

@Serializable
data class CompensateResponse(
    val compensated: Int = 0,
    @SerialName("skipped_ids") val skippedIds: List<Int> = emptyList(),
    @SerialName("suspended_ids") val suspendedIds: List<Int> = emptyList(),
)

@Serializable
data class AnnouncementDto(
    val id: Int,
    val subject: String = "",
    val body: String = "",
    val target: String = "all",
    @SerialName("customer_name") val customerName: String? = null,
    val status: String = "",
    @SerialName("recipient_count") val recipientCount: Int = 0,
    @SerialName("sms_count") val smsCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AnnouncementCreate(
    val subject: String,
    val body: String,
    val channels: List<String>,
    val target: String,
    @SerialName("customer_identity") val customerIdentity: Int? = null,
)
