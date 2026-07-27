package com.afaq.vpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "AfaqVpn")
class AfaqVpnPlugin : Plugin() {

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AfaqVpnService.ACTION_STATE) {
                return
            }

            val result = JSObject()
            result.put(
                "state",
                intent.getStringExtra(AfaqVpnService.EXTRA_STATE) ?: "disconnected"
            )

            intent.getStringExtra("message")?.let { message ->
                result.put("error", message)
            }

            if (AfaqVpnService.connectedAt > 0L) {
                result.put("connectedAt", AfaqVpnService.connectedAt)
            }

            notifyListeners("statusChanged", result)
        }
    }

    override fun load() {
        super.load()

        val filter = IntentFilter(AfaqVpnService.ACTION_STATE)

        ContextCompat.registerReceiver(
            context,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @PluginMethod
    fun prepareVpn(call: PluginCall) {
        val permissionIntent = VpnService.prepare(context)

        if (permissionIntent == null) {
            val result = JSObject()
            result.put("granted", true)
            call.resolve(result)
            return
        }

        startActivityForResult(
            call,
            permissionIntent,
            "vpnPermissionResult"
        )
    }

    @ActivityCallback
    private fun vpnPermissionResult(
        call: PluginCall,
        result: ActivityResult
    ) {
        val response = JSObject()
        response.put("granted", result.resultCode == Activity.RESULT_OK)
        call.resolve(response)
    }

    @PluginMethod
    fun isVpnPermissionGranted(call: PluginCall) {
        val result = JSObject()
        result.put("granted", VpnService.prepare(context) == null)
        call.resolve(result)
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        if (VpnService.prepare(context) != null) {
            call.reject("VPN permission has not been granted")
            return
        }

        val config = call.getObject("config")

        if (config == null) {
            call.reject("Missing WireGuard configuration")
            return
        }

        val privateKey = config.getString("privateKey")?.trim().orEmpty()
        val address = config.getString("address")?.trim().orEmpty()
        val dns = config.getString("dns")?.trim().orEmpty()
        val publicKey = config.getString("publicKey")?.trim().orEmpty()
        val presharedKey = config.getString("presharedKey")?.trim().orEmpty()
        val endpoint = config.getString("endpoint")?.trim().orEmpty()
        val allowedIps = config.getString("allowedIps")?.trim().orEmpty()
        val persistentKeepalive =
            config.getInteger("persistentKeepalive") ?: 25

        if (
            privateKey.isBlank() ||
            address.isBlank() ||
            dns.isBlank() ||
            publicKey.isBlank() ||
            endpoint.isBlank() ||
            allowedIps.isBlank()
        ) {
            call.reject("Incomplete WireGuard configuration")
            return
        }

        if (persistentKeepalive !in 0..65535) {
            call.reject("Invalid PersistentKeepalive value")
            return
        }

        val wireGuardConfig = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("Address = $address")
            appendLine("DNS = $dns")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $publicKey")

            if (presharedKey.isNotBlank()) {
                appendLine("PresharedKey = $presharedKey")
            }

            appendLine("Endpoint = $endpoint")
            appendLine("AllowedIPs = $allowedIps")
            appendLine("PersistentKeepalive = $persistentKeepalive")
        }

        val serviceIntent = Intent(
            context,
            AfaqVpnService::class.java
        ).apply {
            action = AfaqVpnService.ACTION_CONNECT
            putExtra(AfaqVpnService.EXTRA_CONFIG, wireGuardConfig)
        }

        try {
            ContextCompat.startForegroundService(
                context,
                serviceIntent
            )

            val result = JSObject()
            result.put("state", "connecting")
            call.resolve(result)
        } catch (exception: Exception) {
            call.reject(
                "Unable to start the VPN service",
                exception
            )
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        val serviceIntent = Intent(
            context,
            AfaqVpnService::class.java
        ).apply {
            action = AfaqVpnService.ACTION_DISCONNECT
        }

        try {
            context.startService(serviceIntent)

            val result = JSObject()
            result.put("state", "disconnecting")
            call.resolve(result)
        } catch (exception: Exception) {
            call.reject(
                "Unable to stop the VPN service",
                exception
            )
        }
    }

    @PluginMethod
    fun getConnectionStatus(call: PluginCall) {
        val result = JSObject()
        result.put("state", AfaqVpnService.state)

        if (AfaqVpnService.connectedAt > 0L) {
            result.put(
                "connectedAt",
                AfaqVpnService.connectedAt
            )
        }

        call.resolve(result)
    }

    @PluginMethod
    fun getTrafficStats(call: PluginCall) {
        val result = JSObject()
        result.put(
            "receivedBytes",
            AfaqVpnService.rxBytes
        )
        result.put(
            "transmittedBytes",
            AfaqVpnService.txBytes
        )
        call.resolve(result)
    }

    @PluginMethod
    fun openVpnSettings(call: PluginCall) {
        try {
            val settingsIntent = Intent(
                Settings.ACTION_VPN_SETTINGS
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(settingsIntent)
            call.resolve()
        } catch (exception: Exception) {
            call.reject(
                "Unable to open Android VPN settings",
                exception
            )
        }
    }

    override fun handleOnDestroy() {
        try {
            context.unregisterReceiver(stateReceiver)
        } catch (_: IllegalArgumentException) {
        }

        super.handleOnDestroy()
    }
}
