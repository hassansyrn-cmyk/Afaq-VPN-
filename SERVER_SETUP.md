# Oracle Cloud WireGuard server setup

Oracle Free Tier capacity is not guaranteed. Create an Ubuntu VM in an available region, attach a public IPv4 address, and download or provide an SSH public key. Keep the SSH private key offline and restricted (`chmod 600`). Connect with `ssh -i your-key ubuntu@YOUR_SERVER_IP`.

In the Oracle VCN Security List or Network Security Group, add an ingress rule for UDP 51820 from the intended source range. Avoid opening SSH broadly; restrict TCP 22 to your administration IP. On Ubuntu, upload this repository's `server` directory, inspect every script, then run:

```bash
sudo bash server/install-wireguard.sh
sudo bash server/add-client.sh client1 10.66.66.2/32
sudo bash server/show-status.sh
```

`install-wireguard.sh` updates packages, installs WireGuard/UFW, detects the default Internet interface automatically, enables IPv4 forwarding, creates a backup before replacing any existing config, creates server keys with restrictive permissions, writes `/etc/wireguard/wg0.conf`, opens UDP 51820, and enables `wg-quick@wg0`.

The add-client command prints a client configuration once. Treat it as sensitive. Put its values in the local `.env` described in README, add only the client's public key to the server, and never share either private key.

Useful commands:

```bash
sudo wg show
sudo systemctl status wg-quick@wg0
sudo journalctl -u wg-quick@wg0 --no-pager -n 100
sudo systemctl restart wg-quick@wg0
sudo bash server/remove-client.sh client1
sudo bash server/backup-config.sh
```

To rotate a client, remove it and add it again with a new unique address. To rotate the server, schedule downtime, back up first, generate a new server key pair, update `PrivateKey` in `wg0.conf`, restart the interface, and distribute the new server public key through a trusted channel. Never paste private keys into issues, chat, screenshots, or Git.

For IPv6, the app routes `::/0` by default. The included server baseline config handles IPv4 only. Configure a real routed IPv6 prefix and firewall rules before claiming IPv6 support. Otherwise disclose the limitation and remove `::/0` from the client configuration during controlled testing. Verify DNS and IPv6 behavior independently.
