/**
 * Copyright 2013-present Netshot
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
	name: "ZPENodeGrid", /* Unique identifier of the driver within Netshot. */
	description: "ZPE NodeGrid", /* Description to be used in the UI. */
	author: "Netshot Team",
	version: "0.2.0" /* Version will appear in the Admin tab. */
};

/**
 * 'Config' object = Data fields to be included in each configuration revision.
 */
const Config = {
	"softwareVersion": { /* This stores the detected software version on each snapshot. */
		type: "Text",
		title: "NodeGrid version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## NodeGrid version:",
			preLine: "##  "
		}
	},
	"settings": { /* This stores the full textual configuration of the device on each snapshot. */
		type: "LongText",
		title: "Configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Settings (taken on %when%):",
			post: "## End of settings"
		}
	}
};

/**
 * 'Device' object = Data fields to add to devices of this type.
 */
const Device = {
	"biosVersion": { /* This stores the last BIOS version. */
		type: "Text",
		title: "BIOS version",
		searchable: true,
		checkable: true
	},
	"licenseCount": { /* This stores the license count. */
		type: "Numeric",
		title: "Licenses",
		searchable: true,
		checkable: true
	},
	"mainMemorySize": { /* This stores the main memory size. */
		type: "Numeric",
		title: "Main memory size (MB)",
		searchable: true,
		checkable: true
	},
	"cpuInfo": { /* This stores the CPU model. */
		type: "Text",
		title: "CPU info",
		searchable: true,
		checkable: true
	},

};

/**
 * 'CLI' object = Definition of the finite state machine to recognize and handle the CLI prompt changes.
 */
var CLI = {
	ssh: { /* Entry point for SSH access. */
		macros: {
			basic: {
				options: ["basic"],
				target: "basic"
			}
		}
	},
	basic: { /* The basic NodeGrid prompt. */
		prompt: /^\[[A-Za-z\-_0-9\.@]+\s+\/\]#(\s+)?(\x07)?$/,
		error: /^(Error: Invalid (command|path))/m,
		pager: { /* 'pager': define how to handle the pager for long outputs. */
			match: /^-- more --:/,
			response: "\r"
		}
	},
	screen: { /* Screened command */
		prompt: /^\[[A-Za-z\-_0-9\.@]+\s+\/\]#(\s+)?(\x07)?$|\(h\->Help, q\->Quit\)/,
		clearPrompt: true,
		macros: {
			screen: {
				cmd: "q",
				options: ["basic"],
				target: "basic",
			}
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
	var configCleanup = function (config) {
		config = config.replace(/^\x07/m, "");
		return config;
	};

	// Targets the 'basic' CLI mode.
	cli.macro("basic");

	// Retrieve the hostname
	const hostname = cli.command("hostname");

	// Retrieve 'show /system/about/'
	const about = cli.command("show /system/about/");

	// Retrieve memory info
	const memoryUsage = cli.command("show /system/system_usage/memory_usage/")

	// Retrieve devices
	const devices = cli.command("show /settings/devices");

	// Retrieve the full settings, with a long waiting time
	const settings = cli.command("show_settings", { timeout: 120000 });

	// Assign settings
	config.set("settings", configCleanup(settings));

	// Hostname
	const nameMatch = hostname.match(/^(.+)/m);
	if (nameMatch) {
		device.set("name", nameMatch[1]);
	}

	// Extract memory size
	const memorySizeMatch = memoryUsage.match(/^ *Mem\s+([0-9]+)\s+/m);
	if (memorySizeMatch) {
		const memorySize = Math.round(parseInt(memorySizeMatch[1], 10) / 1024);
		device.set("mainMemorySize", memorySize);
	}

	// Extract software version
	const versionMatch = about.match(/^software: v([0-9\.]+)/m);
	const version = versionMatch ? versionMatch[1] : "Unknown";
	device.set("softwareVersion", version);
	config.set("softwareVersion", version);

	// Extract BIOS version
	const biosMatch = about.match(/^bios_version: (.+)/m);
	device.set("biosVersion", biosMatch ? biosMatch[1] : "Unknown");

	// Extract license count
	const licenseMatch = about.match(/^licenses: ([0-9]+)/m);
	device.set("licenseCount", licenseMatch ? parseInt(licenseMatch[1]) : 0);

	// Extract CPU info
	const cpuMatch = about.match(/^cpu: (.+)/m);
	if (cpuMatch) {
		let cpuInfo = cpuMatch[1];
		const coreMatch = about.match(/^cpu_cores: ([0-9]+)/m);
		if (coreMatch) {
			cpuInfo += ` (${coreMatch[1]} core(s))`;
		}
		device.set("cpuInfo", cpuInfo);
	}
	else {
		device.set("cpuInfo", "Unknown");
	}

	// Extract family info
	const modelMatch = about.match(/^model: (.+)/m);
	device.set("family", modelMatch ? modelMatch[1] : "Unknown NodeGrid");

	// Extract inventory info
	const serialMatch = about.match(/^serial_number: (.+)/m);
	const partMatch = about.match(/^part_number: (.+)/m);
	if (serialMatch && partMatch) {
		const module = {
			slot: "Chassis",
			partNumber: partMatch[1],
			serialNumber: serialMatch[1],
		};
		device.add("module", module);
		device.set("serialNumber", serialMatch[1]);
	}
	else {
		device.set("serialNumber", "");
	}

	// Static network class
	device.set("networkClass", "SWITCH");

	// Guess the user who performed the last change
	let auditLog = cli.command("event_system_audit\r   ", { noCr: true, mode: "screen" });
	let user;
	const changePattern = /Event ID 108: The configuration has changed. Change made(.*)by user: (.+?)\./mg;
	let eventMatch;
	while (eventMatch = changePattern.exec(auditLog)) {
		user = eventMatch[2];
	}
	if (user) {
		config.set("author", user);
	}

	// Extract contact and location from settings
	const contactMatch = settings.match(/^\/settings\/snmp\/system\s+syscontact=(.*)/m);
	if (contactMatch) {
		device.set("contact", contactMatch[1].replace(/^"(.*?) *"$/, "$1"));
	}
	const locationMatch = settings.match(/^\/settings\/snmp\/system\s+syslocation=(.*)/m);
	if (locationMatch) {
		device.set("location", locationMatch[1].replace(/^"(.*?) *"$/, "$1"));
	}

	// Extract network interface info
	const allIfSettings = {};
	const ifPattern = /^\/settings\/network_connections\/(.+?)\s+(.+)=(.+)/mg;
	let ifMatch;
	while (ifMatch = ifPattern.exec(settings)) {
		const ifName = ifMatch[1];
		const key = ifMatch[2];
		const value = ifMatch[3];
		const ifSettings = allIfSettings[ifName] || {
			name: ifName,
		};
		allIfSettings[ifName] = ifSettings;
		ifSettings[key] = value;
	}
	Object.values(allIfSettings).forEach((ifSettings) => {
		const networkInterface = {
			name: ifSettings.name,
			ip: [],
		};
		if (ifSettings["ipv4_address"] && ifSettings["ipv4_bitmask"]) {
			networkInterface.ip.push({
				ip: ifSettings["ipv4_address"],
				mask: parseInt(ifSettings["ipv4_bitmask"]),
				usage: "PRIMARY",
			});
		}
		device.add("networkInterface", networkInterface);
	});

	// Add console ports as interfaces
	const portPattern = /^ *(.+?)\s+(ttyS[0-9]+|usbS[0-9]+)\s+/mg;
	let portMatch;
	while (portMatch = portPattern.exec(devices)) {
		const description = portMatch[1].replace(/^\* /, "");
		device.add("networkInterface", {
			name: portMatch[2],
			description,
			ip: [],
		});
	}

};

/**
 * The 'analyzeSyslog' function entry point = WIll be called when receiving a Syslog message
 * from a device of this type.
 * @param message = the received Syslog message
 * @returns true if the passed message indicates a configuration changes
 *          (this means initiating a new snapshot).
 */
function analyzeSyslog(message) {
	return !!message.match(/The configuration has changed./);
}

/**
 * The 'analyzeTrap' function entry point = Will be called when receiving a trap for a device of this type.
 * @param trap = the SNMP data embedded in the trap.
 * @returns true if the trap indicates a configuration changes (this means to initiate a new snapshot).
 */
function analyzeTrap(trap) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.42518.100.120.0.108";
}

/**
 * The 'snmpAutoDiscover' function entry point = Will be called with the sysObjectID and sysDesc
 * of a device when auto discovering the type of a device.
 * @param sysObjectID = The SNMP sysObjectID of a device being discovered.
 * @param sysDesc = The SNMP sysDesc of a device being discovered.
 * @returns true if the scanned device is supported by this driver.
 */
function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.startsWith("1.3.6.1.4.1.42518.") && sysDesc.match(/NodeGrid/);
}

