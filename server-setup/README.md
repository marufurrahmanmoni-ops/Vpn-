# Add your own free server (Oracle Cloud Always Free)

VPN Gate's volunteer servers come and go. For a server **you** control in a country you choose, run one
on a free cloud VM. The best free option is **Oracle Cloud "Always Free"**: an Arm VM (up to 4 OCPU /
24 GB RAM in total) plus **10 TB of outbound traffic per month**, free for the life of the account.
The catch: Always Free VMs can only be created in your **home region**, which you pick once at sign-up
and cannot change. Pick a region close to your users, e.g. Singapore, Mumbai, Hyderabad, Dubai,
Tokyo, Seoul, Frankfurt, London or Ashburn (whatever the sign-up page offers).

(Google Cloud's free e2-micro includes only ~1 GB of outbound traffic a month, and AWS's free tier is
time-limited – both are unsuitable for a VPN.)

## Steps

1. Sign up at https://www.oracle.com/cloud/free/ (a card is needed for identity verification;
   Always Free resources are not charged).
2. *Compute → Instances → Create*: image **Ubuntu 24.04**, shape **VM.Standard.A1.Flex** (e.g. 2 OCPU,
   12 GB) – it must show the *Always Free-eligible* label. Download the SSH key. If you get
   "out of host capacity", try again later or pick another availability domain.
3. *Networking → Virtual cloud networks → your VCN → Security Lists → Default → Add Ingress Rules*:
   - Source `0.0.0.0/0`, **UDP**, destination port **1194**
   - Source `0.0.0.0/0`, **TCP**, destination port **443**
4. SSH in and run:
   ```bash
   curl -fsSLO https://raw.githubusercontent.com/marufurrahmanmoni-ops/Vpn-/main/server-setup/install-openvpn-server.sh
   sudo COUNTRY_CODE=SG SERVER_NAME="Singapore Free" bash install-openvpn-server.sh
   ```
5. Copy the entries printed into `/root/community-entry.json` into the `servers` array of
   `servers/community.json` in this repo and commit.
6. The *Update verified servers* workflow (every 4 h, or run it by hand) tests your server like every
   other one. If it is good, it appears in the app automatically – no new APK needed.

## Responsibility

When you publish a server in the app, **you are its operator**: abuse complaints about traffic from
the VM's IP go to you and to Oracle. The script blocks outgoing e-mail (port 25) to prevent spam, keeps
no logs, and allows at most 200 simultaneous users per protocol. Read Oracle's acceptable-use policy,
and remove the server from `community.json` if you no longer want to run it.
