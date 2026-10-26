#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Cisco Small Business Sx switches
 *
 * Run by entrypoint.sh:
 *   node Cisco_Sx.js snmp config    - print a full snmpd.conf, or nothing
 *   node Cisco_Sx.js ssh start      - start the CLI (the switch prompts for
 *                                     its own credentials over SSH as well)
 *   node Cisco_Sx.js telnet start   - prompt the credentials, then start the CLI
 * See Cisco_IOS.js for the general design notes (raw-PTY, telnetd -E).
 *
 * NETSHOT_SIMULATOR_VARIANT picks the platform:
 *   (unset)  SG300-28 (legacy firmware 1.4): "User Name:" prompt,
 *            "terminal datadump", "show system" and "show inventory"
 *   "sx220"  SG220-26P (firmware 1.1): "Username:" prompt, password lifetime
 *            prompt after login, "terminal length 0", no "show system" or
 *            "show inventory" - the identity is in "show version" only
 *
 * Like the real switches, the CLI asks for its own User Name/Password once
 * the SSH session is up, in addition to the SSH authentication (done at the
 * OS/PAM level, see entrypoint.sh).
 *
 * Credentials default to admin/admin, see NETSHOT_SIMULATOR_USERNAME/_PASSWORD.
 * NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
 * optional) customize the device identity.
 */
'use strict';

const SX220 = process.env.NETSHOT_SIMULATOR_VARIANT === 'sx220';
const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || (SX220 ? 'sw2' : 'sw1');
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || (SX220 ? '1.1.0.14' : '1.4.11.5');
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '192.0.2.20';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Room 42';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'netshot@example.com';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || (SX220 ? 'PSZ00000220' : 'PSZ00000300');
const USERNAME = process.env.NETSHOT_SIMULATOR_USERNAME || 'admin';
const PASSWORD = process.env.NETSHOT_SIMULATOR_PASSWORD || 'admin';
const MODEL = SX220 ? 'SG220-26P' : 'SG300-28';
const SYSTEM_MAC = '00:00:5e:00:53:20';

const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// Matches this driver's own snmpAutoDiscover() in
// src/main/resources/drivers/Cisco_Sx.js.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid ${SX220 ? '1.3.6.1.4.1.9.6.1.86.26.2' : '1.3.6.1.4.1.9.6.1.83.28.1'}
sysdescr ${MODEL} ${SX220 ? '26-Port Gigabit PoE Smart Plus Switch' : '28-Port Gigabit Managed Switch'}
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

const RUNNING_CONFIG = `config-file-header
${HOSTNAME}
v${VERSION}
CLI v1.0
@
!
hostname ${HOSTNAME}
username ${USERNAME} password encrypted 0000000000000000000000000000000000000000 privilege 15
snmp-server location "${LOCATION}"
snmp-server contact "${CONTACT}"
ip ssh server
!
interface vlan 1
 ip address ${MGMT_IP} 255.255.255.0
 no ip address dhcp
!
interface vlan 10
 name USERS
 shutdown
!
interface gigabitethernet1
 description "Uplink to core"
 switchport mode trunk
!
interface gigabitethernet2
 switchport access vlan 10
!
exit
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
	out(`${HOSTNAME}#`);
}

function unrecognized() {
	out(`\n% Unrecognized command\n\n`);
	prompt();
}

function showVersion() {
	if (SX220) {
		out(`
Cisco Sx220 Series Switch Software, Version ${VERSION}, RELEASE SOFTWARE
Copyright (c) 2015 by Cisco Systems, Inc.
ROM: Bootstrap program is Sx220 boot loader
BOOTLDR: Sx220 Boot Loader Version 1.0.0.6, RELEASE SOFTWARE
${HOSTNAME} uptime is 12 days, 3 hours, 41 mins, 29 secs
Base MAC Address                    : ${SYSTEM_MAC}
Model Number                        : ${MODEL}
Serial Number                       : ${SERIAL}
PID                                 : ${MODEL}-K9
VID                                 : V01

`);
	}
	else {
		out(`
SW version    ${VERSION} ( date  13-Feb-2020 time  16:18:35 )
Boot version    1.3.5.06 ( date  21-Jul-2013 time  15:12:10 )
HW version    V04

`);
	}
	prompt();
}

function showSystem() {
	if (SX220) {
		unrecognized();
		return;
	}
	out(`
System Description:                       ${MODEL} 28-Port Gigabit Managed Switch
System Up Time (days,hour:min:sec):       12,03:41:29
System Contact:                           ${CONTACT}
System Name:                              ${HOSTNAME}
System Location:                          ${LOCATION}
System MAC Address:                       ${SYSTEM_MAC}
System Object ID:                         1.3.6.1.4.1.9.6.1.83.28.1

`);
	prompt();
}

function showInventory() {
	if (SX220) {
		unrecognized();
		return;
	}
	out(`
NAME: "1"       DESCR: "${MODEL} 28-Port Gigabit Managed Switch"
PID: ${MODEL}-K9      VID: V04     SN: ${SERIAL}

`);
	prompt();
}

function handleCommand(command) {
	if (command === 'show running-config' || command === 'show startup-config') {
		out(`\n${RUNNING_CONFIG}\n`);
		prompt();
	}
	else if (command === 'show version') {
		showVersion();
	}
	else if (command === 'show system') {
		showSystem();
	}
	else if (command === 'show inventory') {
		showInventory();
	}
	else if (command === (SX220 ? 'terminal length 0' : 'terminal datadump') || command === '') {
		prompt();
	}
	else {
		unrecognized();
	}
}

function startCli() {
	onLine = handleCommand;
	out('\n');
	prompt();
}

/** The switch CLI's own login, over both Telnet and SSH. The Sx220 then
 * warns about the password age before giving the prompt. */
function startLogin() {
	const usernamePrompt = SX220 ? 'Username:' : 'User Name:';
	let typedUsername = '';
	onLine = function onUsernameLine(line) {
		typedUsername = line;
		onLine = function onPasswordLine(line) {
			if (typedUsername !== USERNAME || line !== PASSWORD) {
				out('Authentication failed - Authentication failure.\n\n');
				out(usernamePrompt);
				onLine = onUsernameLine;
			}
			else if (SX220) {
				out('\nYour password has exceeded the maximum lifetime. '
					+ 'Please change your password.\nDo you want to change it now (Y/N)[N] ?');
				onLine = startCli;
			}
			else {
				startCli();
			}
		};
		out('Password:');
	};
	out(`\n${usernamePrompt}`);
}

function main() {
	const [subcommand, action] = process.argv.slice(2);
	if (subcommand === 'snmp' && action === 'config') {
		out(SNMP_CONFIG);
		return;
	}
	startRawIO();
	if ((subcommand === 'ssh' || subcommand === 'telnet') && action === 'start') {
		startLogin();
	}
	else {
		process.stderr.write('Usage: node Cisco_Sx.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
