package com.helper.beamnetworks.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.JobType
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.DateField
import com.helper.beamnetworks.ui.components.DropdownField
import com.helper.beamnetworks.ui.components.FieldLabel
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.QtyStepper
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.SuggestField
import com.helper.beamnetworks.ui.components.SwitchRow
import com.helper.beamnetworks.ui.components.TimeField
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.components.pressable
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.Tones

/** Renders whichever sheet [vm] has open. */
@Composable
fun SheetHost(vm: SheetViewModel, onDeleteExpense: (String) -> Unit) {
    val type = vm.sheet ?: return
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val d = vm.draft

    val (title, cta) = when (type) {
        SheetType.Create -> "Create new" to null
        is SheetType.Payment -> "Record payment" to "Save payment"
        SheetType.NewCustomer -> "New customer" to "Add customer"
        is SheetType.Job -> if (type.editId == null) "Schedule a job" to "Schedule" else "Edit job" to "Save changes"
        is SheetType.Ticket -> "Raise a ticket" to "Raise ticket"
        is SheetType.Expense -> if (type.editId == null) "Log an expense" to "Log expense" else "Edit expense" to "Save changes"
        is SheetType.Product -> if (type.editId == null) "Add a product" to "Add product" else "Edit product" to "Save changes"
        is SheetType.CompleteJob -> "Mark job done" to "Mark done"
        is SheetType.Extend -> "Reconnect customer" to "Reconnect"
    }

    BeamSheet(
        title = title,
        onDismiss = vm::close,
        toast = toast,
        ctaLabel = cta?.let { if (vm.busy) "Saving…" else it },
        onCta = vm::submit,
        ctaEnabled = !vm.busy,
    ) {
        when (type) {
            SheetType.Create -> CreateMenu(vm)
            is SheetType.Payment -> {
                CustomerField(vm, "Customer")
                FormField(
                    "Amount (Ksh)", d.amount, { v -> vm.edit { copy(amount = v) } },
                    placeholder = "0.00", keyboardType = KeyboardType.Decimal, large = true,
                    trailingIcon = BeamIcons.MessageSquare, trailingLabel = "Pick from M-Pesa SMS", onTrailing = { vm.openPicker(Picker.Sms) },
                )
                FormField("M‑Pesa reference (optional)", d.reference, { v -> vm.edit { copy(reference = v.uppercase()) } }, placeholder = "e.g. SJK4X1P9QA")
            }
            SheetType.NewCustomer -> NewCustomerFields(vm)
            is SheetType.Job -> JobFields(vm)
            is SheetType.Ticket -> TicketFields(vm)
            is SheetType.Expense -> {
                FormField("What for", d.title, { v -> vm.edit { copy(title = v) } }, placeholder = "e.g. Fuel, drop cable")
                FormField(
                    "Amount (Ksh)", d.amount, { v -> vm.edit { copy(amount = v) } },
                    placeholder = "0.00", keyboardType = KeyboardType.Decimal, large = true,
                    trailingIcon = BeamIcons.MessageSquare, trailingLabel = "Pick from M-Pesa SMS", onTrailing = { vm.openPicker(Picker.Sms) },
                )
                TwoColumns(
                    { DropdownField("Category", d.category, ExpenseCategories, { v -> vm.edit { copy(category = v) } }) },
                    { DateField("Date", d.date, { v -> vm.edit { copy(date = v) } }) },
                )
                if (type.editId != null) {
                    SecondaryButton("Delete expense", { onDeleteExpense(type.editId) }, textColor = BeamColors.Danger, height = 48.dp, bold = true)
                }
            }
            is SheetType.Product -> {
                FormField("Product name", d.name, { v -> if (type.editId == null) vm.edit { copy(name = v) } })
                TwoColumns(
                    { FormField("In stock", d.qty, { v -> vm.edit { copy(qty = v.filter { it.isDigit() }) } }, keyboardType = KeyboardType.Number) },
                    { FormField("Reorder at", d.min, { v -> vm.edit { copy(min = v.filter { it.isDigit() }) } }, keyboardType = KeyboardType.Number) },
                )
                if (type.editId == null) {
                    Text("If the product already exists, the quantity is added to its stock.", style = BeamType.Meta)
                }
            }
            is SheetType.CompleteJob -> CompleteJobFields(vm)
            is SheetType.Extend -> {
                Text("Suspended customers can't be reactivated directly. This restores service for a set number of days; a payment in that time keeps them connected.", style = BeamType.Small.copy(color = BeamColors.TextSecondary))
                FormField("Days", d.days, { v -> vm.edit { copy(days = v.filter { it.isDigit() }.take(2)) } }, keyboardType = KeyboardType.Number)
                FormField("Reason", d.notes, { v -> vm.edit { copy(notes = v) } }, placeholder = "e.g. Promised to pay on Friday")
            }
        }
    }
}

@Composable
private fun CreateMenu(vm: SheetViewModel) {
    val options = listOf(
        "Customer" to SheetType.NewCustomer,
        "Payment" to SheetType.Payment(),
        "Install" to SheetType.Job(),
        "Ticket" to SheetType.Ticket(),
        "Expense" to SheetType.Expense(),
        "Product" to SheetType.Product(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { col, (label, type) ->
                    val tone = Tones.avatarPalette[row * 3 + col]
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 92.dp)
                            .pressable(BeamShapes.Card, BeamColors.TileFill, BeamColors.TilePressed, onClick = { vm.open(type) })
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .pressable(BeamShapes.TileSmall, tone.background, tone.background, onClick = null),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(BeamIcons.Plus, null, tint = tone.content, modifier = Modifier.size(18.dp))
                        }
                        Text(label, style = BeamType.SmallStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    }
                }
            }
        }
    }
}

/** Customer picker: type a name or phone and choose from the matches. */
@Composable
private fun CustomerField(vm: SheetViewModel, label: String, allowCallLog: Boolean = false) {
    val state by vm.customers.customers.state.collectAsStateWithLifecycle()
    val d = vm.draft
    SuggestField(
        label = label,
        value = d.customerText,
        onValueChange = { v -> vm.edit { copy(customerText = v, customerId = null) } },
        suggestions = state.items,
        suggestionText = { it.name },
        onPick = { c: Customer -> vm.pickCustomer(c) },
        placeholder = if (state.loading && state.items.isEmpty()) "Loading customers…" else "Search name",
        trailingIcon = if (allowCallLog) BeamIcons.Phone else null,
        trailingLabel = "Pick from call log",
        onTrailing = if (allowCallLog) ({ vm.openPicker(Picker.CallLog) }) else null,
    )
}

@Composable
private fun NewCustomerFields(vm: SheetViewModel) {
    val d = vm.draft
    val plans by vm.customers.plans.state.collectAsStateWithLifecycle()
    val networks by vm.customers.networks.state.collectAsStateWithLifecycle()
    FormField("Full name", d.name, { v -> vm.edit { copy(name = v) } })
    FormField("Phone", d.phone, { v -> vm.edit { copy(phone = v) } }, placeholder = "2547…", keyboardType = KeyboardType.Phone)
    FormField("Email (optional)", d.email, { v -> vm.edit { copy(email = v) } }, keyboardType = KeyboardType.Email)
    val planNames = plans.items.map { it.name }
    DropdownField(
        "Plan",
        plans.items.firstOrNull { it.id == d.planId }?.name.orEmpty(),
        if (plans.loading && planNames.isEmpty()) listOf("Loading plans…") else planNames,
        { name -> plans.items.firstOrNull { it.name == name }?.let { p -> vm.edit { copy(planId = p.id) } } },
    )
    val plan = plans.items.firstOrNull { it.id == d.planId }
    if (plan?.networkScope == null && networks.items.size > 1) {
        DropdownField(
            "Network",
            networks.items.firstOrNull { it.id == d.networkId }?.name.orEmpty(),
            networks.items.map { it.name },
            { name -> networks.items.firstOrNull { it.name == name }?.let { n -> vm.edit { copy(networkId = n.id) } } },
        )
    }
    Text("Beam texts the customer their portal login. They have one hour to pay before service is suspended.", style = BeamType.Meta)
}

@Composable
private fun JobFields(vm: SheetViewModel) {
    val d = vm.draft
    val customers by vm.customers.customers.state.collectAsStateWithLifecycle()
    val staff by vm.jobs.staff.state.collectAsStateWithLifecycle()
    DropdownField(
        "Job type",
        d.jobType.label,
        JobType.entries.map { it.label },
        { label -> vm.edit { copy(jobType = JobType.entries.first { it.label == label }) } },
    )
    SuggestField(
        label = "Customer name",
        value = d.name,
        onValueChange = { v -> vm.edit { copy(name = v, customerText = v, customerId = null) } },
        suggestions = customers.items,
        suggestionText = { it.name },
        onPick = { c: Customer -> vm.pickCustomer(c) },
    )
    FormField(
        "Phone", d.phone, { v -> vm.edit { copy(phone = v) } }, placeholder = "07…", keyboardType = KeyboardType.Phone,
        trailingIcon = BeamIcons.Phone, trailingLabel = "Pick from call log", onTrailing = { vm.openPicker(Picker.CallLog) },
    )
    FormField(
        "Location", d.location, { v -> vm.edit { copy(location = v) } }, placeholder = "Estate, building, house no.",
        trailingIcon = BeamIcons.MapPin, trailingLabel = "Pick on map", onTrailing = { vm.openPicker(Picker.Map) },
    )
    TwoColumns(
        { DateField("Date", d.date, { v -> vm.edit { copy(date = v) } }) },
        { TimeField("Time", d.time, { v -> vm.edit { copy(time = v) } }) },
    )
    val unassigned = "Unassigned"
    DropdownField(
        "Technician",
        staff.items.firstOrNull { it.userId == d.technicianId }?.name ?: unassigned,
        listOf(unassigned) + staff.items.map { it.name },
        { name -> vm.edit { copy(technicianId = staff.items.firstOrNull { it.name == name }?.userId) } },
    )
    if (d.jobType == JobType.Installation) {
        SwitchRow("Customer already has a router", d.hasRouter) { v -> vm.edit { copy(hasRouter = v) } }
    }
    FormField("Notes (optional)", d.notes, { v -> vm.edit { copy(notes = v) } })
}

@Composable
private fun TicketFields(vm: SheetViewModel) {
    val d = vm.draft
    val issues by vm.issues.issues.collectAsStateWithLifecycle()
    CustomerField(vm, "Customer", allowCallLog = true)
    if (d.customerId == null) {
        FormField("Phone", d.phone, { v ->
            vm.edit { copy(phone = v) }
            vm.phoneMatches(v)?.let { vm.pickCustomer(it) }
        }, placeholder = "07…", keyboardType = KeyboardType.Phone)
    }
    SuggestField(
        label = "What's the issue?",
        value = d.issue,
        onValueChange = { v -> vm.edit { copy(issue = v) } },
        suggestions = issues,
        suggestionText = { it },
        onPick = { v: String -> vm.edit { copy(issue = v) } },
        placeholder = "e.g. No internet since morning",
    )
    PillTabs(
        options = listOf(PillTabOption(false, "Normal"), PillTabOption(true, "High")),
        selected = d.high,
        onSelect = { v -> vm.edit { copy(high = v) } },
    )
}

@Composable
private fun CompleteJobFields(vm: SheetViewModel) {
    val d = vm.draft
    val items by vm.inventory.items.state.collectAsStateWithLifecycle()
    Text("Add any stock used on this job. It's subtracted from inventory.", style = BeamType.Small.copy(color = BeamColors.TextSecondary))
    when {
        items.moduleDisabled -> Text("Inventory isn't enabled for your Beam account, so no stock is recorded.", style = BeamType.Meta)
        items.loading && items.items.isEmpty() -> Text("Loading stock…", style = BeamType.Meta)
        items.items.isEmpty() -> Text("No products in stock yet.", style = BeamType.Meta)
        else -> FieldLabel("Products used") {
            Column(Modifier.fillMaxWidth().pressable(BeamShapes.Input, BeamColors.InputFill, BeamColors.InputFill, onClick = null).padding(horizontal = 12.dp)) {
                items.items.forEachIndexed { index, item ->
                    if (index > 0) BeamDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = BeamType.RowTitle)
                            Text("${item.quantity} in stock", style = BeamType.Meta)
                        }
                        val used = d.used[item.id] ?: 0
                        QtyStepper(
                            used,
                            onDecrement = { vm.edit { copy(used = this.used + (item.id to (used - 1).coerceAtLeast(0))) } },
                            onIncrement = { vm.edit { copy(used = this.used + (item.id to (used + 1).coerceAtMost(item.quantity))) } },
                        )
                    }
                }
            }
        }
    }
}

/** Keeps a picker's result flowing back into the draft; see [SheetViewModel.resumeAfterPicker]. */
@Composable
fun rememberSheetPickerResume(vm: SheetViewModel, entryKey: Any?, read: () -> Map<String, String?>) {
    LaunchedEffect(entryKey) {
        if (vm.parkedSheet != null) vm.resumeAfterPicker(read())
    }
}
