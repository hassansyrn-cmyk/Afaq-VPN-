package com.afaq.vpn

import android.app.Activity
import android.content.*
import android.net.VpnService
import android.provider.Settings
import com.getcapacitor.*
import com.getcapacitor.annotation.*

@CapacitorPlugin(name = "AfaqVpn", requestCodes = [PermissionCallbackData(REQUEST_VPN)])
class AfaqVpnPlugin : Plugin() {
    companion object { const val REQUEST_VPN = 8401 }
    private var prepareCall: PluginCall? = null
    private val receiver = object: BroadcastReceiver(){ override fun onReceive(c:Context?,i:Intent?){ val o=JSObject();o.put("state",i?.getStringExtra(AfaqVpnService.EXTRA_STATE));o.put("message",i?.getStringExtra("message"));notifyListeners("statusChanged",o) } }
    override fun load(){ context.registerReceiver(receiver,IntentFilter(AfaqVpnService.ACTION_STATE),Context.RECEIVER_NOT_EXPORTED) }
    @PluginMethod fun prepareVpn(call:PluginCall){ val intent=VpnService.prepare(context);if(intent==null){call.resolve(JSObject().put("granted",true))}else{prepareCall=call;startActivityForResult(call,intent,REQUEST_VPN)} }
    @ActivityCallback fun vpnPrepared(call:PluginCall?,result:ActivityResult){ (prepareCall?:call)?.resolve(JSObject().put("granted",result.resultCode==Activity.RESULT_OK));prepareCall=null }
    @PluginMethod fun isVpnPermissionGranted(call:PluginCall)=call.resolve(JSObject().put("granted",VpnService.prepare(context)==null))
    @PluginMethod fun connect(call:PluginCall){
        val c=call.getObject("config")?:return call.reject("Missing config")
        val required=listOf("privateKey","address","dns","publicKey","endpoint","allowedIps")
        if(required.any{c.getString(it).isNullOrBlank()})return call.reject("Incomplete WireGuard configuration")
        val ps=c.getString("presharedKey")
        val text=buildString{append("[Interface]\nPrivateKey = ${c.getString("privateKey")}\nAddress = ${c.getString("address")}\nDNS = ${c.getString("dns")}\n\n[Peer]\nPublicKey = ${c.getString("publicKey")}\n");if(!ps.isNullOrBlank())append("PresharedKey = $ps\n");append("Endpoint = ${c.getString("endpoint")}\nAllowedIPs = ${c.getString("allowedIps")}\nPersistentKeepalive = ${c.getInteger("persistentKeepalive")?:25}\n")}
        val i=Intent(context,AfaqVpnService::class.java).setAction(AfaqVpnService.ACTION_CONNECT).putExtra(AfaqVpnService.EXTRA_CONFIG,text)
        context.startForegroundService(i);call.resolve(JSObject().put("state","connecting"))
    }
    @PluginMethod fun disconnect(call:PluginCall){context.startService(Intent(context,AfaqVpnService::class.java).setAction(AfaqVpnService.ACTION_DISCONNECT));call.resolve(JSObject().put("state","disconnecting"))}
    @PluginMethod fun getConnectionStatus(call:PluginCall)=call.resolve(JSObject().put("state",AfaqVpnService.state).put("connectedAt",AfaqVpnService.connectedAt))
    @PluginMethod fun getTrafficStats(call:PluginCall)=call.resolve(JSObject().put("receivedBytes",AfaqVpnService.rxBytes).put("transmittedBytes",AfaqVpnService.txBytes))
    @PluginMethod fun openVpnSettings(call:PluginCall){context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));call.resolve()}
    override fun handleOnDestroy(){try{context.unregisterReceiver(receiver)}catch(_:Exception){};super.handleOnDestroy()}
}
