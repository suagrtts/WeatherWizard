package cit.edu.delosreyes.weatherwizardui

object UserSession {
    var name = "Guest"
    var email = ""

    fun clear() {
        name = "Guest"
        email = ""
    }
}