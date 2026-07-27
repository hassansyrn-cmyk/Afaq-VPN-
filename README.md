# Afaq VPN

A real Android WireGuard MVP built with React, TypeScript, Vite, Capacitor 8, Kotlin, and the official `com.wireguard.android:tunnel` library.

## Architecture

- React UI renders onboarding, explicit VPN consent, connection state, server, settings, RTL/LTR, and light/dark modes.
- `AfaqVpnPlugin.kt` bridges JavaScript and Android, requests the official Android VPN consent, validates fields, and starts a foreground service.
- `AfaqVpnService.kt` parses the WireGuard configuration with the official library and controls `GoBackend`, whose embedded `VpnService` carries traffic.
- No analytics, ads, Firebase, custom cryptography, certificate bypass, browsing logs, traffic-content logs, or DNS-query logs are included.

## Requirements

- Node.js 22
- JDK 21 to run Gradle
- Android SDK 36
- Android 7.0/API 24 or later
- A WireGuard server and a unique client peer

## Local setup

1. Run `npm install`.
2. Copy `.env.example` to `.env`.
3. Put the real values only in `.env`:
   - `VITE_WG_ENDPOINT`: server public IP and port, for example `203.0.113.10:51820`.
   - `VITE_WG_SERVER_PUBLIC_KEY`: output of the server public-key command.
   - `VITE_WG_CLIENT_ADDRESS`: address assigned to this peer, for example `10.66.66.2/32`.
   - `VITE_WG_CLIENT_PRIVATE_KEY`: private key for this exact client. Never commit it.
4. Run `npm run android:debug`.
5. Install `android/app/build/outputs/apk/debug/app-debug.apk` on a physical Android device.

## Important security limitation

Vite environment values are compiled into the APK. `.gitignore` protects Git, but it does not make an embedded client private key secret from someone who possesses the APK. For production, generate the key on-device, store it with Android Keystore-backed encryption, and register only the public key through an authenticated backend. The current placeholder flow is suitable for bringing up a private test peer, not distributing one shared production key.

The full-tunnel default is `0.0.0.0/0, ::/0`. IPv4 and IPv6 are included intentionally. If the server has no routed IPv6, either configure proper IPv6 routing/NAT and test it, or clearly disclose and intentionally remove `::/0`; do not claim leak protection without testing.

## Commands

- `npm run dev`: browser UI only, no VPN tunnel.
- `npm run build`: TypeScript check and web build.
- `npm run android:sync`: copy web output and native dependencies.
- `npm run android:debug`: build web, sync, and build debug APK.

## MVP limitations and TODO

- Public-IP lookup and ping are deliberately shown as unavailable until a reviewed HTTPS endpoint is selected. No external tracker was silently added.
- Reconnect and auto-connect preferences are stored, but production-grade network-callback and boot behavior remain TODO and must be tested across OEM battery managers.
- Traffic counters depend on backend statistics wiring and require device verification.
- TODO: on-device key generation and protected provisioning API.
- TODO: authenticated multi-server catalog, Premium/Billing, AdMob outside the VPN tunnel, accounts, admin console, and optional Crashlytics with an updated privacy notice.

## Test plan

Test on API 24 and the newest supported Android: grant and deny permission, connect/disconnect, invalid config, unavailable server, background/force-close behavior, notification stop action, Wi-Fi loss, Wi-Fi to cellular handoff, reboot, DNS leak, IPv4/IPv6 leak, key absence from Logcat, and notification removal. Validate public IP and DNS using independent reputable test sites before and during the tunnel. Do not treat a successful UI state as proof of leak protection.
