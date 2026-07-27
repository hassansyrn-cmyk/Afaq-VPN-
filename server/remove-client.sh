#!/usr/bin/env bash
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
[[ $# -eq 1 ]] || { echo "Usage: $0 CLIENT_NAME" >&2; exit 1; }
NAME="$1"; CONF=/etc/wireguard/wg0.conf
[[ "$NAME" =~ ^[A-Za-z0-9_-]+$ ]] || exit 1
grep -q "# client:$NAME$" "$CONF" || { echo "Client not found." >&2; exit 1; }
echo "This will remove client '$NAME'. Type REMOVE to continue:"
read -r answer; [[ "$answer" == REMOVE ]] || { echo "Cancelled."; exit 1; }
"$(dirname "$0")/backup-config.sh" >/dev/null
awk -v marker="# client:$NAME" 'BEGIN{drop=0} $0==marker{drop=1;next} drop && /^# client:/{drop=0} drop && /^\[Peer\]/{next} !drop{print}' "$CONF" > "$CONF.tmp"
install -m 600 "$CONF.tmp" "$CONF"; rm -f "$CONF.tmp"
wg syncconf wg0 <(wg-quick strip wg0)
echo "Client removed."
