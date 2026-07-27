package com.afaq.vpn

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object AfaqRegisterClient {
    private const val BACKEND_URL = "https://afaq-vpn-api.duckdns.org/v1/register"
    private const val TIMEOUT_MS = 10000

    interface RegisterCallback {
        fun onSuccess(existing: Boolean)
        fun onFailure(errorMessage: String, isRecoverableError: Boolean, retryAfterSeconds: Int)
    }

    fun register(
        context: Context,
        deviceId: String,
        publicKey: String,
        integrityToken: String?,
        callback: RegisterCallback
    ) {
        // Run in a background thread to avoid NetworkOnMainThreadException
        Thread {
            try {
                val url = URL(BACKEND_URL)
                val conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.doInput = true
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().apply {
                    put("device_id", deviceId)
                    put("public_key", publicKey)
                    if (!integrityToken.isNullOrBlank()) {
                        put("integrity_token", integrityToken)
                    }
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpsURLConnection.HTTP_OK || responseCode == HttpsURLConnection.HTTP_CREATED) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val responseStr = reader.use { it.readText() }
                    val json = JSONObject(responseStr)

                    val address = json.optString("address").trim()
                    val dns = json.optString("dns").trim()
                    val serverPublicKey = json.optString("server_public_key").trim()
                    val presharedKey = json.optString("preshared_key").trim()
                    val endpoint = json.optString("endpoint").trim()
                    val allowedIps = json.optString("allowed_ips").trim()
                    val persistentKeepalive = json.optInt("persistent_keepalive", 25)
                    val existing = json.optBoolean("existing", false)

                    // Verify requirements:
                    // If existing is true, backend might not return preshared_key again.
                    // We must securely preserve the original preshared key.
                    var finalPresharedKey = presharedKey
                    if (finalPresharedKey.isBlank()) {
                        // Retrieve preserved local preshared key
                        val preservedKey = AfaqSecureStorage.getString(context, "wg_preshared_key")
                        if (!preservedKey.isNullOrBlank()) {
                            finalPresharedKey = preservedKey
                        } else if (existing) {
                            // Local credentials are lost and server didn't send a preshared key
                            callback.onFailure(
                                "Secure credentials are incomplete. Please register this device again.",
                                true,
                                0
                            )
                            return@Thread
                        }
                    }

                    if (address.isBlank() || dns.isBlank() || serverPublicKey.isBlank() || endpoint.isBlank() || allowedIps.isBlank()) {
                        callback.onFailure("Incomplete WireGuard configuration from server.", false, 0)
                        return@Thread
                    }

                    // Securely save dynamic configuration
                    AfaqSecureStorage.saveString(context, "wg_address", address)
                    AfaqSecureStorage.saveString(context, "wg_dns", dns)
                    AfaqSecureStorage.saveString(context, "wg_server_public_key", serverPublicKey)
                    AfaqSecureStorage.saveString(context, "wg_preshared_key", finalPresharedKey)
                    AfaqSecureStorage.saveString(context, "wg_endpoint", endpoint)
                    AfaqSecureStorage.saveString(context, "wg_allowed_ips", allowedIps)
                    AfaqSecureStorage.saveString(context, "wg_persistent_keepalive", persistentKeepalive.toString())
                    AfaqSecureStorage.saveBoolean(context, "is_registered", true)

                    callback.onSuccess(existing)
                } else if (responseCode == 429) {
                    val retryAfterHeader = conn.getHeaderField("Retry-After")
                    val retryAfterSeconds = retryAfterHeader?.toIntOrNull() ?: 10 // Short local cooldown default
                    callback.onFailure("TOO_MANY_REQUESTS", false, retryAfterSeconds)
                } else {
                    // Do not leak raw HTML, stack traces, or response bodies. Return a clean user-friendly status message.
                    callback.onFailure("Registration failed with status $responseCode.", false, 5)
                }
            } catch (e: Exception) {
                callback.onFailure("Network error. Please check your connection and try again.", false, 5)
            }
        }.start()
    }
}
