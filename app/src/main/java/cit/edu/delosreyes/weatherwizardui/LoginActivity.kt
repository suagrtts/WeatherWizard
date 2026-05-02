package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * LoginActivity — authenticates users against SQLite WeatherDatabase.
 *
 * Auth flow:
 *   1. Email+password validated locally (format checks)
 *   2. db.authenticateUser() queries the users table
 *   3. On success, UserSession.createLoginSession() + UserSession.registerAccount() sync
 *   4. Google sign-in: registers the Google user in SQLite on first use
 */
class LoginActivity : AppCompatActivity() {

    private val WEB_CLIENT_ID =
        "373644796614-bgpg2oko7h1sb06de2ijd29qtgdufjf5.apps.googleusercontent.com"

    private lateinit var editUsername    : TextInputEditText
    private lateinit var editPassword    : TextInputEditText
    private lateinit var btnLogin        : Button
    private lateinit var btnGoogle       : Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvCreateAccount : TextView
    private lateinit var tvGuestAccess   : TextView

    private val db      by lazy { WeatherDatabase.getInstance(this) }
    private val session by lazy { UserSession(this) }

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

        // ── Email + password login ─────────────────────────────────────────────
        btnLogin.setOnClickListener {
            val email = editUsername.text.toString().trim()
            val pass  = editPassword.text.toString().trim()

            if (email.isEmpty())                                    { editUsername.error = "Email is required";   return@setOnClickListener }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())  { editUsername.error = "Enter a valid email"; return@setOnClickListener }
            if (pass.isEmpty())                                     { editPassword.error = "Password is required";return@setOnClickListener }
            if (pass.length < 6)                                    { editPassword.error = "Minimum 6 characters";return@setOnClickListener }

            val displayName = db.authenticateUser(email, pass)
            if (displayName != null) {
                // Sync session + legacy SharedPreferences
                session.createLoginSession(displayName, email)
                session.registerAccount(displayName, email, pass)
                AppToast.show(this, "Welcome back, $displayName!")
                openMain("Welcome back, $displayName!")
            } else {
                AppToast.show(this, "Invalid email or password. Please try again.")
                editPassword.text?.clear()
            }
        }

        // ── Google sign-in ────────────────────────────────────────────────────
        btnGoogle.setOnClickListener {
            lifecycleScope.launch { triggerGoogleSignIn(credentialManager) }
        }

        // ── Forgot password ───────────────────────────────────────────────────
        tvForgotPassword.setOnClickListener {
            AppToast.show(this, "Password reset coming soon.")
        }

        // ── Go to Register ────────────────────────────────────────────────────
        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // ── Guest access ──────────────────────────────────────────────────────
        tvGuestAccess.setOnClickListener {
            AppToast.show(this, "Continuing as Guest")
            openMain("Welcome, Guest!")
        }
    }

    private fun openMain(message: String) {
        startActivity(
            Intent(this, MainActivity::class.java).putExtra("WELCOME_MESSAGE", message)
        )
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private suspend fun triggerGoogleSignIn(credentialManager: CredentialManager) {
        val nonce = java.util.UUID.randomUUID().toString()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result     = credentialManager.getCredential(request = request, context = this)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred  = GoogleIdTokenCredential.createFrom(credential.data)
                val email       = googleCred.id
                val displayName = googleCred.displayName ?: "Google User"

                // Register in SQLite on first Google login
                if (!db.userExists(email)) {
                    db.registerUser(displayName, email, java.util.UUID.randomUUID().toString())
                }

                session.createLoginSession(displayName, email)
                AppToast.show(this, "Welcome, $displayName!")
                openMain("Welcome, $displayName!")
            } else {
                AppToast.show(this, "Unexpected credential type.")
            }
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Credential error: ${e.message}")
            AppToast.show(this, "Google Sign-in failed: ${e.message}", longDuration = true)
        } catch (e: Exception) {
            Log.e("Auth", "Sign-in error: ${e.message}")
            AppToast.show(this, "An error occurred during sign-in.")
        }
    }
}
