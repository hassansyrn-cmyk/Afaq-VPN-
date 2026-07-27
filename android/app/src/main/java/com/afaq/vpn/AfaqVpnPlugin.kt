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
    fun getProvisioningStatus(call: PluginCall) {
        val result = JSObject()
        val isRegistered = AfaqSecureStorage.getBoolean(context, "is_registered", false)
        val hasIdentity = AfaqIdentityManager.hasIdentity(context)

        result.put("isRegistered", isRegistered)
        result.put("hasIdentity", hasIdentity)
        result.put("legacyFallbackEnabled", BuildConfig.ENABLE_LEGACY_DEBUG_FALLBACK)

        if (isRegistered) {
            val address = AfaqSecureStorage.getString(context, "wg_address") ?: ""
            val endpoint = AfaqSecureStorage.getString(context, "wg_endpoint") ?: ""
            result.put("address", address)
            result.put("endpoint", endpoint)
        }
        call.resolve(result)
    }

    @PluginMethod
    fun provisionDevice(call: PluginCall) {
        val deviceId = AfaqIdentityManager.getOrCreateInstallationId(context)
        val keyPair = AfaqIdentityManager.getOrCreateWireGuardKeyPair(context)
        val publicKey = keyPair.second

        val integrityManager = AfaqIntegrityManager(context)
        val isDebug = BuildConfig.DEBUG
        val projectNumber = AfaqIntegrityConfig.GOOGLE_CLOUD_PROJECT_NUMBER

        if (!isDebug && projectNumber == 0L) {
            call.reject("Real Google Cloud Project Number is not configured. Play Integrity is required in production release builds.")
            return
        }

        if (projectNumber == 0L) {
            // Debug build bypass of Google Play Integrity token retrieval
            AfaqRegisterClient.register(
                context,
                deviceId,
                publicKey,
                null,
                object : AfaqRegisterClient.RegisterCallback {
                    override fun onSuccess(existing: Boolean) {
                        val res = JSObject()
                        res.put("state", "registered")
                        res.put("existing", existing)
                        call.resolve(res)
                    }

                    override fun onFailure(errorMessage: String, isRecoverableError: Boolean) {
                        val res = JSObject()
                        res.put("state", "failed")
                        res.put("error", errorMessage)
                        res.put("isRecoverableError", isRecoverableError)
                        call.resolve(res)
                    }
                }
            )
        } else {
            // Standard Play Integrity flow
            integrityManager.requestToken(deviceId, publicKey, object : AfaqIntegrityManager.TokenCallback {
                override fun onSuccess(token: String) {
                    AfaqRegisterClient.register(
                        context,
                        deviceId,
                        publicKey,
                        token,
                        object : AfaqRegisterClient.RegisterCallback {
                            override fun onSuccess(existing: Boolean) {
                                val res = JSObject()
                                res.put("state", "registered")
                                res.put("existing", existing)
                                call.resolve(res)
                            }

                            override fun onFailure(errorMessage: String, isRecoverableError: Boolean) {
                                val res = JSObject()
                                res.put("state", "failed")
                                res.put("error", errorMessage)
                                res.put("isRecoverableError", isRecoverableError)
                                call.resolve(res)
                            }
                        }
                    )
                }

                override fun onFailure(error: Exception) {
                    val res = JSObject()
                    res.put("state", "failed")
                    res.put("error", "Play Integrity failed: ${error.message}")
                    res.put("isRecoverableError", false)
                    call.resolve(res)
                }
            })
        }
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        if (VpnService.prepare(context) != null) {
            call.reject("VPN permission has not been granted")
            return
        }

        val isRegistered = AfaqSecureStorage.getBoolean(context, "is_registered", false)

        val privateKey: String
        val address: String
        val dns: String
        val publicKey: String
        val presharedKey: String
        val endpoint: String
        val allowedIps: String
        val persistentKeepalive: Int

        if (isRegistered) {
            // Use dynamically provisioned WireGuard credentials
            val localKeyPair = AfaqIdentityManager.getOrCreateWireGuardKeyPair(context)
            privateKey = localKeyPair.first
            address = AfaqSecureStorage.getString(context, "wg_address")?.trim().orEmpty()
            dns = AfaqSecureStorage.getString(context, "wg_dns")?.trim().orEmpty()
            publicKey = AfaqSecureStorage.getString(context, "wg_server_public_key")?.trim().orEmpty()
            presharedKey = AfaqSecureStorage.getString(context, "wg_preshared_key")?.trim().orEmpty()
            endpoint = AfaqSecureStorage.getString(context, "wg_endpoint")?.trim().orEmpty()
            allowedIps = AfaqSecureStorage.getString(context, "wg_allowed_ips")?.trim().orEmpty()
            persistentKeepalive = AfaqSecureStorage.getString(context, "wg_persistent_keepalive")?.toIntOrNull() ?: 25

            if (privateKey.isBlank() || address.isBlank() || dns.isBlank() || publicKey.isBlank() || endpoint.isBlank() || allowedIps.isBlank()) {
                call.reject("Secure credentials are incomplete. Please register this device again.")
                return
            }
        } else {
            // Try to fall back to legacy credentials in internal debug builds only
            val isDebug = BuildConfig.DEBUG
            val legacyEnabled = BuildConfig.ENABLE_LEGACY_DEBUG_FALLBACK

            if (!isDebug || !legacyEnabled) {
                call.reject("Secure credentials are incomplete. Please register this device again.")
                return
            }

            val config = call.getObject("config")
            if (config == null) {
                call.reject("Missing WireGuard configuration")
                return
            }

            privateKey = config.getString("privateKey")?.trim().orEmpty()
            address = config.getString("address")?.trim().orEmpty()
            dns = config.getString("dns")?.trim().orEmpty()
            publicKey = config.getString("publicKey")?.trim().orEmpty()
            presharedKey = config.getString("presharedKey")?.trim().orEmpty()
            endpoint = config.getString("endpoint")?.trim().orEmpty()
            allowedIps = config.getString("allowedIps")?.trim().orEmpty()
            persistentKeepalive = config.getInteger("persistentKeepalive") ?: 25

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
        AfaqVpnService.instance?.updateTrafficStats()
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
