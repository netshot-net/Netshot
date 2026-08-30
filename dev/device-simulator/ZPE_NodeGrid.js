#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - ZPE NodeGrid
 *
 * Run by entrypoint.sh:
 *   node ZPE_NodeGrid.js snmp config    - print a full snmpd.conf, or nothing
 *   node ZPE_NodeGrid.js ssh start      - start the CLI, already "authenticated"
 *   node ZPE_NodeGrid.js telnet start   - this driver is SSH-only, so this
 *                                         just declines and disconnects
 * See Cisco_IOS.js for the general design notes (raw-PTY, telnetd -E).
 *
 * The pager sends a full "\r" to advance to the next page (no bare/raw
 * keystroke needed), so this device only needs normal CR-buffered dispatch.
 *
 * NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
 * optional) customize the device identity - e.g. to run several
 * distinct-looking ZPE NodeGrid instances side by side in a demo. This
 * driver is SSH-only, so NETSHOT_SIMULATOR_USERNAME/_PASSWORD (SSH auth
 * itself happens at the OS/PAM level, set up by entrypoint.sh - this script
 * only needs the username to show in its prompt) are the only
 * credential-related ones that apply here.
 */
'use strict';

const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'NODEGRID-1';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || '3.1.16';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '10.10.16.16';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Nodegrid';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'support@zpesystems.com';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || '1416161616';
const USERNAME = process.env.NETSHOT_SIMULATOR_USERNAME || 'admin';
const PAGE_SIZE = 13;

// SNMP_COMMUNITY (v1/v2c) and SNMPV3_USER/_AUTH_PASSWORD/_PRIV_PASSWORD
// (authPriv, SHA/AES) are both always available side by side, same as a
// real device configured for either.
const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// Matches this driver's own snmpAutoDiscover() in
// src/main/resources/drivers/ZPE_NodeGrid.js, so a real SNMP-based scan of
// this simulated device lands on the right driver, same as it would for the real
// thing.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.42518.1
sysdescr NodeGrid Serial Console
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

let mode = 'BASIC';
let pagedLines = [];
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
	if (mode === 'BASIC') {
		out(`[${USERNAME}@${HOSTNAME} /]# `);
	}
}

function printPaged(text) {
	if (text !== null) {
		pagedLines = text.split('\n');
	}
	let chunk = '';
	let i = 0;
	while (pagedLines.length > 0 && i < PAGE_SIZE) {
		chunk += pagedLines.shift() + '\n';
		i++;
	}
	out(chunk);
	if (pagedLines.length > 0) {
		mode = 'PAGING';
		out('-- more --:');
	}
	else {
		mode = 'BASIC';
		prompt();
	}
}

function cmdShowSystemAbout() {
	printPaged(`system: NodeGrid Serial Console\r
licenses: 16\r
software: v${VERSION} (Jul 16 2016 - 16:16:16)\r
cpu: Intel(R) Atom(TM) CPU E3827  @ 1.74GHz\r
cpu_cores: 2\r
bogomips_per_core: 3416.16\r
serial_number: ${SERIAL}\r
uptime: 16 days, 16 hours, 16 minutes\r
model: NSC-T16S\r
part_number:  NSC-T16S-STND-DAC-F-SFP\r
bios_version: 80168T00\r
psu: 2\r
\x07`);
}

function cmdShowSystemUsageMemory() {
	printPaged(`  memory type  total (kb)  used (kb)  free (kb)\r
  ===========  ==========  =========  =========\r
  Mem          3934644     2224184    1710460  \r
  Swap         976892      520612     456280   \r
\x07`);
}

function cmdShowSettingsDevices() {
	printPaged(`  * name                     connected through  type          access \r
  * =======================  =================  ============  =======\r
  monitoring   \r
  =============\r
  * AAA-AAA-AAA-AAA-AAAA    ttyS1              local_serial  enabled\r
  not supported\r
  * BBB-BBB-BBB-BBB-BBBB    ttyS2              local_serial  enabled\r
  not supported\r
  * CCC-CCC-CCC-CCC-CCCC    ttyS3              local_serial  enabled\r
  not supported\r
  * DDD-DDD-DDD-DDD-DDDD    ttyS4              local_serial  enabled\r
  not supported\r
  * EEE-EEE-EEE-EEE-EEEE    ttyS5              local_serial  enabled\r
  not supported\r
  * FFF-FFF-FFF-FFF-FFFF    ttyS6              local_serial  enabled\r
  not supported\r
  * GGG-GGG-GGG-GGG-GGGG    ttyS7              local_serial  enabled\r
  not supported\r
  * HHH-HHH-HHH-HHH-HHHH    ttyS8              local_serial  enabled\r
  not supported\r
  * III-III-III-III-IIII    ttyS9              local_serial  enabled\r
  not supported\r
  * JJJ-JJJ-JJJ-JJJ-JJJJ    ttyS10             local_serial  enabled\r
  not supported\r
  * usbS1                   usbS1              usb_serialB   enabled\r
  not supported\r
`);
}

function cmdShowSettings() {
	out(`/settings/system_preferences help_url=http://www.zpesystems.com/ng/v3_0/NodeGrid-UserGuide-v3_0.pdf\r
/settings/system_preferences idle_timeout=1500\r
/settings/system_preferences enable_banner=no\r
/settings/network_connections/ETH0 ethernet_interface=eth0\r
/settings/network_connections/ETH0 connect_automatically=no\r
/settings/network_connections/ETH0 set_as_primary_connection=yes\r
/settings/network_connections/ETH0 enable_lldp=no\r
/settings/network_connections/ETH0 ipv4_mode=dhcp\r
/settings/network_connections/ETH0 ipv6_mode=address_auto_configuration\r
/settings/network_connections/ETH1 ethernet_interface=eth1\r
/settings/network_connections/ETH1 connect_automatically=no\r
/settings/network_connections/ETH1 set_as_primary_connection=no\r
/settings/network_connections/ETH1 enable_lldp=no\r
/settings/network_connections/ETH1 ipv4_mode=dhcp\r
/settings/network_connections/ETH1 ipv6_mode=address_auto_configuration\r
/settings/network_connections/bond connect_automatically=yes\r
/settings/network_connections/bond set_as_primary_connection=no\r
/settings/network_connections/bond enable_lldp=no\r
/settings/network_connections/bond primary_interface=eth0\r
/settings/network_connections/bond secondary_interface=eth1\r
/settings/network_connections/bond bonding_mode=active_backup\r
/settings/network_connections/bond link_monitoring=mii\r
/settings/network_connections/bond monitoring_frequency=100\r
/settings/network_connections/bond link_up_delay=0\r
/settings/network_connections/bond link_down_delay=0\r
/settings/network_connections/bond arp_validate=none\r
/settings/network_connections/bond bond_mac_policy=primary_interf\r
/settings/network_connections/bond ipv4_mode=static\r
/settings/network_connections/bond ipv4_address=${MGMT_IP}\r
/settings/network_connections/bond ipv4_bitmask=24\r
/settings/network_connections/bond ipv4_gateway=10.10.16.254\r
/settings/network_connections/bond ipv6_mode=no_ipv6_address\r
/settings/snmp/system syscontact=${CONTACT}\r
/settings/snmp/system syslocation="${LOCATION} "\r
/settings/local_accounts/admin username=admin\r
\r
`);
	prompt();
}

function cmdEventSystemAudit() {
	out(`<2022-01-03T04:01:16Z> Event ID 201: A user logged out of the system. User: alib/r/naba@10.16.2.3. Session type: HTTPS.\r
<2022-01-03T11:21:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r
<2022-01-03T11:21:16Z> Event ID 201: A user logged out of the system. User: nets/r/nhot@10.16.2.16. Session type: SSH.\r
<2022-01-03T12:23:16Z> Event ID 200: A user logged into the system. User: homer@/r/n10.16.2.3. Session type: SSH. Authentication Method: TACACS+.\r
<2022-01-03T12:23:16Z> Event ID 201: A user logged out of the system. User: home/r/nr@10.16.2.3. Session type: SSH.\r
<2022-01-04T01:52:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r
<2022-01-04T01:53:16Z> Event ID 201: A user logged out of the system. User: nets/r/nhot@10.16.2.16. Session type: SSH.\r
<2022-01-04T02:52:16Z> Event ID 108: The configuration has changed. Change made by user: homer.\r
<2022-01-04T03:04:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r
`);
}

function cmdHostname() {
	printPaged(`${HOSTNAME}\r\n`);
}

function handleCommand(command) {
	if (mode === 'PAGING' && command === '') {
		printPaged(null);
	}
	else if (mode === 'BASIC' && /^ *hostname/.test(command)) {
		cmdHostname();
	}
	else if (mode === 'BASIC' && /^ *show \/?system\/about\/?/.test(command)) {
		cmdShowSystemAbout();
	}
	else if (mode === 'BASIC' && /^ *show \/?system\/system_usage\/memory_usage\/?/.test(command)) {
		cmdShowSystemUsageMemory();
	}
	else if (mode === 'BASIC' && /^ *show \/?settings\/devices\/?/.test(command)) {
		cmdShowSettingsDevices();
	}
	else if (mode === 'BASIC' && /^ *show_settings/.test(command)) {
		cmdShowSettings();
	}
	else if (mode === 'BASIC' && /^ *event_system_audit/.test(command)) {
		cmdEventSystemAudit();
		out('(h->Help, q->Quit)');
		mode = 'SCREEN';
	}
	else if (mode === 'SCREEN' && command.trim() === 'q') {
		out('\r\n');
		mode = 'BASIC';
		prompt();
	}
	else {
		out(`Error: Invalid command: ${command}\r\n`);
		prompt();
	}
}

function startCli() {
	onLine = handleCommand;
	prompt();
}

function main() {
	const [subcommand, action] = process.argv.slice(2);
	if (subcommand === 'snmp' && action === 'config') {
		out(SNMP_CONFIG);
		return;
	}
	if (subcommand === 'telnet' && action === 'start') {
		out('This device does not support Telnet access.\r\n');
		return;
	}
	startRawIO();
	if (subcommand === 'ssh' && action === 'start') {
		startCli();
	}
	else {
		process.stderr.write('Usage: node ZPE_NodeGrid.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
