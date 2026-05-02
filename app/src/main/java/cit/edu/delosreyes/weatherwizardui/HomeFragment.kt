package cit.edu.delosreyes.weatherwizardui

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.util.*

/**
 * HomeFragment — live current weather + hourly + 5-day forecast on screen.
 * Refined to match prototype UI/UX tokens.
 */
class HomeFragment : Fragment() {

    private val repo    by lazy { WeatherRepository(requireContext()) }
    private val db      by lazy { WeatherDatabase.getInstance(requireContext()) }
    private val session by lazy { UserSession(requireContext()) }

    // Views
    private var tvCity          : TextView?       = null
    private var tvIcon          : TextView?       = null
    private var tvTemp          : TextView?       = null
    private var tvDescription   : TextView?       = null
    private var tvFeelsHumidity : TextView?       = null
    private var tvHumidityVal   : TextView?       = null
    private var tvWindVal       : TextView?       = null
    private var tvVisibilityVal : TextView?       = null
    private var llHourly        : LinearLayout?   = null
    private var llDaily         : LinearLayout?   = null
    private var tvNotifBtn      : TextView?       = null
    private var tvSeeForecast   : TextView?       = null
    private var didTryLocationLookup = false
    private val emulatorDefaultLat = 37.4219983
    private val emulatorDefaultLon = -122.084

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!isAdded) return@registerForActivityResult
        if (granted) {
            loadByDeviceLocation()
        } else {
            AppToast.show(requireContext(), "Location permission denied. Showing default city weather.")
            loadDefaultWeather()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCity          = view.findViewById(R.id.tvHomeCity)
        tvIcon          = view.findViewById(R.id.tvHomeIcon)
        tvTemp          = view.findViewById(R.id.tvHomeTemp)
        tvDescription   = view.findViewById(R.id.tvHomeDescription)
        tvFeelsHumidity = view.findViewById(R.id.tvHomeFeelsHumidity)
        tvHumidityVal   = view.findViewById(R.id.tvHomeHumidityVal)
        tvWindVal       = view.findViewById(R.id.tvHomeWindVal)
        tvVisibilityVal = view.findViewById(R.id.tvHomeVisibilityVal)
        llHourly        = view.findViewById(R.id.llHomeHourly)
        llDaily         = view.findViewById(R.id.llHomeDaily)
        tvNotifBtn      = view.findViewById(R.id.tvNotifBtn)
        tvSeeForecast   = view.findViewById(R.id.tvSeeForecast)

        tvNotifBtn?.setOnClickListener {
            (activity as? MainActivity)?.apply {
                loadFragment(AlertsFragment())
                findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.nav_alerts
            }
        }

        tvSeeForecast?.setOnClickListener {
            val city = session.getLastCity() ?: "Cebu City"
            (activity as? MainActivity)?.let { act ->
                act.loadFragment(ForecastFragment.newInstance(city))
                act.findViewById<BottomNavigationView>(R.id.bottomNav)
                    ?.selectedItemId = R.id.nav_forecast
            }
        }

        // If the user manually selected a city from Profile, skip GPS entirely.
        if (session.hasManualCity()) {
            loadWeather(session.getManualCity())
        } else {
            requestLocationAndLoadWeather()
        }
    }

    private fun loadWeather(city: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val forecastResult = repo.getForecast(city)
            forecastResult.onSuccess { forecast ->
                // Only update LAST_CITY from GPS results when no manual override is active
                if (!session.hasManualCity()) {
                    session.setLastCity(forecast.current.city)
                    session.setLastCountry(forecast.current.country)
                }
                val email = session.getEmail()
                if (email.isNotEmpty()) db.addSearchHistory(email, forecast.current.city)
                bindCurrentWeather(forecast.current)
                bindHourly(forecast.hourly, forecast.current.timezoneOffsetSec)
                bindDaily(forecast.daily)
            }.onFailure {
                repo.getCurrentWeather(city).onSuccess { w ->
                    if (!session.hasManualCity()) {
                        session.setLastCity(w.city)
                        session.setLastCountry(w.country)
                    }
                    bindCurrentWeather(w)
                }
            }
        }
    }

    private fun loadWeatherByCoords(lat: Double, lon: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            val forecastResult = repo.getForecastByCoords(lat, lon)
            forecastResult.onSuccess { forecast ->
                // GPS-resolved city — only write when no manual override is active
                if (!session.hasManualCity()) {
                    session.setLastCity(forecast.current.city)
                    session.setLastCountry(forecast.current.country)
                }
                val email = session.getEmail()
                if (email.isNotEmpty()) db.addSearchHistory(email, forecast.current.city)
                bindCurrentWeather(forecast.current)
                bindHourly(forecast.hourly, forecast.current.timezoneOffsetSec)
                bindDaily(forecast.daily)
            }.onFailure {
                val city = session.getLastCity() ?: "Cebu City"
                loadWeather(city)
            }
        }
    }

    private fun bindCurrentWeather(w: WeatherRepository.CurrentWeather) {
        tvCity?.text          = "${w.city}, ${w.country}"
        tvIcon?.text          = iconEmoji(w.iconCode)
        tvTemp?.text          = "%.0f°".format(w.tempC)
        tvDescription?.text   = w.description
        tvFeelsHumidity?.text = "Feels like %.0f° · Humidity %d%%".format(w.feelsLikeC, w.humidity)
        tvHumidityVal?.text   = "${w.humidity}%"
        tvWindVal?.text       = "%.0f km/h".format(w.windKph)
        tvVisibilityVal?.text = "%.1f km".format(w.visibility / 1000.0)
    }

    private fun bindHourly(hourly: List<WeatherRepository.HourlyItem>, timezoneOffsetSec: Int) {
        val ll = llHourly ?: return
        ll.removeAllViews()
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val use24h = is24HourFormatPreferred()

        val nowSec = System.currentTimeMillis() / 1000L
        
        // Filter: only show upcoming hours (limit to next 24 hours of data points)
        val filtered = hourly.filter { it.time > nowSec - 3600 }.take(8)

        filtered.forEachIndexed { i, h ->
            val isNow   = i == 0
            val bg      = if (isNow) R.drawable.bg_hourly_now else R.drawable.bg_glass_card
            val cell    = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                setPadding(
                    (6 * dp).toInt(), (9 * dp).toInt(),
                    (6 * dp).toInt(), (9 * dp).toInt()
                )
                setBackgroundResource(bg)
                val lp = LinearLayout.LayoutParams((52 * dp).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = (8 * dp).toInt()
                layoutParams = lp
            }

            fun tv(text: String, sizeSp: Float, bold: Boolean = false,
                   color: Int = android.graphics.Color.WHITE) = TextView(ctx).apply {
                this.text      = text
                textSize       = sizeSp
                gravity        = Gravity.CENTER
                setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }

            val timeLabel  = if (isNow) "Now" else h.timeLabel(use24h)
            val mutedColor = ContextCompat.getColor(ctx, R.color.mist)
            val rainColor  = ContextCompat.getColor(ctx, R.color.rain)

            cell.addView(tv(timeLabel, 10f, color = if (isNow) android.graphics.Color.WHITE else mutedColor))
            cell.addView(tv(iconEmoji(h.iconCode), 18f).apply {
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            })
            cell.addView(tv("%.0f°".format(h.tempC), 12f, bold = true))
            cell.addView(tv("${(h.pop * 100).toInt()}%", 10f, color = rainColor))
            
            cell.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                showForecastDetailPopup(
                    whenLabel = h.timeLabel(use24h),
                    icon      = iconEmoji(h.iconCode),
                    summary   = h.description,
                    temp      = "Temp: %.0f°C  ·  Feels %.0f°C".format(h.tempC, h.feelsLikeC),
                    rain      = "Rain: ${(h.pop * 100).toInt()}%  ·  Humidity: ${h.humidity}%  ·  Wind: ${"%.0f".format(h.windKph)} km/h"
                )
            }

            ll.addView(cell)
        }
    }

    private fun bindDaily(daily: List<WeatherRepository.DailyItem>) {
        val ll = llDaily ?: return
        ll.removeAllViews()
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val globalMin = daily.minOfOrNull { it.minC } ?: 0.0
        val globalMax = daily.maxOfOrNull { it.maxC } ?: 40.0
        val range = (globalMax - globalMin).coerceAtLeast(1.0)

        val azureColor = ContextCompat.getColor(ctx, R.color.azure)
        val dawnColor = ContextCompat.getColor(ctx, R.color.dawn)
        val mistColor = ContextCompat.getColor(ctx, R.color.mist)

        daily.forEachIndexed { i, d ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(
                    (10 * dp).toInt(), (8 * dp).toInt(),
                    (10 * dp).toInt(), (8 * dp).toInt()
                )
                setBackgroundResource(if (i == 0) R.drawable.bg_today_row else R.drawable.bg_glass_card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (4 * dp).toInt()
                layoutParams = lp
            }

            val dayLabel = if (i == 0) "Today" else d.shortDayLabel()

            row.addView(TextView(ctx).apply {
                text      = dayLabel
                textSize  = 12f
                setTextColor(android.graphics.Color.WHITE)
                val lp = LinearLayout.LayoutParams((40 * dp).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            })

            row.addView(TextView(ctx).apply {
                text    = iconEmoji(d.iconCode)
                textSize = 16f
                gravity  = Gravity.CENTER
                val lp   = LinearLayout.LayoutParams((30 * dp).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = (10 * dp).toInt()
                layoutParams = lp
            })

            val barContainer = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, (4 * dp).toInt(), 1f).also {
                    it.marginEnd = (10 * dp).toInt()
                }
                setBackgroundResource(R.drawable.bg_temp_bar_track)
            }

            val leftWeight = ((d.minC - globalMin) / range).toFloat().coerceIn(0f, 1f)
            val barWeight  = ((d.maxC - d.minC) / range).toFloat().coerceIn(0.1f, 1f)

            val barInnerLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                
                if (leftWeight > 0) addView(View(ctx), LinearLayout.LayoutParams(0, 1, leftWeight))
                
                addView(View(ctx).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(azureColor, dawnColor)
                    ).apply { cornerRadius = 2 * dp }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, barWeight))
                
                val rightWeight = (1f - leftWeight - barWeight).coerceAtLeast(0f)
                if (rightWeight > 0) addView(View(ctx), LinearLayout.LayoutParams(0, 1, rightWeight))
            }
            barContainer.addView(barInnerLayout)
            row.addView(barContainer)

            row.addView(TextView(ctx).apply {
                text      = "%.0f° / %.0f°".format(d.minC, d.maxC)
                textSize  = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(mistColor)
            })
            row.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                showForecastDetailPopup(
                    whenLabel = d.dayLabel(),
                    icon      = iconEmoji(d.iconCode),
                    summary   = d.summary,
                    temp      = "High %.0f°C  ·  Low %.0f°C".format(d.maxC, d.minC),
                    rain      = "Rain: ${(d.maxPop * 100).toInt()}%  ·  Humidity: ${d.avgHumidity}%  ·  ${"%.0f".format(d.maxWindKph)} km/h"
                )
            }

            ll.addView(row)
        }
    }

    private fun requestLocationAndLoadWeather() {
        val ctx = context ?: return
        val fineGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            loadByDeviceLocation()
            return
        }

        val shouldShowRationale = shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (shouldShowRationale) {
            AlertDialog.Builder(ctx)
                .setTitle("Allow location access")
                .setMessage("WeatherWizard uses your location to show accurate weather for your area.")
                .setPositiveButton("Allow") { _, _ ->
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                .setNegativeButton("Not now") { _, _ -> loadDefaultWeather() }
                .show()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun loadByDeviceLocation() {
        val ctx = context ?: return
        val fineGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            if (!didTryLocationLookup) requestLocationAndLoadWeather() else loadDefaultWeather()
            return
        }

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        requestFreshLocation(lm) { freshLocation ->
            if (!isAdded) return@requestFreshLocation
            val lastKnown = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).mapNotNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }.filter { it.hasAccuracy() }.minByOrNull { it.accuracy }
                ?: listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).firstNotNullOfOrNull { provider ->
                    runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                }

            val location = freshLocation ?: lastKnown

            if (location != null) {
                didTryLocationLookup = true
                val isLikelyEmulatorDefault = isNear(
                    location.latitude,
                    location.longitude,
                    emulatorDefaultLat,
                    emulatorDefaultLon,
                    threshold = 0.02
                )
                if (isLikelyEmulatorDefault && session.getLastCity().isNullOrEmpty()) {
                    AppToast.show(
                        ctx,
                        "Emulator location is using default coordinates. Set a custom emulator location.",
                        longDuration = true
                    )
                    loadDefaultWeather()
                } else {
                    loadWeatherByCoords(location.latitude, location.longitude)
                }
            } else {
                AppToast.show(ctx, "Unable to read current location. Showing default city weather.")
                loadDefaultWeather()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun requestFreshLocation(
        locationManager: LocationManager,
        onResult: (Location?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        var delivered = false
        var bestLocation: Location? = null
        var listeners = mutableListOf<LocationListener>()

        fun deliverOnce(location: Location?) {
            if (delivered) return
            delivered = true
            listeners.forEach { locationManager.removeUpdates(it) }
            onResult(location)
        }

        fun isBetter(newLoc: Location): Boolean {
            val best = bestLocation ?: return true
            return newLoc.hasAccuracy() && newLoc.accuracy < best.accuracy
        }

        val gpsListener = LocationListener { location ->
            if (isBetter(location)) bestLocation = location
            if (location.hasAccuracy() && location.accuracy <= 100f) {
                deliverOnce(bestLocation)
            }
        }
        val networkListener = LocationListener { location ->
            if (isBetter(location)) bestLocation = location
        }

        listeners.add(gpsListener)
        listeners.add(networkListener)

        mainHandler.postDelayed({ deliverOnce(bestLocation) }, 10_000L)

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                gpsListener,
                Looper.getMainLooper()
            )
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                0L,
                0f,
                networkListener,
                Looper.getMainLooper()
            )
        }
    }

    private fun loadDefaultWeather() {
        // If the user already set a manual city, use that instead of the hardcoded default
        if (session.hasManualCity()) {
            loadWeather(session.getManualCity())
            return
        }
        session.setLastCity("Cebu City")
        session.setLastCountry("PH")
        loadWeather("Cebu City")
    }

    private fun isNear(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        threshold: Double
    ): Boolean {
        return kotlin.math.abs(lat1 - lat2) <= threshold &&
            kotlin.math.abs(lon1 - lon2) <= threshold
    }

    private fun is24HourFormatPreferred(): Boolean {
        val email = session.getEmail().ifEmpty { WeatherDatabase.GUEST_EMAIL }
        return db.getSettings(email).timeFormat == "24-hour"
    }

    private fun showForecastDetailPopup(
        whenLabel: String,
        icon: String,
        summary: String,
        temp: String,
        rain: String
    ) {
        if (!isAdded) return
        val existing = childFragmentManager.findFragmentByTag("ForecastDetailDialog")
        if (existing != null) return
        ForecastDetailDialogFragment.newInstance(
            title = "Forecast Details",
            whenLabel = whenLabel,
            icon = icon,
            summary = summary,
            temp = temp,
            rain = rain
        ).show(childFragmentManager, "ForecastDetailDialog")
    }

    /** Map OWM icon codes to representative emoji. */
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
}
