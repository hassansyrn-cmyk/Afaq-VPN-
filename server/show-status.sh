#!/usr/bin/env bash
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo "Run as root." >&2; exit 1; }
systemctl --no-pager --full status wg-quick@wg0 || true
wg show
