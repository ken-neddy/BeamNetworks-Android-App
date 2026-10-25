package com.helper.beamnetworks.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the Beam billing API (see handoff/.../API.md). Money is sent as strings with
// two decimals; datetimes are ISO 8601 with offset.

@Serializable
data class Page<T>(
    val count: Int = 0,
    val next: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class DetailResponse(val detail: String? = null, val code: String? = null)

// ---- Auth ----

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val access: String, val refresh: String, val user: TenantUserDto)

@Serializable
data class TenantUserDto(
    val id: Int,
    val email: String = "",
    @SerialName("role_type") val roleType: String = "",
    @SerialName("tenant_id") val tenantId: Int? = null,
    @SerialName("tenant_slug") val tenantSlug: String? = null,
    val permissions: List<String> = emptyList(),
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
)

@Serializable
data class RefreshRequest(val refresh: String)

@Serializable
data class TokenPair(val access: String, val refresh: String? = null)

@Serializable
data class TenantProfileDto(val id: Int, val name: String = "", val email: String = "")

// ---- Customers ----

@Serializable
data class CustomerDto(
    val id: Int,
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    val email: String? = null,
    val plan: Int? = null,
    @SerialName("plan_name") val planName: String? = null,
    val network: Int? = null,
    @SerialName("network_name") val networkName: String? = null,
    @SerialName("radius_username") val radiusUsername: String? = null,
    @SerialName("account_code") val accountCode: String? = null,
    val status: String = "active",
    val balance: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val estate: String? = null,
    val road: String? = null,
    val building: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    @SerialName("sub_county") val subCounty: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CustomerCreate(
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val plan: Int,
    val network: Int,
    val email: String? = null,
)

@Serializable
data class ExtendRequest(val reason: String, val days: Int)

@Serializable
data class PlanDto(
    val id: Int,
    val name: String = "",
    @SerialName("plan_type") val planType: String = "pppoe",
    val price: String? = null,
    @SerialName("network_scope") val networkScope: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class NetworkDto(val id: Int, val name: String = "")

@Serializable
data class EnrollmentDto(
    val id: Int,
    @SerialName("customer_id") val identityId: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("service_type") val serviceType: String? = null,
    @SerialName("legacy_pppoe_customer") val pppoeCustomer: Int? = null,
)

@Serializable
data class PppoeSessionDto(val id: Int, val customer: Int? = null, val status: String = "")

// ---- Payments and invoices ----

@Serializable
data class PaymentDto(
    val id: Int,
    val method: String? = null,
    val provider: String? = null,
    val status: String = "",
    val amount: String = "0",
    @SerialName("pppoe_customer") val pppoeCustomer: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("mpesa_transaction_id") val mpesaTransactionId: String? = null,
    val reference: String? = null,
    val note: String? = null,
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CashPaymentRequest(
    @SerialName("customer_type") val customerType: String,
    @SerialName("customer_id") val customerId: Int,
    val amount: String,
    val note: String,
    val reference: String? = null,
)

@Serializable
data class InvoiceDto(
    val id: Int,
    val customer: Int? = null,
    val status: String = "",
    val total: String = "0",
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---- Tickets ----

@Serializable
data class TicketDto(
    val id: Int,
    val subject: String = "",
    val description: String = "",
    val status: String = "open",
    val priority: String = "medium",
    @SerialName("customer_identity") val customerIdentity: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val source: String? = null,
    @SerialName("created_by") val createdBy: Int? = null,
    @SerialName("sla_status") val slaStatus: String? = null,
)

@Serializable
data class TicketCreate(
    val subject: String,
    val description: String,
    val priority: String,
    @SerialName("customer_identity") val customerIdentity: Int? = null,
)

@Serializable
data class NoteRequest(val note: String? = null)

// ---- Field jobs ----

@Serializable
data class JobDto(
    val id: Int,
    val title: String = "",
    val description: String? = null,
    val status: String = "open",
    @SerialName("job_type") val jobType: String? = null,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("customer_identity") val customerIdentity: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("assigned_to") val assignedTo: Int? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val priority: String? = null,
    @SerialName("check_in_at") val checkInAt: String? = null,
    @SerialName("source_ticket") val sourceTicket: Int? = null,
    @SerialName("source_ticket_subject") val sourceTicketSubject: String? = null,
    @SerialName("technician_notes") val technicianNotes: String? = null,
    @SerialName("completion_notes") val completionNotes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class JobCreate(
    val title: String,
    val description: String,
    @SerialName("job_type") val jobType: String = "installation",
    @SerialName("scheduled_for") val scheduledFor: String,
    @SerialName("customer_identity") val customerIdentity: Int? = null,
)

@Serializable
data class JobAssign(
    @SerialName("assigned_to") val assignedTo: Int,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
)

@Serializable
data class JobCheckIn(@SerialName("check_in_notes") val notes: String? = null)

@Serializable
data class InventoryUse(@SerialName("item_id") val itemId: Int, val quantity: Int)

@Serializable
data class JobComplete(
    @SerialName("completion_notes") val completionNotes: String? = null,
    @SerialName("inventory_used") val inventoryUsed: List<InventoryUse> = emptyList(),
)

@Serializable
data class StaffDto(
    val id: Int,
    val user: Int? = null,
    val name: String = "",
    val email: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)

// ---- Inventory ----

@Serializable
data class InventoryItemDto(
    val id: Int,
    val name: String = "",
    val sku: String = "",
    @SerialName("item_type") val itemType: String = "consumable",
    @SerialName("quantity_in_stock") val quantity: Int = 0,
    @SerialName("reorder_threshold") val reorderThreshold: Int = 0,
    @SerialName("is_low_stock") val isLowStock: Boolean? = null,
)

@Serializable
data class InventoryCreate(
    val name: String,
    val sku: String,
    @SerialName("item_type") val itemType: String = "consumable",
    @SerialName("quantity_in_stock") val quantity: Int,
    @SerialName("reorder_threshold") val reorderThreshold: Int,
)

@Serializable
data class InventoryUpdate(@SerialName("reorder_threshold") val reorderThreshold: Int)

@Serializable
data class AdjustRequest(val delta: Int, val notes: String? = null)

// ---- Monitoring, outages and account status (notifications) ----

@Serializable
data class DeviceDto(
    val id: Int,
    val name: String = "",
    @SerialName("network_name") val networkName: String? = null,
    val status: String = "",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class MonitoringAlertDto(
    val id: Int,
    val title: String = "",
    val message: String = "",
    val severity: String = "",
    val state: String = "open",
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("network_name") val networkName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class OfflinePeriodDto(
    val id: Int,
    val customer: Int? = null,
    @SerialName("customer_name") val customerName: String = "",
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("customer_status") val customerStatus: String? = null,
    @SerialName("network_name") val networkName: String? = null,
    @SerialName("scheduled_maintenance") val scheduledMaintenance: Int? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
    @SerialName("snapshot_ward") val ward: String? = null,
)

@Serializable
data class ActivityPage(val count: Int = 0, val results: List<ActivityEventDto> = emptyList())

@Serializable
data class ActivityEventDto(
    val id: kotlinx.serialization.json.JsonElement? = null,
    val type: String = "",
    val timestamp: String? = null,
    @SerialName("customer_id") val customerId: Int? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val title: String? = null,
    val detail: String? = null,
)

@Serializable
data class AccountStatusDto(
    val status: String = "",
    @SerialName("trial_ends_at") val trialEndsAt: String? = null,
    @SerialName("trial_expired") val trialExpired: Boolean = false,
    @SerialName("read_only") val readOnly: Boolean = false,
    val suspended: Boolean = false,
    @SerialName("amount_due") val amountDue: String? = null,
)

@Serializable
data class BalanceDto(
    val provider: String? = null,
    val available: Boolean? = null,
    val balance: String? = null,
    val low: Boolean = false,
    val applicable: Boolean = true,
)
