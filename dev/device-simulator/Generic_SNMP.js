#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Generic SNMP (SNMP-only, no CLI)
 *
 * Run by entrypoint.sh:
 *   node Generic_SNMP.js snmp config    - print a full snmpd.conf
 *   node Generic_SNMP.js ssh start      - declines, no CLI on this driver
 *   node Generic_SNMP.js telnet start   - declines, no CLI on this driver
 *
 * The real Generic_SNMP driver has an empty CLI = {} - it never connects
 * over SSH or Telnet - so both start subcommands just say so and disconnect
 * instead of pretending to be an interactive CLI.
 *
 * NETSHOT_SIMULATOR_LOCATION/_CONTACT (both optional) customize the device
 * identity. NETSHOT_SIMULATOR_NAME/_VERSION don't apply to this device: sysName
 * comes from the container's own hostname (set at the `docker run`/compose
 * level, not by this script), and there's no sysDescr override to version
 * (see below).
 *
 * NETSHOT_SIMULATOR_SNMP_COMMUNITY (v1/v2c) and NETSHOT_SIMULATOR_SNMPV3_USER/
 * _AUTH_PASSWORD/_PRIV_PASSWORD (authPriv, SHA/AES) are both always available
 * side by side, same as a real device configured for either.
 */
'use strict';

const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Rack 1';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'someone@example.com';
const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// No sysobjectid/sysdescr override: this device is meant to be
// unidentifiable by any vendor-specific driver, so it keeps net-snmp's own
// defaults - matching snmpAutoDiscover() in
// src/main/resources/drivers/Generic_SNMP.js, which accepts any device.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

function main() {
	const [subcommand, action] = process.argv.slice(2);
	if (subcommand === 'snmp' && action === 'config') {
		process.stdout.write(SNMP_CONFIG);
		return;
	}
	if ((subcommand === 'ssh' || subcommand === 'telnet') && action === 'start') {
		process.stdout.write('This device has no CLI access (SNMP only).\r\n');
		return;
	}
	process.stderr.write('Usage: node Generic_SNMP.js <snmp config|ssh start|telnet start>\n');
	process.exitCode = 1;
}

main();
