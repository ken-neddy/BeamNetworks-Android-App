package com.helper.beamnetworks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.Job
import com.helper.beamnetworks.util.whenLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Customers and installations from the billing API, for matching calls to people. */
class CallLogViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as BeamNetworksApplication).container

    val customers = container.customers.customers.state
        .map { state -> state.items.map { CustomerData(it.name, it.phone, it.planName) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installations = container.jobs.jobs.state
        .map { state -> state.items.map { it.toInstallationData() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        container.customers.customers.ensureLoaded()
        container.jobs.jobs.ensureLoaded()
    }
}

fun Job.toInstallationData() = InstallationData(
    id = id.toString(),
    clientName = customerName,
    clientPhone = phone.orEmpty(),
    clientLocation = location,
    installationDate = whenLabel(scheduled),
    moreNotes = notes,
    status = if (status.isUpcoming) "Upcoming" else "Completed",
    hasRouter = hasRouter,
)
