package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Add saved location
        view.findViewById<TextView>(R.id.tvAddLocation).setOnClickListener {
            Toast.makeText(requireContext(), "Add location coming soon.", Toast.LENGTH_SHORT).show()
        }

        // Temperature unit row
        view.findViewById<LinearLayout>(R.id.rowTempUnit).setOnClickListener {
            val tv = view.findViewById<TextView>(R.id.tvTempUnitValue)
            tv.text = if (tv.text == "Celsius") "Fahrenheit" else "Celsius"
            Toast.makeText(requireContext(), "Unit: ${tv.text}", Toast.LENGTH_SHORT).show()
        }

        // Time format row
        view.findViewById<LinearLayout>(R.id.rowTimeFormat).setOnClickListener {
            val tv = view.findViewById<TextView>(R.id.tvTimeFormatValue)
            tv.text = if (tv.text == "12-hour") "24-hour" else "12-hour"
        }

        // Activity type row
        view.findViewById<LinearLayout>(R.id.rowActivity).setOnClickListener {
            Toast.makeText(requireContext(), "Activity type coming soon.", Toast.LENGTH_SHORT).show()
        }

        // Change password row
        view.findViewById<LinearLayout>(R.id.rowChangePassword).setOnClickListener {
            Toast.makeText(requireContext(), "Change password coming soon.", Toast.LENGTH_SHORT).show()
        }

        // Log out row — confirmation dialog
        view.findViewById<LinearLayout>(R.id.rowLogout).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
