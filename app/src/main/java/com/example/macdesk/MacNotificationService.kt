package com.example.macdesk

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow

@Immutable
class NotifItem(val key: String, val app: String, val title: String, val text: String, val intent: PendingIntent?)

object NotificationHub {
    val items = MutableStateFlow<List<NotifItem>>(emptyList())
    @Volatile var service: MacNotificationService? = null
}

/** Reads active notifications (needs the user's "Notification access") so the menu bar can show a count. */
class MacNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { NotificationHub.service = this; refresh() }
    override fun onListenerDisconnected() { NotificationHub.service = null; NotificationHub.items.value = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        val pm = packageManager
        NotificationHub.items.value = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedByDescending { it.postTime }
            .map { s ->
                val ex = s.notification.extras
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString() }.getOrDefault(s.packageName)
                NotifItem(s.key, app, ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(), s.notification.contentIntent)
            }
    }
}

object NotificationShade {
    /** Opens the native notification shade (needs the normal EXPAND_STATUS_BAR permission). */
    fun open(ctx: Context): Boolean = runCatching {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        true
    }.getOrDefault(false)
}
