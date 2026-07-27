# Security

Do not report vulnerabilities in a public issue if they include keys, server addresses intended to be private, or an exploitable detail. Configure a private security-reporting address before launch.

Never commit `.env`, WireGuard private keys, preshared keys, server backups, keystores, or `wg0.conf`. Rotate a key immediately if exposed. The app rejects incomplete configuration and does not log the full WireGuard config.

Production checklist: generate unique keys on-device; protect key material using Android Keystore-backed encryption; authenticate and authorize peer registration; rate-limit provisioning; pin no certificates unless a safe rotation plan exists; use HTTPS with normal trust validation; patch Android, Ubuntu, and WireGuard; restrict SSH; review server logs and retention; run DNS and IPv6 leak tests.
