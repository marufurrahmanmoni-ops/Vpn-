#!/usr/bin/env bash
# Free Global VPN – turn a free Ubuntu VM (e.g. Oracle Cloud "Always Free") into an OpenVPN server
# that the app can use. Runs OpenVPN on UDP 1194 and TCP 443 (TCP gets through networks that block UDP).
#
#   sudo bash install-openvpn-server.sh            # install
#   then add the printed JSON entry to servers/community.json in the GitHub repo.
#
# Privacy: the server keeps NO connection or traffic logs (verb 0, no status file, logs to /dev/null).
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }

PUBLIC_IP="${PUBLIC_IP:-$(curl -fsS https://api.ipify.org)}"
CC="${COUNTRY_CODE:-ZZ}"          # e.g. SG, IN, AE, JP – shown in the app
NAME="${SERVER_NAME:-My free server}"
DIR=/etc/openvpn/server

apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q openvpn easy-rsa iptables-persistent curl

# --- PKI (one shared client certificate, so any app user can connect) --------------------------
rm -rf /etc/openvpn/easy-rsa && make-cadir /etc/openvpn/easy-rsa && cd /etc/openvpn/easy-rsa
export EASYRSA_BATCH=1 EASYRSA_REQ_CN="FreeGlobalVPN-CA"
./easyrsa init-pki >/dev/null
./easyrsa --days=3650 build-ca nopass >/dev/null
./easyrsa --days=3650 build-server-full server nopass >/dev/null
./easyrsa --days=3650 build-client-full freeglobalvpn-public nopass >/dev/null
mkdir -p $DIR
cp pki/ca.crt pki/issued/server.crt pki/private/server.key $DIR/
openvpn --genkey secret $DIR/tc.key

write_conf() { # $1=proto $2=port $3=subnet $4=name
cat > $DIR/$4.conf <<CONF
port $2
proto $1
dev tun
ca ca.crt
cert server.crt
key server.key
dh none
ecdh-curve prime256v1
tls-crypt tc.key
tls-version-min 1.2
data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305
server $3 255.255.255.0
topology subnet
duplicate-cn
max-clients 200
push "redirect-gateway def1 bypass-dhcp"
push "dhcp-option DNS 1.1.1.1"
push "dhcp-option DNS 1.0.0.1"
keepalive 10 60
user nobody
group nogroup
persist-tun
verb 0
log /dev/null
$( [ "$1" = udp ] && echo "explicit-exit-notify 1" )
CONF
}
write_conf udp 1194 10.8.0.0 fgv-udp
write_conf tcp-server 443 10.9.0.0 fgv-tcp

# --- routing / NAT / firewall ------------------------------------------------------------------
echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-openvpn.conf && sysctl -q -p /etc/sysctl.d/99-openvpn.conf
WAN=$(ip route show default | awk '{print $5; exit}')
iptables -t nat -C POSTROUTING -s 10.8.0.0/23 -o "$WAN" -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -s 10.8.0.0/23 -o "$WAN" -j MASQUERADE
# Oracle Ubuntu images REJECT new inbound traffic by default: open our ports before that rule.
iptables -C INPUT -p udp --dport 1194 -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p udp --dport 1194 -j ACCEPT
iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT
iptables -C FORWARD -s 10.8.0.0/23 -j ACCEPT 2>/dev/null || iptables -I FORWARD 1 -s 10.8.0.0/23 -j ACCEPT
iptables -C FORWARD -d 10.8.0.0/23 -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -I FORWARD 1 -d 10.8.0.0/23 -m state --state RELATED,ESTABLISHED -j ACCEPT
# Abuse protection: no outgoing mail (spam) through the VPN.
iptables -C FORWARD -s 10.8.0.0/23 -p tcp --dport 25 -j REJECT 2>/dev/null || iptables -I FORWARD 1 -s 10.8.0.0/23 -p tcp --dport 25 -j REJECT
netfilter-persistent save >/dev/null

systemctl enable --now openvpn-server@fgv-udp openvpn-server@fgv-tcp

# --- client configs ------------------------------------------------------------------------------
client() { # $1=proto $2=port
cat <<CL
client
dev tun
proto $1
remote $PUBLIC_IP $2
nobind
persist-key
persist-tun
remote-cert-tls server
data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305
verb 3
<ca>
$(cat /etc/openvpn/easy-rsa/pki/ca.crt)
</ca>
<cert>
$(openssl x509 -in /etc/openvpn/easy-rsa/pki/issued/freeglobalvpn-public.crt)
</cert>
<key>
$(cat /etc/openvpn/easy-rsa/pki/private/freeglobalvpn-public.key)
</key>
<tls-crypt>
$(cat $DIR/tc.key)
</tls-crypt>
CL
}
client udp 1194 > /root/freeglobalvpn-udp.ovpn
client tcp 443  > /root/freeglobalvpn-tcp.ovpn

python3 - "$NAME" "$CC" > /root/community-entry.json <<'PY'
import json, sys
name, cc = sys.argv[1], sys.argv[2]
entries = []
for proto, f in (("UDP", "/root/freeglobalvpn-udp.ovpn"), ("TCP", "/root/freeglobalvpn-tcp.ovpn")):
    entries.append({"name": f"{name} ({proto})", "cc": cc, "operator": "Free Global VPN (self-hosted)",
                    "log_policy": "No logs (verb 0, no status file)", "config": open(f).read()})
print(json.dumps(entries, indent=2))
PY
echo
echo "OpenVPN is running on $PUBLIC_IP (UDP 1194, TCP 443)."
echo "Client files: /root/freeglobalvpn-udp.ovpn and /root/freeglobalvpn-tcp.ovpn"
echo "Add the entries from /root/community-entry.json to servers/community.json in your GitHub repo."
echo "REMEMBER: also open UDP 1194 and TCP 443 in the cloud firewall (Oracle: VCN → Security List → Ingress rules)."
