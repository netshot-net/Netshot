#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - HPE ArubaOS-Switch (2530-24G-PoE+)
 *
 * Run by entrypoint.sh:
 *   node HPE_ArubaOSSwitch.js snmp config    - print a full snmpd.conf, or nothing
 *   node HPE_ArubaOSSwitch.js ssh start      - start the CLI, already "authenticated"
 *   node HPE_ArubaOSSwitch.js telnet start   - prompt Username/Password ourselves
 *                                              first, then start the CLI
 * See Cisco_IOS.js for the general design notes (raw-PTY, telnetd -E).
 *
 * Either way, the switch shows its banner and waits for "Press any key to
 * continue" first, as the real one does.
 *
 * NETSHOT_SIMULATOR_VARIANT picks the platform:
 *   (unset)      standalone switch
 *   "commander"  Stack Management Commander: the member selection menu
 *                ("Enter switch number to connect to or <CR>:") comes before
 *                the CLI, <CR> connects to the Commander itself
 *
 * Credentials default to admin/admin, see NETSHOT_SIMULATOR_USERNAME/_PASSWORD.
 * NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
 * optional) customize the device identity.
 */
'use strict';

const COMMANDER = process.env.NETSHOT_SIMULATOR_VARIANT === 'commander';
const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'sw3';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || 'YA.16.11.0010';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '192.0.2.30';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Room 42';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'netshot@example.com';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || 'CN00000001';
const USERNAME = process.env.NETSHOT_SIMULATOR_USERNAME || 'admin';
const PASSWORD = process.env.NETSHOT_SIMULATOR_PASSWORD || 'admin';

const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// Matches this driver's own snmpAutoDiscover() in
// src/main/resources/drivers/HPE_ArubaOSSwitch.js.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.11.2.3.7.11.141
sysdescr HP J9773A 2530-24G-PoEP Switch, revision ${VERSION}, ROM YA.15.20 (/ws/swbuildm/rel_yakima_qaoff/code/build/lp(swbuildm_rel_yakima_qaoff_rel_yakima))
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

const RUNNING_CONFIG = `
; J9773A Configuration Editor; Created on release #${VERSION}
; Ver #14:41.44.00.04.19.02.13.98.82.34.61.18.28.f3.84.9c.63.ff.37.27:05
hostname "${HOSTNAME}"
module 1 type j9773a
snmp-server contact "${CONTACT}"
snmp-server location "${LOCATION}"
oobm
   disable
   exit
vlan 1
   name "DEFAULT_VLAN"
   untagged 1-28
   ip address ${MGMT_IP} 255.255.255.0
   exit
vlan 10
   name "USERS"
   tagged 1
   no ip address
   exit
interface 1
   name "Uplink to core"
   exit
password manager
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
	out(`${HOSTNAME}# `);
}

function invalidInput(command) {
	out(`Invalid input: ${command.split(' ').pop()}\n\n`);
	prompt();
}

function showVersion() {
	out(`
Image stamp:    /ws/swbuildm/rel_yakima_qaoff/code/build/lp(swbuildm_rel_yakima_qaoff_rel_yakima)
                Mar 10 2023 05:32:18
                ${VERSION}
                1045
Boot Image:     Primary

Build Options:  QA

`);
	prompt();
}

function showSystem() {
	out(`
 Status and Counters - General System Information

  System Name        : ${HOSTNAME}
  System Contact     : ${CONTACT}
  System Location    : ${LOCATION}

  MAC Age Time (sec) : 300

  Time Zone          : 0
  Daylight Time Rule : None

  Software revision  : ${VERSION}        Base MAC Addr      : 00005e-005330
  ROM Version        : YA.15.20             Serial Number      : ${SERIAL}

  Up Time            : 12 days              Memory   - Total   : 140,267,008
  CPU Util (%)       : 0                               Free    : 79,005,948

  IP Mgmt  - Pkts Rx : 1,029,383            Packet   - Total   : 6750
             Pkts Tx : 885,281              Buffers    Free    : 5084
                                                       Lowest  : 4936
                                                       Missed  : 0

`);
	prompt();
}

function showFlash() {
	out(`
Image             Size (bytes) Date     Version
----------------- ------------ -------- --------------
Primary Image   :     16723453 03/10/23 ${VERSION}
Secondary Image :     16609382 08/23/22 YA.16.10.0012

Boot ROM Version
----------------
Primary Boot ROM Version   : YA.15.20

Default Boot Image   : Primary

`);
	prompt();
}

function showModules() {
	out(`
 Status and Counters - Module Information

  Chassis: 2530-24G-PoEP J9773A          Serial Number:   ${SERIAL}

`);
	prompt();
}

function showInterfacesConfig() {
	out(`
 Port Settings

  Port  Type      | Enabled Mode         Flow Ctrl MDI
  ----- --------- + ------- ------------ --------- ----
  1     100/1000T | Yes     Auto         Disable   Auto
  2     100/1000T | Yes     Auto         Disable   Auto
  3     100/1000T | No      Auto         Disable   Auto

`);
	prompt();
}

function handleCommand(command) {
	if (command === 'show running-config') {
		out(`\nRunning configuration:\n${RUNNING_CONFIG}\n`);
		prompt();
	}
	else if (command === 'show version') {
		showVersion();
	}
	else if (command === 'show system') {
		showSystem();
	}
	else if (command === 'show flash') {
		showFlash();
	}
	else if (command === 'show modules') {
		showModules();
	}
	else if (command === 'show interfaces config') {
		showInterfacesConfig();
	}
	else if (command === 'no page' || command === '') {
		prompt();
	}
	else {
		invalidInput(command);
	}
}

function startCli() {
	onLine = handleCommand;
	out('\n');
	prompt();
}

/** Stack Management Commander member selection, before the Commander's own CLI. */
function startStackSelect() {
	onLine = function onMemberLine(line) {
		if (line === '') {
			startCli();
		}
		else {
			out(`\nInvalid switch number.\n`);
			startStackSelect();
		}
	};
	out(`
 HP J9773A 2530-24G-PoEP Switch
 Stack Management Commander

  Stack Name : STACK1

  SN  MAC Address   System Name     Device Type                  Status
  --- ------------- --------------- ---------------------------- ----------------
   0  00005e-005330 ${HOSTNAME.padEnd(15)} HP J9773A 2530-24G-PoEP      Commander Up
   1  00005e-005331 ${(HOSTNAME + '-m1').padEnd(15)} HP J9773A 2530-24G-PoEP      Member Up

Enter switch number to connect to or <CR>: `);
}

function afterLogin() {
	if (COMMANDER) {
		startStackSelect();
	}
	else {
		startCli();
	}
}

function startTelnetLogin() {
	let typedUsername = '';
	onLine = function onUsernameLine(line) {
		typedUsername = line;
		onLine = function onPasswordLine(line) {
			if (typedUsername === USERNAME && line === PASSWORD) {
				afterLogin();
			}
			else {
				out('Incorrect Credentials\n\nUsername: ');
				onLine = onUsernameLine;
			}
		};
		out('Password: ');
	};
	out('Username: ');
}

/** The banner, then "Press any key to continue". */
function startBanner(next) {
	onLine = next;
	out(`
HP J9773A 2530-24G-PoEP Switch
Software revision ${VERSION}

(C) Copyright 2023 Hewlett Packard Enterprise Development LP

                            RESTRICTED RIGHTS LEGEND
 Confidential computer software. Valid license from Hewlett Packard Enterprise
 Development LP required for possession, use or copying.

Press any key to continue`);
}

function main() {
	const [subcommand, action] = process.argv.slice(2);
	if (subcommand === 'snmp' && action === 'config') {
		out(SNMP_CONFIG);
		return;
	}
	startRawIO();
	if (subcommand === 'ssh' && action === 'start') {
		startBanner(afterLogin);
	}
	else if (subcommand === 'telnet' && action === 'start') {
		startBanner(startTelnetLogin);
	}
	else {
		process.stderr.write('Usage: node HPE_ArubaOSSwitch.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
