package cit.edu.delosreyes.weatherwizardui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

class ChangePasswordFragment : Fragment() {
    // Tracks the current password in-session (starts as the hardcoded admin password)
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
            val session = UserSession(requireContext())
            var currentPassword = session.getRegisteredPassword()

            val current = editCurrent.text.toString()
            val new     = editNew.text.toString()
            val confirm = editConfirm.text.toString()

            // 1. Check current password
            if (current != currentPassword) {
                editCurrent.error = "Incorrect current password"
                return@setOnClickListener
            }
            // 2. Validate new password
            if (new.length < 6) {
                editNew.error = "Minimum 6 characters"
                return@setOnClickListener
            }
            // 3. Check new == confirm
            if (new != confirm) {
                editConfirm.error = "Passwords do not match"
                return@setOnClickListener
            }
            // 4. Save and go back
            currentPassword = new
            session.changePassword(new)
            Toast.makeText(requireContext(), "Password changed successfully.", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }
}