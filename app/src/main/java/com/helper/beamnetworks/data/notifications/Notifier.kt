package com.helper.beamnetworks.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.helper.beamnetworks.MainActivity
import com.helper.beamnetworks.R
import com.helper.beamnetworks.ui.theme.BrandNavy
import androidx.compose.ui.graphics.toArgb

/** Posts [AppNotification]s to the system tray, one channel per [NotificationCategory]. */
class Notifier(private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationCategory.entries.forEach { category ->
            manager.createNotificationChannel(
                NotificationChannel(category.channelId, category.title, category.importance).apply { description = category.description },
            )
        }
    }

    fun post(events: List<AppNotification>) {
        if (events.isEmpty() || !canPost()) return
        val manager = NotificationManagerCompat.from(context)
        events.groupBy { it.category }.forEach { (category, items) ->
            if (items.size > MAX_SINGLE) {
                // A burst (e.g. ten payments) becomes one expandable summary
                val inbox = NotificationCompat.InboxStyle()
                items.take(7).forEach { inbox.addLine(it.title) }
                if (items.size > 7) inbox.setSummaryText("+${items.size - 7} more")
                manager.notifyIfAllowed(
                    category.ordinal + SUMMARY_BASE,
                    builder(category, summaryTitle(category, items.size), items.first().title, Links.FEED).setStyle(inbox).build(),
                )
            } else {
                items.forEach { event ->
                    manager.notifyIfAllowed(
                        event.id.hashCode(),
                        builder(category, event.title, event.body, event.link ?: Links.FEED)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
                            .setWhen(event.time)
                            .build(),
                    )
                }
            }
        }
    }

    /** A sample notification so staff can check delivery (and Xiaomi/MIUI background settings). */
    fun postTest() {
        if (!canPost()) return
        NotificationManagerCompat.from(context).notifyIfAllowed(
            TEST_ID,
            builder(NotificationCategory.Routers, "Notifications are working", "You'll see router, outage and customer alerts like this one.", Links.FEED).build(),
        )
    }

    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun builder(category: NotificationCategory, title: String, body: String, link: String): NotificationCompat.Builder {
        val intent = Intent(Intent.ACTION_VIEW, link.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(context, link.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(BrandNavy.toArgb())
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(if (category == NotificationCategory.Routers || category == NotificationCategory.Outages) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (category.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun summaryTitle(category: NotificationCategory, count: Int) = when (category) {
        NotificationCategory.Payments -> "$count payments received"
        NotificationCategory.Accounts -> "$count customers suspended or reconnected"
        NotificationCategory.Downtime -> "$count customer downtime updates"
        NotificationCategory.Routers -> "$count router status changes"
        NotificationCategory.Outages -> "$count outage and monitoring alerts"
        NotificationCategory.Tickets -> "$count ticket updates"
        NotificationCategory.Billing -> "$count Beam account alerts"
        NotificationCategory.Stock -> "$count products running low"
        NotificationCategory.Jobs -> "$count jobs assigned to you"
        NotificationCategory.Updates -> "$count app updates"
    }

    @Suppress("MissingPermission")
    private fun NotificationManagerCompat.notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (canPost()) notify(id, notification)
    }

    private companion object {
        const val MAX_SINGLE = 3
        const val SUMMARY_BASE = 70_000
        const val TEST_ID = 69_999
    }
}
