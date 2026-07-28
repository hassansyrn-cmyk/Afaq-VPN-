package com.afaq.vpn

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import java.security.MessageDigest

object AfaqIntegrityConfig {
    // Confirmed production Google Cloud project number linked with Play Console
    const val GOOGLE_CLOUD_PROJECT_NUMBER: Long = 12432926218L
}

class AfaqIntegrityManager(private val context: Context) {
    private val integrityManager: StandardIntegrityManager by lazy {
        IntegrityManagerFactory.createStandard(context.applicationContext)
    }

    private var tokenProvider: StandardIntegrityTokenProvider? = null

    interface TokenCallback {
        fun onSuccess(token: String)
        fun onFailure(error: Exception)
    }

    fun prepare(onComplete: (Boolean) -> Unit) {
        val projectNumber = AfaqIntegrityConfig.GOOGLE_CLOUD_PROJECT_NUMBER
        if (projectNumber == 0L) {
            onComplete(false)
            return
        }

        try {
            integrityManager.prepareIntegrityToken(
                PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(projectNumber)
                    .build()
            ).addOnSuccessListener { provider ->
                tokenProvider = provider
                onComplete(true)
            }.addOnFailureListener {
                onComplete(false)
            }
        } catch (e: Exception) {
            onComplete(false)
        }
    }

    fun requestToken(deviceId: String, publicKey: String, callback: TokenCallback) {
        // Enforce that release builds must not bypass or proceed without real config
        val projectNumber = AfaqIntegrityConfig.GOOGLE_CLOUD_PROJECT_NUMBER
        if (projectNumber == 0L) {
            callback.onFailure(IllegalStateException("Google Cloud Project Number is not configured. Real Play Integrity values are required."))
            return
        }

        val requestHash = computeRequestHash(deviceId, publicKey)
        val provider = tokenProvider

        if (provider == null) {
            // Try to prepare again and then request
            prepare { success ->
                if (success && tokenProvider != null) {
                    executeTokenRequest(tokenProvider!!, requestHash, callback)
                } else {
                    callback.onFailure(IllegalStateException("StandardIntegrityTokenProvider is not initialized."))
                }
            }
        } else {
            executeTokenRequest(provider, requestHash, callback)
        }
    }

    private fun executeTokenRequest(
        provider: StandardIntegrityTokenProvider,
        requestHash: String,
        callback: TokenCallback
    ) {
        try {
            provider.request(
                StandardIntegrityTokenRequest.builder()
                    .setRequestHash(requestHash)
                    .build()
            ).addOnSuccessListener { response ->
                callback.onSuccess(response.token())
            }.addOnFailureListener { e ->
                callback.onFailure(e)
            }
        } catch (e: Exception) {
            callback.onFailure(e)
        }
    }

    fun computeRequestHash(deviceId: String, publicKey: String): String {
        val canonical = "device_id:$deviceId|public_key:$publicKey"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
