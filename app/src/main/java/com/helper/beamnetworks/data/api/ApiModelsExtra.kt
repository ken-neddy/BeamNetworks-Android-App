package com.helper.beamnetworks.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire models for the third wave: hotspot, the full payments and invoices registers, SMS log,
// organisation settings, stock movements and the extra customer/ticket/job actions.

// ---- Hotspot (API.md §9.2, §11, §21.4) ----

@Serializable
data class HotspotCustomerDto(
    val id: Int,
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("network_name") val networkName: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("voucher_code") val voucherCode: String? = null,
    val status: String = "active",
    @SerialName("session_status") val sessionStatus: String? = null,
    @SerialName("session_started_at") val sessionStartedAt: String? = null,
    @SerialName("session_expires_at") val sessionExpiresAt: String? = null,
    @SerialName("session_last_seen_at") val sessionLastSeenAt: String? = null,
    @SerialName("session_bytes_used") val sessionBytesUsed: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class HotspotSessionDto(
    val id: Int,
    val customer: Int? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    val status: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("bytes_used") val bytesUsed: Long = 0,
)

@Serializable
data class VoucherDto(
    val id: Int,
    @SerialName("voucher_id") val voucherId: String = "",
    val code: String = "",
    @SerialName("customer_phone") val customerPhone: String? = null,
    val source: String? = null,
    val billed: Boolean = false,
    @SerialName("plan_name") val planName: String? = null,
    val price: String? = null,
    val status: String = "",
    @SerialName("redeemed_at") val redeemedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class VoucherIssueRequest(
    @SerialName("plan_id") val planId: Int,
    @SerialName("customer_phone") val customerPhone: String,
    val billed: Boolean,
)

// ---- Payments register (API.md §12.1) ----

@Serializable
data class PaymentRecordDto(
    val id: Int,
    val method: String? = null,
    val provider: String? = null,
    val status: String = "",
    val amount: String = "0",
    @SerialName("recorded_by_name") val recordedByName: String? = null,
    @SerialName("pppoe_customer") val pppoeCustomer: Int? = null,
    @SerialName("hotspot_customer") val hotspotCustomer: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_type") val customerType: String? = null,
    @SerialName("mpesa_transaction_id") val mpesaTransactionId: String? = null,
    @SerialName("mpesa_phone") val mpesaPhone: String? = null,
    val reference: String? = null,
    val note: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    @SerialName("refunded_at") val refundedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---- SMS / email log (API.md §14.1) ----

@Serializable
data class NotificationLogDto(
    val id: Int,
    @SerialName("event_type") val eventType: String = "",
    val category: String? = null,
    val channel: String = "",
    val recipient: String = "",
    @SerialName("customer_name") val customerName: String? = null,
    val status: String = "",
    val body: String = "",
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("retry_count") val retryCount: Int = 0,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class DeliveryCheckDto(
    val checked: Int = 0,
    val delivered: Int = 0,
    val failed: Int = 0,
    @SerialName("still_pending") val stillPending: Int = 0,
)

// ---- Customer actions (API.md §9.1) ----

/** Extend by weeks/days, or until [resumeAt]; the two forms are mutually exclusive. */
@Serializable
data class ExtendServiceRequest(
    val reason: String,
    val weeks: Int? = null,
    val days: Int? = null,
    @SerialName("resume_at") val resumeAt: String? = null,
)

@Serializable
data class StaticIpRequest(val enabled: Boolean, val address: String? = null)

// ---- Tickets and jobs ----

@Serializable
data class TicketEscalateRequest(val note: String? = null, val assignee: Int? = null, val queue: String? = null)

@Serializable
data class TicketQueueRequest(
    val assignee: Int? = null,
    val queue: String? = null,
    val priority: String? = null,
    val note: String? = null,
)

@Serializable
data class JobCompleteFull(
    @SerialName("completion_notes") val completionNotes: String? = null,
    @SerialName("technician_notes") val technicianNotes: String? = null,
    @SerialName("completion_code") val completionCode: String? = null,
    @SerialName("customer_confirmed") val customerConfirmed: Boolean = false,
    @SerialName("inventory_used") val inventoryUsed: List<InventoryUse> = emptyList(),
)

// ---- Inventory movements (API.md §17) ----

@Serializable
data class InventoryMoveRequest(
    @SerialName("movement_type") val movementType: String,
    val quantity: Int = 1,
    @SerialName("assigned_to_user") val assignedToUser: Int? = null,
    @SerialName("customer_identity") val customerIdentity: Int? = null,
    val reference: String? = null,
    val notes: String? = null,
)

@Serializable
data class InventoryMovementDto(
    val id: Int,
    val item: Int? = null,
    @SerialName("item_name") val itemName: String? = null,
    @SerialName("movement_type") val movementType: String = "",
    val quantity: Int = 0,
    @SerialName("assigned_to_user") val assignedToUser: Int? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    @SerialName("assigned_to_email") val assignedToEmail: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---- Organisation settings (API.md §6). Read as raw JSON; only changed fields are PATCHed. ----

typealias SettingsDto = JsonObject
