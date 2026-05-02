package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarFragment — live outdoor/indoor activity planner.
 *
 * Fixes & improvements
 * ────────────────────
 *  1. Activity type from UserSettings is passed to rateDaysForOutdoor — Indoor
 *     users see ratings appropriate for staying in.
 *  2. Partial-day cells show a "~" prefix on the day number as a visual caveat.
 *  3. Detail card shows humidity, wind, and feels-like alongside temp/rain.
 *  4. Days beyond the 5-day forecast window are clearly styled as "no data".
 *  5. Today's cell is highlighted with a ring/accent distinct from rated cells.
 *  6. Cell sizes enlarged for readability.
 *  7. Best-day summary respects activity type label.
 */
class CalendarFragment : Fragment() {

    private val repo    by lazy { WeatherRepository(requireContext()) }
    private val session by lazy { UserSession(requireContext()) }
    private val db      by lazy { WeatherDatabase.getInstance(requireContext()) }

    private var bestDay : WeatherRepository.DayRating? = null
    private var ratings : List<WeatherRepository.DayRating> = emptyList()

    private var llGrid         : LinearLayout? = null
    private var llDetailCard   : LinearLayout? = null
    private var llBestDay      : LinearLayout? = null
    private var tvCalMonth     : TextView?     = null
    private var tvDetailDate   : TextView?     = null
    private var tvDetailRating : TextView?     = null
    private var tvDetailTemp   : TextView?     = null
    private var tvDetailRain   : TextView?     = null
    private var tvDetailReason : TextView?     = null
    private var tvBestLabel    : TextView?     = null
    private var tvBestReason   : TextView?     = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llGrid         = view.findViewById(R.id.llCalendarGrid)
        llDetailCard   = view.findViewById(R.id.llCalDetailCard)
        llBestDay      = view.findViewById(R.id.llCalBestDay)
        tvCalMonth     = view.findViewById(R.id.tvCalendarMonth)
        tvDetailDate   = view.findViewById(R.id.tvCalDetailDate)
        tvDetailRating = view.findViewById(R.id.tvCalDetailRating)
        tvDetailTemp   = view.findViewById(R.id.tvCalDetailTemp)
        tvDetailRain   = view.findViewById(R.id.tvCalDetailRain)
        tvDetailReason = view.findViewById(R.id.tvCalDetailReason)
        tvBestLabel    = view.findViewById(R.id.tvCalBestDayLabel)
        tvBestReason   = view.findViewById(R.id.tvCalBestDayReason)

        tvCalMonth?.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

        // Dismiss detail card when tapped
        llDetailCard?.setOnClickListener {
            llDetailCard?.visibility = View.GONE
            llBestDay?.visibility    = View.VISIBLE
        }

        view.findViewById<Button>(R.id.btnExportCalendar)?.setOnClickListener {
            val day = bestDay
            if (day == null) AppToast.show(requireContext(), "Loading forecast — try again shortly.")
            else exportToCalendar(day)
        }

        val city = if (session.hasManualCity()) session.getManualCity()
                   else session.getLastCity() ?: "Cebu City"
        loadForecast(city)
    }

    // ── Load & rate ───────────────────────────────────────────────────────────

    private fun loadForecast(city: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get user's activity preference so ratings match their lifestyle
            val email        = session.getEmail().ifEmpty { WeatherDatabase.GUEST_EMAIL }
            val activityType = db.getSettings(email).activity   // "Outdoor" | "Indoor" | "Both"

            repo.getForecast(city).onSuccess { forecast ->
                ratings = repo.rateDaysForOutdoor(forecast.daily, activityType)
                bestDay = ratings.firstOrNull { it.rating == "BEST" }
                    ?: ratings.firstOrNull { it.rating == "MODERATE" }

                buildCalendarGrid(ratings)
                updateBestDaySummary(activityType)

            }.onFailure {
                context?.let { ctx ->
                    AppToast.show(ctx, "Could not load forecast: ${it.message}", longDuration = true)
                }
                buildCalendarGrid(emptyList())
            }
        }
    }

    // ── Calendar grid ─────────────────────────────────────────────────────────

    private fun buildCalendarGrid(ratings: List<WeatherRepository.DayRating>) {
        val ll = llGrid ?: return
        ll.removeAllViews()

        val ctx      = requireContext()
        val dp       = ctx.resources.displayMetrics.density
        val today    = Calendar.getInstance()
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val todayMon = today.get(Calendar.MONTH)
        val todayYr  = today.get(Calendar.YEAR)

        // Map day-of-month → rating
        val ratingMap = mutableMapOf<Int, WeatherRepository.DayRating>()
        ratings.forEach { r ->
            val cal = Calendar.getInstance().apply { timeInMillis = r.date * 1000L }
            if (cal.get(Calendar.MONTH) == todayMon && cal.get(Calendar.YEAR) == todayYr) {
                ratingMap[cal.get(Calendar.DAY_OF_MONTH)] = r
            }
        }

        val firstDay  = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val startDow  = firstDay.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMon = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)

        var dayNum = 1
        var row    = buildRowLayout(ctx, dp)
        var col    = 0

        repeat(startDow) { row.addView(emptyCell(ctx, dp)); col++ }

        while (dayNum <= daysInMon) {
            if (col == 7) { ll.addView(row); row = buildRowLayout(ctx, dp); col = 0 }

            val rating    = ratingMap[dayNum]
            val isPast    = dayNum < todayDay
            val isToday   = dayNum == todayDay
            val isFuture  = dayNum > todayDay

            val bgRes = when {
                isToday                    -> R.drawable.bg_cal_today
                rating?.rating == "BEST"     -> R.drawable.bg_cal_best
                rating?.rating == "MODERATE" -> R.drawable.bg_cal_moderate
                rating?.rating == "AVOID"    -> R.drawable.bg_cal_avoid
                else                         -> R.drawable.bg_glass_card
            }

            val emoji = when (rating?.rating) {
                "BEST"     -> "☀️"
                "MODERATE" -> "🌤"
                "AVOID"    -> "🌧"
                else       -> if (isPast) "·" else ""
            }

            val capturedDay = dayNum

            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                // FIX: increased padding for bigger cells
                setPadding((5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt())
                setBackgroundResource(bgRes)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = if (col < 6) (3 * dp).toInt() else 0
                layoutParams = lp

                if (rating != null) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showDayDetail(rating, capturedDay) }
                }
                if (isPast && rating == null) alpha = 0.45f
            }

            // Emoji icon row
            if (emoji.isNotEmpty()) {
                cell.addView(TextView(ctx).apply {
                    text     = emoji
                    textSize = 14f   // FIX: larger emoji
                    gravity  = Gravity.CENTER
                })
            }

            // Day number — partial day gets "~" prefix as caveat
            val dayLabel = if (rating?.isPartialDay == true) "~$capturedDay" else "$capturedDay"
            cell.addView(TextView(ctx).apply {
                text     = dayLabel
                textSize = 13f   // FIX: larger day number
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isToday) Color.parseColor("#3B82F6") else Color.WHITE)
                gravity  = Gravity.CENTER
                if (isFuture && rating == null) alpha = 0.5f
            })

            // "No data" dot for future unrated days
            if (isFuture && rating == null && !isToday) {
                cell.addView(TextView(ctx).apply {
                    text     = "?"
                    textSize = 9f
                    gravity  = Gravity.CENTER
                    setTextColor(Color.parseColor("#64748B"))
                })
            }

            row.addView(cell)
            col++
            dayNum++
        }

        while (col % 7 != 0) { row.addView(emptyCell(ctx, dp)); col++ }
        if (col > 0) ll.addView(row)
    }

    private fun buildRowLayout(ctx: android.content.Context, dp: Float) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = (6 * dp).toInt()   // FIX: more row spacing
        layoutParams    = lp
    }

    private fun emptyCell(ctx: android.content.Context, dp: Float) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(0, (58 * dp).toInt(), 1f)
            .also { it.marginEnd = (3 * dp).toInt() }
    }

    // ── Detail card ───────────────────────────────────────────────────────────

    private fun showDayDetail(r: WeatherRepository.DayRating, dayNum: Int) {
        val ratingColor = when (r.rating) {
            "BEST"  -> Color.parseColor("#10B981")
            "AVOID" -> Color.parseColor("#EF4444")
            else    -> Color.parseColor("#F59E0B")
        }
        val ratingStr = when (r.rating) {
            "BEST"     -> "✅ Best Day to Go Out"
            "MODERATE" -> "🟡 Moderate Conditions"
            else       -> "🚫 Avoid Going Out"
        }

        tvDetailDate?.text   = "📅 ${r.dateLabel().replace("\n", ", ")}"
        tvDetailRating?.text = ratingStr
        tvDetailRating?.setTextColor(ratingColor)
        tvDetailDate?.setTextColor(ratingColor)

        // FIX: richer detail — temp, feels-like, humidity, wind, rain
        tvDetailTemp?.text = buildString {
            append("🌡 High %.0f°C  ·  Feels %.0f°C".format(r.tempC, r.feelsLikeC))
            if (r.isPartialDay) append("  (~partial day)")
        }
        tvDetailRain?.text = buildString {
            append("💧 ${(r.pop * 100).toInt()}% rain")
            append("  💨 %.0f km/h".format(r.maxWindKph))
            append("  💦 ${r.humidity}% humidity")
        }
        tvDetailReason?.text = r.reason

        llDetailCard?.visibility = View.VISIBLE
        llBestDay?.visibility    = View.GONE
    }

    // ── Best-day summary ──────────────────────────────────────────────────────

    private fun updateBestDaySummary(activityType: String) {
        val best = bestDay ?: run {
            tvBestLabel?.text  = "No ideal day found in the next 5 days."
            tvBestReason?.text = ""
            return
        }
        val label = when (activityType) {
            "Indoor" -> "🏠 Best day to stay in"
            else     -> "📅 Best day to go out"
        }
        tvBestLabel?.text  = "$label: ${best.dateLabel().replace("\n", ", ")}"
        tvBestReason?.text = best.reason
    }

    // ── Calendar export ───────────────────────────────────────────────────────

    private fun exportToCalendar(day: WeatherRepository.DayRating) {
        val city    = if (session.hasManualCity()) session.getManualCity()
                      else session.getLastCity() ?: "City"
        val beginMs = day.date * 1000L
        val title   = when (day.rating) {
            "BEST"     -> "✅ Great day outdoors — $city"
            "MODERATE" -> "🌤 Decent day outdoors — $city"
            else       -> "🌧 Stay in — $city"
        }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME,   beginMs + 3_600_000L)
            putExtra(CalendarContract.Events.TITLE,       title)
            putExtra(CalendarContract.Events.DESCRIPTION,
                "${day.reason}\n🌡 ${day.tempC.toInt()}°C  💧 ${(day.pop * 100).toInt()}% rain  💨 ${day.maxWindKph.toInt()} km/h")
            putExtra(CalendarContract.Events.ALL_DAY, false)
        }
        try { startActivity(intent) }
        catch (_: Exception) { AppToast.show(requireContext(), "No calendar app found.") }
    }
}
