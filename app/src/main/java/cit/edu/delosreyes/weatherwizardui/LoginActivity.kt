package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException

class LoginActivity : AppCompatActivity() {
    private val WEB_CLIENT_ID = "373644796614-bgpg2oko7h1sb06de2ijd29qtgdufjf5.apps.googleusercontent.com"
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

        val credentialManager = CredentialManager.create(this)

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

            val session = UserSession(this)

            // Get the saved account from SharedPreferences
            val savedEmail = session.getRegisteredEmail()
            val savedPassword = session.getRegisteredPassword()
            val savedName = session.getRegisteredName() ?: "User"

            // Check credentials FIRST
            if (email == adminEmail && password == adminPassword) {
                session.createLoginSession("Admin User", email)
                Toast.makeText(this, "Welcome, Admin!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("WELCOME_MESSAGE", "Welcome back, Admin User!")
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            } else if (savedEmail != null && email == savedEmail && password == savedPassword) {
                session.createLoginSession(savedName, email)
                Toast.makeText(this, "Welcome back, $savedName!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("WELCOME_MESSAGE", "Welcome back, $savedName!")
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            } else {
                Toast.makeText(this, "Invalid credentials. Please try again.", Toast.LENGTH_SHORT).show()
                editPassword.text?.clear()
            }
        }

        // ── Google ────────────────────────────────────────────────────────────
        btnGoogle.setOnClickListener {
            lifecycleScope.launch {
                triggerGoogleSignIn(credentialManager)
            }
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

    private suspend fun triggerGoogleSignIn(credentialManager: CredentialManager) {
        val nonce = java.util.UUID.randomUUID().toString()

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .setNonce(nonce)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                request = request,
                context = this@LoginActivity
            )

            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                val email = googleIdTokenCredential.id
                // Use the Google name, or default to "Google User" if it's null
                val displayName = googleIdTokenCredential.displayName ?: "Google User"

                Log.d("Auth", "Google Sign-in Success: $email, $displayName")

                // ── FIX: Correctly instantiate and save Google data to SharedPreferences ──
                val session = UserSession(this@LoginActivity)
                session.createLoginSession(displayName, email)

                Toast.makeText(this, "Welcome, $displayName!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                // Pass data to next screen (Assignment Requirement)
                intent.putExtra("WELCOME_MESSAGE", "Welcome back, $displayName!")
                startActivity(intent)
                finish()

            } else {
                Log.e("Auth", "Unexpected type of credential: ${credential.type}")
                Toast.makeText(this, "Unexpected login error", Toast.LENGTH_SHORT).show()
            }

        } catch (e: GetCredentialException) {
            Log.e("Auth", "Credential Manager Error: ${e.message}, Type: ${e.type}")
            Toast.makeText(this, "Google Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("Auth", "General Error: ${e.message}")
            Toast.makeText(this, "An error occurred during sign-in", Toast.LENGTH_SHORT).show()
        }
    }
}