package app.nightfall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Täglicher Wecker nach der Uhr des Handys. Die nächste Auslösung wird immer aus der
 * aktuellen Zeitzone berechnet – Sommer-/Winterzeit und Zeitzonenwechsel stimmen automatisch.
 */
object Scheduler {

    private const val REQUEST_CODE = 2300

    fun nextTrigger(
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): ZonedDateTime {
        var at = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!at.isAfter(now)) at = now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
        return at
    }

    /**
     * @param afterTrigger true, wenn der Wecker gerade ausgelöst hat – dann sicher erst morgen wieder.
     */
    fun apply(context: Context, afterTrigger: Boolean = false) {
        val prefs = Prefs(context)
        if (prefs.enabled) schedule(context, prefs, afterTrigger) else cancel(context)
    }

    private fun schedule(context: Context, prefs: Prefs, afterTrigger: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now(ZoneId.systemDefault()).let { if (afterTrigger) it.plusSeconds(61) else it }
        val at = nextTrigger(prefs.hour, prefs.minute, now).toInstant().toEpochMilli()
        val pi = pendingIntent(context)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    fun canExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).setAction("app.nightfall.TRIGGER"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
