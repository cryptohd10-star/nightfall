package app.nightfall

import android.content.Context
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("nightfall", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var hour: Int
        get() = sp.getInt("hour", 23)
        set(v) = sp.edit().putInt("hour", v).apply()

    var minute: Int
        get() = sp.getInt("minute", 0)
        set(v) = sp.edit().putInt("minute", v).apply()

    /** "de" oder "en". Standard: Sprache des Handys. */
    var lang: String
        get() = sp.getString("lang", null)
            ?: if (Locale.getDefault().language == "de") "de" else "en"
        set(v) = sp.edit().putString("lang", v).apply()

    /** Xiaomi-Autostart lässt sich nicht abfragen – wir merken uns, dass es geöffnet wurde. */
    var autostartConfirmed: Boolean
        get() = sp.getBoolean("autostart_ok", false)
        set(v) = sp.edit().putBoolean("autostart_ok", v).apply()
}
