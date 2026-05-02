package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/**
 * RegisterActivity — creates a new account in SQLite and starts a session.
 *
 * On success:
 *   1. db.registerUser() inserts into the users table (UNIQUE email constraint)
 *   2. session.registerAccount() caches credentials in SharedPreferences
 *   3. session.createLoginSession() sets IS_LOGGED_IN = true
 *   4. Navigates to MainActivity
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var tvBack             : TextView
    private lateinit var editFullName       : TextInputEditText
    private lateinit var editEmail          : TextInputEditText
    private lateinit var editPassword       : TextInputEditText
    private lateinit var editConfirmPassword: TextInputEditText
    private lateinit var cbTerms            : CheckBox
    private lateinit var btnRegister        : Button
    private lateinit var btnGoogleRegister  : Button
    private lateinit var tvLoginLink        : TextView

    private val db      by lazy { WeatherDatabase.getInstance(this) }
    private val session by lazy { UserSession(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        tvBack              = findViewById(R.id.tvBack)
        editFullName        = findViewById(R.id.editFullName)
        editEmail           = findViewById(R.id.editEmail)
        editPassword        = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        cbTerms             = findViewById(R.id.cbTerms)
        btnRegister         = findViewById(R.id.btnRegister)
        btnGoogleRegister   = findViewById(R.id.btnGoogleRegister)
        tvLoginLink         = findViewById(R.id.tvLoginLink)

        tvBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        btnRegister.setOnClickListener {
            val name    = editFullName.text.toString().trim()
            val email   = editEmail.text.toString().trim().lowercase()
            val pass    = editPassword.text.toString().trim()
            val confirm = editConfirmPassword.text.toString().trim()

            // ── Validation ───────────────────────────────────────────────────
            if (name.isEmpty())                                      { editFullName.error  = "Full name is required";      return@setOnClickListener }
            if (email.isEmpty())                                     { editEmail.error     = "Email is required";          return@setOnClickListener }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())   { editEmail.error     = "Enter a valid email";        return@setOnClickListener }
            if (pass.isEmpty())                                      { editPassword.error  = "Password is required";       return@setOnClickListener }
            if (pass.length < 6)                                     { editPassword.error  = "Minimum 6 characters";       return@setOnClickListener }
            if (confirm != pass)                                     { editConfirmPassword.error = "Passwords do not match"; return@setOnClickListener }
            if (!cbTerms.isChecked) {
                AppToast.show(this, "Please accept the Terms & Privacy Policy")
                return@setOnClickListener
            }

            // ── SQLite insert ─────────────────────────────────────────────────
            val rowId = db.registerUser(name, email, pass)
            if (rowId == -1L) {
                editEmail.error = "An account with this email already exists"
                return@setOnClickListener
            }

            // ── Session sync ──────────────────────────────────────────────────
            session.registerAccount(name, email, pass)
            session.createLoginSession(name, email)

            AppToast.show(this, "Account created! Welcome, $name!")

            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("WELCOME_MESSAGE", "Welcome to WeatherWizard, $name!")
            )
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finishAffinity()
        }

        btnGoogleRegister.setOnClickListener {
            AppToast.show(this, "Google Sign-Up coming soon.")
        }

        tvLoginLink.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
