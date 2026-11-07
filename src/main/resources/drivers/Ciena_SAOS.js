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

var Info = {
	name: "CienaSAOS",
	description: "Ciena SAOS",
	author: "Netshot Team",
	version: "1.1"
};

var Config = {
	"runningPackage": {
		type: "Text",
		title: "Running package",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Running package:",
			preLine: "!!  "
		}
	},
	"configuration": {
		type: "LongText",
		title: "Running configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Running configuration (taken on %when%):",
			post: "!! End of running configuration"
		}
	}
};

var Device = {
	"configurationStatus": {
		type: "Text",
		title: "Configuration status",
		searchable: true
	},
	"configurationDirty": {
		type: "Binary",
		title: "Configuration dirty",
		searchable: true
	},
	"chassisMacAddress": {
		type: "Text",
		title: "Chassis MAC address",
		searchable: true
	},
};

var CLI = {
	telnet: {
		macros: {
			shell: {
				options: [ "username", "password", "shell" ],
				target: "shell"
			}
		}
	},
	ssh: {
		macros: {
			shell: {
				options: [ "shell" ],
				target: "shell"
			}
		}
	},
	username: {
		prompt: /^[^ ]{2,63} +login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "usernameAgain" ]
			}
		}
	},
	password: {
		prompt: /^Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "usernameAgain", "disable", "enable" ]
			}
		}
	},
	usernameAgain: {
		prompt: /Uu]sername: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	shell: {
		prompt: /^([^ ]{2,63}> )$/,
		error: /^SHELL PARSE FAILURE: (.*)/m,
		macros: {
			save: {
				cmd: "configuration save",
				options: [ "shell" ],
				target: "shell"
			}
		}
	},
};

function snapshot(cli, device, config) {

	function parseTable(command) {
		var output = cli.command(command);
		var table = {};
		var pattern = /^\| (.+?):? +(?:\||:) (.+?) +\|$/mg;
		var match;
		while (match = pattern.exec(output)) {
			if (table[match[1]]) {
				if (!table[match[1]].push) {
					table[match[1]] = [table[match[1]]];
				}
				table[match[1]].push(match[2]);
			}
			else {
				table[match[1]] = match[2];
			}
		}
		return table;
	}

	cli.macro("shell");
	var runningConfig = cli.command("configuration show brief");
	config.set("configuration", runningConfig);

	var configStatus = parseTable("configuration show status");
	if (configStatus["Status"]) {
		device.set("configurationStatus", configStatus["Status"]);
	}
	if (configStatus["Dirty"]) {
		device.set("configurationDirty", configStatus["Dirty"] === "Yes");
	}

	var hostnameMatch = runningConfig.match(/^system set host-name (.+)/m);
	if (hostnameMatch) {
		device.set("name", hostnameMatch[1]);
	}

	var software = parseTable("software show");
	var runningPackage = software["Running Package"];
	if (runningPackage) {
		var packageMatch = runningPackage.match(/saos-(.*)/);
		if (packageMatch) {
			config.set("runningPackage", packageMatch[1]);
			device.set("softwareVersion", packageMatch[1]);
		}
	}

	var chassisShowDevice = parseTable("chassis show device-id");
	var chassisMacAddress = chassisShowDevice["Chassis MAC Address"];
	if (chassisMacAddress) {
		device.set("chassisMacAddress", chassisMacAddress);
	}
	var serialNumber = chassisShowDevice["Serial Number"] || "Unknown";
	device.add("module", {
		slot: "Chassis",
		partNumber: chassisShowDevice["Part Number/Revision"] || "Unknown",
		serialNumber,
	});
	device.set("serialNumber", serialNumber);

	var chassisShowCapa = parseTable("chassis show capabilities");
	var platformName = chassisShowCapa["Platform Name"];
	if (platformName) {
		device.set("family", "Ciena " + platformName);
	}
	else {
		device.set("family", "Ciena SAOS device");
	}

	device.set("networkClass", "SWITCH");

	var snmpShow = parseTable("snmp show");
	device.set("contact", snmpShow["System Contact"] || "");
	device.set("location", snmpShow["System Location"] || "");

	device.add("module", {
		slot: "",
		partNumber: "",
		serialNumber: "",
	});

	var portShowStatus = cli.command("port show status");
	var portPattern = /^\|([0-9]+) *\|/mg;
	var portMatch;
	while (portMatch = portPattern.exec(portShowStatus)) {
		var portName = portMatch[1];
		var portShowPort = parseTable("port show port " + portName);
		var networkInterface = {
			name: portName,
			description: portShowPort["Description"] || "",
			mac: portShowPort["MAC Address"] || undefined,
			ip: [],
			level3: false,
		};
		var linkState = portShowPort["Link State"];
		if (linkState.match(/^Disabled/)) {
			networkInterface.enabled = false;
		}
		device.add("networkInterface", networkInterface);
	}


	var interfaceShowRemote = parseTable("interface show remote");
	var ifName = interfaceShowRemote["Name"];
	if (ifName) {
		var networkInterface = {
			name: ifName,
			enabled: interfaceShowRemote["Admin State"] === "Enabled",
			description: interfaceShowRemote["Domain"] || "",
			mac: interfaceShowRemote["MAC Address"] || undefined,
			ip: [],
		};
		var ipPattern = /^interface remote set ip (\d+\.\d+\.\d+\.\d+)\/(\d+)/mg;
		while (match = ipPattern.exec(runningConfig)) {
			var ip = {
				ip: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		var ipv6Pattern = /^interface remote set ip ([0-9A-Fa-f:]+)\/(\d+)/mg;
		while (match = ipv6Pattern.exec(runningConfig)) {
			var ip = {
				ipv6: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}

		device.add("networkInterface", networkInterface);
	}
};

/**
 * Configuration for syslog event messages upon config change:
 * syslog send event 0x40010
 */
function analyzeSyslog(message) {
	if (message.match(/CONFIG-5-CONFIG_CHANGED/)) {
		return true;
	}
	return false;
}

/*
 * Configuration for config traps:
 * snmp notification ciena CIENA-CES-CONFIG-MGMT-MIB enable notification-name cienaCesConfigMgmtConfigChangeNotification
 * snmp create community-index netshot-traps community NTSCOMM sec-name private transport-tag anywhereTag
 * snmp create target netshot addr 10.0.223.7/32 param-name netshot-param
 * snmp create target-param netshot-param sec-name private sec-model v2c sec-level noAuth
 */
function analyzeTrap(trap, debug) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.1271.2.2.36.0.2";
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.6141.1.")) {
		return true;
	}
	return false;
}