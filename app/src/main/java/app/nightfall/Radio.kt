package app.nightfall

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Flugmodus an + Bluetooth aus über Shizuku (ADB-Rechte).
 * Pro Aufgabe genau ein offizieller Android-Befehl, danach wird das Ergebnis geprüft.
 */
object Radio {

    private const val TAG = "Nightfall"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    enum class Outcome { OK, NO_SHIZUKU, NO_PERMISSION, AIRPLANE_FAILED, BLUETOOTH_FAILED }

    fun shizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun alive(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    private fun permitted(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun state(context: Context): Status = when {
        !alive() -> if (shizukuInstalled(context)) Status.NOT_RUNNING else Status.NOT_INSTALLED
        !permitted() -> Status.NO_PERMISSION
        else -> Status.READY
    }

    /** Wartet, bis Shizuku dem (evtl. gerade gestarteten) App-Prozess den Zugang geschickt hat. */
    fun waitForShizuku(timeoutMs: Long): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (alive()) return true
            SystemClock.sleep(250)
        }
        return alive()
    }

    /** Muss im Hintergrund laufen. */
    fun goDark(context: Context): Outcome {
        if (!waitForShizuku(20_000)) return Outcome.NO_SHIZUKU
        if (!permitted()) return Outcome.NO_PERMISSION

        // 1) Flugmodus – offizieller Befehl seit Android 12
        if (!airplaneOn(context)) {
            exec("cmd connectivity airplane-mode enable")
        }
        if (!waitUntil(6_000) { airplaneOn(context) }) return Outcome.AIRPLANE_FAILED

        // 2) Bluetooth – kann im Flugmodus an bleiben, darum immer ausdrücklich aus
        val btCommand = if (Build.VERSION.SDK_INT >= 33) "cmd bluetooth_manager disable"
                        else "svc bluetooth disable"
        exec(btCommand)
        if (!waitUntil(8_000) { bluetoothOff() }) return Outcome.BLUETOOTH_FAILED

        return Outcome.OK
    }

    private fun airplaneOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** bluetooth_on: 0 = aus, 1 = an, 2 = an im Flugmodus */
    private fun bluetoothOff(): Boolean = exec("settings get global bluetooth_on").trim() == "0"

    private inline fun waitUntil(timeoutMs: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return true
            SystemClock.sleep(400)
        }
        return check()
    }

    /** Shell-Befehl mit ADB-Rechten über Shizuku. */
    private fun exec(command: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", "$command 2>&1"), null, null) as Process
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor(10, TimeUnit.SECONDS)
        p.destroy()
        Log.d(TAG, "$command -> ${out.trim()}")
        out
    } catch (t: Throwable) {
        Log.e(TAG, "exec failed: $command", t)
        ""
    }
}
