package dev.ame.polaris

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.ame.polaris.reminders.Reminder
import dev.ame.polaris.reminders.ReminderPlatform
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** Android's side of reminders: real notifications, woken up by the system alarm clock. */
class AndroidReminderPlatform(private val context: Context) : ReminderPlatform {

    fun createChannel() {
        // Android 8+ groups notifications into "channels" the user can tune in system settings.
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Reminders")
                .setDescription("Events, tasks due, and schedule blocks")
                .build()
        )
    }

    @SuppressLint("MissingPermission") // checked just below
    override fun show(reminder: Reminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return // the user said no to notifications

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_polaris)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.key.hashCode(), notification)
    }

    /**
     * One alarm, always for the *next* reminder. When it rings, ReminderReceiver ticks,
     * which shows what's due and sets the alarm for the one after. Setting it again
     * replaces the old one, so there's nothing to clean up.
     */
    override fun wakeAt(at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val ring = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            alarms.cancel(ring)
            return
        }
        val millis = at.toEpochMilliseconds()
        // Exact alarms ring on the minute even when the phone is idle. If the system
        // doesn't allow them, fall back to an inexact one (may be a few minutes late).
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, ring)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, ring)
        }
    }

    companion object {
        const val ChannelId = "reminders"
    }
}

/**
 * Runs when the reminder alarm rings, and also after the phone restarts or the clock /
 * time zone changes (alarms are wiped on reboot, so they must be set again).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PolarisApp
        // goAsync() lets us finish the database work after onReceive returns.
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.tickReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
