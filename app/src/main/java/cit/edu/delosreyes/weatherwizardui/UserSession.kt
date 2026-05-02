package cit.edu.delosreyes.weatherwizardui

import android.content.Context
import android.content.SharedPreferences

/**
 * UserSession — lightweight SharedPreferences wrapper for the active login session.
 *
 * Keys
 * ────
 *  IS_LOGGED_IN, USERNAME, EMAIL      → set on login / google sign-in
 *  REGISTERED_*                       → kept in sync with SQLite for quick reads
 *  LAST_CITY / LAST_COUNTRY           → last city resolved by GPS (may be overwritten by GPS)
 *  MANUAL_CITY / HAS_MANUAL_CITY      → city explicitly chosen by the user in Profile;
 *                                       HomeFragment respects this and skips GPS when set.
 */
class UserSession(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("WeatherWizardPrefs", Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────────
    // Session management
    // ─────────────────────────────────────────────────────────────────────────

    fun createLoginSession(username: String, email: String) {
        prefs.edit()
            .putBoolean("IS_LOGGED_IN", true)
            .putString("USERNAME", username)
            .putString("EMAIL", email)
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("IS_LOGGED_IN", false)

    fun logoutUser() {
        prefs.edit()
            .putBoolean("IS_LOGGED_IN", false)
            .putString("USERNAME", "Guest")
            .putString("EMAIL", "")
            .putString("LAST_CITY", null)
            .putString("LAST_COUNTRY", null)
            .putString("MANUAL_CITY", null)
            .putBoolean("HAS_MANUAL_CITY", false)
            .apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User info (written on register / login)
    // ─────────────────────────────────────────────────────────────────────────

    fun registerAccount(username: String, email: String, password: String) {
        prefs.edit()
            .putString("REGISTERED_NAME", username)
            .putString("REGISTERED_EMAIL", email.lowercase())
            .putString("REGISTERED_PASSWORD", password)
            .apply()
    }

    fun changePassword(newPassword: String) {
        prefs.edit().putString("REGISTERED_PASSWORD", newPassword).apply()
    }

    fun updateUsername(newName: String) {
        prefs.edit()
            .putString("USERNAME", newName)
            .putString("REGISTERED_NAME", newName)
            .apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters
    // ─────────────────────────────────────────────────────────────────────────

    fun getUsername(): String = prefs.getString("USERNAME", "Guest") ?: "Guest"
    fun getEmail(): String   = prefs.getString("EMAIL", "") ?: ""

    fun getRegisteredEmail(): String?    = prefs.getString("EMAIL", null)?.takeIf { it.isNotEmpty() }
    fun getRegisteredName(): String      = prefs.getString("REGISTERED_NAME", "User") ?: "User"
    fun getRegisteredPassword(): String? = prefs.getString("REGISTERED_PASSWORD", null)

    // ─────────────────────────────────────────────────────────────────────────
    // GPS-resolved city  (written by HomeFragment after a successful API call)
    // HomeFragment MUST NOT write this when hasManualCity() == true.
    // ─────────────────────────────────────────────────────────────────────────

    fun getLastCity(): String?    = prefs.getString("LAST_CITY", null)
    fun getLastCountry(): String? = prefs.getString("LAST_COUNTRY", null)

    /** Called by HomeFragment after a GPS-based weather fetch. Skipped when manual override is active. */
    fun setLastCity(city: String) {
        prefs.edit().putString("LAST_CITY", city).apply()
    }

    fun setLastCountry(country: String?) {
        prefs.edit().putString("LAST_COUNTRY", country).apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual city override  (written by ProfileFragment when user picks a city)
    // HomeFragment checks hasManualCity() first — if true it skips GPS entirely
    // and loads weather for getManualCity() instead.
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true when the user has explicitly chosen a city from Profile. */
    fun hasManualCity(): Boolean = prefs.getBoolean("HAS_MANUAL_CITY", false)

    /** The city the user explicitly selected. Null-safe — falls back to getLastCity(). */
    fun getManualCity(): String  = prefs.getString("MANUAL_CITY", null) ?: getLastCity() ?: "Cebu City"

    /**
     * Called by ProfileFragment when the user selects a saved location as primary.
     * Also updates LAST_CITY so Profile and other screens show the right city immediately.
     */
    fun setManualCity(city: String) {
        prefs.edit()
            .putString("MANUAL_CITY", city)
            .putBoolean("HAS_MANUAL_CITY", true)
            .putString("LAST_CITY", city)     // keep LAST_CITY in sync for Profile header
            .putString("LAST_COUNTRY", null)  // country will be resolved after next API call
            .apply()
    }

    /** Call this if the user wants to go back to GPS-based location (e.g. from a future "Use GPS" button). */
    fun clearManualCity() {
        prefs.edit()
            .putString("MANUAL_CITY", null)
            .putBoolean("HAS_MANUAL_CITY", false)
            .apply()
    }
}
