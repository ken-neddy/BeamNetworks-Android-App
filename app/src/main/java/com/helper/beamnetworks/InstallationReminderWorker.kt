package com.helper.beamnetworks

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helper.beamnetworks.data.Job
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import java.time.LocalDate

/**
 * Daily: reminds about installations scheduled three days from now (field jobs from the API),
 * and refreshes the offline caller ID directory.
 */
class InstallationReminderWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BeamNetworksApplication).container
        if (container.sessions.current == null) return Result.success()
        container.jobs.jobs.refresh()
        container.customers.customers.refresh()
        val state = container.jobs.jobs.state.value
        val customers = container.customers.customers.state.value

        // Keep the offline caller ID directory current even if nobody opens the app
        if (customers.error == null && customers.loaded) {
            container.callerDirectory.update(customers.items, if (state.error == null) state.items else emptyList())
        }
        if (state.error != null && !state.moduleDisabled) return Result.retry()

        val target = LocalDate.now(zone).plusDays(3)
        state.items
            .filter { it.status.isUpcoming && it.scheduled?.withZoneSameInstant(zone)?.toLocalDate() == target }
            .forEach(::sendNotification)
        return Result.success()
    }

    private fun sendNotification(job: Job) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("beamnetworks://app/installation_details/${job.id}")
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, job.id, intent, PendingIntent.FLAG_IMMUTABLE)
        val soundUri = Uri.parse("android.resource://${applicationContext.packageName}/" + R.raw.beam_networks_notification)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, "installation_reminder_channel_v4")
            .setContentTitle("Upcoming ${job.type.label.lowercase()}")
            .setContentText("${job.type.label} for ${job.customerName} is scheduled for ${whenLabel(job.scheduled)}.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .build()
        notificationManager.notify(job.id, notification)
    }
}
