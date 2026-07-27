#!/usr/bin/env bash
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
CONF=/etc/wireguard/wg0.conf; [[ -f "$CONF" ]] || { echo "No config to back up." >&2; exit 1; }
DEST=/var/backups/afaq-wireguard; install -d -m 700 "$DEST"
FILE="$DEST/wg0-$(date -u +%Y%m%dT%H%M%SZ).conf"
install -m 600 "$CONF" "$FILE"
echo "Backup created: $FILE"
