package cit.edu.delosreyes.weatherwizardui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav    : BottomNavigationView
    private lateinit var bottomAppBar : BottomAppBar
    private lateinit var fabForecast  : FloatingActionButton
    private val session by lazy { UserSession(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav    = findViewById(R.id.bottomNav)
        bottomAppBar = findViewById(R.id.bottomAppBar)
        fabForecast  = findViewById(R.id.fabForecast)

        // Default screen on launch
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }

        // Sync bottom nav & show/hide bar when back stack changes
        supportFragmentManager.addOnBackStackChangedListener {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            val isSubPage = current is ChangePasswordFragment
            setNavVisible(!isSubPage)
            if (!isSubPage) {
                bottomNav.selectedItemId = when (current) {
                    is HomeFragment     -> R.id.nav_home
                    is CalendarFragment -> R.id.nav_calendar
                    is ForecastFragment -> R.id.nav_forecast
                    is AlertsFragment   -> R.id.nav_alerts
                    is ProfileFragment  -> R.id.nav_profile
                    else                -> bottomNav.selectedItemId
                }
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> { loadFragment(HomeFragment());     true }
                R.id.nav_calendar -> { loadFragment(CalendarFragment()); true }
                R.id.nav_alerts   -> { loadFragment(AlertsFragment());   true }
                R.id.nav_profile  -> { loadFragment(ProfileFragment());  true }
                R.id.nav_forecast -> {
                    val city = session.getLastCity() ?: "Cebu City"
                    loadFragment(ForecastFragment.newInstance(city))
                    true
                }
                else -> false
            }
        }

        fabForecast.setOnClickListener {
            val city = session.getLastCity() ?: "Cebu City"
            loadFragment(ForecastFragment.newInstance(city))
            bottomNav.selectedItemId = R.id.nav_forecast
        }
    }

    fun loadFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val isSubPage = fragment is ChangePasswordFragment
        setNavVisible(!isSubPage)

        val tx = supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }

    private fun setNavVisible(visible: Boolean) {
        val vis = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        bottomNav.visibility    = vis
        bottomAppBar.visibility = vis
        fabForecast.visibility  = vis
    }
}
