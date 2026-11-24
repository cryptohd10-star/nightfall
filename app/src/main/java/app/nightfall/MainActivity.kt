package app.nightfall

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var text: Text

    private lateinit var main: View
    private lateinit var setup: View
    private lateinit var time: TextView
    private lateinit var status: TextView
    private lateinit var switchLabel: TextView
    private lateinit var toggle: Toggle
    private lateinit var setupTitle: TextView
    private lateinit var setupSub: TextView
    private lateinit var steps: LinearLayout
    private lateinit var headers: List<View>

    private val ui = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.3f, 0f, 0f, 1f)
    private var startedAt = 0L
    private var setupShown = false

    private val ink by lazy { ContextCompat.getColor(this, R.color.ink) }
    private val muted by lazy { ContextCompat.getColor(this, R.color.muted) }
    private val faint by lazy { ContextCompat.getColor(this, R.color.faint) }

    private val onBinder = Shizuku.OnBinderReceivedListener { ui.post { refresh() } }
    private val onBinderDead = Shizuku.OnBinderDeadListener { ui.post { refresh() } }
    private val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> ui.post { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        text = Text(prefs.lang)

        main = findViewById(R.id.main)
        setup = findViewById(R.id.setup)
        time = findViewById(R.id.time)
        status = findViewById(R.id.status)
        switchLabel = findViewById(R.id.switchLabel)
        toggle = findViewById(R.id.toggle)
        setupTitle = findViewById(R.id.setupTitle)
        setupSub = findViewById(R.id.setupSub)
        steps = findViewById(R.id.steps)
        headers = listOf(findViewById(R.id.headerMain), findViewById(R.id.headerSetup))

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        for (h in headers) {
            h.findViewById<View>(R.id.langDe).setOnClickListener { setLang("de") }
            h.findViewById<View>(R.id.langEn).setOnClickListener { setLang("en") }
        }

        time.setOnClickListener { pickTime() }
        findViewById<View>(R.id.switchRow).setOnClickListener { flip() }

        toggle.setChecked(prefs.enabled, animate = false)
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermission)
        startedAt = SystemClock.elapsedRealtime()
        render(animateTime = false)
    }

    override fun onResume() {
        super.onResume()
        Scheduler.apply(this)
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onBinderDead)
        Shizuku.removeRequestPermissionResultListener(onPermission)
        super.onDestroy()
    }

    // ---------------- Hauptbildschirm ----------------

    private fun flip() {
        val on = !prefs.enabled
        prefs.enabled = on
        Scheduler.apply(this)
        toggle.setChecked(on, animate = true)
        toggle.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        render(animateTime = true)
    }

    private fun is24h() = DateFormat.is24HourFormat(this)

    private fun plainTime(h: Int, m: Int): String =
        if (is24h()) String.format("%02d:%02d", h, m)
        else String.format("%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

    private fun bigTime(h: Int, m: Int): CharSequence {
        if (is24h()) return String.format("%02d:%02d", h, m)
        val core = String.format("%d:%02d", if (h % 12 == 0) 12 else h % 12, m)
        val s = SpannableString("$core ${if (h < 12) "AM" else "PM"}")
        s.setSpan(RelativeSizeSpan(0.28f), core.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun render(animateTime: Boolean) {
        val on = prefs.enabled
        time.text = bigTime(prefs.hour, prefs.minute)
        val target = if (on) ink else faint
        if (animateTime) {
            val from = time.currentTextColor
            android.animation.ValueAnimator.ofArgb(from, target).apply {
                duration = 320; interpolator = ease
                addUpdateListener { time.setTextColor(it.animatedValue as Int) }
                start()
            }
        } else {
            time.setTextColor(target)
        }
        status.text = if (on) text.statusOn(plainTime(prefs.hour, prefs.minute)) else text.statusOff
        switchLabel.text = if (on) text.on else text.off
        for (h in headers) {
            h.findViewById<TextView>(R.id.langDe).setTextColor(if (prefs.lang == "de") ink else faint)
            h.findViewById<TextView>(R.id.langEn).setTextColor(if (prefs.lang == "en") ink else faint)
        }
        setupTitle.text = text.setupTitle
        setupSub.text = text.setupSub
    }

    private fun setLang(lang: String) {
        if (prefs.lang == lang) return
        prefs.lang = lang
        text = Text(lang)
        val fade = listOf(status, switchLabel, setupTitle, setupSub, steps)
        fade.forEach { it.animate().alpha(0f).setDuration(120).start() }
        ui.postDelayed({
            render(animateTime = false)
            refresh()
            fade.forEach { it.animate().alpha(1f).setDuration(220).setInterpolator(ease).start() }
        }, 130)
    }

    private fun pickTime() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24h()) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(prefs.hour)
            .setMinute(prefs.minute)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setTitleText(text.pickTitle)
            .setPositiveButtonText(text.ok)
            .setNegativeButtonText(text.cancel)
            .build()
        picker.addOnPositiveButtonClickListener {
            prefs.hour = picker.hour
            prefs.minute = picker.minute
            Scheduler.apply(this)
            time.alpha = 0f
            render(animateTime = false)
            time.animate().alpha(1f).setDuration(280).setInterpolator(ease).start()
        }
        picker.show(supportFragmentManager, "time")
    }

    // ---------------- Einrichtung ----------------

    private class Step(val title: String, val done: Boolean, val action: String, val onTap: () -> Unit)

    private fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }

    private fun steps(): List<Step> {
        val list = mutableListOf<Step>()
        val st = Radio.state(this)

        list += when (st) {
            Radio.Status.NOT_INSTALLED -> Step(text.stepInstallShizuku, false, text.download) {
                open(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL)))
            }
            Radio.Status.NOT_RUNNING -> Step(text.stepStartShizuku, false, text.open) { openShizuku() }
            else -> Step(text.stepStartShizuku, true, text.done) {}
        }
        list += Step(text.stepShizukuPermission, st == Radio.Status.READY, text.allow) {
            requestShizukuPermission()
        }

        if (!Scheduler.canExact(this)) {
            list += Step(text.stepExact, false, text.allow) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        list += Step(text.stepBattery, unrestricted, text.allow) {
            @SuppressLint("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            open(i)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            list += Step(text.stepNotifications, granted, text.allow) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        if (isXiaomi()) {
            list += Step(text.stepAutostart, prefs.autostartConfirmed, text.open) {
                prefs.autostartConfirmed = true
                val ok = open(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
                if (!ok) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        return list
    }

    private fun refresh() {
        val all = steps()
        val missing = all.any { !it.done }

        // Shizuku schickt seinen Zugang kurz nach dem App-Start – nicht vorschnell die Einrichtung zeigen
        val waitingForBinder = missing && !Radio.alive() && Radio.shizukuInstalled(this) &&
                SystemClock.elapsedRealtime() - startedAt < 1500
        if (waitingForBinder) {
            ui.postDelayed({ refresh() }, 300)
            return
        }

        if (missing) buildSteps(all)
        showSetup(missing)
    }

    private fun buildSteps(all: List<Step>) {
        steps.removeAllViews()
        val inflater = LayoutInflater.from(this)
        all.forEachIndexed { i, s ->
            val row = inflater.inflate(R.layout.row_step, steps, false)
            row.findViewById<TextView>(R.id.num).text = String.format("%02d", i + 1)
            val title = row.findViewById<TextView>(R.id.title)
            val action = row.findViewById<TextView>(R.id.action)
            title.text = s.title
            action.text = if (s.done) text.done else s.action
            title.setTextColor(if (s.done) faint else ink)
            action.setTextColor(if (s.done) faint else ink)
            if (!s.done) row.findViewById<View>(R.id.body).setOnClickListener { s.onTap() }
            steps.addView(row)
        }
        // abschliessende Linie
        steps.addView(View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.line))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp())
        })
    }

    private fun showSetup(show: Boolean) {
        if (show == setupShown) return
        setupShown = show
        if (show) {
            setup.visibility = View.VISIBLE
            setup.animate().alpha(1f).setDuration(260).setInterpolator(ease).start()
        } else {
            setup.animate().alpha(0f).setDuration(260).setInterpolator(ease)
                .withEndAction { setup.visibility = View.GONE }.start()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun requestShizukuPermission() {
        try {
            when {
                !Radio.alive() -> openShizuku()
                Shizuku.isPreV11() -> toast(text.shizukuOld)
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    toast(text.shizukuDenied); openShizuku()
                }
                else -> Shizuku.requestPermission(7)
            }
        } catch (_: Throwable) {
            openShizuku()
        }
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage(Radio.SHIZUKU_PACKAGE)
        if (i == null || !open(i)) open(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL)))
    }

    private fun open(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    companion object {
        const val SHIZUKU_URL = "https://github.com/thedjchi/Shizuku/releases"
    }
}
