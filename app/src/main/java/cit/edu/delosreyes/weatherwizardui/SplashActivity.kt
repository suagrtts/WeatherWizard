package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo    = findViewById<ImageView>(R.id.ivSplashLogo)
        val appName = findViewById<TextView>(R.id.tvSplashName)
        val tagline = findViewById<TextView>(R.id.tvSplashTagline)

        logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        appName.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        tagline.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2200)
    }
}
