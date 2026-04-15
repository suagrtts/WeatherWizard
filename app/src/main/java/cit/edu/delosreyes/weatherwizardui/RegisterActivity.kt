package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : AppCompatActivity() {

    private lateinit var tvBack: TextView
    private lateinit var editFullName: TextInputEditText
    private lateinit var editEmail: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var editConfirmPassword: TextInputEditText
    private lateinit var cbTerms: CheckBox
    private lateinit var btnRegister: Button
    private lateinit var btnGoogleRegister: Button
    private lateinit var tvLoginLink: TextView

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

        // ── Back arrow ────────────────────────────────────────────────────────
        tvBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // ── Create account ────────────────────────────────────────────────────
        btnRegister.setOnClickListener {
            val name    = editFullName.text.toString().trim()
            val email   = editEmail.text.toString().trim()
            val pass    = editPassword.text.toString().trim()
            val confirm = editConfirmPassword.text.toString().trim()

            if (name.isEmpty()) {
                editFullName.error = "Full name is required"; return@setOnClickListener
            }
            if (email.isEmpty()) {
                editEmail.error = "Email is required"; return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                editEmail.error = "Enter a valid email"; return@setOnClickListener
            }
            if (pass.isEmpty()) {
                editPassword.error = "Password is required"; return@setOnClickListener
            }
            if (pass.length < 6) {
                editPassword.error = "Minimum 6 characters"; return@setOnClickListener
            }
            if (confirm != pass) {
                editConfirmPassword.error = "Passwords do not match"; return@setOnClickListener
            }
            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please accept the Terms & Privacy Policy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── FIX: Save the new user to SharedPreferences ──
            val session = UserSession(this)

            session.registerAccount(name, email, pass)

            session.createLoginSession(name, email)

            Toast.makeText(this, "Account created! Welcome, $name!", Toast.LENGTH_SHORT).show()

            // ── FIX: Attach the data to the NEW intent ──
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.putExtra("WELCOME_MESSAGE", "Welcome to WeatherWizard, $name!")

            startActivity(mainIntent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finishAffinity()
        }

        // ── Google (placeholder) ──────────────────────────────────────────────
        btnGoogleRegister.setOnClickListener {
            Toast.makeText(this, "Google Sign-Up coming soon.", Toast.LENGTH_SHORT).show()
        }

        // ── Back to Login ─────────────────────────────────────────────────────
        tvLoginLink.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}