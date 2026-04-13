package cit.edu.delosreyes.weatherwizardui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class AlertsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_alerts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Settings gear
        view.findViewById<TextView>(R.id.tvAlertsSettings).setOnClickListener {
            Toast.makeText(requireContext(), "Alert settings coming soon.", Toast.LENGTH_SHORT).show()
        }
    }
}
