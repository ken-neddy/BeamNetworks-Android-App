package com.helper.beamnetworks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shows the caller ID popup while the phone rings and removes it when the call ends.
 *
 * Android delivers PHONE_STATE twice per state; only the copy sent to apps holding
 * READ_CALL_LOG carries the number, so the one without it is ignored.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
                onRinging(context, number)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> CallerIdPopup.hide(context)
        }
    }

    private fun onRinging(context: Context, number: String) {
        if (!CallerIdPopup.canShow(context)) return
        val container = (context.applicationContext as BeamNetworksApplication).container
        if (container.sessions.current == null) return

        // Saved directory first: instant, and works offline
        container.callerDirectory.lookup(number)?.let {
            CallerIdPopup.show(context, it)
            return
        }

        // Unknown number: the directory may be stale, so refresh once from the API
        val pending = goAsync()
        container.scope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    container.customers.customers.refresh()
                    container.jobs.jobs.refresh()
                }
                container.callerDirectory.lookup(number)?.let { CallerIdPopup.show(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
