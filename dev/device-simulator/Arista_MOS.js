#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Arista MOS (Metamako MetaConnect)
 *
 * Run by entrypoint.sh:
 *   node Arista_MOS.js snmp config    - print a full snmpd.conf, or nothing
 *   node Arista_MOS.js ssh start      - start the CLI, already "authenticated"
 *   node Arista_MOS.js telnet start   - prompt Username/Password ourselves
 *                                        first, then start the CLI
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
 * distinct-looking Arista MOS instances side by side in a demo. The same
 * SERIAL is reported by both "show version" and "show inventory", same as a
 * real device would.
 */
'use strict';

const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'switch1';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || '0.31.0';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '10.18.25.40';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'SNMPLOCATION';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'SNMPCONTACT';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || 'C48-A6-12627-0';
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
// src/main/resources/drivers/Arista_MOS.js, so a real SNMP-based scan of
// this simulated device lands on the right driver, same as it would for the real
// thing.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.43191.1.2.1
sysdescr Metamako MOS - MetaConnect 48
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

let mode = 'DISABLE'; // DISABLE -> ENABLE
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
			else {
				buffer += ch;
				out(ch);
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
	out(`Device: Metamako MetaConnect 48
SKU: DCS-7130-48
Serial number: ${SERIAL}
 
Software image version: ${VERSION}
Internal build ID: mos-0.31+12
Applications: netconf-0.9
 
System management controller version: 1.3.2 release-platmicro-1.3+2
 
Uptime: 177 days, 9:00:05.700000
 
`);
}

function showRunning() {
	let cfg = `! command: show running-config
! time: Tue 11 Oct 2022 09:02:39
! device: ${HOSTNAME} (C48-A6, MOS-${VERSION})
 
hostname ${HOSTNAME}
username admin secret sha512 $6$Iwn/TscxWEdXQVcu$yeqcWHWUt1qVmldsfPVM/O9z2hiYs/iL35WNP6zOcM.PwkGRVgTO8r3kWp3k4DpRGHYnohK/xx3gw//rxqlPo1
tacacs-server host 10.18.18.1 key 7 095C4F1A0A1218000F
tacacs-server host 10.18.19.1 key 7 12090404011C03162E
 
clock timezone GB
ntp server 10.18.18.12 prefer
ntp server 10.18.19.12
 
logging host 10.1.18.10
 
`;
	for (let i = 1; i <= 48; i++) {
		cfg += `interface et${i}\n`;
		if (i === 13) {
			cfg += '    description to switch2\n    source et15\n';
		}
		else if (i === 15) {
			cfg += '    description to switch2\n    source et13\n';
		}
		else if (i === 21) {
			cfg += '    description to switch3\n    source et23\n';
		}
		else if (i === 23) {
			cfg += '    description to switch4\n    negotiation\n    source et21\n';
		}
		else if (i <= 12) {
			const src = { 1: 3, 2: 1, 3: 1, 4: 3, 5: 7, 6: 5, 7: 5, 8: 7, 9: 11, 10: 9, 11: 9, 12: 11 }[i];
			cfg += `    source et${src}\n`;
		}
		else {
			cfg += '    shutdown\n';
		}
		cfg += ' \n';
	}
	cfg += `interface ma1
    ip address ${MGMT_IP} 255.255.255.0
    ip default-gateway 10.18.25.254
 
management snmp
    snmp-server community comm1 ro
    snmp-server community comm2 ro
    snmp-server community comm3 ro
    snmp-server host 10.1.18.135 version 2c comm1
    snmp-server host 10.1.20.135 version 2c comm1
    snmp-server host 10.2.18.135 version 2c comm2
    snmp-server host 10.2.18.135 version 2c comm2
 
end
`;
	out(cfg);
}

function showInventory() {
	out(`System Information:
    Model: C48-A6
    Serial number: ${SERIAL}
    Software image version: ${VERSION}
    System management controller version: 1.3.2 release-platmicro-1.3+2
    Description: 1RU 48-port layer-1 crosspoint switch
 
PLD:
    Specification: 2.4
    Version: P505.001C
 
Mezzanine Module Information:
 
FPGA Information:
 
Clock Module Information:
 
Power Supply Information: System has 2 power supply slots
 
Slot Model            Serial           Airflow              Capacity
---- ---------------- ---------------- -------------------- --------
1    DS460S-3-002     J756TY005WZBZ    FRONT-TO-BACK (STD)      460W
2    DS460S-3-002     J756TY005WZBY    FRONT-TO-BACK (STD)      460W
 
Fan Information: System has 4 fan modules
 
Fan  Airflow
---- ------------------------
1    FRONT-TO-BACK (STD)
2    FRONT-TO-BACK (STD)
3    FRONT-TO-BACK (STD)
4    FRONT-TO-BACK (STD)
 
Port Information: System has 49 ports
    Switched: 48
    Management: 1
 
Transceiver Information:
 
Port Name                   Type        Vendor          Vendor PN        Vendor SN
---- ---------------------- ----------- --------------- ---------------- ---------------
et1                         10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7756
et2                         10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7856
et3                         1000BASE-LX CISCO           RTXM191-404-C88  ACW21170215
et4                         10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7956
et5
et6
et7
et8
et9
et10                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7156
et11
et12                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7256
et13 to switch2             10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7356
et14
et15 to switch2             10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7655
et16
et17
et18
et19
et20
et21 to switch3             10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7658
et22
et23 to switch4             10GBASE-LR  OEM             SFP-10G-LR-CURV  XN2353C7651
et24
et25
et26
et27
et28
et29
et30
et31
et32
et33
et34
et35
et36
et37
et38
et39
et40
et41                        1000BASE-SX OEM             GLC-SX-MM-CURV   XN2353C7642
et42
et43                        1000BASE-LX OEM             GLC-LH-SM-CURV   XN2353C7643
et44                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7646
et45                        10GBASE-LR  OEM             SFP-10G-LR-CURV  N153517EF105
et46                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7658
et47                        10GBASE-LR  CISCO-FINISAR   FTLX1474D3BCL-C1 FNS170566J9
et48                        10GBASE-SR  OEM             SFP-10G-SR-CURV  XN2353C7659
ma1                         100/1000
Drives:
    Count: 1
    /dev/sda (internal):
        User Capacity: 64,023,257,088 bytes [64.0 GB]
        ATA Version is: ACS-2 (minor revision not indicated)
        Local Time is: Tue Oct 15 11:18:09 2022 BST
        SATA Version is: SATA 3.1, 6.0 Gb/s (current: 3.0 Gb/s)
        Power mode is: ACTIVE or IDLE
        Serial Number: D271220319
        Device Model: TS64GMTS400
        Sector Size: 512 bytes logical/physical
        Firmware Version: O1225G
        Model Family: Silicon Motion based SSDs
        SMART support is: Enabled
        Rotation Rate: Solid State Device
 
`);
}

function showLogging() {
	out(`Oct 10 09:21:11 ${HOSTNAME} user.info cli: Configured from cli by other on pts/0 (10.218.2.3)
Oct 11 09:01:15 ${HOSTNAME} user.info cli: Configured from cli by admin on pts/0 (10.218.2.3)
`);
}

function handleCommand(command) {
	if (mode === 'ENABLE' && command === 'show running-config') {
		showRunning();
		prompt();
	}
	else if (mode === 'ENABLE' && command === 'show inventory') {
		showInventory();
		prompt();
	}
	else if (mode === 'ENABLE' && command === 'show logging system | include Configured') {
		showLogging();
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command === 'show version') {
		showVersion();
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command === 'show snmp v2-mib location') {
		out(`Location: ${LOCATION}\n`);
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command === 'show snmp v2-mib contact') {
		out(`Contact: ${CONTACT}\n`);
		prompt();
	}
	else if ((mode === 'ENABLE' || mode === 'DISABLE') && command === 'terminal length 0') {
		prompt();
	}
	else if (mode === 'DISABLE' && command === 'enable') {
		mode = 'ENABLE';
		prompt();
	}
	else {
		out('           ^\n% Invalid input detected at \'^\' marker.\n');
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
				out('% Authentication failed.\r\n\r\n');
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
		process.stderr.write('Usage: node Arista_MOS.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
