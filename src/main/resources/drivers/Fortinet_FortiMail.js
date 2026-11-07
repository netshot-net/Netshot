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
var Info = {
	name: "FortinetFortiMail", /* Unique identifier of the driver within Netshot. */
	description: "Fortinet FortiMail", /* Description to be used in the UI. */
	author: "Najihel",
	version: "1.1" /* Version will appear in the Admin tab. */
};

/**
 * 'Config' object = Data fields to be included in each configuration revision.
 */
var Config = {
	"osVersion": { /* This stores the detected FortiMail OS version on each snapshot. */
		type: "Text",
		title: "FortiMail OS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## FortiMail OS version:",
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
var Device = {
	"haPeer": { /* This stores the name of an optional HA peer. */
		type: "Text",
		title: "HA peer name",
		searchable: true,
		checkable: true
	}
};

/**
 * 'CLI' object = Definition of the finite state machine to recognize and handle the CLI prompt changes.
 */
var CLI = {
	telnet: { /* Entry point for Telnet access. */
		macros: { /* List of available macros in the CLI mode. */
			basic: { /* 'basic' macro (will be called in the snapshot procedure. */
				options: [ "login", "password", "basic" ], /* Possible next modes, Netshot will test the associated prompt regexp's. */
				target: "basic" /* Netshot will target this mode. */
			}
		}
	},
	ssh: { /* Entry point for SSH access. */
		macros: {
			basic: {
				options: [ "basic" ],
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
	basic: { /* The basic FortiOS prompt. */
		prompt: /^[A-Za-z0-9_\-\~]+?\s(\([A-Za-z0-9_\-\~]+?\)\s)?[#$]\s?$/,
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
	var status = cli.command("get system status");

	// Read version and family from the 'status' output.
	var version = status.match(/Version:[\s]*(FortiMail([a-zA-Z0-9\-]*)) v(.*)/);
	var family = "FortiMail";
	device.set("family", family);
	version = (version ? version[3] : "Unknown");
	device.set("softwareVersion", version);
	config.set("osVersion", version);

	device.set("networkClass", "SERVER");

	// The main configuration
	var configuration = null;

	// Store the interface config block
	var showSystemInterface = cli.command("show system interface");
	// Store the SNMP config block
	var showSystemSnmp = cli.command("show system snmp sysinfo");
	// Store the HA status
	var getHa = cli.command("get system ha");

	var configuration;
	// The configuration is retrieved by a simple 'show' at the root level. Add grep to avoid paging.
	configuration = cli.command("show", { timeout: 120000 });

	// Read the device hostname from the 'status' output.
	var hostname = status.match(/Hostname[\s]*:[\s]*(.*)$/m);
	if (hostname) {
		hostname = hostname[1];
		device.set("name", hostname);
	}
	// Read the serial number from the 'status' output.
	var serial = status.match(/Serial-Number[\s]*:[\s]*(.*)$/m);
	if (serial) {
		var module = {
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
	var sysInfos = cli.findSections(showSystemSnmp, /config system snmp sysinfo/);
	for (var s in sysInfos) {
		var contact = sysInfos[s].config.match(/set contact (.*)/);
		if (contact) {
			device.set("contact", contact[1]);
		}
		var location = sysInfos[s].config.match(/set location (.*)/);
		if (location) {
			device.set("location", location[1]);
		}
	}
	var peerPattern = /^([\s]*config-peer-ip[\s]*: )(.*)$/gm;
	var match;
	while (match = peerPattern.exec(getHa)) {
		device.set("haPeer", match[2]);
		break;
	}

	// Read the list of interfaces.
	var interfaces = cli.findSections(showSystemInterface, /^ *edit (.*)/m);
	for (var i in interfaces) {
		var networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		var ipAddress = interfaces[i].config.match(/set ip (\d+\.\d+\.\d+\.\d+)\/(\d{2})/);
		if (ipAddress) {
			var ip = {
				ip: ipAddress[1],
				mask: parseInt(ipAddress[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}

		if (interfaces[i].config.match(/set status down/)) {
			networkInterface.disabled = true;
		}
		var descMatch = interfaces[i].config.match(/set description "(.+)"/);
		if (descMatch) {
			networkInterface.description = descMatch[1];
		}
		device.add("networkInterface", networkInterface);
	}

	var removeChangingParts = function(text) {
		var cleaned = text;
		cleaned = cleaned.replace(/^#conf_file_ver=.*/mg, "");
		cleaned = cleaned.replace(/^ *set [a-z\-]+ ENC .*$/mg, "");
		cleaned = cleaned.replace(/^ *set private-key "(.|[\r\n])*?"$/mg, "");
		return cleaned;
	}

	// If only the passwords are changing (they are hashed with a new salt at each 'show') then
	// just keep the previous configuration.
	// That means we could miss a password change in the history of configurations, but no choice...
	var previousConfiguration = device.get("configuration");
	if (typeof previousConfiguration === "string" &&
			removeChangingParts(previousConfiguration) === removeChangingParts(configuration)) {
		config.set("configuration", previousConfiguration);
	}
	else {
		config.set("configuration", configuration);
	}

};

// No known log message upon configuration change

/**
 * The 'analyzeTrap' function entry point = Will be called when receiving a trap for a device of this type.
 * @param trap = the SNMP data embedded in the trap.
 * @returns true if the trap indicates a configuration changes (this means to initiate a new snapshot).
 */
function analyzeTrap(trap) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.2.1.47.2.0.1";
}

/**
 * The 'snmpAutoDiscover' function entry point = Will be called with the sysObjectID and sysDesc
 * of a device when auto discovering the type of a device.
 * @param sysObjectID = The SNMP sysObjectID of a device being discovered.
 * @param sysDesc = The SNMP sysDesc of a device being discovered.
 * @returns true if the scanned device is supported by this driver.
 */
function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.startsWith("1.3.6.1.4.1.12356.105");
}
