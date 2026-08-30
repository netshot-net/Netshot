/**
 * Copyright 2013-2025 Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */

const Info = {
	name: "F5TMOS",
	description: "F5 TM-OS, 11.x and newer",
	author: "Netshot Team",
	version: "4.1"
};

const Config = {
	"tmosVersion": {
		type: "Text",
		title: "TM-OS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "# TM-OS version:",
			preLine: "#  "
		}
	},
	"runningConfig": {
		type: "LongText",
		title: "Running configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "# Running configuration (taken on %when%):",
			post: "# End of running configuration"
		}
	},
	"license": {
		type: "LongText",
		title: "Licensing information",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "# Licensing information:",
			preLine: "#  "
		}
	},
	"ucsArchive": {
		type: "BinaryFile",
		title: "UCS Archive",
	},
	"scfArchive": {
		type: "BinaryFile",
		title: "SCF Archive",
	},
};

const Device = {
	"product": {
		type: "Text",
		title: "Product",
		searchable: true,
	}
};

const Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive (UCS and SCF)",
		default: true,
	},
};

const CLI = {
	telnet: {
		fail: "Telnet access is not supported."
	},
	ssh: {
		macros: {
			bash: {
				options: [ "bash", "tmsh" ],
				target: "bash"
			},
			tmsh: {
				options: [ "bash", "tmsh" ],
				target: "tmsh"
			}
		}
	},
	bash: {
		prompt: /^(\[.*@.*\] .* # )$/,
		macros: {
			tmsh: {
				cmd: "tmsh",
				options: [ "tmsh" ],
				target: "tmsh"
			}
		}
	},
	tmsh: {
		prompt: /^([A-Za-z\-_0-9\.]+@\(.+\)\(.+\)\(.+\)\(.+\)\(tmos\)# )$/,
		error: /^(Syntax Error: .*)/,
		macros: {
			bash: {
				cmd: "bash",
				options: [ "bash" ],
				target: "bash"
			},
			saveSysAll: {
				cmd: "save /sys config partitions all",
				target: "tmsh"
			}
		}
	}
};

function snapshot(cli, device, config) {
	cli.macro("bash");
	let runningConfig = cli.command("tmsh -q -c 'cd /; show running-config recursive'");
	runningConfig = runningConfig.replace(/^[^\{]*\r?\n/, ""); // Remove first line if doesn't contain {
	config.set("runningConfig", runningConfig);

	if (typeof config.computeHash === "function") {
		// Possible starting with Netshot 0.21
		config.computeHash(runningConfig);
	}
	
	const lastTransaction = cli.command("grep transaction /var/log/audit | tail -n1");
	if (lastTransaction) {
		const user = lastTransaction.match(/user (.+) - transaction/);
		if (user) {
			config.set("author", user[1]);
		}
	}
	
	const listSysGlobalHostname = cli.command("tmsh -q list sys global-settings hostname");
	const hostname = listSysGlobalHostname.match(/^\s*hostname +(.+)/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	const showSysVersion = cli.command("tmsh -q show /sys version");
	const version = showSysVersion.match(/^ *Version +(.*)$/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("tmosVersion", version[1]);
	}
	const product = showSysVersion.match(/^ *Product +(.*)$/m);
	if (product) {
		device.set("product", product[1]);
	}
	
	const showSysHardware = cli.command("tmsh -q show /sys hardware");
	const platformSections = cli.findSections(showSysHardware, /^Platform/m);
	for (const platformSection of platformSections) {
		const family = platformSection.config.match(/^ *Name +(.*)/m);
		if (family) {
			device.set("family", family[1]);
		}
	}
	const systemSections = cli.findSections(showSysHardware, /^System Information/m);
	for (const systemSection of systemSections) {
		const details = systemSection.config;
		const applianceSerial = details.match(/^ *Appliance Serial +(.*)/m);
		const partNumberMatch = details.match(/^ *Part Number +(.*)/m);
		const chassisSerial = details.match(/^ *Chassis Serial +(.*)/m);
		const type = details.match(/^ *Type +(.*)/m);
		
		let partNumber = "";
		if (partNumberMatch) {
			partNumber = partNumberMatch[1];
		}
		else if (type) {
			partNumber = type[1];
		}

		if (applianceSerial) {
			device.add("module", {
				slot: "Appliance",
				partNumber,
				serialNumber: applianceSerial[1]
			});
			device.set("serialNumber", applianceSerial[1]);
		}
		if (chassisSerial) {
			device.add("module", {
				slot: "Chassis",
				partNumber,
				serialNumber: chassisSerial[1]
			});
			device.set("serialNumber", chassisSerial[1]);
		}
	}
	const hardwarePattern = /^  Name +(.*)\r?\n  Type +(.*)\r?\n  Model +(.*)\r?\n  Parameters.*\r?\n((^              .*\r?\n)*)/mg;
	let hardwareMatch;
	while ((hardwareMatch = hardwarePattern.exec(showSysHardware))) {
		const parameters = hardwareMatch[4];
		const serialNumber = parameters.match(/^ *Serial number +(.*)/m);
		device.add("module", {
			slot: hardwareMatch[1],
			partNumber: hardwareMatch[3],
			serialNumber: serialNumber ? serialNumber[1] : "",
		});
	}
	
	const platform = showSysHardware.match(/^Platform\r?\n +Name +(.*)/m);
	if (platform) {
		device.set("family", platform[1]);
	}
	device.set("networkClass", "LOADBALANCER");
	

	const license = cli.command("tmsh -q -c 'cd /; show /sys license'");
	config.set("license", license);
	
	
	const snmpSections = cli.findSections(runningConfig, /^sys snmp \{$/mg);
	for (const snmpSection of snmpSections) {
		const snmpConfig = snmpSection.config;
		const sysContact = snmpConfig.match(/^ *sys-contact (.+)/m);
		const sysLocation = snmpConfig.match(/^ *sys-location (.+)/m);
		device.set("contact", sysContact ? sysContact[1].replace(/^"(.*)"$/, "$1") : "");
		device.set("location", sysLocation ? sysLocation[1].replace(/^"(.*)"$/, "$1") : "");
	}
	
	const ifRouteDomains = {};
	const routeDomains = cli.findSections(runningConfig, /^net route-domain (.+) \{/mg);
	for (const routeDomain of routeDomains) {
		const idMatch = routeDomain.config.match(/^ *id +([0-9]+)/);
		const id = idMatch ? idMatch[1] : "";
		const vlanMatch = routeDomain.config.match(/^( +)vlans \{((\r|\n|.)*)^\1\}/m);
		if (vlanMatch) {
			const vlans = vlanMatch[2].trim().split(/[\r\n ]+/);
			for (const vlan of vlans) {
				ifRouteDomains[vlan] = `${routeDomain.match[1]} (${id})`;
			}
		}
	}
	
	const vlanIps = {};
	const selfIps = cli.findSections(runningConfig, /^net self (.+) \{/mg);
	for (const selfIp of selfIps) {
		const ipMatch = selfIp.config.match(/^ *address ([0-9a-fA-F:\.]+)(%[0-9]+)?\/([0-9]+)/m);
		const vlanMatch = selfIp.config.match(/^ *vlan (.+)/m);
		if (ipMatch && vlanMatch) {
			const vlan = vlanMatch[1];
			if (!vlanIps[vlan]) {
				vlanIps[vlan] = [];
			}
			const ip = {
				mask: parseInt(ipMatch[3]),
				usage: "PRIMARY"
			};
			ip[ipMatch[1].match(/:/) ? "ipv6" : "ip"] = ipMatch[1];
			vlanIps[vlan].push(ip);
		}
	}
	
	const vlanMac = {};
	try {
		const showVlan = cli.command("tmsh -q -c 'cd /; show /net vlan'");
		const macPattern = /^Interface Name +(.+)\r?\nMac Address.* +([0-9A-Za-z:]+)$/mg;
		let match;
		while ((match = macPattern.exec(showVlan))) {
			vlanMac[match[1]] = match[2];
		}
	}
	catch (error) {
		// Go on
	}

	const mgmtIps = [];
	const sysMgmtIps = cli.findSections(runningConfig, /^sys management-ip ([0-9a-fA-F:\.]+)\/([0-9]+) \{/mg);
	for (const sysMgmtIp of sysMgmtIps) {
		const ip = {
			mask: parseInt(sysMgmtIp.match[2]),
			usage: "PRIMARY"
		};
		const a = sysMgmtIp.match[1];
		ip[a.match(/:/) ? "ipv6" : "ip"] = a;
		mgmtIps.push(ip);
	}

	
	const interfaces = cli.findSections(runningConfig, /^net interface (.+) \{/mg);
	for (const intf of interfaces) {
		const networkInterface = {
			name: intf.match[1],
			ip: [],
			level3: false
		};
		if (intf.config.match(/^ *disabled$/m)) {
			networkInterface.enabled = false;
		}
		const macMatch = intf.config.match(/^ *mac-address ([0-9A-Za-z:]+)/m);
		if (macMatch) {
			networkInterface.mac = macMatch[1];
		}

		if (networkInterface.name === "mgmt") {
			for (const p in mgmtIps) {
				networkInterface.ip.push(mgmtIps[p]);
			}
		}

		device.add("networkInterface", networkInterface);
	}
	const trunks = cli.findSections(runningConfig, /^net trunk (.+) \{/mg);
	for (const trunk of trunks) {
		const networkInterface = {
			name: trunk.match[1],
			ip: [],
			level3: false
		};
		const interfacesMatch = trunk.config.match(/^( +)interfaces \{((\r|\n|.)*)^\1\}/m);
		if (interfacesMatch) {
			const interfaceNames = interfacesMatch[2].trim().split(/[\r\n ]+/);
			networkInterface.description = `Trunk (${interfaceNames.join(", ")})`;
		}
		device.add("networkInterface", networkInterface);
	}
	
	const vlans = cli.findSections(runningConfig, /^net vlan (.+) \{/mg);
	for (const vlan of vlans) {
		const vlanName = vlan.match[1];
		const networkInterface = {
			name: vlanName.replace(/^.*\//, ""),
			virtualDevice: vlanName.replace(/^(.*)\/.*/, "$1"),
			ip: vlanIps[vlanName] || []
		};
		if (ifRouteDomains[vlanName]) {
			networkInterface.vrf = ifRouteDomains[vlanName].replace(/^Common\//, "");
		}
		if (vlanMac[vlanName]) {
			networkInterface.mac = vlanMac[vlanName];
		}
		const tagMatch = vlan.config.match(/ *tag +([0-9]+)/m);
		if (tagMatch) {
			networkInterface.name = `${networkInterface.name} (Vlan${tagMatch[1]})`;
		}
		const descriptionMatch = vlan.config.match(/ *description +(.*)/);
		if (descriptionMatch) {
			networkInterface.description = descriptionMatch[1].replace(/^"(.*)"$/, "$1");
		}
		
		device.add("networkInterface", networkInterface);
	}

	const computeHash = (path) => {
		const output = cli.command(`sha256sum ${path}`);
		const match = output.match(/^([0-9a-f]{64})\s+(.+?)\s*$/m);
		if (match && match[2] === path) {
			return match[1];
		}
		throw `Unable to compute hash of file ${path}:\n${output}`;
	};

	if (!device.options || device.options.fullBackup) {
		// Save and download UCS
		const ucsPath = "/var/local/ucs/netshot.ucs";
		cli.command(`rm -f ${ucsPath}`);
		const saveUcs = cli.command(`tmsh -q save /sys ucs ${ucsPath}`, { timeout: 5 * 60 * 1000 });
		if (!saveUcs.match(/is saved/)) {
			throw `Unable to save UCS archive:\n${saveUcs}`;
		}
		const ucsCheckum = computeHash(ucsPath);
		config.download("ucsArchive", ucsPath, { method: "scp", checksum: ucsCheckum });
		cli.command(`rm -f ${ucsPath}`);

		// Save and download SCF
		const scfPath = "/var/local/scf/netshot";
		const saveScf = cli.command(
			`tmsh -q save /sys config file ${scfPath} no-passphrase`, { timeout: 5 * 60 * 1000 });
		if (!saveScf.match(/\.tar/)) {
			throw `Unable to save SCF archive:\n${saveScf}`;
		}
		const scfTarPath = `${scfPath}.tar`;
		const scfChecksum = computeHash(scfTarPath);
		config.download("scfArchive", scfTarPath, { method: "scp", checksum: scfChecksum });
		cli.command(`rm -f ${scfPath}*`);
	}
}


// To forward the audit logs to the Netshot server, use the following command line in tmsh:
// # modify /sys syslog include "destination d_netshot {udp(\"a.b.c.d\" port (514));};log {source(s_syslog_pipe);filter(f_audit);destination(d_netshot);};"
//  where a.b.c.d is the actual Netshot IP address
function analyzeSyslog(message) {
	if (message.match(/AUDIT - client (.+?), user (.+?) - transaction #/)) {
		return true;
	}
	return false;
}

// Add the Netshot server as a SNMP trap destination:
// # modify /sys snmp traps add { iNetshot { community Netsh01 host a.b.c.d version 2c network mgmt } }
// where a.b.c.d is the actual Netshot IP address.
// To generate traps upon configuration changes, edit the file /config/user_alert.conf, and add the following:
// /*
//  * Generate traps upon configuration change
//  */
// alert BIGIP_AUDIT_TRANSACTION "AUDIT - client (.*?), user (.*?) - transaction #" {
//     snmptrap OID=".1.3.6.1.4.1.3375.2.4.0.899"
// }
//
function analyzeTrap(trap) {
	if (trap["1.3.6.1.6.3.1.1.4.1.0"] === "1.3.6.1.4.1.3375.2.4.0.899") {
		return true;
	}
	const message = trap["1.3.6.1.4.1.3375.2.4.1.1"];
	if (typeof message === "string" && message.match(/AUDIT - client (.+?), user (.+?) - transaction #/)) {
		return true;
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 23) === "1.3.6.1.4.1.3375.2.1.3." && sysDesc.match(/BIG-IP software/)) {
		return true;
	}
	return false;
}
