package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ProfileFragment : Fragment() {

    private val session by lazy { UserSession(requireContext()) }
    private val db      by lazy { WeatherDatabase.getInstance(requireContext()) }

    private lateinit var tvProfileName    : TextView
    private lateinit var tvProfileEmail   : TextView
    private lateinit var tvProfileLocation: TextView
    private lateinit var tvAddLocation    : TextView
    private lateinit var llSavedLocations : LinearLayout
    private lateinit var tvTempUnitValue  : TextView
    private lateinit var tvTimeFormatValue: TextView
    private lateinit var rowTempUnit      : LinearLayout
    private lateinit var rowTimeFormat    : LinearLayout
    private lateinit var rowActivity      : LinearLayout
    private lateinit var rowChangePassword: LinearLayout
    private lateinit var rowLogout        : LinearLayout

    private var settings = WeatherDatabase.UserSettings()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvProfileName      = view.findViewById(R.id.tvProfileName)
        tvProfileEmail     = view.findViewById(R.id.tvProfileEmail)
        tvProfileLocation  = view.findViewById(R.id.tvProfileLocation)
        tvAddLocation      = view.findViewById(R.id.tvAddLocation)
        llSavedLocations   = view.findViewById(R.id.llSavedLocations)
        tvTempUnitValue    = view.findViewById(R.id.tvTempUnitValue)
        tvTimeFormatValue  = view.findViewById(R.id.tvTimeFormatValue)
        rowTempUnit        = view.findViewById(R.id.rowTempUnit)
        rowTimeFormat      = view.findViewById(R.id.rowTimeFormat)
        rowActivity        = view.findViewById(R.id.rowActivity)
        rowChangePassword  = view.findViewById(R.id.rowChangePassword)
        rowLogout          = view.findViewById(R.id.rowLogout)

        val email = session.getEmail()
        val dbKey = email.ifEmpty { WeatherDatabase.GUEST_EMAIL }

        tvProfileName.text  = session.getUsername()
        tvProfileEmail.text = email.ifEmpty { "Guest" }

        settings = db.getSettings(dbKey)
        tvTempUnitValue.text   = settings.tempUnit
        tvTimeFormatValue.text = settings.timeFormat

        refreshLocationHeader()
        renderSavedLocations(email)

        // ── Use GPS button — clears manual override ───────────────────────────
        view.findViewById<TextView>(R.id.tvUseGps)?.setOnClickListener {
            session.clearManualCity()
            AppToast.show(requireContext(), "Switched back to GPS location")
            refreshLocationHeader()
            renderSavedLocations(email)
        }

        // ── Add location ──────────────────────────────────────────────────────
        tvAddLocation.setOnClickListener {
            if (email.isEmpty()) {
                AppToast.show(requireContext(), "Log in to save locations.")
                return@setOnClickListener
            }
            showAddLocationDialog(email)
        }

        // ── Temperature unit toggle ───────────────────────────────────────────
        rowTempUnit.setOnClickListener {
            val next = if (settings.tempUnit == "Celsius") "Fahrenheit" else "Celsius"
            settings = settings.copy(tempUnit = next)
            tvTempUnitValue.text = next
            db.saveSettings(dbKey, settings)
            AppToast.show(requireContext(), "Temperature unit: $next")
        }

        // ── Time format toggle ────────────────────────────────────────────────
        rowTimeFormat.setOnClickListener {
            val next = if (settings.timeFormat == "12-hour") "24-hour" else "12-hour"
            settings = settings.copy(timeFormat = next)
            tvTimeFormatValue.text = next
            db.saveSettings(dbKey, settings)
            AppToast.show(requireContext(), "Time format: $next")
        }

        // ── Activity type picker ──────────────────────────────────────────────
        rowActivity.setOnClickListener { showActivityDialog(dbKey) }

        // ── Change password ───────────────────────────────────────────────────
        rowChangePassword.setOnClickListener {
            (activity as? MainActivity)?.loadFragment(
                ChangePasswordFragment(), addToBackStack = true
            )
        }

        // ── Log out ───────────────────────────────────────────────────────────
        rowLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    session.logoutUser()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun refreshLocationHeader() {
        // Show the active location (manual override takes precedence)
        val display = when {
            session.hasManualCity() -> session.getManualCity()
            else -> {
                val city    = session.getLastCity()
                val country = session.getLastCountry()
                when {
                    !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
                    !city.isNullOrBlank() -> city
                    else -> "Location unavailable"
                }
            }
        }
        tvProfileLocation.text = display
    }

    // ── Saved locations list ──────────────────────────────────────────────────

    private fun renderSavedLocations(email: String) {
        llSavedLocations.removeAllViews()
        val dp      = requireContext().resources.displayMetrics.density

        // Determine the active primary city label
        val primaryCity = when {
            session.hasManualCity()          -> session.getManualCity()
            !session.getLastCity().isNullOrBlank() -> session.getLastCity()!!
            else -> null
        }

        // Current/primary location row (always shown if known, not removable)
        if (!primaryCity.isNullOrBlank()) {
            llSavedLocations.addView(
                buildRow(
                    icon    = "📍",
                    label   = primaryCity,
                    badge   = "Primary",
                    onTap   = null,
                    onLong  = null,
                    dp      = dp
                )
            )
        }

        if (email.isEmpty()) {
            addHint("Log in to view and save locations.", dp)
            return
        }

        val locations = db.getSavedLocations(email)
            .filter { !it.city.equals(primaryCity, ignoreCase = true) }

        for (loc in locations) {
            llSavedLocations.addView(
                buildRow(
                    icon   = "🏙",
                    label  = loc.city,
                    badge  = null,
                    onTap  = {
                        // Set as primary — this is the key call that persists through Home
                        session.setManualCity(loc.city)
                        AppToast.show(requireContext(), "${loc.city} is now the primary location")
                        refreshLocationHeader()
                        renderSavedLocations(email)
                    },
                    onLong = {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Remove Location")
                            .setMessage("Remove \"${loc.city}\" from saved locations?")
                            .setPositiveButton("Remove") { _, _ ->
                                db.removeLocation(email, loc.city)
                                AppToast.show(requireContext(), "${loc.city} removed")
                                renderSavedLocations(email)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    },
                    dp = dp
                )
            )
        }

        if (locations.isEmpty() && primaryCity.isNullOrBlank()) {
            addHint("No saved locations yet. Tap + Add to save one.", dp)
        } else if (locations.isNotEmpty()) {
            addHint("Tap to set primary  •  Long-press to remove", dp)
        }
    }

    private fun buildRow(
        icon  : String,
        label : String,
        badge : String?,
        onTap : (() -> Unit)?,
        onLong: (() -> Unit)?,
        dp    : Float
    ): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_glass_card)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
            layoutParams = lp
            setPadding(
                (12 * dp).toInt(), (12 * dp).toInt(),
                (12 * dp).toInt(), (12 * dp).toInt()
            )
            isClickable = onTap != null
            isFocusable = onTap != null
            if (onTap  != null) setOnClickListener { onTap() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }

        row.addView(TextView(ctx).apply {
            text = icon; textSize = 18f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (10 * dp).toInt() }
            layoutParams = lp
        })

        row.addView(TextView(ctx).apply {
            text         = label
            textSize     = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        when {
            badge != null -> row.addView(TextView(ctx).apply {
                text = badge; textSize = 11f
                setTextColor(Color.parseColor("#3B82F6"))
            })
            onTap != null -> row.addView(TextView(ctx).apply {
                text = "Set primary ›"; textSize = 11f
                setTextColor(Color.parseColor("#64748B"))
            })
        }

        return row
    }

    private fun addHint(text: String, dp: Float) {
        llSavedLocations.addView(TextView(requireContext()).apply {
            this.text = text; textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), 0)
        })
    }

    // ── Add location dialog — matches app Material style ──────────────────────

    private fun showAddLocationDialog(email: String) {
        val ctx       = requireContext()
        val inflater  = LayoutInflater.from(ctx)
        val dp        = ctx.resources.displayMetrics.density

        // Root container
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * dp).toInt(), (8 * dp).toInt(),
                (24 * dp).toInt(), (8 * dp).toInt()
            )
        }

        // Styled TextInputLayout matching the app's login/change-password style
        val til = TextInputLayout(
            ctx, null,
            com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "City name (e.g. Manila, Tokyo)"
            boxBackgroundColor = Color.parseColor("#12213D")
            setBoxStrokeColorStateList(
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.azure)
                )
            )
            setBoxCornerRadii(14f * dp, 14f * dp, 14f * dp, 14f * dp)
        }

        val edit = TextInputEditText(til.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setPadding(
                (4 * dp).toInt(), (14 * dp).toInt(),
                (4 * dp).toInt(), (14 * dp).toInt()
            )
            inputType   = android.text.InputType.TYPE_CLASS_TEXT or
                          android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions  = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }

        til.addView(edit)
        container.addView(til)

        // Tip text
        container.addView(TextView(ctx).apply {
            text = "💡 Tap a saved location to make it primary.\nLong-press to remove it."
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (10 * dp).toInt() }
            layoutParams = lp
        })

        AlertDialog.Builder(ctx)
            .setTitle("Save Location")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val city = edit.text.toString().trim()
                if (city.isEmpty()) return@setPositiveButton
                val added = db.saveLocation(email, city, null, null)
                AppToast.show(ctx, if (added) "\"$city\" saved!" else "\"$city\" already saved.")
                renderSavedLocations(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Activity dialog — single-choice matching app style ────────────────────

    private fun showActivityDialog(dbKey: String) {
        val labels  = arrayOf("🏃  Outdoor", "🏠  Indoor", "🔀  Both")
        val values  = arrayOf("Outdoor",     "Indoor",    "Both")
        val current = values.indexOf(settings.activity).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Activity Type")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                settings = settings.copy(activity = values[which])
                db.saveSettings(dbKey, settings)
                AppToast.show(requireContext(), "Activity set to: ${values[which]}")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
