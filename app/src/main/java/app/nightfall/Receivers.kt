package app.nightfall

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/** Täglich zur eingestellten Uhrzeit. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!Prefs(app).enabled) return

        Scheduler.apply(app, afterTrigger = true) // gleich den nächsten Tag planen

        val pending = goAsync()
        val wl = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nightfall:run")
        wl.acquire(50_000)

        thread(name = "nightfall") {
            try {
                val text = Text(Prefs(app).lang)
                when (Radio.goDark(app)) {
                    Radio.Outcome.OK -> Radio.prepareShizukuAutostart()
                    Radio.Outcome.NO_SHIZUKU -> Notifier.problem(app, text.failNoShizuku)
                    Radio.Outcome.NO_PERMISSION -> Notifier.problem(app, text.failNoPermission)
                    Radio.Outcome.AIRPLANE_FAILED -> Notifier.problem(app, text.failAirplane)
                    Radio.Outcome.BLUETOOTH_FAILED -> Notifier.problem(app, text.failBluetooth)
                }
            } catch (t: Throwable) {
                Notifier.problem(app, Text(Prefs(app).lang).failAirplane)
            } finally {
                if (wl.isHeld) wl.release()
                pending.finish()
            }
        }
    }
}

/** Nach Neustart, Update oder Zeit-/Zeitzonenänderung neu planen. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.apply(context.applicationContext)
    }
}

/** Meldet sich nur, wenn etwas nicht geklappt hat. */
object Notifier {
    private const val CHANNEL = "problems"

    fun problem(context: Context, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, Text(Prefs(context).lang).channel, NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            1,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Nightfall")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
