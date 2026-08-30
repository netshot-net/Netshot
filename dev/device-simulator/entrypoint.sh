#!/bin/sh
# Starts real snmpd (config as reported by the driver's own script - see
# "snmp config" below), real sshd, and a real telnetd (via socat, since
# telnetd itself only knows how to run under inetd) for the driver named by
# NETSHOT_SIMULATOR_DRIVER (default: Cisco_IOS).
#
# NETSHOT_SIMULATOR_DRIVER must match one of the driver filenames under
# src/main/resources/drivers/ (case sensitive), e.g. Cisco_IOS, Juniper_Junos.
# Adding a new simulated device only requires dropping a matching .js file
# into this folder - nothing here needs to change.
#
# Each script is a tiny multi-command CLI of its own:
#   node <script>.js snmp config    - print a full snmpd.conf, or nothing
#   node <script>.js ssh start      - start the CLI already "authenticated"
#                                     (real sshd already did password auth)
#   node <script>.js telnet start   - prompt for credentials itself first (a
#                                     real device's CLI does the same over
#                                     Telnet - there's no transport-level
#                                     auth to rely on), then start the CLI
#
# NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
# optional, each script defaults to its own canned value when unset) let one
# customize the device identity - e.g. to run several distinct-looking
# instances of the same driver side by side in a demo. sshd/telnetd don't
# forward the container's environment into the session they start, so those
# values are baked as literal exports into the generated wrapper scripts
# below instead of being left to environment inheritance.
#
# NETSHOT_SIMULATOR_USERNAME/_PASSWORD (default admin/admin) are the
# SSH/Telnet login credentials. Telnet checks them itself (see each script's
# "telnet start" - a real device's CLI does its own prompting, there's no
# transport-level auth to rely on), but SSH auth happens at the OS/PAM level
# before our script ever runs, so the matching Linux account is created (or
# renamed/re-passworded, if it already exists as a leftover from a previous
# driver's env) right here, every time the container starts.
set -e

DRIVER="${NETSHOT_SIMULATOR_DRIVER:-Cisco_IOS}"
USERNAME="${NETSHOT_SIMULATOR_USERNAME:-admin}"
PASSWORD="${NETSHOT_SIMULATOR_PASSWORD:-admin}"
SCRIPT_DIR="/opt/netshot-device-simulator"
SHELL_PATH="/usr/local/bin/netshot-simulator-cli"
TELNET_PATH="/usr/local/bin/netshot-simulator-telnet"
SCRIPT="${SCRIPT_DIR}/${DRIVER}.js"

if [ ! -f "$SCRIPT" ]; then
	echo "Unknown NETSHOT_SIMULATOR_DRIVER: ${DRIVER}" >&2
	echo "Available drivers:" >&2
	for f in "${SCRIPT_DIR}"/*.js; do
		echo "  $(basename "$f" .js)" >&2
	done
	exit 1
fi

SNMP_CONFIG=$(node "$SCRIPT" snmp config)
if [ -n "$SNMP_CONFIG" ]; then
	printf '%s\n' "$SNMP_CONFIG" > /etc/snmp/snmpd.conf
	/usr/sbin/snmpd -Lo -u root -g root
fi

cat > "$SHELL_PATH" << WRAPPER
#!/bin/sh
export NETSHOT_SIMULATOR_NAME="${NETSHOT_SIMULATOR_NAME}"
export NETSHOT_SIMULATOR_VERSION="${NETSHOT_SIMULATOR_VERSION}"
export NETSHOT_SIMULATOR_MGMT_IP="${NETSHOT_SIMULATOR_MGMT_IP}"
export NETSHOT_SIMULATOR_LOCATION="${NETSHOT_SIMULATOR_LOCATION}"
export NETSHOT_SIMULATOR_CONTACT="${NETSHOT_SIMULATOR_CONTACT}"
export NETSHOT_SIMULATOR_SERIAL="${NETSHOT_SIMULATOR_SERIAL}"
export NETSHOT_SIMULATOR_USERNAME="${USERNAME}"
export NETSHOT_SIMULATOR_PASSWORD="${PASSWORD}"
export NETSHOT_SIMULATOR_ENABLE_SECRET="${NETSHOT_SIMULATOR_ENABLE_SECRET}"
exec node "$SCRIPT" ssh start
WRAPPER
chmod 755 "$SHELL_PATH"

cat > "$TELNET_PATH" << WRAPPER
#!/bin/sh
export NETSHOT_SIMULATOR_NAME="${NETSHOT_SIMULATOR_NAME}"
export NETSHOT_SIMULATOR_VERSION="${NETSHOT_SIMULATOR_VERSION}"
export NETSHOT_SIMULATOR_MGMT_IP="${NETSHOT_SIMULATOR_MGMT_IP}"
export NETSHOT_SIMULATOR_LOCATION="${NETSHOT_SIMULATOR_LOCATION}"
export NETSHOT_SIMULATOR_CONTACT="${NETSHOT_SIMULATOR_CONTACT}"
export NETSHOT_SIMULATOR_SERIAL="${NETSHOT_SIMULATOR_SERIAL}"
export NETSHOT_SIMULATOR_USERNAME="${USERNAME}"
export NETSHOT_SIMULATOR_PASSWORD="${PASSWORD}"
export NETSHOT_SIMULATOR_ENABLE_SECRET="${NETSHOT_SIMULATOR_ENABLE_SECRET}"
exec node "$SCRIPT" telnet start
WRAPPER
chmod 755 "$TELNET_PATH"

# The Linux account backing SSH's own (PAM/password) authentication - not
# built into the image, since the username/password are only known at
# container start time.
if id "$USERNAME" > /dev/null 2>&1; then
	usermod -s "$SHELL_PATH" "$USERNAME"
else
	useradd -m -s "$SHELL_PATH" "$USERNAME"
fi
echo "${USERNAME}:${PASSWORD}" | chpasswd

socat TCP-LISTEN:23,reuseaddr,fork EXEC:"/usr/sbin/telnetd -E ${TELNET_PATH}" &

ssh-keygen -A
exec /usr/sbin/sshd -D -e
