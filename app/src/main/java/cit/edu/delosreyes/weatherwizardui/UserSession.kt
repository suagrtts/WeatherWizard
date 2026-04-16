package cit.edu.delosreyes.weatherwizardui

import android.content.Context
import android.content.SharedPreferences

class UserSession(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("WeatherWizardPrefs", Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()

    fun createLoginSession(username: String, email: String) {
        editor.putBoolean("IS_LOGGED_IN", true)
        editor.putString("USERNAME", username)
        editor.putString("EMAIL", email)
        editor.apply()
    }

    fun registerAccount(username: String, email: String, password: String) {
        editor.putString("REGISTERED_NAME", username)
        editor.putString("REGISTERED_EMAIL", email)
        editor.putString("REGISTERED_PASSWORD", password)
        editor.apply()
    }

    fun changePassword(newPassword: String) {
        editor.putString("REGISTERED_PASSWORD", newPassword)
        editor.apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("IS_LOGGED_IN", false)
    }

    fun getUsername(): String? {
        return prefs.getString("USERNAME", "Guest")
    }

    fun updateUsername(newName: String) {
        editor.putString("USERNAME", newName)
        editor.apply()
    }

    fun logoutUser() {
        editor.putBoolean("IS_LOGGED_IN", false)
        editor.putString("USERNAME", "Guest")
        editor.putString("EMAIL", "No email provided")
        editor.apply()
    }

    // These act as our "Database Read" during login
    fun getRegisteredEmail(): String? {
        return prefs.getString("REGISTERED_EMAIL", null)
    }

    fun getRegisteredPassword(): String? {
        return prefs.getString("REGISTERED_PASSWORD", null)
    }

    fun getRegisteredName(): String? {
        return prefs.getString("REGISTERED_NAME", "User")
    }
}