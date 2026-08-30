#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Juniper Junos (MX-like router)
 *
 * Run by entrypoint.sh:
 *   node Juniper_Junos.js snmp config    - print a full snmpd.conf, or nothing
 *   node Juniper_Junos.js ssh start      - start the CLI, already "authenticated"
 *   node Juniper_Junos.js telnet start   - prompt login/Password ourselves
 *                                          first, then start the CLI
 * See Cisco_IOS.js for the general design notes (raw-PTY, telnetd -E).
 *
 * Credentials default to admin/admin, matching the
 * DeviceSshAccount/DeviceTelnetAccount used by the Netshot test/demo - see
 * NETSHOT_SIMULATOR_USERNAME/_PASSWORD below. SSH auth itself happens at the
 * OS/PAM level (entrypoint.sh creates the matching Linux account), not in
 * this script - only Telnet's login prompt is handled here.
 *
 * NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
 * optional) customize the device identity - e.g. to run several
 * distinct-looking Juniper Junos instances side by side in a demo.
 */
'use strict';

const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'mx1';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || '21.4R3.15';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '10.0.0.1';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Room 42';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'netshot@example.com';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || 'JN123F456789';
const USERNAME = process.env.NETSHOT_SIMULATOR_USERNAME || 'admin';
const PASSWORD = process.env.NETSHOT_SIMULATOR_PASSWORD || 'admin';

// SNMP_COMMUNITY (v1/v2c) and SNMPV3_USER/_AUTH_PASSWORD/_PRIV_PASSWORD
// (authPriv, SHA/AES) are both always available side by side, same as a
// real device configured for either.
const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// Matches this driver's own snmpAutoDiscover() in
// src/main/resources/drivers/Juniper_Junos.js, so a real SNMP-based scan of
// this simulated device lands on the right driver, same as it would for the real
// thing.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.2636.1.1.1.2.57
sysdescr Juniper Networks, Inc. mx240 internet router, kernel JUNOS ${VERSION}
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

let onLine = null;

function out(s) { process.stdout.write(s); }

function startRawIO() {
	if (process.stdin.isTTY) {
		process.stdin.setRawMode(true);
	}
	process.stdin.resume();
	process.stdin.setEncoding('utf8');
	let buffer = '';
	process.stdin.on('data', (chunk) => {
		for (const ch of chunk) {
			if (ch === '\r' || ch === '\n') {
				out('\n');
				const line = buffer;
				buffer = '';
				onLine(line);
			}
			else if (ch === '\x7f' || ch === '\b') {
				if (buffer.length > 0) {
					buffer = buffer.slice(0, -1);
					out('\b \b');
				}
			}
			else {
				buffer += ch;
				out(ch);
			}
		}
	});
	process.stdin.on('end', () => process.exit(0));
}

function prompt() {
	out(`${USERNAME}@${HOSTNAME}> `);
}

function showConfiguration() {
	out(`## Last commit: 2024-01-01 08:00:00 UTC by admin
version ${VERSION};
system {
    host-name ${HOSTNAME};
    root-authentication {
        encrypted-password "$6$netshot$fakehashfakehashfakehash";
    }
    services {
        ssh;
        netconf {
            ssh;
        }
    }
}
interfaces {
    ge-0/0/0 {
        description "Uplink to core";
        unit 0 {
            family inet {
                address ${MGMT_IP}/30;
            }
        }
    }
    ge-0/0/1 {
        disable;
    }
}
snmp {
    location "${LOCATION}";
    contact "${CONTACT}";
}
`);
	prompt();
}

function showConfigurationSet() {
	out(`set version ${VERSION}
set system host-name ${HOSTNAME}
set system services ssh
set system services netconf ssh
set interfaces ge-0/0/0 description "Uplink to core"
set interfaces ge-0/0/0 unit 0 family inet address ${MGMT_IP}/30
set interfaces ge-0/0/1 disable
set snmp location "${LOCATION}"
set snmp contact "${CONTACT}"
`);
	prompt();
}

function showVersion() {
	out(`Hostname: ${HOSTNAME}
Model: mx240
JUNOS Software Release [${VERSION}]
`);
	prompt();
}

function showChassisHardware() {
	// The chassis row's Serial number column is fixed-width (the driver
	// parses it by column position against the header above), so SERIAL is
	// padded to keep "MX240" (Description) aligned regardless of its length.
	const chassisRow = 'Chassis'.padEnd(39) + SERIAL.padEnd(18) + 'MX240';
	out(`Hardware inventory:
Item             Version  Part number  Serial number     Description
${chassisRow}
Routing Engine 0 REV 08   740-031116   RE-9012345678     RE-S-1800X4
`);
	prompt();
}

function showInterfaces() {
	out(`Physical interface: ge-0/0/0, Enabled, Physical link is Up
  Interface index: 142, SNMP ifIndex: 512
  Description: Uplink to core
  Link-level type: Ethernet, MTU: 1514, Speed: 1000mbps
  Hardware address: 00:11:22:33:44:55
  Logical interface ge-0/0/0.0 (Index 69) (SNMP ifIndex 513)
    Description: Uplink to core unit 0
    Flags: SNMP-Traps 0x0 Encapsulation: ENET2
    Protocol inet, MTU: 1500
      Flags: Sendbcast-pkt-to-re
      Addresses, Flags: Is-Preferred Is-Primary
        Destination: 10.0.0.0/30, Local: ${MGMT_IP}, Broadcast: 10.0.0.3
Physical interface: ge-0/0/1, Administratively down, Physical link is Down
  Interface index: 143, SNMP ifIndex: 514
  Link-level type: Ethernet, MTU: 1514, Speed: 1000mbps
  Hardware address: 00:11:22:33:44:56
`);
	prompt();
}

function handleCommand(command) {
	if (command === 'show configuration') {
		showConfiguration();
	}
	else if (command === 'show configuration | display set') {
		showConfigurationSet();
	}
	else if (command === 'show version') {
		showVersion();
	}
	else if (command === 'show chassis hardware') {
		showChassisHardware();
	}
	else if (command === 'show interfaces') {
		showInterfaces();
	}
	else if (command === 'set cli complete-on-space off' || command === 'set cli screen-length 0') {
		prompt();
	}
	else {
		out(`unknown command: ${command}\n`);
		out('              ^\n');
		prompt();
	}
}

function startCli() {
	onLine = handleCommand;
	prompt();
}

/** telnetd hands us a bare pty with no OS-level auth - the device's own CLI
 * has to prompt for login/Password itself, matching this driver's own
 * telnet `username`/`password`/`usernameAgain` modes ("login: ", not
 * "Username: " - that's a Junos-ism, unlike the Cisco-style devices). */
function startTelnetLogin() {
	let typedUsername = '';
	onLine = function onUsernameLine(line) {
		typedUsername = line;
		onLine = function onPasswordLine(line) {
			if (typedUsername === USERNAME && line === PASSWORD) {
				startCli();
			}
			else {
				out('Login incorrect\n\n');
				out('login: ');
				onLine = onUsernameLine;
			}
		};
		out('Password:');
	};
	out('login: ');
}

function main() {
	const [subcommand, action] = process.argv.slice(2);
	if (subcommand === 'snmp' && action === 'config') {
		out(SNMP_CONFIG);
		return;
	}
	startRawIO();
	if (subcommand === 'ssh' && action === 'start') {
		startCli();
	}
	else if (subcommand === 'telnet' && action === 'start') {
		startTelnetLogin();
	}
	else {
		process.stderr.write('Usage: node Juniper_Junos.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
