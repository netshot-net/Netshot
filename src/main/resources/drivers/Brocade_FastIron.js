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
	name: "BrocadeFastIron",
	description: "Brocade FastIron",
	author: "Netshot Team",
	version: "1.4"
};

var Config = {
	"bootVersion": {
		type: "Text",
		title: "BootROM version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"iwVersion": {
		type: "Text",
		title: "IronWare version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! IronWare version:",
			preLine: "!!  "
		}
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

var Device = {
	"dram": {
		type: "Numeric",
		title: "DRAM (MB)",
		searchable: true
	},
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true
	}
};

var CLI = {
	telnet: {
		macros: {
			enable: {
				options: [ "username", "password", "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "username", "password", "enable", "disable" ],
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
		prompt: /^Username: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password" ]
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
		prompt: /^Username: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	disable: {
		prompt: /^((telnet|ssh|SSH)@[A-Za-z\-_0-9\. ]+\>)$/,
		pager: {
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
		prompt: /^(Enable )?Password:/m,
		macros: {
			auto: {
				cmd: "$$NetshotSuperPassword$$",
				options: [ "disable", "enable", "enableSecretAgain" ]
			}
		}
	},
	enableSecretAgain: {
		prompt: /^Password:/m,
		fail: "Authentication failed - Wrong enable password."
	},

	enable: {
		prompt: /^((telnet|ssh|SSH)@[A-Za-z\-_0-9\. ]+#)$/,
		error: /^Invalid input/m,
		pager: {
			avoid: "skip-page-display",
			match: /^--More--$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "enable", "configure" ],
				target: "configure"
			},
			save: {
				cmd: "write memory",
				options: [ "saveDone" ],
				target: "saveDone"
			}
		}
	},
	
	saveDone: {
		prompt: /Write startup-config done\./,
		macros: {
			auto: {
				options: [ "enable" ],
				target: "enable"
			}
		}
	},
	
	configure: {
		prompt: /^((telnet|ssh|SSH)@[A-Za-z\-_0-9\. ]+\(conf[0-9\-a-zA-Z]+\)#)$/,
		error: /^Invalid input/m,
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
	
	var configCleanup = function(config) {
		config = config.replace(/^Current configuration:$/m, "");
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};
	
	cli.macro("enable");
	var runningConfig = cli.command("show running-config");
	runningConfig = configCleanup(runningConfig);
	config.set("runningConfig", runningConfig);
	
	var startupConfig = cli.command("show configuration");
	startupConfig = configCleanup(startupConfig);
	device.set("configurationSaved", startupConfig == runningConfig);
	
	var showVersion = cli.command("show version");
	
	var hostname = runningConfig.match(/^hostname (.*)$/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	var version = showVersion.match(/SW: Version (.*)( Copyright|)/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("iwVersion", version[1]);
	}
	else {
		device.set("softwareVersion", "Unknown");
		config.set("iwVersion", "Unknown");
	}
	
	var bootVersion = showVersion.match(/(BootROM: Version |Boot-Monitor.*Version:)([0-9a-zA-Z\.]+)/);
	if (bootVersion) {
		config.set("bootVersion", bootVersion[2]);
	}
	else {
		config.set("bootVersion", "Unknown");
	}
	
	var dram = showVersion.match(/([0-9]+) MB DRAM/);
	if (dram) {
		device.set("dram", parseInt(dram[1]));
	}
	else {
		device.set("dram", -1);
	}
	
	device.set("networkClass", "SWITCH");
	
	var family = showVersion.match(/\(PROM-TYPE (.*)\)/);
	if (family) {
		device.set("family", family[1]);
	}
	else {
		device.set("family", "Unknown FastIron");
	}
	
	var serial = showVersion.match(/HW: (.+?)[\r\n](.|\r|\n)+Serial *#: (.*)/);
	if (serial) {
		var partNumber = serial[1];
		partNumber = partNumber.replace(/ \(.*\)/g, "");
		partNumber = partNumber.replace(/Stackable /, "");
		var serialNumber = serial[3];
		device.add("module", {
			slot: "Chassis",
			partNumber: partNumber,
			serialNumber: serialNumber
		});
		device.set("serialNumber", serialNumber)
	}
	
	var location = runningConfig.match(/^snmp-server location (.*)/m);
	if (location) {
		device.set("location", location[1]);
	}
	else {
		device.set("location", "");
	}
	var contact = runningConfig.match(/^snmp-server contact (.*)/m);
	if (contact) {
		device.set("contact", contact[1]);
	}
	else {
		device.set("contact", "");
	}
	
	var interfaces = cli.findSections(runningConfig, /^interface (.+)/m);
	for (var i in interfaces) {
		var networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		var description = interfaces[i].config.match(/^ *port-name (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		if (interfaces[i].match[1].match(/^ethernet/m)) {
			networkInterface.level3 = false;
		}
		var ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)( secondary)?/mg;
		while (match = ipPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[1],
				mask: match[2],
				usage: "PRIMARY"
			};
			if (match[3] == " secondary") {
				ip.usage = "SECONDARY";
			}
			networkInterface.ip.push(ip);
		}
		var ipv6Pattern = /^ *ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg;
		while (match = ipv6Pattern.exec(interfaces[i].config)) {
			var ip = {
				ipv6: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		var showInterface = cli.command("show interface " + networkInterface.name + " | inc address|line protocol");
		var macAddress = showInterface.match(/address is ([0-9a-fA-F]{4}\.[0-9a-fA-F]{4}\.[0-9a-fA-F]{4})/);
		if (macAddress) {
			networkInterface.mac = macAddress[1];
		}
		if (showInterface.match(/ is disabled/)) {
			networkInterface.enabled = false;
		}
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	if (message.match(/config was changed by (.*)/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.1991.0.73";
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 21) == "1.3.6.1.4.1.1991.1.3."
		&& sysDesc.match(/IronWare Version/)) {
		return true;
	}
	return false;
}
