package com.helper.beamnetworks.ui.screens.tickets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.JobType
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.CommentRequest
import com.helper.beamnetworks.data.api.EscalateToJobRequest
import com.helper.beamnetworks.data.api.TicketAssignRequest
import com.helper.beamnetworks.data.api.TicketDetailDto
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.DateField
import com.helper.beamnetworks.ui.components.DropdownField
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.InputBox
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TimeField
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.components.avatarSeed
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.isoOffset
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable data class TicketDetailRoute(val id: Int)

fun statusTone(status: String): Pair<String, TonePair> = when (status) {
    "in_progress" -> "In progress" to Tones.Purple
    "resolved" -> "Resolved" to Tones.Green
    "closed" -> "Closed" to Tones.Neutral
    else -> "Open" to Tones.Orange
}

/** SLA warning tag for open tickets: past due, or close to it. Null when on track. */
fun slaTag(slaStatus: String?): Pair<String, TonePair>? = when {
    slaStatus == null -> null
    slaStatus.contains("breach", true) -> "Past SLA" to Tones.Red
    slaStatus.contains("risk", true) || slaStatus.contains("warn", true) || slaStatus.contains("soon", true) -> "SLA soon" to Tones.Orange
    else -> null
}

val TicketQueues = listOf("general" to "General", "technical" to "Technical", "billing" to "Billing", "field" to "Field", "network" to "Network")

fun priorityTone(priority: String): Pair<String, TonePair> = when (priority) {
    "urgent" -> "Urgent" to Tones.Red
    "high" -> "High" to Tones.Red
    "low" -> "Low" to Tones.Neutral
    else -> "Normal" to Tones.Neutral
}

class TicketDetailViewModel(private val app: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<TicketDetailRoute>().id
    var ticket by mutableStateOf<TicketDetailDto?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    val staff = app.jobs.staff.state
    val toasts = app.toasts

    init {
        load()
        app.jobs.staff.ensureLoaded()
        app.customers.customers.ensureLoaded()
    }

    fun load() {
        viewModelScope.launch {
            try {
                ticket = apiCall { app.api.ticket(id) }
                error = null
            } catch (e: ApiException) {
                error = e.message
            }
        }
    }

    /** Phone from the matching customer, for the Call button. */
    fun phone(t: TicketDetailDto): String? = app.customers.customers.items.firstOrNull { t.customerIdentity != null && it.identityId == t.customerIdentity }?.phone
        ?: t.description.lineSequence().firstOrNull { it.startsWith("Phone: ") }?.removePrefix("Phone: ")

    fun reply(body: String, internal: Boolean, onDone: () -> Unit) = act("reply", if (internal) "Note added" else "Reply sent to the customer") {
        apiCall { app.api.commentTicket(id, CommentRequest(body.trim(), internal)) }.also { onDone() }
    }

    fun resolve() = act("status", "Ticket resolved") { apiCall { app.api.resolveTicketDetail(id) } }
    fun close() = act("status", "Ticket closed") { apiCall { app.api.closeTicket(id) } }
    fun reopen() = act("status", "Ticket reopened") { apiCall { app.api.reopenTicket(id) } }

    fun assign(assignee: Int?, priority: String, queue: String?, onDone: () -> Unit) = act("assign", "Ticket updated") {
        apiCall { app.api.routeTicket(id, com.helper.beamnetworks.data.api.TicketQueueRequest(assignee = assignee, priority = priority, queue = queue)) }.also { onDone() }
    }

    fun escalate(note: String, assignee: Int?, queue: String?, onDone: () -> Unit) = act("escalate", "Ticket escalated") {
        apiCall { app.api.escalateTicket(id, com.helper.beamnetworks.data.api.TicketEscalateRequest(note.trim().ifBlank { null }, assignee, queue)) }.also { onDone() }
    }

    fun makeJob(type: JobType, technician: Int?, date: LocalDate, time: LocalTime, onDone: () -> Unit) = act("job", "Field job created") {
        val response = apiCall {
            app.api.escalateToJob(id, EscalateToJobRequest(jobType = type.key, assignedTo = technician, scheduledFor = isoOffset(date, time.hour, time.minute)))
        }
        app.jobs.jobs.refreshAsync()
        onDone()
        response.ticket ?: apiCall { app.api.ticket(id) }
    }

    private fun act(key: String, success: String, block: suspend () -> TicketDetailDto) {
        if (busy != null) return
        busy = key
        viewModelScope.launch {
            try {
                ticket = block()
                app.toasts.show(success)
                app.tickets.tickets.refreshAsync()
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Something went wrong")
            } finally {
                busy = null
            }
        }
    }
}

@Composable
fun TicketDetailScreen() {
    val vm = containerViewModel { app, handle -> TicketDetailViewModel(app, handle) }
    val t = vm.ticket
    val context = LocalContext.current
    var reply by rememberSaveable { mutableStateOf("") }
    var internal by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }

    if (t == null) {
        EmptyState(if (vm.error != null) "Couldn't open the ticket" else "Loading…", vm.error.orEmpty(), actionLabel = vm.error?.let { "Try again" }, onAction = vm::load)
        return
    }
    val open = t.status == "open" || t.status == "in_progress"
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.weight(1f)) {
            item {
                BeamCard(shape = BeamShapes.CardLarge, padding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#T-${t.id}", style = BeamType.MetaStrong.copy(color = BeamColors.TextMuted), modifier = Modifier.weight(1f))
                        priorityTone(t.priority).let { (label, tone) -> Tag(label, tone, large = true) }
                        statusTone(t.status).let { (label, tone) -> Tag(label, tone, large = true) }
                    }
                    Text(t.subject, style = BeamType.DetailName)
                    Text(t.description, style = BeamType.Body.copy(lineHeight = 1.45.em, color = BeamColors.TextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (open) slaTag(t.slaStatus)?.let { (label, tone) -> Tag(label, tone) }
                        if (t.escalationLevel > 0) Tag("Escalated ×${t.escalationLevel}", Tones.Purple)
                    }
                }
            }
            item {
                ListCard(Modifier.padding(top = 12.dp)) {
                    val rows = listOfNotNull(
                        "Customer" to (t.customerName?.ifBlank { null } ?: "—"),
                        "Assigned to" to (t.assigneeEmail?.ifBlank { null } ?: "Nobody"),
                        t.queue?.let { "Queue" to it.replaceFirstChar { c -> c.uppercase() } },
                        t.networkName?.ifBlank { null }?.let { "Network" to it },
                        parseDateTime(t.slaDueAt)?.let { "Due" to whenLabel(it) },
                        "Opened" to relativeAge(parseDateTime(t.createdAt)) + (t.createdByEmail?.let { " by $it" } ?: ""),
                    )
                    rows.forEachIndexed { i, (k, v) -> InfoRow(k, v, showDivider = i < rows.lastIndex) }
                }
            }
            item {
                TwoColumns(
                    {
                        SecondaryButton("Call", {
                            vm.phone(t)?.let { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$it".toUri())) } ?: vm.toasts.show("No phone number for this customer")
                        })
                    },
                    { SecondaryButton("Assign", { sheet = "assign" }) },
                    Modifier.padding(top = 12.dp),
                )
            }
            item {
                TwoColumns(
                    { SecondaryButton("Make field job", { sheet = "job" }) },
                    {
                        when {
                            open -> SecondaryButton(if (vm.busy == "status") "…" else "Resolve", vm::resolve, textColor = BeamColors.Success, bold = true)
                            t.status == "resolved" -> SecondaryButton("Close", vm::close)
                            else -> SecondaryButton("Reopen", vm::reopen, textColor = BeamColors.Warning)
                        }
                    },
                    Modifier.padding(top = 8.dp),
                )
            }
            if (open) {
                item { SecondaryButton("Escalate", { sheet = "escalate" }, Modifier.padding(top = 8.dp), textColor = BeamColors.Purple, bold = true) }
            }
            item { SectionHeader("Conversation") { Text("${t.comments.size}", style = BeamType.Meta) } }
            if (t.comments.isEmpty()) {
                item { Text("No replies yet. Your reply is sent to the customer.", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(start = 2.dp)) }
            }
            items(t.comments, key = { it.id }) { c ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val author = c.authorEmail?.substringBefore('@')?.ifBlank { null } ?: "Customer"
                    InitialsAvatar(author, avatarSeed(author), 30.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .background(if (c.isInternal) BeamColors.WarningTint else BeamColors.Surface, BeamShapes.Card)
                            .padding(12.dp),
                    ) {
                        Row {
                            Text(author, style = BeamType.MetaStrong.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            Text(relativeAge(parseDateTime(c.createdAt)), style = BeamType.Meta)
                        }
                        if (c.isInternal) Text("Internal note", style = BeamType.Tag.copy(color = BeamColors.Warning))
                        Text(c.body, style = BeamType.Body.copy(lineHeight = 1.4.em), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (t.history.isNotEmpty()) {
                item { SectionHeader("History") }
                item {
                    ListCard {
                        t.history.forEachIndexed { i, h ->
                            InfoRow(
                                (h.fromStatus?.let { statusTone(it).first + " → " } ?: "") + statusTone(h.toStatus).first,
                                relativeAge(parseDateTime(h.createdAt)) + (h.changedByEmail?.let { " · ${it.substringBefore('@')}" } ?: ""),
                                showDivider = i < t.history.lastIndex,
                            )
                        }
                    }
                }
            }
            item { BottomSpacer() }
        }
        // Reply composer
        Column(
            Modifier
                .fillMaxWidth()
                .background(BeamColors.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillTabs(
                options = listOf(PillTabOption(false, "Reply to customer"), PillTabOption(true, "Internal note")),
                selected = internal,
                onSelect = { internal = it },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    InputBox(reply, { reply = it }, placeholder = if (internal) "Only staff see this" else "Write a reply…", singleLine = false)
                }
                Box(Modifier.width(88.dp)) {
                    PrimaryButton(
                        if (vm.busy == "reply") "…" else "Send",
                        { if (reply.isNotBlank()) vm.reply(reply, internal) { reply = "" } },
                        height = 48.dp,
                        enabled = reply.isNotBlank() && vm.busy == null,
                    )
                }
            }
        }
    }

    when (sheet) {
        "assign" -> AssignSheet(vm, t) { sheet = null }
        "job" -> MakeJobSheet(vm, t) { sheet = null }
        "escalate" -> EscalateSheet(vm, t) { sheet = null }
    }
}

@Composable
private fun EscalateSheet(vm: TicketDetailViewModel, t: TicketDetailDto, onClose: () -> Unit) {
    val staff by vm.staff.collectAsStateWithLifecycle()
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }
    var assignee by rememberSaveable { mutableStateOf(t.assignee) }
    var queue by rememberSaveable { mutableStateOf(t.queue) }
    BeamSheet("Escalate ticket", onClose, toast, ctaLabel = if (vm.busy == "escalate") "Escalating…" else "Escalate", onCta = { vm.escalate(note, assignee, queue, onClose) }, ctaEnabled = vm.busy == null) {
        Text("Raises the escalation level (now ${t.escalationLevel}) so it stands out on the web dashboard.", style = BeamType.Small.copy(color = BeamColors.TextSecondary))
        DropdownField(
            "Hand to",
            staff.items.firstOrNull { it.userId == assignee }?.name ?: "Keep current",
            listOf("Keep current") + staff.items.map { it.name },
            { name -> assignee = staff.items.firstOrNull { it.name == name }?.userId ?: t.assignee },
        )
        DropdownField("Queue", TicketQueues.firstOrNull { it.first == queue }?.second ?: "Keep current", TicketQueues.map { it.second }, { label -> queue = TicketQueues.first { it.second == label }.first })
        com.helper.beamnetworks.ui.components.FormField("Why (optional)", note, { note = it }, placeholder = "e.g. Customer offline 3 days")
    }
}

@Composable
private fun AssignSheet(vm: TicketDetailViewModel, t: TicketDetailDto, onClose: () -> Unit) {
    val staff by vm.staff.collectAsStateWithLifecycle()
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var assignee by rememberSaveable { mutableStateOf(t.assignee) }
    var priority by rememberSaveable { mutableStateOf(t.priority) }
    var queue by rememberSaveable { mutableStateOf(t.queue ?: "general") }
    BeamSheet("Assign ticket", onClose, toast, ctaLabel = if (vm.busy == "assign") "Saving…" else "Save", onCta = { vm.assign(assignee, priority, queue.takeIf { it != t.queue }, onClose) }, ctaEnabled = vm.busy == null) {
        DropdownField("Queue", TicketQueues.firstOrNull { it.first == queue }?.second ?: queue, TicketQueues.map { it.second }, { label -> queue = TicketQueues.first { it.second == label }.first })
        DropdownField(
            "Assign to",
            staff.items.firstOrNull { it.userId == assignee }?.name ?: "Nobody",
            listOf("Nobody") + staff.items.map { it.name },
            { name -> assignee = staff.items.firstOrNull { it.name == name }?.userId },
        )
        Text("Priority", style = BeamType.Label)
        PillTabs(
            options = listOf("low", "medium", "high", "urgent").map { PillTabOption(it, priorityTone(it).first) },
            selected = priority,
            onSelect = { priority = it },
        )
        Text("Changing priority recalculates the SLA.", style = BeamType.Meta)
    }
}

@Composable
private fun MakeJobSheet(vm: TicketDetailViewModel, t: TicketDetailDto, onClose: () -> Unit) {
    val staff by vm.staff.collectAsStateWithLifecycle()
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(JobType.Repair) }
    var tech by rememberSaveable { mutableStateOf<Int?>(null) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now(zone).plusDays(1)) }
    var time by rememberSaveable { mutableStateOf(LocalTime.of(10, 0)) }
    BeamSheet("Send a technician", onClose, toast, ctaLabel = if (vm.busy == "job") "Creating…" else "Create job", onCta = { vm.makeJob(type, tech, date, time, onClose) }, ctaEnabled = vm.busy == null) {
        Text("Creates a field job linked to #T-${t.id} and moves the ticket to the field queue.", style = BeamType.Small.copy(color = BeamColors.TextSecondary))
        DropdownField("Job type", type.label, JobType.entries.map { it.label }, { label -> type = JobType.entries.first { it.label == label } })
        DropdownField(
            "Technician",
            staff.items.firstOrNull { it.userId == tech }?.name ?: "Unassigned",
            listOf("Unassigned") + staff.items.map { it.name },
            { name -> tech = staff.items.firstOrNull { it.name == name }?.userId },
        )
        TwoColumns({ DateField("Date", date, { date = it }) }, { TimeField("Time", time, { time = it }) })
    }
}

