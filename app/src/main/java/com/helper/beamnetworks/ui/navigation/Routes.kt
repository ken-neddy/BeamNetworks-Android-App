package com.helper.beamnetworks.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.helper.beamnetworks.BeamNetworksApplication
import com.helper.beamnetworks.data.AppContainer
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class CustomersRoute(val filter: String? = null)
@Serializable data class CustomerDetailRoute(val id: Int)
@Serializable data class InstallsRoute(val segment: String? = null)
@Serializable data class InstallDetailRoute(val installationId: Int)
@Serializable data class TicketsRoute(val segment: String? = null)
@Serializable data object AccountRoute
@Serializable data object StockRoute
@Serializable data object AccountsRoute
@Serializable data object NotificationsRoute
@Serializable data object NotificationSettingsRoute
@Serializable data object NetworkRoute

// Older full-screen pickers, kept as they were; they return results through the previous
// back-stack entry's SavedStateHandle.
@Serializable data object CallLogRoute
@Serializable data object MapRoute
@Serializable data object MessagePickerRoute

/** Creates a ViewModel that gets the app's [AppContainer] and its SavedStateHandle. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as BeamNetworksApplication
            create(app.container, createSavedStateHandle())
        }
    },
)
