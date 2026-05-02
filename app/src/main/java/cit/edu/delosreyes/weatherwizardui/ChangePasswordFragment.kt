package cit.edu.delosreyes.weatherwizardui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

/**
 * ChangePasswordFragment — verifies the current password against SQLite,
 * then updates both SQLite and the SharedPreferences cache.
 *
 * IDs used (all present in fragment_change_password.xml):
 *   tvBack  editCurrentPassword  editNewPassword  editConfirmNewPassword  btnSavePassword
 */
class ChangePasswordFragment : Fragment() {

    private val db      by lazy { WeatherDatabase.getInstance(requireContext()) }
    private val session by lazy { UserSession(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_change_password, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editCurrent = view.findViewById<TextInputEditText>(R.id.editCurrentPassword)
        val editNew     = view.findViewById<TextInputEditText>(R.id.editNewPassword)
        val editConfirm = view.findViewById<TextInputEditText>(R.id.editConfirmNewPassword)

        view.findViewById<TextView>(R.id.tvBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btnSavePassword).setOnClickListener {
            val email   = session.getEmail()
            val current = editCurrent.text.toString()
            val new     = editNew.text.toString()
            val confirm = editConfirm.text.toString()

            if (email.isEmpty()) {
                AppToast.show(requireContext(), "No account session found.")
                return@setOnClickListener
            }

            // ── Verify current password via SQLite ─────────────────────────────
            if (db.authenticateUser(email, current) == null) {
                editCurrent.error = "Incorrect current password"
                return@setOnClickListener
            }
            if (new.length < 6) {
                editNew.error = "Minimum 6 characters"
                return@setOnClickListener
            }
            if (new != confirm) {
                editConfirm.error = "Passwords do not match"
                return@setOnClickListener
            }

            // ── Update SQLite and SharedPreferences cache ─────────────────────
            db.updatePassword(email, new)
            session.changePassword(new)

            AppToast.show(requireContext(), "Password changed successfully.")
            parentFragmentManager.popBackStack()
        }
    }
}
