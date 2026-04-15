package cit.edu.delosreyes.weatherwizardui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val session = UserSession(this)

        val logo    = findViewById<ImageView>(R.id.ivSplashLogo)
        val appName = findViewById<TextView>(R.id.tvSplashName)
        val tagline = findViewById<TextView>(R.id.tvSplashTagline)

        logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        appName.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        tagline.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))

        Handler(Looper.getMainLooper()).postDelayed({
            if(session.isLoggedIn()) {
                val intent = Intent(this, MainActivity:: class.java)
                startActivity(intent)
            }else {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
            finish()
        }, 2200)
    }
}
