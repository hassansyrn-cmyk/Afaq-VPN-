package com.afaq.vpn

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class AfaqVpnService : Service() {
    companion object {
        const val ACTION_CONNECT = "com.afaq.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.afaq.vpn.DISCONNECT"
        const val ACTION_STATE = "com.afaq.vpn.STATE"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_STATE = "state"
        const val CHANNEL = "afaq_vpn"
        const val NOTIFICATION_ID = 4107
        @Volatile var state = "disconnected"
        @Volatile var connectedAt = 0L
        @Volatile var rxBytes = 0L
        @Volatile var txBytes = 0L
        @Volatile var instance: AfaqVpnService? = null
    }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var backend: GoBackend
    private val tunnel = AfaqTunnel { emit(if (it == Tunnel.State.UP) "connected" else "disconnected") }
    override fun onCreate() { super.onCreate(); backend = GoBackend(this); createChannel(); instance = this }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startForeground(NOTIFICATION_ID, notification())
                val raw = intent.getStringExtra(EXTRA_CONFIG) ?: return fail("Missing configuration")
                emit("connecting")
                rxBytes = 0L
                txBytes = 0L
                executor.execute {
                    try {
                        val config = Config.parse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)))
                        backend.setState(tunnel, Tunnel.State.UP, config)
                        connectedAt = System.currentTimeMillis(); emit("connected")
                    } catch (_: Exception) { safeDown(); fail("WireGuard rejected the configuration or tunnel start failed") }
                }
            }
            ACTION_DISCONNECT -> executor.execute { emit("disconnecting"); safeDown(); emit("disconnected"); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_STICKY
    }
    fun updateTrafficStats() {
        if (state == "connected") {
            try {
                val stats = backend.getStatistics(tunnel)
                rxBytes = stats.totalRx()
                txBytes = stats.totalTx()
            } catch (_: Exception) {}
        }
    }
    private fun safeDown() { try { backend.setState(tunnel, Tunnel.State.DOWN, null) } catch (_: Exception) {} }
    private fun fail(message: String): Int { emit("failed", message); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
    private fun emit(value: String, message: String? = null) { state=value; if(value!="connected") connectedAt=0L; sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_STATE,value).putExtra("message",message)) }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"VPN connection",NotificationManager.IMPORTANCE_LOW)) }
    private fun notification():Notification {
        val stop=PendingIntent.getService(this,2,Intent(this,AfaqVpnService::class.java).setAction(ACTION_DISCONNECT),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,1,packageManager.getLaunchIntentForPackage(packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_secure).setContentTitle("Afaq VPN").setContentText("VPN tunnel is active").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).addAction(0,"Stop",stop).build()
    }
    override fun onDestroy(){ executor.shutdown(); instance = null; super.onDestroy() }
}
