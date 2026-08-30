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
	name: "FortinetFortiManager", /* Unique identifier of the driver within Netshot. */
	description: "Fortinet FortiManager and FortiAnalyzer", /* Description to be used in the UI. */
	author: "Najihel",
	version: "3.1" /* Version will appear in the Admin tab. */
};

/**
 * 'Config' object = Data fields to be included in each configuration revision.
 */
const Config = {
	"osVersion": { /* This stores the detected FortiManager OS version on each snapshot. */
		type: "Text",
		title: "FortiManager OS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## FortiManager OS version:",
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
	},
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
};

/**
 * 'Device' object = Data fields to add to devices of this type.
 */
const Device = {
	"haPeer": { /* This stores the name of an optional HA peer. */
		type: "Text",
		title: "HA peer name",
		searchable: true,
		checkable: true
	}
};

/**
 * 'Options' object = Per-device, user-configurable settings.
 */
const Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive",
		default: true,
	},
};

/**
 * 'CLI' object = Definition of the finite state machine to recognize and handle the CLI prompt changes.
 */
const CLI = {
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
	const status = cli.command("get system status");

	// Read version and family from the 'status' output.
	let version = status.match(/^Version[\s]*: v([0-9]+.*)/m);
	let platform = status.match(/^Platform Full Name[\s]*: ((.*?)(-.*)?)$/m);
	const family = (platform ? platform[2] : "FortiManager");
	platform = (platform ? platform[1] : "FortiManager");
	device.set("family", family);
	version = (version ? version[1] : "Unknown");
	device.set("softwareVersion", version);
	config.set("osVersion", version);

	device.set("networkClass", "SERVER");

	// Store the interface config block
	const showSystemInterface = cli.command("show system interface");
	// Store the SNMP config block
	const showSystemSnmp = cli.command("show system snmp sysinfo");

	// The configuration is retrieved by a simple 'show' at the root level.
	const configuration = cli.command("show", { timeout: 120000 });
	config.set("configuration", configuration);

	// Read the device hostname from the 'status' output.
	let hostname = status.match(/^Hostname[\s]*:[\s]*(.*)$/m);
	if (hostname) {
		hostname = hostname[1];
		device.set("name", hostname);
	}
	// Read the serial number from the 'status' output.
	const serial = status.match(/^Serial Number[\s]*:[\s]*(.*)$/m);
	if (serial) {
		const module = {
			slot: "Chassis",
			partNumber: platform,
			serialNumber: serial[1]
		};
		device.add("module", module);
		device.set("serialNumber", serial[1]);
	}
	else {
		device.set("serialNumber", "");
	}


	// Store the HA status
	for (const haCommand of ["get system ha-status", "diagnose ha status"]) {
		try {
			const getHa = cli.command(haCommand);
			const peerPattern = /^([\s]*hostname:[\s]*)(.*)$/gm;
			while (true) {
				const match = peerPattern.exec(getHa);
				if (!match) break;
				if (match[2] !== hostname) {
					device.set("haPeer", match[2]);
					break;
				}
			}
		}
		catch (e) {
			// continue
		}
	}

	// Read the contact and location fields from the configuration directly.
	device.set("contact", "");
	device.set("location", "");
	const sysInfos = cli.findSections(showSystemSnmp, /config system snmp sysinfo/);
	for (const sysInfo of sysInfos) {
		const contact = sysInfo.config.match(/set contact_info "(.*)"/);
		if (contact) {
			device.set("contact", contact[1]);
		}
		const location = sysInfo.config.match(/set location "(.*)"/);
		if (location) {
			device.set("location", location[1]);
		}
	}

	// Read the list of interfaces.
	const interfaces = cli.findSections(showSystemInterface, /^ *edit "(.*)"/m);
	for (const iface of interfaces) {
		const networkInterface = {
			name: iface.match[1],
			ip: []
		};
		const ipAddress = iface.config.match(/set ip (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/);
		if (ipAddress) {
			const ip = {
				ip: ipAddress[1],
				mask: ipAddress[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}

		if (iface.config.match(/set status down/)) {
			networkInterface.disabled = true;
		}
		const descMatch = iface.config.match(/set description "(.+)"/);
		if (descMatch) {
			networkInterface.description = descMatch[1];
		}
		device.add("networkInterface", networkInterface);
	}

	var removeChangingParts = function(text) {
		var cleaned = text;
		cleaned = cleaned.replace(/^#conf_file_ver=.*/mg, "");
		cleaned = cleaned.replace(/^ *set (passphrase|password|passwd|secondary-secret|crptpasswd) ENC .*$/mg, "");
		cleaned = cleaned.replace(/^ *set private-key "(.|[\r\n])*?"$/mg, "");
		return cleaned;
	}

	if (!device.options || device.options.fullBackup) {
		try {
			const ticket = config.requestUpload();
			const backupName = "backup.tar";
			const encryptPass = "netshot";
			cli.command(`execute backup all-settings sftp ${ticket.host}:${ticket.port} ${backupName} ${ticket.username} ${ticket.password} ${encryptPass}`);
			let md5 = null;
			let attempt = 0;
			while (true) {
				const backupOutput = cli.command("", {
					mode: { prompt: null },
					discoverWaitTime: 5000,
				});
				const md5Match = backupOutput.match(/MD5: ([0-9a-f]+)/);
				if (md5Match) {
					md5 = md5Match[1];
					cli.debug(`Archive MD5 checksum should be ${md5}`);
				}
				const resultMatch = backupOutput.match(/Backup all settings...(.+)/);
				if (resultMatch) {
					const result = resultMatch[1];
					if (result !== "Ok.") {
						cli.debug(`Full backup output:\n${backupOutput}`);
						throw `Full backup (execute backup all-settings) returned ${result}`;
					}
					break;
				}
				attempt += 1;
				if (attempt > 360) {
					throw `Could not get full backup (execute backup all-settings) result after 15 minutes`;
				}
			}
			const uploadResult = config.awaitUpload(ticket.id);
			if (uploadResult.files.length !== 1) {
				throw `Invalid number of files (${uploadResult.files.length}) received by Netshot server`;
			}
			const file = uploadResult.files[0];
			config.commitUpload(ticket.id, file.id, "backupArchive", { checksum: md5 });
		}
		catch (e) {
			const error = String(e);
			if (error.match(/server is not running/)) {
				cli.debug("Netshot SSH server is disabled, the full backup won't be taken");
				config.computeHash(removeChangingParts(configuration));
			}
			else {
				throw e;
			}
		}
	}
	else {
		config.computeHash(removeChangingParts(configuration));
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
	return sysObjectID.startsWith("1.3.6.1.4.1.12356.103.");
}
