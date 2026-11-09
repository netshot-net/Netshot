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

/**
 * 'Info' object = Meta data of the driver.
 */
const Info = {
	name: "FortinetFortiOS", /* Unique identifier of the driver within Netshot. */
	description: "Fortinet FortiOS", /* Description to be used in the UI. */
	author: "Netshot Team",
	version: "7.3" /* Version will appear in the Admin tab. */
};

/**
 * 'Config' object = Data fields to be included in each configuration revision.
 */
const Config = {
	"osVersion": { /* This stores the detected FortiOS version on each snapshot. */
		type: "Text",
		title: "FortiOS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## FortiOS version:",
			preLine: "##  "
		}
	},
	"configuration": { /* This stores the full textual configuration of the device on each snapshot. */
		type: "LongText",
		title: "Configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Configuration (taken on %when%):",
			post: "## End of configuration"
		}
	}
};

/**
 * 'Device' object = Data fields to add to devices of this type.
 */
const Device = {
	"haPeer": { /* This stores the name of an optional HA peer. */
		type: "Text",
		title: "HA peer name",
		searchable: true,
		checkable: true,
	},
	"haGroupName": { /* This stores the name of an optional HA Group Name. */
		type: "Text",
		title: "HA group name",
		searchable: true,
		checkable: true,
	},
	"haGroupId": { /* This stores the name of an optional HA Group ID. */
		type: "Text",
		title: "HA group ID",
		searchable: true,
		checkable: true
	},
	"haMode": { /* This stores the HA mode of the Device : 'standalone' or 'a-p' or 'a-a'. */
		type: "Text",
		title: "HA mode",
		searchable: true,
		checkable: true
	}
};

/**
 * 'CLI' object = Definition of the finite state machine to recognize and handle the CLI prompt changes.
 */
const CLI = {
	telnet: { /* Entry point for Telnet access. */
		macros: { /* List of available macros in the CLI mode. */
			basic: { /* 'basic' macro (will be called in the snapshot procedure. */
				options: [ "login", "password", "basic", "postLoginBanner" ], /* Possible next modes, Netshot will test the associated prompt regexp's. */
				target: "basic" /* Netshot will target this mode. */
			}
		}
	},
	ssh: { /* Entry point for SSH access. */
		macros: {
			basic: {
				options: [ "basic", "postLoginBanner" ],
				target: "basic"
			}
		}
	},
	login: { /* 'login' prompt: send the username and expect the password prompt. */
		prompt: / login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password" ]
			}
		}
	},
	password: { /* 'password' prompt: send the password, and expect the basic prompt. */
		prompt: /^Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "loginAgain", "basic" ]
			}
		}
	},
	loginAgain: { /* 'login' prompt again: this means that authentication failed. */
		prompt: / login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	postLoginBanner: { /* Post login banner. */
		prompt: /^\(Press 'a' to accept\):/,
		macros: {
			auto: {
				cmd: "a",
				noCr: true,
				options: [ "basic" ]
			}
		}
	},
	basic: { /* The basic FortiOS prompt. */
		prompt: /^[A-Za-z0-9_\-\~]+?\s(\([A-Za-z0-9_\-\~\. ]+?\)\s)?[#$]\s?$/,
		error: /^(Unknown action|Command fail)/m,
		pager: { /* 'pager': define how to handle the pager for long outputs. */
			match: /^--More-- /,
			response: " "
		},
		macros: {
		}
	}
};

/**
 * The 'snapshot' function entry point = Will be called by Netshot when initiating a snapshot of this type of device.
 * @param cli = object used to interact with the current device via CLI.
 * @param device = used to store data at the device level.
 * @param config = used to store data at the configuration revision level.
 */
function snapshot(cli, device, config) {
	// Targets the 'basic' CLI mode.
	cli.macro("basic");

	// 'status' will be used to read the version, hostname, etc.
	const status = cli.command("get system status");

	// Read version and family from the 'status' output.
	const version = status.match(/Version: (.*) v([0-9]+.*)/);
	const family = (version ? version[1] : "FortiOS device");
	device.set("family", family);
	const osVersion = (version ? version[2] : "Unknown");
	device.set("softwareVersion", osVersion);
	config.set("osVersion", osVersion);

	device.set("networkClass", "FIREWALL");

	// Whether to use SCP to download the configuration rather than show
	let useScp = false;
	// Configuration of 'global' mode
	let globalConfig = null;

	// Read the HA peer hostname from a 'get system ha status' in global mode.
	let vdomMode = true;
	try {
		cli.command("config global", { clearPrompt: true });
	}
	catch (e) {
		vdomMode = false;
	}

	try {
		const getSystemGlobalAdminScp = cli.command("get system global | grep admin-scp");
		if (getSystemGlobalAdminScp.match(/enable/)) {
			useScp = true;
		}
	}
	catch (e) {
		// Ignore
	}

	// Store the interface config block
	const showSystemInterface = cli.command("show system interface | grep .");
	// Store the SNMP config block
	const showSystemSnmp = cli.command("show system snmp sysinfo | grep .");
	// Store the HA status
	const getHa = cli.command("get system ha status | grep .");
	// Store the HA config block
	const showHa = cli.command("show full-configuration system ha | grep .");

	if (vdomMode) {
		if (!useScp && osVersion.match(/^6\.2\..*/)) {
			// In 6.2, some config blocks are missing from the root 'show'
			// We'll take a 'show' in 'config global' mode and replace
			globalConfig = cli.command("show | grep .", { timeout: 120000 });
		}

		cli.command("end", { clearPrompt: true });
	}

	// The main configuration
	let configuration = null;
	if (useScp) {
		try {
			configuration = device.textDownload("sys_config", { method: "scp" });
		}
		catch (e) {
			cli.debug("Error with SCP method, falling back to show command");
			useScp = false;
		}
	}

	if (!useScp) {
		// The configuration is retrieved by a simple 'show' at the root level. Add grep to avoid paging.
		configuration = cli.command("show | grep .", { timeout: 120000 });
		if (globalConfig) {
			configuration = configuration.replace(/^(config global\r?\n)(?:.*\r?\n)*?(^end\r?\nconfig vdom)/m,
					function(m, p, s) { return p + globalConfig + s; });
		}
	}

	// Read the device hostname from the 'status' output.
	let hostname = status.match(/Hostname: (.*)$/m);
	if (hostname) {
		hostname = hostname[1];
		device.set("name", hostname);
	}
	// Read the serial number from the 'status' output.
	const serial = status.match(/Serial-Number: (.*)/);
	if (serial) {
		const module = {
			slot: "Chassis",
			partNumber: family,
			serialNumber: serial[1]
		};
		device.add("module", module);
		device.set("serialNumber", serial[1]);
	}
	else {
		device.set("serialNumber", "");
	}

	// Read the contact and location fields from the configuration directly.
	device.set("contact", "");
	device.set("location", "");
	const sysInfos = cli.findSections(showSystemSnmp, /config system snmp sysinfo/);
	for (const s in sysInfos) {
		const contact = sysInfos[s].config.match(/set contact-info "(.*)"/);
		if (contact) {
			device.set("contact", contact[1]);
		}
		const location = sysInfos[s].config.match(/set location "(.*)"/);
		if (location) {
			device.set("location", location[1]);
		}
	}

	// Read HA Peers from the unit directly.
	const peerPattern = /^(?:(Master|Slave|Primary|Secondary) *: *)?(.+?) *, +([A-Z0-9]+)(, HA cluster index = [0-9]|, cluster index = [0-9])*$/gm;
	while (true) {
		const match = peerPattern.exec(getHa);
		if (!match) break;
		if (match[2] != hostname) {
			device.set("haPeer", match[2]);
			break;
		}
	}

	// Read the HA Group Name and HA Group ID fields from the configuration directly.
	const haInfos = cli.findSections(showHa, /config system ha/);
	for (const s in haInfos) {
		const haName = haInfos[s].config.match(/set group-name "(.*)"/);
		device.set("haGroupName", haName ? haName[1] : "");
		const haId = haInfos[s].config.match(/set group-id (.*)/);
		device.set("haGroupId", haId ? haId[1] : "");
		var haMode = haInfos[s].config.match(/set mode (.*)/);
		device.set("haMode", haMode ? haMode[1] : "");
		if (haMode) break;
	}

	// Extract the MAC addresses from ifconfig
	const ifMacs = {};
	try {
		const ifconfig = cli.command("fnsysctl ifconfig");
		const macPattern = /^(.+?)\s+Link encap:(.+?)\s+HWaddr ([0-9a-fA-F:]+)/mg;
		while (true) {
			const macMatch = macPattern.exec(ifconfig);
			if (!macMatch) break;
			ifMacs[macMatch[1]] = macMatch[3];
		}
	}
	catch (err) {
		cli.debug("fnsysctl ifconfig failed, MAC addresses cannot be retrieved");
	}

	// Read the list of interfaces.
	const interfaces = cli.findSections(showSystemInterface, /^ *edit "(.*)"/m);
	for (const i in interfaces) {
		const networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		const interfaceConfig = interfaces[i].config;
		const ipAddress = interfaceConfig.match(/set ip (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/);
		if (ipAddress) {
			const ip = {
				ip: ipAddress[1],
				mask: ipAddress[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		const vdomMatch = interfaceConfig.match(/set vdom "(.*?)"/);
		if (vdomMatch) {
			networkInterface.virtualDevice = vdomMatch[1];
		}
		if (interfaceConfig.match(/set status down/)) {
			networkInterface.disabled = true;
		}
		const descMatch = interfaceConfig.match(/set description "(.+)"/);
		if (descMatch) {
			networkInterface.description = descMatch[1];
		}
		if (ifMacs[networkInterface.name]) {
			networkInterface.mac = ifMacs[networkInterface.name];
		}

		const secIpConfigs = cli.findSections(interfaceConfig, /^ *config secondaryip/m);
		for (const c in secIpConfigs) {
			const secIpConfig = secIpConfigs[c].config;
			const ipPattern = /^ *set ip (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/mg;
			while (true) {
				const ipMatch = ipPattern.exec(secIpConfig);
				if (!ipMatch) break;
				const ip = {
					ip: ipMatch[1],
					mask: ipMatch[2],
					usage: "SECONDARY"
				};
				networkInterface.ip.push(ip);
			}
		}

		const ipv6Configs = cli.findSections(interfaceConfig, /^ *config ipv6/m);
		for (const c in ipv6Configs) {
			const ipv6Config = ipv6Configs[c].config;
			const ip6AddressMatch = ipv6Config.match(/set ip6-address ([0-9a-f:]+)\/(\d+)/);
			if (ip6AddressMatch) {
				const ip = {
					ipv6: ip6AddressMatch[1],
					mask: parseInt(ip6AddressMatch[2]),
					usage: "PRIMARY"
				};
				networkInterface.ip.push(ip);
			}
			const ip6ExtraAddrs = cli.findSections(ipv6Config, /^ *config ip6-extra-addr/m);
			for (const a in ip6ExtraAddrs) {
				const ip6Pattern = /edit ([0-9a-f:]+)\/(\d+)/mg;
				while (true) {
					const ip6Match = ip6Pattern.exec(ip6ExtraAddrs[a].config);
					if (!ip6Match) break;
					const ip = {
						ipv6: ip6Match[1],
						mask: parseInt(ip6Match[2]),
						usage: "SECONDARY"
					};
					networkInterface.ip.push(ip);
				}
			}
		}

		device.add("networkInterface", networkInterface);
	}

	const removeChangingParts = function(text) {
		let cleaned = text;
		cleaned = cleaned.replace(/^#conf_file_ver=.*/mg, "");
		cleaned = cleaned.replace(/^ *set (passphrase|password|passwd|secondary-secret) ENC .*$/mg, "");
		cleaned = cleaned.replace(/^ *set private-key "(.|[\r\n])*?"$/mg, "");
		return cleaned;
	}

	if (typeof config.computeHash === "function") {
		// Possible starting with Netshot 0.21
		// Sets the config hash based on configuration without salted/encrypted parts.
		config.computeHash(removeChangingParts(configuration));
		config.set("configuration", configuration);
	}
	else {
		// Legacy mode
		// If only the passwords are changing (they are hashed with a new salt at each 'show') then
		// just keep the previous configuration.
		// That means we could miss a password change in the history of configurations, but no choice...
		const previousConfiguration = device.get("configuration");
		if (typeof previousConfiguration === "string" &&
				removeChangingParts(previousConfiguration) === removeChangingParts(configuration)) {
			config.set("configuration", previousConfiguration);
		}
		else {
			config.set("configuration", configuration);
		}
	}

};

// No known log message upon configuration change

/**
 * The 'analyzeTrap' function entry point = Will be called when receiving a trap for a device of this type.
 * @param trap = the SNMP data embedded in the trap.
 * @returns true if the trap indicates a configuration changes (this means to initiate a new snapshot).
 */
function analyzeTrap(trap) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.12356.101.6.0.1003" ||
		trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.2.1.47.2.0.1";
}

/**
 * The 'snmpAutoDiscover' function entry point = Will be called with the sysObjectID and sysDesc
 * of a device when auto discovering the type of a device.
 * @param sysObjectID = The SNMP sysObjectID of a device being discovered.
 * @param sysDesc = The SNMP sysDesc of a device being discovered.
 * @returns true if the scanned device is supported by this driver.
 */
function snmpAutoDiscover(sysObjectID, sysDesc) {
	return (sysObjectID.substring(0, 22) == "1.3.6.1.4.1.12356.101.");
}
