package com.helper.beamnetworks.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.helper.beamnetworks.util.whenLabel

/** Texts a technician from this phone when a job is assigned to them (the billing API has no staff SMS). */
class TechnicianAlerts(private val context: Context) {

    fun canText(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** The text sent to the technician, also used as the in-app notification body. */
    fun message(job: Job): String = buildString {
        append("Beam: new ${job.type.label.lowercase()} for ${job.customerName}")
        job.scheduled?.let { append(" on ").append(whenLabel(it)) }
        append('.')
        if (job.location.isNotBlank()) append(" Location: ").append(job.location).append('.')
        job.phone?.takeIf { it.isNotBlank() }?.let { append(" Phone: ").append(it).append('.') }
        if (job.type == JobType.Installation) {
            append(" Customer has router: ").append(if (job.hasRouter) "yes" else "no").append('.')
            append(" Has PoE: ").append(if (job.hasPoe) "yes" else "no").append('.')
        }
    }

    /** Returns true when the SMS was handed to the network; false when it couldn't be sent. */
    @Suppress("DEPRECATION")
    fun text(phone: String, body: String): Boolean {
        if (phone.isBlank() || !canText()) return false
        return runCatching {
            val sms = SmsManager.getDefault()
            sms.sendMultipartTextMessage(phone.trim(), null, sms.divideMessage(body), null, null)
        }.isSuccess
    }
}
