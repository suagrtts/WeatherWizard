package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var editUsername: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvCreateAccount: TextView
    private lateinit var tvGuestAccess: TextView

    private val adminEmail    = "admin123@gmail.com"
    private val adminPassword = "123456"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editUsername     = findViewById(R.id.editUsername)
        editPassword     = findViewById(R.id.editPassword)
        btnLogin         = findViewById(R.id.btnLogin)
        btnGoogle        = findViewById(R.id.btnGoogle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvCreateAccount  = findViewById(R.id.tvCreateAccount)
        tvGuestAccess    = findViewById(R.id.tvGuestAccess)

        // ── Log In ────────────────────────────────────────────────────────────
        btnLogin.setOnClickListener {
            val email    = editUsername.text.toString().trim()
            val password = editPassword.text.toString().trim()

            if (email.isEmpty()) {
                editUsername.error = "Email is required"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                editUsername.error = "Enter a valid email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                editPassword.error = "Password is required"
                return@setOnClickListener
            }
            if (password.length < 6) {
                editPassword.error = "Minimum 6 characters"
                return@setOnClickListener
            }

            if (email == adminEmail && password == adminPassword) {
                Toast.makeText(this, "Welcome, Admin!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            } else {
                Toast.makeText(this, "Invalid credentials. Please try again.", Toast.LENGTH_SHORT).show()
                editPassword.text?.clear()
            }
        }

        // ── Google (placeholder) ──────────────────────────────────────────────
        btnGoogle.setOnClickListener {
            Toast.makeText(this, "Google Sign-In coming soon.", Toast.LENGTH_SHORT).show()
        }

        // ── Forgot password ───────────────────────────────────────────────────
        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Password reset coming soon.", Toast.LENGTH_SHORT).show()
        }

        // ── Go to Register ────────────────────────────────────────────────────
        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // ── Guest access ──────────────────────────────────────────────────────
        tvGuestAccess.setOnClickListener {
            Toast.makeText(this, "Continuing as Guest", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }
}
