package com.afaq.vpn

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the system splash screen
        installSplashScreen()

        registerPlugin(AfaqVpnPlugin::class.java)
        super.onCreate(savedInstanceState)

        // Custom splash overlay
        val rootView = findViewById<android.view.ViewGroup>(android.R.id.content)
        val splashView = layoutInflater.inflate(R.layout.custom_splash, rootView, false)
        rootView.addView(splashView)

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var dismissed = false
        val dismissSplash = Runnable {
            if (!dismissed) {
                dismissed = true
                splashView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        rootView.removeView(splashView)
                    }
            }
        }

        // Safety timeout of 2000ms
        handler.postDelayed(dismissSplash, 2000)

        // Poll webView progress to dismiss early
        val pollProgress = object : Runnable {
            override fun run() {
                if (dismissed) return
                if (bridge != null && bridge.webView != null) {
                    if (bridge.webView.progress == 100) {
                        dismissSplash.run()
                    } else {
                        handler.postDelayed(this, 100)
                    }
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(pollProgress)
    }
}
