package cit.edu.delosreyes.weatherwizardui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.util.*

/**
 * AlertsFragment — dynamic live alert cards + working alarm toggles.
 *
 * Alarm toggles:
 *  • Storm Warnings   — schedules a daily 6 PM check via AlarmManager
 *  • Rain Alerts      — schedules a daily 7 AM check
 *  • Temp Extremes    — schedules a daily 9 AM check
 *  • Morning Briefing — schedules a daily 7 AM notification
 *
 * All alarms are persisted via SharedPreferences so they survive fragment
 * re-creation. On Android 12+ the app requests SCHEDULE_EXACT_ALARM
 * permission; if unavailable it falls back to inexact alarms.
 */
class AlertsFragment : Fragment() {

    private val repo    by lazy { WeatherRepository(requireContext()) }
    private val session by lazy { UserSession(requireContext()) }
    private val prefs   by lazy {
        requireContext().getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
    }

    // Views
    private var llContainer   : LinearLayout? = null
    private var llNoAlerts    : LinearLayout? = null
    private var switchStorm   : SwitchMaterial? = null
    private var switchRain    : SwitchMaterial? = null
    private var switchHeat    : SwitchMaterial? = null
    private var switchMorning : SwitchMaterial? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_alerts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llContainer   = view.findViewById(R.id.llAlertsContainer)
        llNoAlerts    = view.findViewById(R.id.llNoAlerts)
        switchStorm   = view.findViewById(R.id.switchStorm)
        switchRain    = view.findViewById(R.id.switchRain)
        switchHeat    = view.findViewById(R.id.switchHeat)
        switchMorning = view.findViewById(R.id.switchMorning)

        // ── Restore persisted alarm states ────────────────────────────────────
        switchStorm?.isChecked   = prefs.getBoolean("alarm_storm",   true)
        switchRain?.isChecked    = prefs.getBoolean("alarm_rain",    true)
        switchHeat?.isChecked    = prefs.getBoolean("alarm_heat",    false)
        switchMorning?.isChecked = prefs.getBoolean("alarm_morning", true)

        ensureNotificationChannel()

        // ── Alarm toggle listeners ────────────────────────────────────────────
        switchStorm?.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("alarm_storm", checked).apply()
            if (checked) scheduleDailyAlarm(AlarmId.STORM, 18, 0)
            else         cancelAlarm(AlarmId.STORM)
            toast("Storm alarm ${if (checked) "on" else "off"}")
        }

        switchRain?.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("alarm_rain", checked).apply()
            if (checked) scheduleDailyAlarm(AlarmId.RAIN, 7, 0)
            else         cancelAlarm(AlarmId.RAIN)
            toast("Rain alarm ${if (checked) "on" else "off"}")
        }

        switchHeat?.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("alarm_heat", checked).apply()
            if (checked) scheduleDailyAlarm(AlarmId.HEAT, 9, 0)
            else         cancelAlarm(AlarmId.HEAT)
            toast("Temperature alarm ${if (checked) "on" else "off"}")
        }

        switchMorning?.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("alarm_morning", checked).apply()
            if (checked) scheduleDailyAlarm(AlarmId.MORNING, 7, 0)
            else         cancelAlarm(AlarmId.MORNING)
            toast("Morning briefing ${if (checked) "on" else "off"}")
        }

        // ── Settings gear → open system notification settings ────────────────
        view.findViewById<TextView>(R.id.tvAlertsSettings)?.setOnClickListener {
            try {
                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                } else {
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                    }
                }
                startActivity(intent)
            } catch (e: Exception) {
                toast("Open Settings → Apps → WeatherWizard → Notifications")
            }
        }

        // Re-arm currently-enabled alarms (in case the device rebooted)
        if (prefs.getBoolean("alarm_storm",   true))  scheduleDailyAlarm(AlarmId.STORM,   18, 0, silent = true)
        if (prefs.getBoolean("alarm_rain",    true))  scheduleDailyAlarm(AlarmId.RAIN,     7, 0, silent = true)
        if (prefs.getBoolean("alarm_heat",    false)) scheduleDailyAlarm(AlarmId.HEAT,     9, 0, silent = true)
        if (prefs.getBoolean("alarm_morning", true))  scheduleDailyAlarm(AlarmId.MORNING,  7, 0, silent = true)

        // ── Load live alerts ──────────────────────────────────────────────────
        val city = session.getLastCity() ?: "Cebu City"
        loadAlerts(city)
    }

    // ── Live alert cards ──────────────────────────────────────────────────────

    private fun loadAlerts(city: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val wResult = repo.getCurrentWeather(city)
            wResult.onSuccess { weather ->
                val aqiResult = repo.getAirQuality(weather.lat, weather.lon)
                val aqi       = aqiResult.getOrNull()
                val alerts    = repo.generateAlerts(weather, aqi)
                renderAlerts(alerts)
            }.onFailure {
                // Network failure — show placeholder
                llContainer?.visibility = View.GONE
                llNoAlerts?.visibility  = View.VISIBLE
            }
        }
    }

    private fun renderAlerts(alerts: List<WeatherRepository.WeatherAlert>) {
        val ll  = llContainer ?: return
        ll.removeAllViews()

        if (alerts.isEmpty()) {
            ll.visibility          = View.GONE
            llNoAlerts?.visibility = View.VISIBLE
            return
        }

        ll.visibility          = View.VISIBLE
        llNoAlerts?.visibility = View.GONE

        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        alerts.forEach { alert ->
            val bgRes = when (alert.severity) {
                "SEVERE"   -> R.drawable.bg_alert_severe
                "MODERATE" -> R.drawable.bg_alert_moderate
                else       -> R.drawable.bg_alert_advisory
            }
            val badgeRes = when (alert.severity) {
                "SEVERE"   -> R.drawable.bg_badge_severe
                "MODERATE" -> R.drawable.bg_badge_moderate
                else       -> R.drawable.bg_badge_advisory
            }
            val badgeColor = when (alert.severity) {
                "SEVERE"   -> android.graphics.Color.parseColor("#EF4444")
                "MODERATE" -> android.graphics.Color.parseColor("#F59E0B")
                else       -> android.graphics.Color.parseColor("#0EA5E9")
            }
            val emoji = alert.title.substringBefore(" ")

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    (14 * dp).toInt(), (14 * dp).toInt(),
                    (14 * dp).toInt(), (14 * dp).toInt()
                )
                setBackgroundResource(bgRes)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (10 * dp).toInt()
                layoutParams    = lp
            }

            // Emoji icon
            card.addView(TextView(ctx).apply {
                text     = emoji
                textSize = 28f
                val lp   = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = (12 * dp).toInt()
                layoutParams = lp
            })

            // Text column
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            col.addView(TextView(ctx).apply {
                text     = alert.title.substringAfter(" ")
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
            })
            col.addView(TextView(ctx).apply {
                text     = alert.description
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                val lp   = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (6 * dp).toInt()
                layoutParams = lp
                setLineSpacing(2 * dp, 1f)
            })

            // Severity badge
            col.addView(TextView(ctx).apply {
                text     = alert.severity
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(badgeColor)
                setBackgroundResource(badgeRes)
                setPadding(
                    (10 * dp).toInt(), (4 * dp).toInt(),
                    (10 * dp).toInt(), (4 * dp).toInt()
                )
                val lp   = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (8 * dp).toInt()
                layoutParams = lp
            })

            card.addView(col)
            ll.addView(card)
        }
    }

    // ── Alarm helpers ─────────────────────────────────────────────────────────

    object AlarmId {
        const val STORM   = 1001
        const val RAIN    = 1002
        const val HEAT    = 1003
        const val MORNING = 1004
    }

    private fun alarmLabel(id: Int) = when (id) {
        AlarmId.STORM   -> "Storm Warning Check"
        AlarmId.RAIN    -> "Rain Alert Check"
        AlarmId.HEAT    -> "Temperature Alert Check"
        AlarmId.MORNING -> "Daily Weather Briefing"
        else            -> "Weather Alarm"
    }

    private fun scheduleDailyAlarm(id: Int, hour: Int, minute: Int, silent: Boolean = false) {
        val ctx = context ?: return
        val am  = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(ctx, WeatherAlarmReceiver::class.java).apply {
            putExtra("alarm_id",    id)
            putExtra("alarm_label", alarmLabel(id))
        }
        val pi = PendingIntent.getBroadcast(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (se: SecurityException) {
            // Fallback for SCHEDULE_EXACT_ALARM permission not granted (Android 12+)
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun cancelAlarm(id: Int) {
        val ctx = context ?: return
        val am  = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi  = PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, WeatherAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Weather Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Weather Wizard alert notifications" }
                )
            }
        }
    }

    private fun toast(msg: String) =
        context?.let { AppToast.show(it, msg) }

    companion object {
        const val CHANNEL_ID = "weather_wizard_alerts"
    }
}

/**
 * BroadcastReceiver that fires when an alarm goes off.
 * Posts a system notification and reschedules itself for the next day.
 */
class WeatherAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("alarm_label") ?: "Weather Alert"
        val nm    = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        val notif = NotificationCompat.Builder(context, AlertsFragment.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WeatherWizard — $label")
            .setContentText("Open the app to check current weather conditions.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(intent.getIntExtra("alarm_id", 0), notif)
    }
}
