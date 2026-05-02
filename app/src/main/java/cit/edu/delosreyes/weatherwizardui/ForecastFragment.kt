package cit.edu.delosreyes.weatherwizardui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * ForecastFragment — 5-day forecast with UV, AQI, sunrise/sunset.
 *
 * Improvements implemented:
 *  • Loading spinner while data fetches; hidden on success or error
 *  • Error state with Retry button when network fails and no cached data exists
 *  • Temperature unit toggle (°C / °F) reads user settings and persists changes
 *  • Each daily row is tappable and opens ForecastDetailDialogFragment
 *  • Back arrow reliably navigates back even when not on the back stack
 *  • Bottom nav selection synced when navigating away via back arrow
 */
class ForecastFragment : Fragment() {

    companion object {
        private const val ARG_CITY = "city"
        fun newInstance(city: String = "") = ForecastFragment().apply {
            arguments = Bundle().also { it.putString(ARG_CITY, city) }
        }
    }

    private val repo    by lazy { WeatherRepository(requireContext()) }
    private val db      by lazy { WeatherDatabase.getInstance(requireContext()) }
    private val session by lazy { UserSession(requireContext()) }

    private var city     = ""
    private var tempUnit = "Celsius"

    // Views
    private var tvCity       : TextView?     = null
    private var tvSunrise    : TextView?     = null
    private var tvSunset     : TextView?     = null
    private var tvUvValue    : TextView?     = null
    private var tvUvTip      : TextView?     = null
    private var tvAqiBadge   : TextView?     = null
    private var tvAqiLabel   : TextView?     = null
    private var tvAqiDesc    : TextView?     = null
    private var llWeekly     : LinearLayout? = null
    private var progressBar  : ProgressBar?  = null
    private var contentScroll: ScrollView?   = null
    private var errorLayout  : LinearLayout? = null
    private var tvTempToggle : TextView?     = null

    // Keep last successful forecast so unit toggle can re-render without a new fetch
    private var lastForecast: WeatherRepository.ForecastResult? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_forecast, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        city = arguments?.getString(ARG_CITY)?.trim() ?: ""
        if (city.isEmpty()) city = session.getLastCity() ?: "Cebu City"

        val email = session.getEmail().ifEmpty { WeatherDatabase.GUEST_EMAIL }
        tempUnit = db.getSettings(email).tempUnit

        tvCity        = view.findViewById(R.id.tvForecastCity)
        tvSunrise     = view.findViewById(R.id.tvForecastSunrise)
        tvSunset      = view.findViewById(R.id.tvForecastSunset)
        tvUvValue     = view.findViewById(R.id.tvForecastUvValue)
        tvUvTip       = view.findViewById(R.id.tvForecastUvTip)
        tvAqiBadge    = view.findViewById(R.id.tvForecastAqiBadge)
        tvAqiLabel    = view.findViewById(R.id.tvForecastAqiLabel)
        tvAqiDesc     = view.findViewById(R.id.tvForecastAqiDesc)
        llWeekly      = view.findViewById(R.id.llForecastWeekly)
        progressBar   = view.findViewById(R.id.progressForecast)
        contentScroll = view.findViewById(R.id.scrollForecastContent)
        errorLayout   = view.findViewById(R.id.layoutForecastError)
        tvTempToggle  = view.findViewById(R.id.tvForecastTempToggle)

        // Back arrow — works whether or not on the back stack
        view.findViewById<TextView>(R.id.tvForecastBack)?.setOnClickListener {
            if (!parentFragmentManager.popBackStackImmediate()) {
                (activity as? MainActivity)?.let { act ->
                    act.loadFragment(HomeFragment())
                    act.findViewById<BottomNavigationView>(R.id.bottomNav)
                        ?.selectedItemId = R.id.nav_home
                }
            }
        }

        // Temp unit toggle
        tvTempToggle?.text = if (tempUnit == "Celsius") "Switch to °F" else "Switch to °C"
        tvTempToggle?.setOnClickListener {
            tempUnit = if (tempUnit == "Celsius") "Fahrenheit" else "Celsius"
            tvTempToggle?.text = if (tempUnit == "Celsius") "Switch to °F" else "Switch to °C"
            val em = session.getEmail().ifEmpty { WeatherDatabase.GUEST_EMAIL }
            db.saveSettings(em, db.getSettings(em).copy(tempUnit = tempUnit))
            lastForecast?.let { bindAll(it) }
        }

        // Retry button in error layout
        view.findViewById<TextView>(R.id.btnForecastRetry)?.setOnClickListener {
            fetchForecast()
        }

        showLoading()
        fetchForecast()
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private fun showLoading() {
        progressBar?.visibility   = View.VISIBLE
        contentScroll?.visibility = View.GONE
        errorLayout?.visibility   = View.GONE
    }

    private fun showContent() {
        progressBar?.visibility   = View.GONE
        contentScroll?.visibility = View.VISIBLE
        errorLayout?.visibility   = View.GONE
    }

    private fun showError() {
        progressBar?.visibility   = View.GONE
        contentScroll?.visibility = View.GONE
        errorLayout?.visibility   = View.VISIBLE
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private fun fetchForecast() {
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            repo.getForecast(city)
                .onSuccess { forecast ->
                    if (!session.hasManualCity()) session.setLastCity(forecast.current.city)
                    val email = session.getEmail()
                    if (email.isNotEmpty()) db.addSearchHistory(email, forecast.current.city)

                    lastForecast = forecast
                    bindAll(forecast)
                    showContent()

                    // AQI is a separate call; show content first, fill AQI when ready
                    repo.getAirQuality(forecast.current.lat, forecast.current.lon)
                        .onSuccess { aqi -> bindAqi(aqi) }
                        .onFailure {
                            tvAqiLabel?.text = "Unavailable"
                            tvAqiDesc?.text  = "Could not load air quality data."
                        }
                }
                .onFailure {
                    if (lastForecast == null) {
                        showError()
                        context?.let { ctx ->
                            AppToast.show(ctx, "Forecast unavailable: ${it.message}", longDuration = true)
                        }
                    } else {
                        // Stale data still displayed; just warn
                        showContent()
                        context?.let { ctx ->
                            AppToast.show(ctx, "Couldn't refresh. Showing cached data.")
                        }
                    }
                }
        }
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bindAll(forecast: WeatherRepository.ForecastResult) {
        val use24h = is24HourFormatPreferred()
        tvCity?.text    = "${forecast.current.city}, ${forecast.current.country}"
        tvSunrise?.text = forecast.current.sunriseFormatted(use24h)
        tvSunset?.text  = forecast.current.sunsetFormatted(use24h)
        bindUv(forecast.current.uvIndex)
        bindWeekly(forecast.daily)
    }

    private fun bindUv(uv: Double) {
        val (label, tip) = when {
            uv < 3  -> "%.0f — Low".format(uv) to "No protection needed."
            uv < 6  -> "%.0f — Moderate".format(uv) to "Wear sunscreen SPF 30+."
            uv < 8  -> "%.0f — High".format(uv) to "SPF 50+ recommended. Seek shade 10AM–4PM."
            uv < 11 -> "%.0f — Very High".format(uv) to "Wear sunscreen, hat and sunglasses."
            else    -> "%.0f — Extreme".format(uv) to "Avoid sun exposure between 10AM–4PM."
        }
        tvUvValue?.text = label
        tvUvTip?.text   = tip
    }

    private fun bindAqi(aqi: WeatherRepository.AirQuality) {
        tvAqiBadge?.text = "AQI ${aqi.aqi}"
        tvAqiLabel?.text = aqi.label
        tvAqiDesc?.text  = when (aqi.aqi) {
            1    -> "Air quality is satisfactory."
            2    -> "Air quality is acceptable."
            3    -> "Sensitive groups may be affected."
            4    -> "Everyone may experience health effects."
            else -> "Health alert: serious risk for everyone."
        }
    }

    private fun bindWeekly(daily: List<WeatherRepository.DailyItem>) {
        val ll  = llWeekly ?: return
        ll.removeAllViews()
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val globalMin = daily.minOfOrNull { it.minC } ?: 0.0
        val globalMax = daily.maxOfOrNull { it.maxC } ?: 40.0
        val range     = (globalMax - globalMin).coerceAtLeast(1.0)

        daily.forEachIndexed { i, d ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(
                    (10 * dp).toInt(), (10 * dp).toInt(),
                    (10 * dp).toInt(), (10 * dp).toInt()
                )
                setBackgroundResource(if (i == 0) R.drawable.bg_today_row else R.drawable.bg_glass_card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (6 * dp).toInt()
                layoutParams    = lp
                isClickable     = true
                isFocusable     = true
            }

            val dayLabel = if (i == 0) "Today" else d.shortDayLabel()

            // Day label
            row.addView(TextView(ctx).apply {
                text     = dayLabel
                textSize = 13f
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    (44 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Icon
            row.addView(TextView(ctx).apply {
                text     = iconEmoji(d.iconCode)
                textSize = 18f
                gravity  = Gravity.CENTER
                val lp   = LinearLayout.LayoutParams(
                    (30 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = (10 * dp).toInt()
                layoutParams = lp
            })

            // Temperature bar
            val barContainer = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, (4 * dp).toInt(), 1f).also {
                    it.marginEnd = (10 * dp).toInt()
                }
                setBackgroundResource(R.drawable.bg_temp_bar_track)
            }
            val leftWeight  = ((d.minC - globalMin) / range).toFloat().coerceIn(0f, 1f)
            val barWeight   = ((d.maxC - d.minC) / range).toFloat().coerceIn(0.1f, 1f)
            val rightWeight = (1f - leftWeight - barWeight).coerceAtLeast(0f)

            barContainer.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                if (leftWeight > 0) addView(View(ctx), LinearLayout.LayoutParams(0, 1, leftWeight))
                addView(View(ctx).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(
                            android.graphics.Color.parseColor("#3B82F6"),
                            android.graphics.Color.parseColor("#F59E0B")
                        )
                    ).also { it.cornerRadius = 2 * dp }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, barWeight))
                if (rightWeight > 0) addView(View(ctx), LinearLayout.LayoutParams(0, 1, rightWeight))
            })
            row.addView(barContainer)

            // Temperature range — respects selected unit
            val minStr = if (tempUnit == "Celsius") "%.0f".format(d.minC)
                         else "%.0f".format(d.minC * 9.0 / 5 + 32)
            val maxStr = if (tempUnit == "Celsius") "%.0f".format(d.maxC)
                         else "%.0f".format(d.maxC * 9.0 / 5 + 32)
            // Partial-day rows get a "~" prefix on the temperature to signal estimate
            val tempStr = if (d.isPartialDay) "~$minStr–$maxStr°" else "$minStr–$maxStr°"
            row.addView(TextView(ctx).apply {
                text     = tempStr
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            })

            // ── Tap → detail dialog ────────────────────────────────────────────
            val unitLabel   = if (tempUnit == "Celsius") "°C" else "°F"
            val partialNote = if (d.isPartialDay) " (partial day estimate)" else ""
            row.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                val existing = childFragmentManager.findFragmentByTag("ForecastDetailDialog")
                if (existing != null) return@setOnClickListener
                ForecastDetailDialogFragment.safeShow(
                    fm        = childFragmentManager,
                    title     = if (i == 0) "Today's Forecast" else "${d.dayLabel()} Forecast",
                    whenLabel = d.dayLabel(),
                    icon      = iconEmoji(d.iconCode),
                    summary   = d.summary + partialNote,
                    temp      = "High $maxStr – Low $minStr$unitLabel  ·  Humidity: ${d.avgHumidity}%",
                    rain      = "Rain: ${(d.maxPop * 100).toInt()}%  ·  Wind: ${"%.0f".format(d.maxWindKph)} km/h"
                )
            }

            ll.addView(row)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun iconEmoji(code: String): String = when {
        code.startsWith("01") -> "☀️"
        code.startsWith("02") -> "🌤"
        code.startsWith("03") -> "⛅"
        code.startsWith("04") -> "☁️"
        code.startsWith("09") -> "🌧"
        code.startsWith("10") -> "🌦"
        code.startsWith("11") -> "⛈"
        code.startsWith("13") -> "❄️"
        code.startsWith("50") -> "🌫"
        else                  -> "⛅"
    }

    private fun is24HourFormatPreferred(): Boolean {
        val email = session.getEmail().ifEmpty { WeatherDatabase.GUEST_EMAIL }
        return db.getSettings(email).timeFormat == "24-hour"
    }
}
