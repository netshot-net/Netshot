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

const Info = {
	name: "AristaMOS",
	description: "Arista Metamako OS",
	author: "Netshot Team",
	version: "1.0"
};

const Config = {
	"mosVersion": {
		type: "Text",
		title: "MOS version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"runningConfig": {
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

const Device = {
	"totalDiskSize": {
		type: "Numeric",
		title: "Total disk size (MB)",
		searchable: true,
	},
	"managementControllerVersion": {
		type: "Text",
		title: "Management Controller Version",
		searchable: true,
	},
};

const CLI = {
	telnet: {
		macros: {
			enable: {
				options: [ "username", "initialPassword", "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "username", "initialPassword", "enable", "disable" ],
				target: "configure"
			}
		}
	},
	ssh: {
		macros: {
			enable: {
				options: [ "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "enable", "disable" ],
				target: "configure"
			}
		}
	},
	username: {
		prompt: /^[Uu]sername: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "usernameAgain" ]
			}
		}
	},
	initialPassword: {
		prompt: /^[Pp]assword: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "username", "disable", "enable" ]
			}
		},
	},
	password: {
		prompt: /^[Pp]assword: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "usernameAgain", "disable", "enable" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^[Uu]sername: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	disable: {
		prompt: /^([A-Za-z\-_0-9\.\/]+\>)$/,
		pager: {
			avoid: "terminal length 0",
			match: /^--More--$/,
			response: " "
		},
		macros: {
			enable: {
				cmd: "enable",
				options: [ "enable", "disable", "enableSecret" ],
				target: "enable"
			},
			configure: {
				cmd: "enable",
				options: [ "enable", "disable", "enableSecret" ],
				target: "configure"
			}
		}
	},
	enableSecret: {
		prompt: /^[Pp]assword: /m,
		macros: {
			auto: {
				cmd: "$$NetshotSuperPassword$$",
				options: [ "disable", "enable", "enableSecretAgain" ]
			}
		}
	},
	enableSecretAgain: {
		prompt: /^[Pp]assword: /m,
		fail: "Authentication failed - Wrong enable password."
	},

	enable: {
		prompt: /^([A-Za-z\-_0-9\.\/]+#)$/,
		error: /^% (.*)/m,
		pager: {
			avoid: "terminal length 0",
			match: /^ --More--$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "enable", "configure" ],
				target: "configure"
			},
			save: {
				cmd: "copy running-config startup-config",
				options: [ "enable" ],
				target: "enable"
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z\-_0-9\.\/]+\(conf[0-9\-a-zA-Z]+\)#)$/,
		error: /^% (.*)/m,
		clearPrompt: true,
		macros: {
			end: {
				cmd: "end",
				options: [ "enable", "configure" ],
				target: "enable"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	const configCleanup = function(config) {
		config = config.replace(/^! [Cc]ommand: .*\r?\n/m, "");
		config = config.replace(/^! [Tt]ime: .*\r?\n/m, "");
		return config;
	};

	const tryCommands = function(commands) {
		for (const command of commands) {
			try {
				return cli.command(command);
			}
			catch (error) {
				// Try next
			}
		}
		return ""; // default
	}
	
	cli.macro("enable");
	const runningConfig = cli.command("show running-config");
	config.set("runningConfig", configCleanup(runningConfig));

	const configLogs = cli.command("show logging system | include Configured");
	let author;
	const configByPattern = /Configured from (.+?) by (.+?) on/g;
	while (match = configByPattern.exec(configLogs)) {
		author = match[2];
	}
	if (author) {
		config.set("author", author);
	}
	
	const hostname = runningConfig.match(/^hostname (.+)/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	const showVersion = cli.command("show version");
	const version = showVersion.match(/^Software image version: (.+)/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("eosVersion", version[1]);
	}

	const controllerVersion = showVersion.match(/^System management controller version: (.+)/m);
	if (controllerVersion) {
		device.set("managementControllerVersion", controllerVersion[1])
	}
	
	const family = showVersion.match(/^Device: +(.+)/m);
	if (family) {
		device.set("family", family[1].replace(/^Metamako /, ""));
	}
	else {
		device.set("family", "Unknown Metamako device");
	}
	device.set("networkClass", "SWITCH");
	
	const mainSerialNumber = showVersion.match(/^Serial number: +(.+)/m);
	if (mainSerialNumber) {
		device.set("serialNumber", mainSerialNumber[1]);
	}

	const showSnmpLocation = tryCommands(["show snmp v2-mib location", "show snmp location"]);
	const location = showSnmpLocation.match(/^Location: +(.+)/m);
	device.set("location", location ? location[1] : "");

	const showSnmpContact = tryCommands(["show snmp v2-mib contact", "show snmp contact"]);
	const contact = showSnmpContact.match(/^Contact: +(.+)/m);
	device.set("contact", contact ? contact[1] : "");

	const showInventory = cli.command("show inventory");
	const systemInformation = showInventory.match(/^System Information:\r?\n(( +.*\r?\n)*)/m);
	if (systemInformation) {
		const model = systemInformation[1].match(/^ +Model: (.+)/m);
		const serialNumber = systemInformation[1].match(/^ +Serial number: (.+)/m);
		device.add("module", {
			slot: "Chassis",
			partNumber: model[1],
			serialNumber: serialNumber[1],
		});
	}
	const powerSupplies = showInventory.match(
		/Power Supply Information: .*\s*Slot +Model +Serial +.*\r?\n[- ]+\r?\n(([0-9]+ +(\S+) +(\S+) +.*\r?\n)*)/m);
	if (powerSupplies) {
		const psPattern = /^([0-9]+) +(\S+) +(\S+) +.*/mg;
		let psMatch;
		while (psMatch = psPattern.exec(powerSupplies[1])) {
			device.add("module", {
				slot: `PS${psMatch[1]}`,
				partNumber: psMatch[2],
				serialNumber: psMatch[3],
			});
		}
	}
	const disks = showInventory.match(/^Drives:\r?\n(( +.*\r?\n)*)/m);
	let diskSize = 0;
	if (disks) {
		const capaPattern = /^ +User Capacity: ([0-9,]+)/mg;
		let capaMatch;
		while (capaMatch = capaPattern.exec(disks[1])) {
			diskSize += parseInt(capaMatch[1].replace(/,/g, ""));
		}
	}
	device.set("totalDiskSize", Math.round(diskSize / 1024 / 1024));
	
	const interfaceSections = cli.findSections(runningConfig, /^interface ([^ ]+)/m);
	for (const interfaceSection of interfaceSections) {
		const networkInterface = {
			name: interfaceSection.match[1],
			level3: !!interfaceSection.match[1].match(/^ma[0-9]+/),
			ip: [],
		};
		const ipMatch = interfaceSection.config.match(/^ +ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/m);
		if (ipMatch) {
			networkInterface.ip.push({
				ip: ipMatch[1],
				mask: ipMatch[2],
				usage: "PRIMARY",
			});
		}
		if (interfaceSection.config.match(/^ +shutdown$/m)) {
			networkInterface.enabled = false;
		}
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	if (message.match(/Configured from (\S*) by (\S*) on pts/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	for (var t in trap) {
		if (trap[t] == "3" && t.substring(0, 31) == "1.3.6.1.4.1.30065.3.9.1.1.2.1.5") {
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return !!(sysObjectID.startsWith("1.3.6.1.4.1.43191.1.2.")
		&& sysDesc.match(/Metamako MOS/));
}
