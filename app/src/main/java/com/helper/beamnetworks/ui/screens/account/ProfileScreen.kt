package com.helper.beamnetworks.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.ChangePasswordRequest
import com.helper.beamnetworks.data.api.StaffDto
import com.helper.beamnetworks.data.api.StaffUpdate
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.api.fetchAllPages
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.screens.login.MaskedField
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import kotlinx.coroutines.launch

/** The signed-in person's own profile: their staff record, with name and phone editable, and password change. */
class ProfileViewModel(private val app: AppContainer) : ViewModel() {
    var profile by mutableStateOf<StaffDto?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val session get() = app.sessions.session

    fun load() {
        val userId = app.sessions.current?.userId ?: return
        loading = true
        viewModelScope.launch {
            try {
                profile = apiCall { fetchAllPages { app.api.staff(it) } }.firstOrNull { it.user == userId }
                loadFailed = profile == null
            } catch (e: ApiException) {
                // Staff without permission to see the staff list still get the basics from their sign-in
                loadFailed = true
            } finally {
                loading = false
            }
        }
    }

    fun clearError() {
        error = null
    }

    fun save(name: String, phone: String, onDone: () -> Unit) {
        val id = profile?.id ?: return
        if (name.isBlank()) {
            error = "Your name can't be empty."
            return
        }
        launchBusy {
            profile = apiCall { app.api.updateStaff(id, StaffUpdate(name.trim(), phone.trim())) }
            app.toasts.show("Profile updated")
            onDone()
        }
    }

    fun changePassword(current: String, new: String, repeat: String, onDone: () -> Unit) {
        when {
            current.isBlank() -> error = "Enter your current password."
            new.length < 8 -> error = "The new password must be at least 8 characters."
            new != repeat -> error = "The two new passwords do not match."
            new == current -> error = "The new password must be different from the current one."
            else -> launchBusy {
                val response = apiCall { app.api.changePassword(ChangePasswordRequest(current, new)) }
                // Every other session is signed out, so keep this one going with the fresh tokens
                if (response.access != null) app.sessions.updateTokens(response.access, response.refresh)
                app.toasts.show("Password changed")
                onDone()
            }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        busy = true
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    fun signOut() = app.logout()
}

private enum class ProfileSheet { Edit, Password }

@Composable
fun ProfileScreen() {
    val vm = containerViewModel(key = "profile") { app, _ -> ProfileViewModel(app) }
    val session by vm.session.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ProfileSheet?>(null) }
    LaunchedEffect(Unit) { vm.load() }

    val profile = vm.profile
    val name = profile?.name?.takeIf { it.isNotBlank() } ?: session?.email.orEmpty()
    val role = when {
        profile?.roleName?.isNotBlank() == true -> profile.roleName
        session?.isAdmin == true -> "Admin"
        else -> "Staff"
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        item {
            BeamCard(shape = BeamShapes.CardLarge, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    InitialsAvatar(name, session?.userId ?: 0, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = BeamType.RowTitleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(session?.email.orEmpty(), style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            ListCard(Modifier.padding(top = 12.dp)) {
                InfoRow("Name", name)
                InfoRow("Email", session?.email.orEmpty())
                InfoRow("Phone", profile?.phone?.takeIf { it.isNotBlank() } ?: "—")
                InfoRow("Role", role)
                InfoRow("Business", session?.tenantName ?: "Beam Networks", showDivider = false)
            }
        }
        if (vm.loadFailed && !vm.loading) item {
            Text(
                "Couldn't load your full profile, so name and phone can't be edited here.",
                style = BeamType.Meta,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile != null) SecondaryButton("Edit profile", { vm.clearError(); sheet = ProfileSheet.Edit }, height = 48.dp, bold = true)
                SecondaryButton("Change password", { vm.clearError(); sheet = ProfileSheet.Password }, height = 48.dp, bold = true)
                SecondaryButton("Sign out", vm::signOut, textColor = BeamColors.Danger, height = 48.dp, bold = true)
            }
        }
        item { BottomSpacer() }
    }

    when (sheet) {
        ProfileSheet.Edit -> if (profile != null) EditProfileSheet(vm, profile) { sheet = null }
        ProfileSheet.Password -> ChangePasswordSheet(vm) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun EditProfileSheet(vm: ProfileViewModel, profile: StaffDto, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
    BeamSheet(
        title = "Edit profile",
        onDismiss = onDismiss,
        toast = null,
        ctaLabel = if (vm.busy) "Saving…" else "Save changes",
        onCta = { vm.save(name, phone, onDismiss) },
        ctaEnabled = !vm.busy,
    ) {
        FormField("Name", name, { name = it })
        FormField("Phone", phone, { phone = it }, placeholder = "07…", keyboardType = KeyboardType.Phone)
        Text("Your email is your sign-in and can only be changed by Beam.", style = BeamType.Meta)
        vm.error?.let { Text(it, style = BeamType.Meta.copy(color = BeamColors.Danger)) }
    }
}

@Composable
private fun ChangePasswordSheet(vm: ProfileViewModel, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    BeamSheet(
        title = "Change password",
        onDismiss = onDismiss,
        toast = null,
        ctaLabel = if (vm.busy) "Saving…" else "Change password",
        onCta = { vm.changePassword(current, new, repeat, onDismiss) },
        ctaEnabled = !vm.busy,
    ) {
        MaskedField("Current password", current) { current = it }
        MaskedField("New password (at least 8 characters)", new) { new = it }
        MaskedField("Repeat new password", repeat) { repeat = it }
        Text("Changing it signs you out of Beam on other devices.", style = BeamType.Meta)
        vm.error?.let { Text(it, style = BeamType.Meta.copy(color = BeamColors.Danger)) }
    }
}
