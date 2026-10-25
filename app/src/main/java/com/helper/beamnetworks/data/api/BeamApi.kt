package com.helper.beamnetworks.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Tenant-staff endpoints the app uses. Every path keeps its trailing slash (API.md §1.2). */
interface BeamApi {
    // Auth
    @POST("auth/tenant/login/")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/tenant/logout/")
    suspend fun logout(@Body body: RefreshRequest)

    @GET("tenants/profile/")
    suspend fun tenantProfile(): TenantProfileDto

    // Customers
    @GET("customers/pppoe/")
    suspend fun customers(@Query("page") page: Int, @Query("page_size") pageSize: Int = 100): Page<CustomerDto>

    @POST("customers/pppoe/")
    suspend fun createCustomer(@Body body: CustomerCreate): CustomerDto

    @POST("customers/pppoe/{id}/suspend/")
    suspend fun suspendCustomer(@Path("id") id: Int): CustomerDto

    @POST("customers/pppoe/{id}/reactivate/")
    suspend fun reactivateCustomer(@Path("id") id: Int): CustomerDto

    @POST("customers/pppoe/{id}/extend/")
    suspend fun extendCustomer(@Path("id") id: Int, @Body body: ExtendRequest): CustomerDto

    @POST("customers/pppoe/{id}/send-pay-link/")
    suspend fun sendPayLink(@Path("id") id: Int): DetailResponse

    @GET("customers/enrollments/")
    suspend fun enrollments(@Query("page") page: Int, @Query("page_size") pageSize: Int = 100): Page<EnrollmentDto>

    @GET("customers/staff/")
    suspend fun staff(@Query("page") page: Int, @Query("page_size") pageSize: Int = 100, @Query("is_active") active: Boolean = true): Page<StaffDto>

    @GET("plans/")
    suspend fun plans(@Query("type") type: String = "pppoe", @Query("is_active") active: Boolean = true, @Query("page_size") pageSize: Int = 200): Page<PlanDto>

    @GET("networks/")
    suspend fun networks(@Query("page_size") pageSize: Int = 100): Page<NetworkDto>

    @GET("sessions/pppoe/")
    suspend fun pppoeSessions(@Query("status") status: String = "active"): List<PppoeSessionDto>

    // Payments and invoices
    @GET("payments/")
    suspend fun payments(@Query("page") page: Int, @Query("status") status: String = "confirmed", @Query("page_size") pageSize: Int = 200): Page<PaymentDto>

    @POST("payments/cash/")
    suspend fun recordCashPayment(@Body body: CashPaymentRequest): PaymentDto

    @GET("invoices/")
    suspend fun invoices(@Query("page") page: Int, @Query("page_size") pageSize: Int = 200): Page<InvoiceDto>

    // Tickets
    @GET("tickets/")
    suspend fun tickets(@Query("page") page: Int, @Query("page_size") pageSize: Int = 100): Page<TicketDto>

    @POST("tickets/")
    suspend fun createTicket(@Body body: TicketCreate): TicketDto

    @POST("tickets/{id}/resolve/")
    suspend fun resolveTicket(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): TicketDto

    // Field jobs
    @GET("field-jobs/")
    suspend fun jobs(@Query("page") page: Int, @Query("job_type") jobType: String? = null, @Query("page_size") pageSize: Int = 200): Page<JobDto>

    @POST("field-jobs/")
    suspend fun createJob(@Body body: JobCreate): JobDto

    @PATCH("field-jobs/{id}/")
    suspend fun updateJob(@Path("id") id: Int, @Body body: JobCreate): JobDto

    @POST("field-jobs/{id}/assign/")
    suspend fun assignJob(@Path("id") id: Int, @Body body: JobAssign): JobDto

    @POST("field-jobs/{id}/check_in/")
    suspend fun checkInJob(@Path("id") id: Int, @Body body: JobCheckIn = JobCheckIn()): JobDto

    @POST("field-jobs/{id}/complete/")
    suspend fun completeJob(@Path("id") id: Int, @Body body: JobComplete): JobDto

    // Inventory
    @GET("inventory/items/")
    suspend fun inventory(@Query("page") page: Int, @Query("page_size") pageSize: Int = 200): Page<InventoryItemDto>

    @POST("inventory/items/")
    suspend fun createItem(@Body body: InventoryCreate): InventoryItemDto

    @PATCH("inventory/items/{id}/")
    suspend fun updateItem(@Path("id") id: Int, @Body body: InventoryUpdate): InventoryItemDto

    @POST("inventory/items/{id}/adjust/")
    suspend fun adjustItem(@Path("id") id: Int, @Body body: AdjustRequest): InventoryItemDto

    // Monitoring, outages and account status (notifications)
    @GET("devices/")
    suspend fun devices(): List<DeviceDto>

    @GET("monitoring/alerts/")
    suspend fun monitoringAlerts(@Query("state") state: String = "open", @Query("page_size") pageSize: Int = 100): Page<MonitoringAlertDto>

    @POST("monitoring/alerts/{id}/acknowledge/")
    suspend fun acknowledgeAlert(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): MonitoringAlertDto

    @POST("monitoring/alerts/{id}/resolve/")
    suspend fun resolveAlert(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): MonitoringAlertDto

    @GET("outages/offline-periods/")
    suspend fun offlinePeriods(
        @Query("page") page: Int,
        @Query("status") status: String = "open",
        @Query("min_duration_minutes") minDurationMinutes: Int? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("page_size") pageSize: Int = 200,
    ): Page<OfflinePeriodDto>

    @GET("customers/activity-log/")
    suspend fun activityLog(
        @Query("date_from") dateFrom: String? = null,
        @Query("event_type") eventTypes: String? = null,
        @Query("customer") customer: Int? = null,
        @Query("page_size") pageSize: Int = 100,
    ): ActivityPage

    @GET("tenants/account-status/")
    suspend fun accountStatus(): AccountStatusDto

    @GET("settings/sms-balance/")
    suspend fun smsBalance(): BalanceDto

    @GET("settings/payhero-wallet-balance/")
    suspend fun payheroBalance(): BalanceDto

    // ---- Customer 360 ----
    @GET("customers/pppoe/{id}/")
    suspend fun customer(@Path("id") id: Int): CustomerFullDto

    @PATCH("customers/pppoe/{id}/")
    suspend fun updateCustomer(@Path("id") id: Int, @Body body: CustomerUpdate): CustomerDto

    @GET("customers/pppoe/{id}/usage/")
    suspend fun usage(@Path("id") id: Int): List<UsagePointDto>

    @GET("customers/pppoe/{id}/wallet/")
    suspend fun wallet(@Path("id") id: Int): WalletDto

    @GET("customers/pppoe/{id}/pay-link/")
    suspend fun payLink(@Path("id") id: Int): PayLinkDto

    @POST("customers/pppoe/{id}/change-plan/")
    suspend fun changePlan(@Path("id") id: Int, @Body body: ChangePlanRequest): CustomerDto

    @POST("customers/pppoe/{id}/pause/")
    suspend fun pauseCustomer(@Path("id") id: Int, @Body body: PauseRequest): CustomerDto

    @POST("customers/pppoe/{id}/resume/")
    suspend fun resumeCustomer(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): CustomerDto

    @POST("customers/pppoe/{id}/resend-portal-credentials/")
    suspend fun resendPortalCredentials(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): CustomerDto

    @GET("sessions/pppoe/")
    suspend fun sessions(@Query("status") status: String = "active", @Query("customer") customer: Int? = null): List<SessionDto>

    @POST("sessions/pppoe/{id}/disconnect/")
    suspend fun disconnectSession(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): DisconnectResponse

    // ---- Money ----
    @GET("invoices/")
    suspend fun customerInvoices(@Query("customer") customer: Int, @Query("page_size") pageSize: Int = 50): Page<InvoiceFullDto>

    @POST("invoices/")
    suspend fun createInvoice(@Body body: InvoiceCreate): InvoiceFullDto

    @POST("invoices/{id}/send/")
    suspend fun sendInvoice(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): InvoiceFullDto

    @GET("invoices/{id}/pdf/")
    @retrofit2.http.Streaming
    suspend fun invoicePdf(@Path("id") id: Int): okhttp3.ResponseBody

    @GET("credit-notes/")
    suspend fun creditNotes(@Query("customer") customer: Int, @Query("page_size") pageSize: Int = 50): Page<CreditNoteDto>

    @POST("credit-notes/")
    suspend fun createCreditNote(@Body body: CreditNoteCreate): CreditNoteDto

    @GET("unmatched-receipts/")
    suspend fun unmatchedReceipts(@Query("status") status: String = "unmatched", @Query("page_size") pageSize: Int = 200): Page<UnmatchedReceiptDto>

    @POST("unmatched-receipts/{id}/assign/")
    suspend fun assignReceipt(@Path("id") id: Int, @Body body: AssignReceiptRequest): UnmatchedReceiptDto

    @POST("unmatched-receipts/{id}/ignore/")
    suspend fun ignoreReceipt(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): UnmatchedReceiptDto

    @GET("payments/")
    suspend fun pendingPayments(@Query("status") status: String = "pending", @Query("page_size") pageSize: Int = 100): Page<PendingPaymentDto>

    @POST("payments/{id}/verify/")
    suspend fun verifyPayment(@Path("id") id: Int, @Body body: VerifyPaymentRequest): PendingPaymentDto

    @GET("reports/revenue/")
    suspend fun revenueReport(@Query("months") months: Int = 6): kotlinx.serialization.json.JsonObject

    // ---- Tickets ----
    @GET("tickets/{id}/")
    suspend fun ticket(@Path("id") id: Int): TicketDetailDto

    @POST("tickets/{id}/comment/")
    suspend fun commentTicket(@Path("id") id: Int, @Body body: CommentRequest): TicketDetailDto

    @POST("tickets/{id}/assign/")
    suspend fun assignTicket(@Path("id") id: Int, @Body body: TicketAssignRequest): TicketDetailDto

    @POST("tickets/{id}/escalate-to-job/")
    suspend fun escalateToJob(@Path("id") id: Int, @Body body: EscalateToJobRequest): EscalateToJobResponse

    @POST("tickets/{id}/close/")
    suspend fun closeTicket(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): TicketDetailDto

    @POST("tickets/{id}/reopen/")
    suspend fun reopenTicket(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): TicketDetailDto

    @POST("tickets/{id}/resolve/")
    suspend fun resolveTicketDetail(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): TicketDetailDto

    // ---- Field jobs ----
    @POST("field-jobs/{id}/check_in/")
    suspend fun checkInJobAt(@Path("id") id: Int, @Body body: JobCheckInRequest): JobDto

    @POST("field-jobs/{id}/cancel/")
    suspend fun cancelJob(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): JobDto

    // ---- Outages and outreach ----
    @GET("outages/maintenances/")
    suspend fun maintenances(@Query("page_size") pageSize: Int = 50): Page<MaintenanceDto>

    @POST("outages/maintenances/")
    suspend fun createMaintenance(@Body body: MaintenanceCreate): MaintenanceDto

    @GET("outages/maintenances/preview-affected/")
    suspend fun previewAffected(
        @Query("network") network: Int? = null,
        @Query("estate") estate: String? = null,
        @Query("ward") ward: String? = null,
        @Query("road") road: String? = null,
    ): CountResponse

    @POST("outages/maintenances/{id}/start-now/")
    suspend fun startMaintenance(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): MaintenanceDto

    @POST("outages/maintenances/{id}/complete/")
    suspend fun completeMaintenance(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): MaintenanceDto

    @POST("outages/maintenances/{id}/cancel/")
    suspend fun cancelMaintenance(@Path("id") id: Int, @Body body: NoteRequest = NoteRequest()): MaintenanceDto

    @GET("outages/offline-periods/")
    suspend fun downtimeSince(
        @Query("date_from") dateFrom: String,
        @Query("min_duration_minutes") minDurationMinutes: Int,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): Page<OfflinePeriodDto>

    @POST("outages/offline-periods/bulk-compensate/")
    suspend fun compensate(@Body body: CompensateRequest): CompensateResponse

    @GET("communications/announcements/")
    suspend fun announcements(@Query("page_size") pageSize: Int = 30): Page<AnnouncementDto>

    @POST("communications/announcements/")
    suspend fun sendAnnouncement(@Body body: AnnouncementCreate): AnnouncementDto
}
