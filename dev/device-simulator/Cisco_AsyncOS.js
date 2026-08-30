#!/usr/bin/env node
/**
 * Netshot Dev Device Simulator - Cisco AsyncOS (Secure Email/Web Manager appliance)
 *
 * Run by entrypoint.sh:
 *   node Cisco_AsyncOS.js snmp config    - print a full snmpd.conf, or nothing
 *   node Cisco_AsyncOS.js ssh start      - start the CLI, already "authenticated"
 *   node Cisco_AsyncOS.js telnet start   - this driver is SSH-only, so this
 *                                          just declines and disconnects
 * See Cisco_IOS.js for the general design notes (raw-PTY, telnetd -E).
 *
 * The pager on this device sends a single raw space keystroke (no CR) to
 * advance to the next page, so - unlike ZPE - this one needs to react to a
 * bare keystroke immediately, bypassing the normal line buffer.
 *
 * NETSHOT_SIMULATOR_NAME/_VERSION/_MGMT_IP/_LOCATION/_CONTACT/_SERIAL (all
 * optional) customize the device identity - e.g. to run several
 * distinct-looking Cisco AsyncOS instances side by side in a demo. This
 * driver is SSH-only and its prompt doesn't show a username, so
 * NETSHOT_SIMULATOR_USERNAME/_PASSWORD (SSH auth itself happens at the
 * OS/PAM level, set up by entrypoint.sh) aren't referenced in this script at
 * all, but still apply.
 */
'use strict';

const HOSTNAME = process.env.NETSHOT_SIMULATOR_NAME || 'esa.netshot.lab';
const VERSION = process.env.NETSHOT_SIMULATOR_VERSION || '15.0.0-416';
const MGMT_IP = process.env.NETSHOT_SIMULATOR_MGMT_IP || '172.16.254.10';
const LOCATION = process.env.NETSHOT_SIMULATOR_LOCATION || 'Rack 1';
const CONTACT = process.env.NETSHOT_SIMULATOR_CONTACT || 'someone@example.com';
const SERIAL = process.env.NETSHOT_SIMULATOR_SERIAL || 'BE59F09812A56BFCA12A-1478FC8D8A90';
const PAGE_SIZE = 20;
const PAGER = '-Press Any Key For More-';
const POST_PAGER = '\r                         \r';

// SNMP_COMMUNITY (v1/v2c) and SNMPV3_USER/_AUTH_PASSWORD/_PRIV_PASSWORD
// (authPriv, SHA/AES) are both always available side by side, same as a
// real device configured for either.
const SNMP_COMMUNITY = process.env.NETSHOT_SIMULATOR_SNMP_COMMUNITY || 'public';
const SNMPV3_USER = process.env.NETSHOT_SIMULATOR_SNMPV3_USER || 'netshotv3';
const SNMPV3_AUTH_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_AUTH_PASSWORD || 'admin1234';
const SNMPV3_PRIV_PASSWORD = process.env.NETSHOT_SIMULATOR_SNMPV3_PRIV_PASSWORD || 'admin1234';

// Matches this driver's own snmpAutoDiscover() in
// src/main/resources/drivers/Cisco_AsyncOS.js, so a real SNMP-based scan of
// this simulated device lands on the right driver, same as it would for the real
// thing.
const SNMP_CONFIG = `rocommunity ${SNMP_COMMUNITY}
syslocation ${LOCATION}
syscontact ${CONTACT}
sysobjectid 1.3.6.1.4.1.15497.1.1
sysdescr Cisco AsyncOS Version: ${VERSION}
createUser ${SNMPV3_USER} SHA "${SNMPV3_AUTH_PASSWORD}" AES "${SNMPV3_PRIV_PASSWORD}"
rouser ${SNMPV3_USER} priv
`;

let mode = 'OPER'; // OPER -> PAGING -> OPER ; OPER -> SHOWCONFIG_OPTION -> OPER
let nextPages = [];
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
			if (mode === 'PAGING' && ch === ' ') {
				printPaged(null);
				continue;
			}
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
	if (mode === 'OPER') {
		out(`${HOSTNAME}> `);
	}
}

function printPaged(text) {
	if (text !== null) {
		const lines = text.split('\n');
		nextPages = [];
		let page = '';
		let lineCount = 0;
		while (lines.length > 0) {
			if (lineCount === PAGE_SIZE) {
				nextPages.push(page);
				page = '';
				lineCount = 0;
			}
			page += lines.shift() + '\n';
			lineCount++;
		}
		if (page.length > 0) {
			nextPages.push(page);
		}
	}
	if (mode === 'PAGING') {
		out(POST_PAGER);
	}
	out(nextPages.shift());
	if (nextPages.length > 0) {
		mode = 'PAGING';
		out(PAGER);
	}
	else {
		mode = 'OPER';
		prompt();
	}
}

function cmdShowConfigOptions() {
	out(`Choose the passphrase option:\n1. Mask passphrases (Files with masked passphrases cannot be loaded using loadconfig command)\n2. Encrypt passphrases\n[1]> `);
	mode = 'SHOWCONFIG_OPTION';
}

function cmdShowConfig(showSecrets) {
	let config = `<?xml version="1.0" encoding="ISO-8859-1"?>\r
<!DOCTYPE config SYSTEM "config.dtd">\r
\r
<!--\r
  Product: Cisco M600V Secure Email and Web Manager\r
  Model Number: M600V\r
  Version: ${VERSION}\r
  Serial Number: ${SERIAL}\r
  Number of CPUs: 8\r
  Memory (GB): 15\r
  Current Time: Wed Sep 17 15:04:21 2025\r
  Feature "Cisco Centralized Email Reporting": Quantity = 1, Time Remaining = "Perpetual"\r
  Feature "Cisco IronPort Centralized Email Message Tracking": Quantity = 1, Time Remaining = "Perpetual"\r
  Feature "Incoming Mail Handling": Quantity = 1, Time Remaining = "Perpetual"\r
-->\r
<config>\r
<!--\r
******************************************************************************\r
*                           Network Configuration                            *\r
******************************************************************************\r
-->\r
\r
  <hostname>${HOSTNAME}</hostname>\r
\r
  <ports>\r
    <port_interface>\r
      <port_name>Management</port_name>\r
      <direct>\r
        <jack>Management</jack>\r
        <jack_mtu>1500</jack_mtu>\r
      </direct>\r
    </port_interface>\r
    <port_interface>\r
      <port_name>Data 1</port_name>\r
      <direct>\r
        <jack>Data 1</jack>\r
        <jack_mtu>1500</jack_mtu>\r
      </direct>\r
    </port_interface>\r
  </ports>\r
  <interfaces>\r
    <interface>\r
      <interface_name>Production</interface_name>\r
      <ip>172.16.1.10</ip>\r
      <phys_interface>Data 1</phys_interface>\r
      <netmask>255.255.255.224</netmask>\r
      <interface_hostname>ciscom600v.lab.netshot.net</interface_hostname>\r
      <sshd_port>22</sshd_port>\r
      <httpsd_port>443</httpsd_port>\r
      <api_httpd_port>6080</api_httpd_port>\r
      <api_httpsd_port>6443</api_httpsd_port>\r
    </interface>\r
    <interface>\r
      <interface_name>Management</interface_name>\r
      <ip>${MGMT_IP}</ip>\r
      <phys_interface>Management</phys_interface>\r
      <netmask>0xffffffe0</netmask>\r
      <interface_hostname>ciscom600v.lab.netshot.net</interface_hostname>\r
      <sshd_port>22</sshd_port>\r
      <trailblazer_httpsd_port>4431</trailblazer_httpsd_port>\r
      <httpsd_port>443</httpsd_port>\r
      <api_httpd_port>6080</api_httpd_port>\r
      <api_httpsd_port>6443</api_httpsd_port>\r
    </interface>\r
  </interfaces>\r
\r
  <ip_groups>\r
  </ip_groups>\r
\r
  <allow_arp_multicast>0</allow_arp_multicast>\r
  <dehydration_enabled>0</dehydration_enabled>\r
\r
  <ethernet_settings>\r
    <ethernet>\r
      <ethernet_interface>Management</ethernet_interface>\r
      <media>manual</media>\r
      <media_opt></media_opt>\r
      <macaddr>06:2b:74:dc:d4:d7</macaddr>\r
    </ethernet>\r
    <ethernet>\r
      <ethernet_interface>Data 1</ethernet_interface>\r
      <media>manual</media>\r
      <media_opt></media_opt>\r
      <macaddr>06:cd:d8:74:9b:ef</macaddr>\r
    </ethernet>\r
  </ethernet_settings>\r
\r
  <dns>\r
    <local_dns>\r
      <dns_ip priority="0">172.16.254.98</dns_ip>\r
      <dns_ip priority="0">172.16.254.99</dns_ip>\r
    </local_dns>\r
    <dns_ptr_timeout>20</dns_ptr_timeout>\r
\r
  </dns>\r
\r
  <dns_cache_ttl_min>1800</dns_cache_ttl_min>\r
\r
  <dns_interface></dns_interface>\r
\r
  <default_gateway>172.16.1.30</default_gateway>\r
\r
  <routes>\r
    <route>\r
      <route_name>MGMT</route_name>\r
      <destination>172.16.254.0/24</destination>\r
      <gateway>172.16.254.30</gateway>\r
    </route>\r
  </routes>\r
\r
<!--\r
******************************************************************************\r
*                            System Configuration                            *\r
******************************************************************************\r
-->\r
\r
  <fips_mode>0</fips_mode>\r
  <config_encryption>\r
  </config_encryption>\r
  <ntp>\r
    <ntp_server>172.18.114.78</ntp_server>\r
    <ntp_server>172.18.115.56</ntp_server>\r
    <ntp_server_info>\r
      <ntp_server_addr>172.18.114.78</ntp_server_addr>\r
    </ntp_server_info>\r
    <ntp_server_info>\r
      <ntp_server_addr>172.18.115.56</ntp_server_addr>\r
    </ntp_server_info>\r
    <ntp_source_ip_interface></ntp_source_ip_interface>\r
    <ntp_use_auth>0</ntp_use_auth>\r
  </ntp>\r
\r
`;
	if (showSecrets) {
		config += `  <https_certificate>\r
    <certificate>\r
-----BEGIN CERTIFICATE-----\r
MIID8TCCAtmgAwIBAgIUU0V+Vgqhs6fsS4MX6dbNYFn2Nk4wDQYJKoZIhvcNAQEL\r
BQAwgYcxCzAJBgNVBAYTAkZSMQwwCgYDVQQIDANJZEYxDjAMBgNVBAcMBVBhcmlz\r
MRAwDgYDVQQKDAdOZXRzaG90MQ0wCwYDVQQLDARUZXN0MRUwEwYDVQQDDAxBc3lu\r
Y09TIHRlc3QxIjAgBgkqhkiG9w0BCQEWE2NvbnRhY3RAbmV0c2hvdC5uZXQwHhcN\r
MjUwOTIwMTI1MjA5WhcNMjYwOTIwMTI1MjA5WjCBhzELMAkGA1UEBhMCRlIxDDAK\r
BgNVBAgMA0lkRjEOMAwGA1UEBwwFUGFyaXMxEDAOBgNVBAoMB05ldHNob3QxDTAL\r
BgNVBAsMBFRlc3QxFTATBgNVBAMMDEFzeW5jT1MgdGVzdDEiMCAGCSqGSIb3DQEJ\r
ARYTY29udGFjdEBuZXRzaG90Lm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\r
AQoCggEBANNRfOo37uan0pRKrfSVpefgwbZwZ/J5MRtaeZcBqHvompZA8FTz6Yp1\r
amNuKehdKXae/gYCc83cNTwS7IBivz30iZt4/1VewIwKiZEVbK4DbsGQioseb3vO\r
gJoot7FzGrkoKnHn9n9cZmOA2zWiKE7SqVztg1MXcKnZhz5QE1mIG4Abz8dAYnM7\r
yRQ7DuDl9L7ESFQA8NcsML+zZ1q8kpQz82Oq10lolbnMolHCJx8jjAYnnMG/tK4I\r
Q6gUFPxS0gNsgoKnOe6OYMX7Z06hU5sibVc6jF+wCnVZLjEMuOhpCvLcZmbaYTmg\r
Cy7KpGF6ILyKKG83NlOYttHJBSA3eP8CAwEAAaNTMFEwHQYDVR0OBBYEFMABqRHf\r
1AxhRgx/dCyPTbbIkJYjMB8GA1UdIwQYMBaAFMABqRHf1AxhRgx/dCyPTbbIkJYj\r
MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBADQ5fEVeNuw8RaFf\r
hv97+xQfej39CSDCI9foSSFr6LL624S4wyiSwWKrtg9lH+mxozg5q2NXUW+qWKPR\r
NDuOBY9SNVwkEoLqsdXbOIncuvwXBJ+5+/Md/IVjH5O88Gb6fEczhb0snBqp9+8v\r
jD75NoACvUucKL4J5A8M54mw+wZvRn64RhwUhigqQETRilUUsVAJITRdXvXtyYK0\r
Jtb1Bt8xVvOHzMi/n6I8yKdjuIrDiec4+XGaXK9DqNeZc4znTVXkx3Sr1HLs3BT4\r
SlvJ+y6SluxPcAoSFBGy4uC8DhJiWXpPaCDWPY1IE7aBiwMFCzdQrczWnp6vOKIa\r
Ahhsx70=\r
-----END CERTIFICATE-----\r
\r
    </certificate>\r
      <key>REDACTED-TEST-KEY-MATERIAL</key>\r
  </https_certificate>\r
`;
	}
	config += `  <crl_sources>\r
    <crl_listener_enabled>0</crl_listener_enabled>\r
    <crl_delivery_enabled>0</crl_delivery_enabled>\r
    <crl_gui_enabled>0</crl_gui_enabled>\r
  </crl_sources>\r
<!--\r
******************************************************************************\r
*                             Filebeat Configuration.                        *\r
******************************************************************************\r
-->\r
\r
<filebeat_config>\r
  <customer_details>\r
    <cd_allocation></cd_allocation>\r
    <cd_data_center></cd_data_center>\r
    <cd_name></cd_name>\r
  </customer_details>\r
  <kafka>\r
    <cert></cert>\r
    <host></host>\r
    <topic></topic>\r
  </kafka>\r
</filebeat_config>\r
</config>\r
\r
`;
	printPaged(config);
}

function cmdVersion() {
	out(`\r
Current Version\r
===============\r
Product: Cisco M600V Secure Email and Web Manager\r
Model: M600V\r
Version: ${VERSION}\r
Build Date: 2023-11-10\r
Install Date: 2024-06-11 12:17:23\r
Serial #: ${SERIAL}\r
BIOS: 4.11.fp\r
CPUs: 8 expected, 8 allocated\r
Memory: 16384 MB expected, 15360 MB allocated\r
RAID: NA\r
RAID Status: Unknown\r
RAID Type: NA\r
BMC: NA\r
`);
	prompt();
}

function handleCommand(command) {
	if (mode === 'OPER' && command === 'showconfig') {
		cmdShowConfigOptions();
	}
	else if (mode === 'SHOWCONFIG_OPTION' && command === '1') {
		cmdShowConfig(false);
	}
	else if (mode === 'SHOWCONFIG_OPTION' && command === '2') {
		cmdShowConfig(true);
	}
	else if (mode === 'SHOWCONFIG_OPTION') {
		cmdShowConfigOptions();
	}
	else if (mode === 'OPER' && command === 'version') {
		cmdVersion();
	}
	else if (mode === 'OPER') {
		out(`\nUnknown command or missing feature key: ${command}`);
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
		process.stderr.write('Usage: node Cisco_AsyncOS.js <snmp config|ssh start|telnet start>\n');
		process.exitCode = 1;
	}
}

main();
