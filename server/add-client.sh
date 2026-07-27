#!/usr/bin/env bash
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
[[ $# -eq 2 ]] || { echo "Usage: $0 CLIENT_NAME CLIENT_ADDRESS/32" >&2; exit 1; }
NAME="$1"; ADDRESS="$2"
[[ "$NAME" =~ ^[A-Za-z0-9_-]+$ ]] || { echo "Invalid client name." >&2; exit 1; }
[[ "$ADDRESS" =~ ^10\.66\.66\.[0-9]{1,3}/32$ ]] || { echo "Use an unused 10.66.66.x/32 address." >&2; exit 1; }
CONF=/etc/wireguard/wg0.conf
[[ -f "$CONF" ]] || { echo "Install the server first." >&2; exit 1; }
grep -q "# client:$NAME$" "$CONF" && { echo "Client already exists." >&2; exit 1; }
grep -q "AllowedIPs = $ADDRESS$" "$CONF" && { echo "Address already exists." >&2; exit 1; }
"$(dirname "$0")/backup-config.sh" >/dev/null
umask 077
CLIENT_PRIVATE="$(wg genkey)"; CLIENT_PUBLIC="$(printf '%s' "$CLIENT_PRIVATE" | wg pubkey)"; PSK="$(wg genpsk)"
cat >> "$CONF" <<PEER

# client:$NAME
[Peer]
PublicKey = $CLIENT_PUBLIC
PresharedKey = $PSK
AllowedIPs = $ADDRESS
PEER
wg syncconf wg0 <(wg-quick strip wg0)
SERVER_PUBLIC="$(cat /etc/wireguard/keys/server.pub)"
PUBLIC_IP="$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}')"
echo "Sensitive client configuration. Save securely; it will not be stored as a client file."
printf '[Interface]\nPrivateKey = %s\nAddress = %s\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = %s\nPresharedKey = %s\nEndpoint = %s:51820\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\n' "$CLIENT_PRIVATE" "$ADDRESS" "$SERVER_PUBLIC" "$PSK" "$PUBLIC_IP"
unset CLIENT_PRIVATE PSK
