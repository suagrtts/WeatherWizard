package cit.edu.delosreyes.weatherwizardui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // "Details →" button opens ForecastFragment
        view.findViewById<TextView>(R.id.tvSeeForecast).setOnClickListener {
            (activity as MainActivity).loadFragment(
                ForecastFragment(), addToBackStack = true
            )
        }

        // Notification bell
        view.findViewById<TextView>(R.id.tvNotifBtn).setOnClickListener {
            (activity as MainActivity).apply {
                loadFragment(AlertsFragment())
                findViewById<BottomNavigationView>(
                    R.id.bottomNav
                ).selectedItemId = R.id.nav_alerts
            }
        }
    }
}
