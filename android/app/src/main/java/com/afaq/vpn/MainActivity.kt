package com.afaq.vpn

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(AfaqVpnPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
