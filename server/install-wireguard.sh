#!/usr/bin/env bash
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo "Run as root: sudo $0" >&2; exit 1; }
command -v ip >/dev/null || { echo "The ip command is required." >&2; exit 1; }
WAN_IF="$(ip -4 route show default | awk 'NR==1 {print $5}')"
[[ -n "$WAN_IF" ]] || { echo "Could not detect the default Internet interface." >&2; exit 1; }
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard ufw qrencode
install -d -m 700 /etc/wireguard /etc/wireguard/keys
if [[ -e /etc/wireguard/wg0.conf ]]; then
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  cp -a /etc/wireguard/wg0.conf "/etc/wireguard/wg0.conf.backup.$stamp"
  echo "Existing configuration backed up. Refusing to overwrite it." >&2
  exit 1
fi
umask 077
wg genkey | tee /etc/wireguard/keys/server.key | wg pubkey > /etc/wireguard/keys/server.pub
SERVER_KEY="$(cat /etc/wireguard/keys/server.key)"
cat > /etc/sysctl.d/99-afaq-wireguard.conf <<SYSCTL
net.ipv4.ip_forward=1
SYSCTL
sysctl --system >/dev/null
cat > /etc/wireguard/wg0.conf <<WG
[Interface]
Address = 10.66.66.1/24
ListenPort = 51820
PrivateKey = $SERVER_KEY
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT; iptables -t nat -A POSTROUTING -o $WAN_IF -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT; iptables -t nat -D POSTROUTING -o $WAN_IF -j MASQUERADE
WG
chmod 600 /etc/wireguard/wg0.conf /etc/wireguard/keys/server.key
ufw allow 51820/udp
ufw --force enable
systemctl enable --now wg-quick@wg0
echo "WireGuard is running. Server public key:"
cat /etc/wireguard/keys/server.pub
echo "Also open UDP 51820 in the Oracle VCN Security List or NSG."
