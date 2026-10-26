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
import kotlinx.coroutines.delay
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
        SheetType.NewCustomer -> "Create customer" to "Create customer"
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
                FormField(
                    "Amount (Ksh)", d.amount, { v -> vm.edit { copy(amount = v) } },
                    placeholder = "0.00", keyboardType = KeyboardType.Decimal, large = true,
                    trailingIcon = BeamIcons.MessageSquare, trailingLabel = "Fill from an M-Pesa message", onTrailing = { vm.openPicker(Picker.Sms) },
                )
                FormField("Paid to (shop or person)", d.shop, { v -> vm.edit { copy(shop = v) } }, placeholder = "e.g. Faiba Optics")
                FormField("Item", d.title, { v -> vm.edit { copy(title = v) } }, placeholder = "e.g. Fibre cable, fuel")
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
                    val kinds = listOf("consumable" to "Consumable (cable, connectors)", "asset" to "Asset (routers, ONTs)", "tool" to "Tool (lent to technicians)")
                    DropdownField("Type", kinds.first { it.first == d.itemType }.second, kinds.map { it.second }, { label ->
                        vm.edit { copy(itemType = kinds.first { it.second == label }.first) }
                    })
                    FormField("SKU / code (optional)", d.sku, { v -> vm.edit { copy(sku = v.uppercase()) } }, placeholder = "Made up for you if left blank")
                    TwoColumns(
                        { FormField("Unit cost (Ksh)", d.unitCost, { v -> vm.edit { copy(unitCost = v) } }, keyboardType = KeyboardType.Decimal) },
                        { FormField("Selling price (Ksh)", d.unitPrice, { v -> vm.edit { copy(unitPrice = v) } }, keyboardType = KeyboardType.Decimal) },
                    )
                    Text("If the product already exists, the quantity is added to its stock.", style = BeamType.Meta)
                } else {
                    SecondaryButton("Archive product", { vm.archiveProduct(type.editId) }, textColor = BeamColors.Danger, height = 48.dp, bold = true)
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
    val c = d.cust
    val plans by vm.customers.plans.state.collectAsStateWithLifecycle()
    val networks by vm.customers.networks.state.collectAsStateWithLifecycle()
    val customers by vm.customers.customers.state.collectAsStateWithLifecycle()
    val plan = plans.items.firstOrNull { it.id == d.planId }
    fun cust(change: CustomerForm.() -> CustomerForm) = vm.edit { copy(cust = cust.change()) }

    // A pending referral lead for this number fills in the referrer
    LaunchedEffect(d.phone) {
        delay(600)
        vm.lookupReferralLead(d.phone)
    }
    // With one network there's nothing to choose
    LaunchedEffect(networks.items) {
        if (d.networkId == null) networks.items.singleOrNull()?.let { n -> vm.edit { copy(networkId = n.id) } }
    }

    FormField("Full name", d.name, { v -> vm.edit { copy(name = v) } }, placeholder = "Jane Wanjiku")
    FormField(
        "Phone", d.phone, { v -> vm.edit { copy(phone = v) } }, placeholder = "0712345678", keyboardType = KeyboardType.Phone,
        trailingIcon = BeamIcons.Phone, trailingLabel = "Pick from call log", onTrailing = { vm.openPicker(Picker.CallLog) },
    )
    FormField("Email (optional)", d.email, { v -> vm.edit { copy(email = v) } }, placeholder = "customer@example.com", keyboardType = KeyboardType.Email)
    TwoColumns(
        {
            DropdownField(
                "Plan",
                plan?.name.orEmpty(),
                if (plans.loading && plans.items.isEmpty()) listOf("Loading plans…") else plans.items.map { it.name },
                { name ->
                    plans.items.firstOrNull { it.name == name }?.let { p -> vm.edit { copy(planId = p.id, networkId = p.networkScope ?: networkId) } }
                },
            )
        },
        {
            DropdownField(
                "Network",
                networks.items.firstOrNull { it.id == (plan?.networkScope ?: d.networkId) }?.name.orEmpty(),
                if (networks.loading && networks.items.isEmpty()) listOf("Loading…") else networks.items.map { it.name },
                { name -> networks.items.firstOrNull { it.name == name }?.let { n -> vm.edit { copy(networkId = n.id) } } },
            )
        },
    )
    DropdownField("Status", if (c.status == "suspended") "Suspended" else "Active", listOf("Active", "Suspended"), { v -> cust { copy(status = v.lowercase()) } })

    // ---- Location ----
    Text("Location (optional)", style = BeamType.SectionTitle, modifier = Modifier.padding(top = 6.dp))
    Text(
        "None of these are required. The more you fill in, the easier it is to group this customer with others nearby, for outage compensation or a targeted message.",
        style = BeamType.Meta,
    )
    FormField(
        "Map pin", d.location, { v -> vm.edit { copy(location = v) } }, placeholder = "Tap the pin to choose on the map",
        trailingIcon = BeamIcons.MapPin, trailingLabel = "Pick on map", onTrailing = { vm.openPicker(Picker.Map) },
    )
    TwoColumns(
        { FormField("Country", c.country, { v -> cust { copy(country = v) } }) },
        {
            SuggestField(
                "County", c.county, { v -> cust { copy(county = v) } }, vm.locations.cities, { it },
                onPick = { v: String ->
                    cust { copy(county = v) }
                    vm.refreshLocations()
                },
                maxShown = 5,
            )
        },
    )
    TwoColumns(
        {
            SuggestField(
                "Sub-county", c.subCounty, { v -> cust { copy(subCounty = v) } }, vm.locations.subCounties, { it },
                onPick = { v: String ->
                    cust { copy(subCounty = v) }
                    vm.refreshLocations()
                },
                maxShown = 5,
            )
        },
        {
            SuggestField(
                "Ward", c.ward, { v -> cust { copy(ward = v) } }, vm.locations.wards, { it },
                onPick = { v: String -> cust { copy(ward = v) } },
                maxShown = 5,
            )
        },
    )
    TwoColumns(
        { FormField("Road", c.road, { v -> cust { copy(road = v, estate = if (estateSameAsRoad) v else estate) } }) },
        {
            FormField(
                "Estate", if (c.estateSameAsRoad) c.road else c.estate,
                { v -> if (!c.estateSameAsRoad) cust { copy(estate = v) } },
            )
        },
    )
    SwitchRow("Estate is the same as the road", c.estateSameAsRoad) { on -> cust { copy(estateSameAsRoad = on, estate = if (on) road else estate) } }
    TwoColumns(
        { FormField("Street", c.street, { v -> cust { copy(street = v) } }, placeholder = "Kimathi Street") },
        { FormField("Building", c.building, { v -> cust { copy(building = v) } }, placeholder = "Rahimtulla Tower") },
    )
    TwoColumns(
        { FormField("House / unit no.", c.house, { v -> cust { copy(house = v) } }, placeholder = "4B") },
        { FormField("Another common marker", c.landmark, { v -> cust { copy(landmark = v) } }, placeholder = "Blue gate opposite…") },
    )
    FormField("More details", c.details, { v -> cust { copy(details = v) } }, placeholder = "Any other directions or notes that help locate this customer", singleLine = false)

    // ---- Billing ----
    Text("Billing", style = BeamType.SectionTitle, modifier = Modifier.padding(top = 6.dp))
    SwitchRow("Billed account", c.billed) { on -> cust { copy(billed = on) } }
    Text("Invoices are generated and the account is suspended if unpaid. Turn off for test or free accounts.", style = BeamType.Meta)
    val dayOptions = listOf("Today's day of the month") + (1..28).map { ordinal(it) }
    DropdownField(
        "Billing day (optional)",
        c.billingDay?.let { ordinal(it) } ?: dayOptions.first(),
        dayOptions,
        { label -> cust { copy(billingDay = dayOptions.indexOf(label).takeIf { it > 0 }) } },
    )
    Text("Leave as it is to bill on today's day of the month. Days run from the 1st to the 28th.", style = BeamType.Meta)

    // ---- Referral ----
    SwitchRow("This customer was referred by an existing customer", c.referred) { on ->
        cust { if (on) copy(referred = true) else copy(referred = false, referrerId = null, referrerText = "", referralLeadId = null, leadNote = null) }
    }
    if (c.referred) {
        SuggestField(
            label = "Referred by",
            value = c.referrerText,
            onValueChange = { v -> cust { copy(referrerText = v, referrerId = null, referralLeadId = null, leadNote = null) } },
            suggestions = customers.items,
            suggestionText = { it.name },
            onPick = { picked: Customer -> cust { copy(referrerId = picked.id, referrerText = picked.name) } },
            placeholder = "Search the referrer's name",
        )
        val rewards = listOf("credit" to "Credit on their account", "cash" to "Cash payout")
        DropdownField("Reward", rewards.first { it.first == c.rewardMethod }.second, rewards.map { it.second }, { label -> cust { copy(rewardMethod = rewards.first { it.second == label }.first) } })
        c.leadNote?.let { Text(it, style = BeamType.Meta.copy(color = BeamColors.Success)) }
        Text("The reward is given when the new customer makes their first payment.", style = BeamType.Meta)
    }
    Text("Beam texts the customer their portal login. They have one hour to pay before service is suspended.", style = BeamType.Meta)
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
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
    val priorities = listOf("low" to "Low", "medium" to "Normal", "high" to "High", "urgent" to "Urgent")
    DropdownField("Priority", priorities.first { it.first == d.priority }.second, priorities.map { it.second }, { label -> vm.edit { copy(priority = priorities.first { it.second == label }.first) } })
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
    val staff by vm.jobs.staff.state.collectAsStateWithLifecycle()
    Text("Priority", style = BeamType.Label)
    PillTabs(
        options = listOf("low" to "Low", "medium" to "Normal", "high" to "High", "urgent" to "Urgent").map { PillTabOption(it.first, it.second) },
        selected = d.priority,
        onSelect = { v -> vm.edit { copy(priority = v) } },
    )
    val queues = listOf("general" to "General", "technical" to "Technical", "billing" to "Billing", "field" to "Field", "network" to "Network")
    TwoColumns(
        { DropdownField("Queue", queues.first { it.first == d.queue }.second, queues.map { it.second }, { label -> vm.edit { copy(queue = queues.first { it.second == label }.first) } }) },
        {
            val nobody = "Nobody"
            DropdownField(
                "Assign to",
                staff.items.firstOrNull { it.userId == d.technicianId }?.name ?: nobody,
                listOf(nobody) + staff.items.map { it.name },
                { name -> vm.edit { copy(technicianId = staff.items.firstOrNull { it.name == name }?.userId) } },
            )
        },
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
    FormField("What was done (optional)", d.notes, { v -> vm.edit { copy(notes = v) } }, placeholder = "e.g. Replaced patch cord, signal -19 dBm", singleLine = false)
    FormField("Completion code (optional)", d.code, { v -> vm.edit { copy(code = v.uppercase()) } }, placeholder = "Code the customer gives you")
    SwitchRow("Customer confirmed it works", d.confirmed) { v -> vm.edit { copy(confirmed = v) } }
}

/** Keeps a picker's result flowing back into the draft; see [SheetViewModel.resumeAfterPicker]. */
@Composable
fun rememberSheetPickerResume(vm: SheetViewModel, entryKey: Any?, read: () -> Map<String, String?>) {
    LaunchedEffect(entryKey) {
        if (vm.parkedSheet != null) vm.resumeAfterPicker(read())
    }
}
