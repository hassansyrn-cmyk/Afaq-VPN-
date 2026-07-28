package com.afaq.vpn

import android.content.Context
import com.wireguard.crypto.KeyPair
import java.security.SecureRandom

object AfaqIdentityManager {
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_PRIVATE_KEY = "wg_private_key"
    private const val KEY_PUBLIC_KEY = "wg_public_key"

    @Synchronized
    fun getOrCreateInstallationId(context: Context): String {
        var id = AfaqSecureStorage.getString(context, KEY_INSTALLATION_ID)
        if (id.isNullOrBlank()) {
            val random = SecureRandom()
            val bytes = ByteArray(16) // 128 bits
            random.nextBytes(bytes)
            id = bytes.joinToString("") { "%02x".format(it) }
            AfaqSecureStorage.saveString(context, KEY_INSTALLATION_ID, id)
        }
        return id
    }

    @Synchronized
    fun getOrCreateWireGuardKeyPair(context: Context): Pair<String, String> {
        var priv = AfaqSecureStorage.getString(context, KEY_PRIVATE_KEY)
        var pub = AfaqSecureStorage.getString(context, KEY_PUBLIC_KEY)

        if (priv.isNullOrBlank() || pub.isNullOrBlank()) {
            val keyPair = KeyPair()
            priv = keyPair.privateKey.toBase64()
            pub = keyPair.publicKey.toBase64()
            AfaqSecureStorage.saveString(context, KEY_PRIVATE_KEY, priv)
            AfaqSecureStorage.saveString(context, KEY_PUBLIC_KEY, pub)
        }
        return Pair(priv, pub)
    }

    fun hasIdentity(context: Context): Boolean {
        val id = AfaqSecureStorage.getString(context, KEY_INSTALLATION_ID)
        val priv = AfaqSecureStorage.getString(context, KEY_PRIVATE_KEY)
        val pub = AfaqSecureStorage.getString(context, KEY_PUBLIC_KEY)
        return !id.isNullOrBlank() && !priv.isNullOrBlank() && !pub.isNullOrBlank()
    }
}
