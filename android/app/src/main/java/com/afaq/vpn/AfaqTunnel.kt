package com.afaq.vpn

import com.wireguard.android.backend.Tunnel

class AfaqTunnel(private val onChanged: (Tunnel.State) -> Unit) : Tunnel {
    override fun getName() = "afaq"
    override fun onStateChange(newState: Tunnel.State) = onChanged(newState)
}
