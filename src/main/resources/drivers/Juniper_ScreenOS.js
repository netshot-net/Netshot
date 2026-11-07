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
	name: "JuniperScreenOS", /* Unique identifier of the driver within Netshot. */
	description: "Juniper ScreenOS", /* Description to be used in the UI. */
	author: "Netshot Team",
	version: "1.1" /* Version will appear in the Admin tab. */
};

/**
 * 'Config' object = Data fields to be included in each configuration revision.
 */
var Config = {
	"screenOsVersion": {
		type: "Text",
		title: "ScreenOS version",
		comparable: true,
		searchable: true,
		dump: {
			pre: "# ScreenOS version:",
		}
	},
	"screenOsFileName": {
		type: "Text",
		title: "ScreenOS file name",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "# ScreenOS file name:",
			preLine: "##  "
		}
	},
	"licenseKey": {
		type: "LongText",
		title: "License Key",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "# Device License Key:",
			preLine: "##  "
		}
	},
	"configuration": {
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
};

/**
 * 'Device' object = Data fields to add to devices of this type. 
 */
var Device = {
};

/**
 * 'CLI' object = Definition of the finite state machine to recognize and handle the CLI prompt changes. 
 */
var CLI = {
	telnet: {
		macros: {
			operate: {
				options: [ "username", "operate" ],
				target: "operate"
			}
		}
	},
	ssh: {
		macros: {
			operate: {
				options: [ "operate" ],
				target: "operate"
			}
		}
	},
	username: {
		prompt: /^login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password" ]
			}
		}
	},
	password: {
		prompt: /^password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "usernameAgain", "operate" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	operate: {
		prompt: /^([^\s]+\->) $/,
		pager: {
			match: /^--- more --- $/,
			response: " "
		},
		error: /^Failed command/m,
	},
};

/**
 * The 'snapshot' function entry point = Will be called by Netshot when initiating a snapshot of this type of device.
 * @param cli = object used to interact with the current device via CLI.
 * @param device = used to store data at the device level.
 * @param config = used to store data at the configuration revision level.
 */
function snapshot(cli, device, config) {
	
	var configCleanup = function(config) {
		var p = config.search(/^(set|unset) /m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};
	
	cli.macro("operate");
	var getSystem = cli.command("get system");
	var getConfig = cli.command("get config", { timeout: 120000 });
	var configuration = configCleanup(getConfig);
	
	config.set("configuration", configuration);
	
	var location = configuration.match(/^set snmp location ("(.+)"|(.+))$/m);
	location = location ? (location[2] || location[3]) : "";
	device.set("location", location);
	var contact = configuration.match(/^set snmp contact ("(.+)"|(.+))$/m);
	contact = contact ? (contact[2] || contact[3]) : "";
	device.set("contact", contact);
	
	var hostname = configuration.match(/^set hostname (.+)$/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	var version = getSystem.match(/Software Version: ([0-9\.r]+)/m);
	if (version != null) {
		device.set("softwareVersion", version[1]);
		config.set("screenOsVersion", version[1]);
	}
	else {
		device.set("softwareVersion", "Unknown");
		config.set("screenOsVersion", "Unknown");
	}
	
	var fileName = getSystem.match(/File Name: (.+?), /m);
	if (fileName) {
		config.set("screenOsFileName", fileName[1]);
	}
	
	device.set("networkClass", "FIREWALL");

	var productName = getSystem.match(/Product Name: (.*)/m);
	if (productName) {
		device.set("family", productName[1].replace(/\-/g, " "));
	}
	else {
		device.set("family", "NetScreen");
	}
	
	var serialNumber = getSystem.match(/Serial Number: (.+?),/m);
	if (serialNumber) {
		device.set("serialNumber", serialNumber[1]);
		device.add("module", {
			slot: "Chassis",
			partNumber: productName ? productName[1] : "NetScreen",
			serialNumber: serialNumber[1]
		});
	}
	
	var licenseKey = cli.command("get license-key");
	config.set("licenseKey", licenseKey);
	
	var disabledInterfaces = [];
	var disabledIntPattern = /^set interface (.+) phy link-down/mg;
	var match;
	while (match = disabledIntPattern.exec(configuration)) {
		disabledInterfaces.push(match[1]);
	}
	
	var interfaces = cli.findSections(getSystem, /^Interface (.+):/m);
	for (var i in interfaces) {
		var networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		if (disabledInterfaces.indexOf(networkInterface.name) > -1) {
			networkInterface.enabled = true;
		}
		var description = interfaces[i].config.match(/^ *description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		var zone = interfaces[i].config.match(/^ *vsys (.+), zone (.+), vr (.+)$/m);
		if (zone) {
			networkInterface.virtualDevice = zone[1];
			networkInterface.vrf = zone[3];
		}
		var ipPattern = /^ *(\*)?ip (\d+\.\d+\.\d+\.\d+)\/(\d+)/mg;
		while (match = ipPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[2],
				mask: parseInt(match[3]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		var macAddress = interfaces[i].config.match(/mac ([0-9a-fA-F]{4}\.[0-9a-fA-F]{4}\.[0-9a-fA-F]{4})/);
		if (macAddress) {
			networkInterface.mac = macAddress[1];
		}
		device.add("networkInterface", networkInterface);
	}

};

/**
 * The 'analyzeTrap' function entry point = Will be called when receiving a trap for a device of this type.
 * @param trap = the SNMP data embedded in the trap.
 * @returns true if the trap indicates a configuration changes (this means to initiate a new snapshot).
 */
function analyzeSyslog(message) {
	if (message.match(/(system-notification-00009|System configuration)/)) {
		return true;
	}
	return false;
}

/**
 * The 'analyzeTrap' function entry point = Will be called when receiving a trap for a device of this type.
 * @param trap = the SNMP data embedded in the trap.
 * @returns true if the trap indicates a configuration changes (this means to initiate a new snapshot).
 */
function analyzeTrap(trap) {
	return false;
	// The NetScreen device doesn't seem to send any SNMP trap in case of configuration change.
}

/**
 * The 'snmpAutoDiscover' function entry point = Will be called with the sysObjectID and sysDesc
 * of a device when auto discovering the type of a device.
 * @param sysObjectID = The SNMP sysObjectID of a device being discovered.
 * @param sysDesc = The SNMP sysDesc of a device being discovered.
 * @returns true if the scanned device is supported by this driver.
 */
function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.substring(0, 17) == "1.3.6.1.4.1.3224." && sysDesc.match(/NetScreen/i);
}
