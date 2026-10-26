package com.helper.beamnetworks.data

import android.content.Context
import com.helper.beamnetworks.data.api.ApiClient
import com.helper.beamnetworks.data.api.LoginRequest
import com.helper.beamnetworks.data.api.RefreshRequest
import com.helper.beamnetworks.data.api.Session
import com.helper.beamnetworks.data.api.SessionStore
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.notifications.NotificationChecker
import com.helper.beamnetworks.data.notifications.NotificationFeed
import com.helper.beamnetworks.data.notifications.NotificationSettings
import com.helper.beamnetworks.data.notifications.NotificationWorker
import com.helper.beamnetworks.data.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-wide toast (ink pill). Each call replaces the current message; it clears after 2.2s. */
class ToastController(private val scope: CoroutineScope) {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var hideJob: Job? = null

    fun show(text: String) {
        hideJob?.cancel()
        _message.value = text
        hideJob = scope.launch {
            delay(2_200)
            _message.value = null
        }
    }
}

/** Manual dependency container, created once by [com.helper.beamnetworks.BeamNetworksApplication]. */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sessions = SessionStore(context)
    private val client = ApiClient(sessions)
    val api = client.api

    val toasts = ToastController(scope)
    val customers = CustomerRepository(api, scope)
    val payments = PaymentRepository(api, scope)
    val tickets = TicketRepository(api, scope)
    val jobs = JobRepository(api, scope)
    val inventory = InventoryRepository(api, scope)
    val expenses = ExpenseRepository(scope)
    val issues = IssueRepository(scope)
    val callerDirectory = CallerDirectory(context)
    val technicianAlerts = TechnicianAlerts(context)
    val notificationSettings = NotificationSettings(context)
    val notificationFeed = NotificationFeed(context)
    val notifier = Notifier(context)
    val notificationChecker = NotificationChecker(context, api, sessions, notificationSettings, notificationFeed, notifier)

    init {
        // Drop cached data when the user signs out or their session can't be refreshed
        scope.launch {
            sessions.session.distinctUntilChangedBy { it?.userId }.drop(1).collect { if (it == null) clearData() }
        }
        // Background notification checks only while someone is signed in
        scope.launch {
            sessions.session.distinctUntilChangedBy { it?.userId }.collect {
                if (it != null) NotificationWorker.schedule(context) else NotificationWorker.cancel(context)
            }
        }
        // Keep the caller ID directory in step with the latest customer and installation lists
        scope.launch {
            combine(customers.customers.state, jobs.jobs.state) { c, j -> c to j }
                .filter { (c, j) -> c.loaded && c.error == null && (j.loaded || j.moduleDisabled) }
                .collect { (c, j) -> withContext(Dispatchers.IO) { callerDirectory.update(c.items, j.items) } }
        }
    }

    suspend fun login(email: String, password: String): Session {
        val response = apiCall { client.api.login(LoginRequest(email.trim(), password)) }
        val session = Session(
            access = response.access,
            refresh = response.refresh,
            userId = response.user.id,
            email = response.user.email,
            roleType = response.user.roleType,
            permissions = response.user.permissions,
            mustChangePassword = response.user.mustChangePassword,
        )
        sessions.save(session)
        if (!session.mustChangePassword) {
            // The tenant name needs settings.manage; staff without it just see the default
            runCatching { apiCall { api.tenantProfile() } }.getOrNull()?.let { profile ->
                sessions.current?.let { sessions.save(it.copy(tenantName = profile.name.ifBlank { null })) }
            }
        }
        return session
    }

    fun logout() {
        val refresh = sessions.current?.refresh
        sessions.clear()
        if (refresh != null) scope.launch { runCatching { apiCall { api.logout(RefreshRequest(refresh)) } } }
    }

    /** Reloads everything the dashboard shows. */
    fun refreshAll() {
        customers.customers.refreshAsync()
        payments.payments.refreshAsync()
        payments.invoices.refreshAsync()
        tickets.tickets.refreshAsync()
        jobs.jobs.refreshAsync()
        inventory.items.refreshAsync()
    }

    private fun clearData() {
        callerDirectory.clear()
        notificationFeed.clear()
        notificationChecker.reset()
        customers.customers.reset()
        customers.plans.reset()
        customers.networks.reset()
        payments.reset()
        tickets.tickets.reset()
        jobs.jobs.reset()
        jobs.staff.reset()
        inventory.items.reset()
    }
}
