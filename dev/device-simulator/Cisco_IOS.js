#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Cisco IOS (CSR1000V-like)
 *
 * Run by entrypoint.sh, never invoked directly by anything else:
 *   node Cisco_IOS.js snmp config    - print a full snmpd.conf, or nothing
 *   node Cisco_IOS.js ssh start      - start the CLI, already "authenticated"
 *                                       (real sshd already did password auth)
 *   node Cisco_IOS.js telnet start   - prompt Username/Password ourselves
 *                                       first (telnetd hands us a raw pty via
 *                                       -E, no OS-level auth at all - a real
 *                                       device's CLI does its own prompting
 *                                       over telnet too), then start the CLI
 *
 * Either way, this program only ever sees a byte stream on stdin/stdout
 * attached to a PTY, and puts that PTY into raw mode itself (same as any
 * interactive program like `less`/`vim` would) so it can echo characters
 * and react to single keystrokes (e.g. the "enable" password prompt) itself,
 * exactly like a real device's CLI engine does.
 *
 * Credentials default to admin/admin (enable secret: admin too), matching
 * the DeviceSshAccount/DeviceTelnetAccount used by the Netshot test/demo -
 * see NETSHOT_SIMULATOR_USERNAME/_PASSWORD/_ENABLE_SECRET below. SSH auth
 * itself happens at the OS/PAM level (entrypoint.sh creates the matching
 * Linux account), not in this script - only Telnet's login prompt and the
 * "enable" password check are handled here.
 *
 * NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
 * optional) customize the device identity - e.g. to run several
 * distinct-looking Cisco IOS instances side by side in a demo.
 */
'use strict';

const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'router1';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || '15.5(3)S7b';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '192.168.200.101';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'SNMPLOCATION';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'SNMPCONTACT';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || '96NETS96HOT';
const USERNAME = process.env.NETSHOT_SIMULATOR_USERNAME || 'admin';
const PASSWORD = process.env.NETSHOT_SIMULATOR_PASSWORD || 'admin';
const ENABLE_SECRET = process.env.NETSHOT_SIMULATOR_ENABLE_SECRET || 'admin';

// SNMP_COMMUNITY (v1/v2c) and SNMPV3_USER/_AUTH_PASSWORD/_PRIV_PASSWORD
// (authPriv, SHA/AES) are both always available side by side, same as a
// real device configured for either.
const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// Matches this driver's own snmpAutoDiscover() in
// src/main/resources/drivers/Cisco_IOS.js, so a real SNMP-based scan of this
// simulated device lands on the right driver, same as it would for the real thing.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.9.1.1537
sysdescr Cisco IOS Software, CSR1000V Software (X86_64_LINUX_IOSD-UNIVERSALK9-M), Version ${VERSION}, RELEASE SOFTWARE (fc1)
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

let mode = 'DISABLE'; // DISABLE -> ENABLE_PASSWORD -> ENABLE
let hiddenInput = false;
let onLine = null;

function out(s) {
	process.stdout.write(s);
}

/** Wires up raw-PTY reading once; `onLine(line)` handles each Enter-terminated line. */
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
				out('\r\n');
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
			else if (ch === '\x03') { // Ctrl-C
				buffer = '';
				out('^C\r\n');
				if (mode !== 'ENABLE_PASSWORD') {
					prompt();
				}
			}
			else {
				buffer += ch;
				if (!hiddenInput) {
					out(ch);
				}
			}
		}
	});
	process.stdin.on('end', () => process.exit(0));
}

function prompt() {
	if (mode === 'DISABLE') {
		out(`${HOSTNAME}>`);
	}
	else if (mode === 'ENABLE') {
		out(`${HOSTNAME}#`);
	}
}

function showVersion() {
	out(`Cisco IOS XE Software, Version 03.16.07b.S - Extended Support Release\r
Cisco IOS Software, CSR1000V Software (X86_64_LINUX_IOSD-UNIVERSALK9-M), Version ${VERSION}, RELEASE SOFTWARE (fc1)\r
Technical Support: http://www.cisco.com/techsupport\r
Copyright (c) 1986-2018 by Cisco Systems, Inc.\r
Compiled Fri 02-Mar-18 08:11 by mcpre\r
\r
\r
ROM: IOS-XE ROMMON\r
\r
${HOSTNAME} uptime is 18 weeks, 1 day, 43 minutes\r
Uptime for this control processor is 18 weeks, 1 day, 45 minutes\r
System returned to ROM by reload at 18:12:05 UTC Sun Feb 21 2021\r
System image file is "bootflash:packages.conf"\r
Last reload reason: <NULL>\r
\r
License Level: ax\r
License Type: Default. No valid license found.\r
Next reload license Level: ax\r
\r
cisco CSR1000V (VXE) processor (revision VXE) with 1090048K/6147K bytes of memory.\r
Processor board ID 90PIQM03HLS\r
4 Gigabit Ethernet interfaces\r
32768K bytes of non-volatile configuration memory.\r
3022136K bytes of physical memory.\r
7774207K bytes of virtual hard disk at bootflash:.\r
\r
Configuration register is 0x2102\r
`);
}

function showStartupHead() {
	out(`!\r
! Last configuration change at 12:12:12 UTC Sat Jan 12 2022 by admin\r
!\r
`);
}

function showRunning() {
	out(`!\r
! Last configuration change at 12:12:12 UTC Sat Jan 12 2022 by admin\r
!\r
version 15.5\r
service timestamps debug datetime msec\r
service timestamps log datetime msec\r
!\r
hostname ${HOSTNAME}\r
!\r
boot-start-marker\r
boot-end-marker\r
!\r
no aaa new-model\r
!\r
no ip domain lookup\r
!\r
interface Loopback0\r
 ip address 10.255.0.1 255.255.255.255\r
!\r
interface GigabitEthernet1\r
 description Management\r
 ip address ${MGMT_IP} 255.255.255.0\r
 negotiation auto\r
!\r
interface GigabitEthernet2\r
 ip address 10.0.0.1 255.255.255.254\r
 ip ospf network point-to-point\r
 negotiation auto\r
!\r
interface GigabitEthernet3\r
 no ip address\r
 shutdown\r
 negotiation auto\r
!\r
interface GigabitEthernet4\r
 no ip address\r
 shutdown\r
 negotiation auto\r
!\r
router ospf 1\r
 network 10.0.0.0 0.0.0.1 area 0\r
 network 10.255.0.1 0.0.0.0 area 0\r
!\r
ip forward-protocol nd\r
!\r
no ip http server\r
no ip http secure-server\r
ip ssh version 2\r
!\r
access-list 98 permit 192.168.200.0 0.0.0.255\r
!\r
snmp-server community cisco RO 98\r
snmp-server location ${LOCATION}\r
snmp-server contact ${CONTACT}\r
snmp-server enable traps config\r
!\r
!\r
control-plane\r
!\r
!\r
line con 0\r
 stopbits 1\r
line vty 0 4\r
 login local\r
 transport input telnet ssh\r
line vty 5 15\r
 login local\r
 transport input telnet ssh\r
!\r
ntp server pool.ntp.org\r
!\r
end\r
`);
}

function showInventory() {
	out(`NAME: "Chassis", DESCR: "Cisco CSR1000V Chassis"\r
PID: CSR1000V          , VID: V00, SN: ${SERIAL}\r
\r
NAME: "module R0", DESCR: "Cisco CSR1000V Route Processor"\r
PID: CSR1000V          , VID: V00, SN: JAB1616161C\r
\r
NAME: "module F0", DESCR: "Cisco CSR1000V Embedded Services Processor"\r
PID: CSR1000V          , VID:    , SN:\r
`);
}

const INTERFACES = {
	GigabitEthernet1: `GigabitEthernet1 is up, line protocol is up\r
Hardware is CSR vNIC, address is 5000.0001.0000 (bia 5000.0001.0000)\r
Internet address is ${MGMT_IP}/24\r
`,
	GigabitEthernet2: `GigabitEthernet2 is up, line protocol is up\r
Hardware is CSR vNIC, address is 5000.0001.0001 (bia 5000.0001.0001)\r
Internet address is 10.0.0.1/31\r
`,
	GigabitEthernet3: `GigabitEthernet3 is administratively down, line protocol is down\r
Hardware is CSR vNIC, address is 5000.0001.0002 (bia 5000.0001.0002)\r
`,
	GigabitEthernet4: `GigabitEthernet4 is administratively down, line protocol is down\r
Hardware is CSR vNIC, address is 5000.0001.0003 (bia 5000.0001.0003)\r
`,
	Loopback0: `Loopback0 is up, line protocol is up\r
Internet address is 10.255.0.1/32\r
`,
};

function handleCommand(command) {
	if (mode === 'ENABLE' && (command === 'show running-config' || command === 'show startup-config')) {
		showRunning();
		prompt();
	}
	else if (mode === 'ENABLE' && command === 'show startup-config | i ^! .*') {
		showStartupHead();
		prompt();
	}
	else if (mode === 'ENABLE' && command === 'show inventory') {
		showInventory();
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command === 'show version') {
		showVersion();
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command === 'terminal length 0') {
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command.startsWith('show interface')) {
		for (const name of Object.keys(INTERFACES)) {
			if (command.startsWith(`show interface ${name} `)) {
				out(INTERFACES[name]);
				break;
			}
		}
		prompt();
	}
	else if (mode === 'DISABLE' && command === 'enable') {
		mode = 'ENABLE_PASSWORD';
		hiddenInput = true;
		out('Password: ');
	}
	else if (mode === 'ENABLE_PASSWORD') {
		hiddenInput = false;
		if (command === ENABLE_SECRET) {
			mode = 'ENABLE';
		}
		else {
			out('% Bad secrets\r\n');
			mode = 'DISABLE';
		}
		prompt();
	}
	else {
		out('% Unknown command or computer name, or unable to find computer address\r\n');
		prompt();
	}
}

function startCli() {
	onLine = handleCommand;
	prompt();
}

/** telnetd hands us a bare pty with no OS-level auth - the device's own CLI
 * has to prompt for Username/Password itself, matching this driver's own
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
				out('% Login invalid\r\n\r\n');
				out('Username: ');
				onLine = onUsernameLine;
			}
		};
		out('Password: ');
	};
	out('Username: ');
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
		process.stderr.write('Usage: node Cisco_IOS.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
