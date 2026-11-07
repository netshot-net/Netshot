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
	name: "AvocentACS", /* Unique identifier of the driver within Netshot. */
	description: "Avocent ACS", /* Description to be used in the UI. */
	author: "Netshot Team",
	version: "1.3" /* Version will appear in the Admin tab. */
};

/**
 * 'Config' object = Data fields to be included in each configuration revision.
 */
const Config = {
	"firmwareVersion": { /* This stores the detected software version on each snapshot. */
		type: "Text",
		title: "Firmware version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Firmware version:",
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
	"bootCodeVersion": { /* This stores the last boot code version. */
		type: "Text",
		title: "Boot code version",
		searchable: true,
		checkable: true
	},
	"bootMode": { /* This stores the boot mode. */
		type: "Text",
		title: "Boot mode",
		searchable: true,
		checkable: true
	},
	"bootImage": { /* This stores the boot image name. */
		type: "Text",
		title: "Boot image",
		searchable: true,
		checkable: true
	},
	"image1Version": { /* This stores the first image version. */
		type: "Text",
		title: "Image 1 version",
		searchable: true,
		checkable: true
	},
	"image2Version": { /* This stores the second image version. */
		type: "Text",
		title: "Image 2 version",
		searchable: true,
		checkable: true
	},
	"language": { /* This stores the appliance language. */
		type: "Text",
		title: "Appliance language",
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
			cli: {
				options: [ "cli", "bash" ],
				target: "cli"
			}
		}
	},
	cli: { /* The basic Avocent prompt. */
		prompt: /^((\*\*|--)(:-|:#-)\s[a-z\.-_]+\scli->)(\s)?(\x07)?$/,
		clearPrompt: true,
		error: /^(Error: .*)/m,
		pager: { /* 'pager': define how to handle the pager for long outputs. */
			match: /^-- MORE --:/,
			response: "\r",
		},
		macros: {
			bash: {
				cmd: "shell",
				options: [ "shell" ],
				target: "shell",
			},
			commit: {
				cmd: "commit",
				options: [ "cli" ],
				target: "cli",
			},
			revert: {
				cmd: "revert",
				options: [ "cli" ],
				target: "cli",
			},
			delete: {
				cmd: "delete -",
				options: [ "cli" ],
				target: "cli",
			},
			save: {
				cmd: "save --cancelOnError",
				options: [ "cli" ],
				target: "cli",
			},
		},
	},
	bash: {
		prompt: /^(\[[^\n\]]+\]\$ )$/,
		macros: {
			cli: {
				cmd: "cli",
				options: [ "cli" ],
				target: "cli",
			},
		},
	},
};

/**
 * The 'snapshot' function entry point = Will be called by Netshot when initiating a snapshot of this type of device.
 * @param cli = object used to interact with the current device via CLI.
 * @param device = used to store data at the device level.
 * @param config = used to store data at the configuration revision level.
 */
function snapshot(cli, device, config) {

	const cleanCommand = (cmd, options = {}) => {
		const output = cli.command(cmd, options);
		if (output) {
			return output.replace(/\x07/g, "").replace(/\r/g, "");
		}
		return "";
	};

    const cleanConfig = config => config
        .replace(/^#set (month|day|year|hour|minute|second)=.*\n/mg, "");

	// Targets the 'basic' CLI mode.
	cli.macro("cli");

	// Retrieve the hostname
	const hostname = cleanCommand("hostname");

	// Retrieve system information
	const information = cleanCommand("show /system/information");

	// Retrieve boot configuration
	const bootConfiguration = cleanCommand("show /system/boot_configuration", { timeout: 30000 });

	// Retrieve language
	const helpLanguage = cleanCommand("show /system/help_and_language");

	// Retrive full config
	const configuration = cleanCommand("list_configuration", { timeout: 180000 });

	// Retrive access ports
	const showAccess = cleanCommand("show /access");

	// Assign configuration
	config.set("configuration", cleanConfig(configuration));

	// Hostname
	const nameMatch = hostname.match(/^(.+)/m);
	if (nameMatch) {
		device.set("name", nameMatch[1]);
	}

	// Extract software version
	const firmwareMatch = information.match(/^firmware: ([0-9\.]+)/m);
	const version = firmwareMatch ? firmwareMatch[1] : "Unknown";
	device.set("softwareVersion", version);
	config.set("firmwareVersion", version);
	const bootCodeMatch = information.match(/^bootcode: ([0-9\.]+)/m);
	const bootCode = bootCodeMatch ? bootCodeMatch[1] : "Unknown";
	device.set("bootCodeVersion", bootCode);

	// Extract family info
	const modelMatch = information.match(/^type: ([^ ]+)/m);
	device.set("family", modelMatch ? modelMatch[1] : "Unknown ACS");

	// Extract inventory info
	const serialMatch = information.match(/^serial number: (.+)/m);
	if (serialMatch && modelMatch) {
		const module = {
			slot: "Chassis",
			partNumber: modelMatch[1],
			serialNumber: serialMatch[1],
		};
		device.add("module", module);
		device.set("serialNumber", serialMatch[1]);
	}
	else {
		device.set("serialNumber", "");
	}

	// Extract image info
	let imageMatch = bootConfiguration.match(/^image 1 version: ([0-9\.]+)/m);
	if (imageMatch) {
		device.set("image1Version", imageMatch[1]);
	}
	imageMatch = bootConfiguration.match(/^image 2 version: ([0-9\.]+)/m);
	if (imageMatch) {
		device.set("image2Version", imageMatch[1]);
	}
	const bootModeMatch = bootConfiguration.match(/^boot_mode = (.+)/m);
	if (bootModeMatch) {
		device.set("bootMode", bootModeMatch[1]);
	}
	const bootImageMatch = bootConfiguration.match(/^boot_image = (.+)/m);
	if (bootImageMatch) {
		device.set("bootImage", bootImageMatch[1]);
	}

	// Extract language info
	const languageMatch = helpLanguage.match(/appliance_language = (.+)/m);
	if (languageMatch) {
		device.set("language", languageMatch[1]);
	}

	// Static network class
	device.set("networkClass", "CONSOLESERVER");

	// Extract contact and location from configuration
	const contactMatch = configuration.match(/^set syscontact=(.*)/m);
	if (contactMatch) {
		device.set("contact", contactMatch[1].replace(/^"(.+)"$/, "$1"));
	}
	const locationMatch = configuration.match(/^set syslocation=(.*)/m);
	if (locationMatch) {
		device.set("location", locationMatch[1].replace(/^"(.+)"$/, "$1"));
	}

	// Extract network interface info
	const ifPattern = /^cd \/network\/devices\/(.+)\nbatch_mode\n((.*\n)*?)submit/mg;
	let ifMatch;
	while (ifMatch = ifPattern.exec(configuration)) {
		const ifName = ifMatch[1];
		const ifConfig = ifMatch[2];
		const networkInterface = {
			name: ifName,
			ip: [],
		};
		const ipMatch = ifConfig.match(/ipv4_address=([0-9\.]+) ipv4_mask=([0-9\.]+)/);
		if (ipMatch) {
			networkInterface.ip.push({
				ip: ipMatch[1],
				mask: ipMatch[2],
				usage: "PRIMARY",
			});
		}
		device.add("networkInterface", networkInterface);
	}

	// Add console ports as interfaces
	const portPattern = /^ +(.+?) +([0-9]+) +(.+?) +(.+)$/mg;
	let portMatch;
	while (portMatch = portPattern.exec(showAccess)) {
		device.add("networkInterface", {
			name: `${portMatch[3]}${portMatch[2]}`,
			description: portMatch[1],
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
	return !!message.match(/user logged out/);
}

/**
 * The 'analyzeTrap' function entry point = Will be called when receiving a trap for a device of this type.
 * @param trap = the SNMP data embedded in the trap.
 * @returns true if the trap indicates a configuration changes (this means to initiate a new snapshot).
 */
function analyzeTrap(trap) {
	// Generic trap value is missing in the trap object
	return !!(trap["1.3.6.1.4.1.10418.16.2.6.1"] &&
			trap["1.3.6.1.4.1.10418.16.2.6.11"]);
}

/**
 * The 'snmpAutoDiscover' function entry point = Will be called with the sysObjectID and sysDesc
 * of a device when auto discovering the type of a device.
 * @param sysObjectID = The SNMP sysObjectID of a device being discovered.
 * @param sysDesc = The SNMP sysDesc of a device being discovered.
 * @returns true if the scanned device is supported by this driver.
 */
function snmpAutoDiscover(sysObjectID, sysDesc) {
	return !!(sysObjectID.startsWith("1.3.6.1.4.1.10418.")
			&& sysDesc.match(/^(Cyclades|Avocent) ACS [0-9]{4}$/));
}