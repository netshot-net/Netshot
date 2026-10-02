#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Palo Alto PAN-OS (PA-400 series firewall)
 *
 * Run by entrypoint.sh:
 *   node Palo-Alto_PAN-OS.js snmp config    - print a full snmpd.conf, or nothing
 *   node Palo-Alto_PAN-OS.js ssh start      - start the CLI, already "authenticated"
 *   node Palo-Alto_PAN-OS.js telnet start   - prompt login/Password ourselves
 *                                             first, then start the CLI
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
 * distinct-looking PAN-OS instances side by side in a demo.
 *
 * The command outputs are modeled on a real PA-440 running PAN-OS 11.1
 * (anonymized: names, addresses, MACs and serial number are made up).
 */
'use strict';

const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'fw1';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || '11.1.6-h3';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '192.0.2.10';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Room 42';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'netshot@example.com';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || '012345678901';
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
// src/main/resources/drivers/Palo-Alto_PAN-OS.js, so a real SNMP-based scan
// of this simulated device lands on the right driver, same as it would for
// the real thing.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.25461.2.3.66
sysdescr Palo Alto Networks PA-400 series firewall
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

// Logical interfaces, as listed by "show interface logical". An empty zone
// (HA links, interfaces without a zone) leaves a blank column, which the
// driver has to cope with.
const INTERFACES = [
	{ name: 'ethernet1/1', id: 16, zone: 'UNTRUST', forwarding: 'vr:default', tag: 0, address: '198.51.100.2/29', mac: '00:00:5e:00:53:11' },
	{ name: 'ethernet1/2', id: 17, zone: '', forwarding: 'vr:default', tag: 0, address: 'N/A', mac: '00:00:5e:00:53:12' },
	{ name: 'ethernet1/2.810', id: 256, zone: 'DMZ', forwarding: 'vr:default', tag: 810, address: '172.16.10.1/26', mac: null },
	{ name: 'ethernet1/7', id: 22, zone: '', forwarding: 'ha', tag: 0, address: '10.0.0.11/24', mac: '00:00:5e:00:53:17' },
	{ name: 'ethernet1/8', id: 23, zone: '', forwarding: 'ha', tag: 0, address: '10.0.0.12/24', mac: '00:00:5e:00:53:18' },
	{ name: 'vlan', id: 1, zone: '', forwarding: 'N/A', tag: 0, address: 'N/A', mac: '00:00:5e:00:53:01' },
	{ name: 'loopback', id: 3, zone: '', forwarding: 'N/A', tag: 0, address: 'N/A', mac: '00:00:5e:00:53:03' },
	{ name: 'tunnel', id: 4, zone: '', forwarding: 'N/A', tag: 0, address: 'N/A', mac: '00:00:5e:00:53:04' },
];

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
	out(`${USERNAME}@${HOSTNAME}(active)> `);
}

/** "show config running" (local configuration) or "show config merged"
 * (local + Panorama-pushed configuration, here an extra shared address). */
function showConfig(merged) {
	const pushedAddress = merged ? `
    address {
      PANORAMA-NTP {
        ip-netmask 203.0.113.123;
      }
    }` : '';
	out(`
config {
  mgt-config {
    users {
      admin {
        permissions {
          role-based {
            superuser yes;
          }
        }
        phash $5$netshot$fakehashfakehashfakehashfakehashfakehash;
      }
    }
  }
  shared {${pushedAddress}
    application;
    application-group;
    service;
    service-group;
  }
  devices {
    localhost.localdomain {
      network {
        interface {
          ethernet {
            ethernet1/1 {
              layer3 {
                ip {
                  198.51.100.2/29;
                }
              }
              comment UNTRUST;
            }
            ethernet1/2 {
              layer3 {
                units {
                  ethernet1/2.810 {
                    ip {
                      172.16.10.1/26;
                    }
                    tag 810;
                    comment "DMZ servers";
                  }
                }
              }
            }
            ethernet1/7 {
              ha;
            }
            ethernet1/8 {
              ha;
            }
          }
          loopback {
            units;
          }
          vlan {
            units;
          }
          tunnel {
            units;
          }
        }
      }
      deviceconfig {
        system {
          hostname ${HOSTNAME};
          ip-address ${MGMT_IP};
          netmask 255.255.255.248;
          default-gateway 192.0.2.9;
        }
      }
    }
  }
}

`);
	prompt();
}

function showSystemInfo() {
	out(`
hostname: ${HOSTNAME}
ip-address: ${MGMT_IP}
public-ip-address: unknown
netmask: 255.255.255.248
default-gateway: 192.0.2.9
ip-assignment: static
ip6-assignment: static
ipv6-address: unknown
ipv6-link-local-address: fe80::200:5eff:fe00:5300/64
ipv6-default-gateway:
mac-address: 00:00:5e:00:53:00
time: Fri Oct  2 10:22:20 2026
uptime: 380 days, 23:53:04
family: 400
model: PA-440
serial: ${SERIAL}
base_mac: 00:00:5e:00:53:10
mac_count: 254
cloud-mode: non-cloud
sw-version: ${VERSION}
app-version: 9021-9653
threat-version: 0
logdb-version: 11.1.2
platform-family: 400
vpn-disable-mode: off
multi-vsys: off
operational-mode: normal
advanced-routing: off
device-certificate-status: Valid

`);
	prompt();
}

function showInterfaceLogical() {
	const rows = INTERFACES.map((i) =>
		i.name.padEnd(26) + String(i.id).padEnd(6) + '1'.padEnd(5) + i.zone.padEnd(17)
		+ i.forwarding.padEnd(25) + String(i.tag).padEnd(7) + i.address.padEnd(18)).join('\n');
	out(`
total configured logical interfaces: ${INTERFACES.length}

name                      id    vsys zone             forwarding               tag    address
------------------- ----- ---- ---------------- ------------------------ ------ ------------------
${rows}

`);
	prompt();
}

/** "show interface <name> | match "Port MAC"" - subinterfaces have no
 * "Port MAC address" line, so the filter leaves nothing to print. */
function showInterfacePortMac(name) {
	const networkInterface = INTERFACES.find((i) => i.name === name);
	if (!networkInterface) {
		out(`Invalid syntax.\n`);
	}
	else if (networkInterface.mac) {
		out(`  Port MAC address ${networkInterface.mac}\n`);
	}
	prompt();
}

function handleCommand(command) {
	const portMacMatch = command.match(/^show interface (\S+) \| match "Port MAC"$/);
	if (command === 'show config running') {
		showConfig(false);
	}
	else if (command === 'show config merged') {
		showConfig(true);
	}
	else if (command === 'show system info') {
		showSystemInfo();
	}
	else if (command === 'show interface logical') {
		showInterfaceLogical();
	}
	else if (portMacMatch) {
		showInterfacePortMac(portMacMatch[1]);
	}
	else if (command === 'set cli pager off' || command === '') {
		prompt();
	}
	else {
		out(`Unknown command: ${command.split(' ').pop()}\n`);
		prompt();
	}
}

function startCli() {
	onLine = handleCommand;
	out('\nNumber of failed attempts since last successful login: 0\n\n');
	prompt();
}

/** telnetd hands us a bare pty with no OS-level auth - the device's own CLI
 * has to prompt for login/Password itself, matching this driver's own
 * telnet `username`/`password`/`usernameAgain` modes. */
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
		out('Password: ');
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
		process.stderr.write('Usage: node Palo-Alto_PAN-OS.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
