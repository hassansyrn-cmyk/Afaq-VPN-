import { Capacitor, registerPlugin } from '@capacitor/core';
import type { VpnPlugin, WireGuardConfig } from '../types/vpn';
export const AfaqVpn = registerPlugin<VpnPlugin>('AfaqVpn');
export const isNativeAndroid = () => Capacitor.getPlatform() === 'android';
export function envConfig(): WireGuardConfig {
  return {
    privateKey: import.meta.env.VITE_WG_CLIENT_PRIVATE_KEY ?? '', address: import.meta.env.VITE_WG_CLIENT_ADDRESS ?? '',
    dns: import.meta.env.VITE_WG_DNS ?? '1.1.1.1', publicKey: import.meta.env.VITE_WG_SERVER_PUBLIC_KEY ?? '',
    presharedKey: import.meta.env.VITE_WG_PRESHARED_KEY || undefined, endpoint: import.meta.env.VITE_WG_ENDPOINT ?? '',
    allowedIps: import.meta.env.VITE_WG_ALLOWED_IPS ?? '0.0.0.0/0, ::/0', persistentKeepalive: 25
  };
}
export const configReady = (c: WireGuardConfig) => Boolean(c.privateKey && c.address && c.publicKey && c.endpoint);
