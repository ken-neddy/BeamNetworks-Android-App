package com.helper.beamnetworks

/** Installation shape used by the call log screen and the caller ID popup. */
data class InstallationData(
    var id: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val clientLocation: String = "",
    val installationDate: String = "",
    val moreNotes: String = "",
    var status: String = "Upcoming",
    val hasRouter: Boolean = false,
)
