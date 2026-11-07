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
	name: "CiscoASA",
	description: "Cisco ASA",
	author: "Netshot Team",
	version: "1.7"
};

var Config = {
	"imageFile": {
		type: "Text",
		title: "Image file",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Image file:",
			preLine: "!!  "
		}
	},
	"asaVersion": {
		type: "Text",
		title: "ASA version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"asdmVersion": {
		type: "Text",
		title: "ASDM version",
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

var Device = {
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
		prompt: /^([A-Za-z\-_0-9\.]+(\/[A-Za-z\-]+)*\> )$/,
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
		prompt: /^Password: /m,
		macros: {
			auto: {
				cmd: "$$NetshotSuperPassword$$",
				options: [ "disable", "enable", "enableSecretAgain" ]
			}
		}
	},
	enableSecretAgain: {
		prompt: /^Password: /m,
		fail: "Authentication failed - Wrong enable password."
	},

	enable: {
		prompt: /^([A-Za-z\-_0-9\.]+(\/[A-Za-z\-]+)*# )$/,
		error: /^% (.*)/m,
		pager: {
			avoid: "terminal pager 0",
			match: /^<--- More --->$/,
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
				options: [ "enable", "saveSource" ],
				target: "enable"
			}
		}
	},
	saveSource: {
		prompt: /Source filename \[running-config\]\?/,
		macros: {
			auto: {
				cmd: "",
				options: [ "enable" ]
			}
		}
	},
	
	configure: {
		prompt: /^([A-Za-z\-_0-9\.]+(\/[A-Za-z\-]+)*\(conf[0-9\-a-zA-Z]+\)# )$/,
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

	cli.macro("enable");
	var showVersion = cli.command("show version");
	
	var hostname = showVersion.match(/^(.*) up [0-9]/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	var version = showVersion.match(/Cisco Adaptive Security Appliance Software Version ([0-9a-zA-Z\(\)\.]+)/m);
	version = (version ? version[1] : "Unknown");
	device.set("softwareVersion", version);
	config.set("asaVersion", version);

	var asdm = showVersion.match(/Device Manager Version (.*)/m);
	config.set("asdmVersion", (asdm ? asdm[1] : "Unknown"));

	var serialNumber = showVersion.match(/Serial Number: (.*)/);
	device.set("serialNumber", (serialNumber ? serialNumber[1] : ""));
	
	var author = showVersion.match(/Configuration last modified by (.*) at/);
	if (author) {
		config.set("author", author[1]);
	}

	var hardware = showVersion.match(/^Hardware: *([^,]+)/m);
	device.set("family", (hardware ? hardware[1].replace(/ASA/, "ASA ") : "Cisco ASA"));

	var image = showVersion.match(/System image file is "(.*)"/);
	config.set("imageFile", (image ? image[1] : ""));

	var showInventory = cli.command("show inventory");
	var inventoryPattern = /(?:NAME|Name): \"(.*)\", +DESCR: \"(.*)\"[\r\n]+PID: (.*?) *, +VID: (.*), +SN: (.*)/g;
	var match;
	while (match = inventoryPattern.exec(showInventory)) {
		var module = {
			slot: match[1],
			partNumber: match[3],
			serialNumber: match[5]
		};
		device.add("module", module);
		if (module.slot.match(/Chassis/)) {
			device.set("serialNumber", module.serialNumber);
		}
	}

	device.set("networkClass", "FIREWALL");
	
	var configCleanup = function(config) {
		config = config.replace(/^ASA Version .*/m, "");
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};
	
	var runningConfig = cli.command("more system:running-config");
	runningConfig = configCleanup(runningConfig);	
	config.set("runningConfig", runningConfig);

	var location = runningConfig.match(/^snmp-server location (.*)/m);
	device.set("location", (location ? location[1] : ""));
	var contact = runningConfig.match(/^snmp-server contact (.*)/m);
	device.set("contact", (contact ? contact[1] : ""));
	
	var interfaces = cli.findSections(runningConfig, /^interface (.+)/m);
	for (var i in interfaces) {
		var networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		var description = interfaces[i].config.match(/^ *description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		var ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)( standby (\d+\.\d+\.\d+\.\d+))?/mg;
		while (match = ipPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[1],
				mask: match[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
			if (typeof(match[4]) == "string") {
				var ip = {
					ip: match[4],
					mask: match[2],
					usage: "SECONDARY"
				};
				networkInterface.ip.push(ip);
			}
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
		var showInterface = cli.command("show interface " + networkInterface.name + " | i , is|MAC");
		var macAddress = showInterface.match(/MAC address ([0-9a-fA-F]{4}\.[0-9a-fA-F]{4}\.[0-9a-fA-F]{4})/);
		if (macAddress) {
			networkInterface.mac = macAddress[1];
		}
		if (!showInterface.match(/, is up,/)) {
			networkInterface.enabled = false;
		}
		var nameif = interfaces[i].config.match(/^ *nameif (.*)$/m);
		if (nameif) {
			networkInterface.name = networkInterface.name + " [" + nameif[1] + "]";
		}
		device.add("networkInterface", networkInterface);
	}

};


// The ASA doesn't seem to send any trap upon configuration change
// even with snmp-server enable traps entity config-change

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 16) == "1.3.6.1.4.1.9.1."
		&& sysDesc.match(/Cisco Adaptive Security Appliance/)) {
		return true;
	}
	return false;
}

function analyzeSyslog(message) {
	if (message.match(/%ASA-5-111008: User '(.*)' executed the 'configure terminal'/)) {
		return true;
	}
	return false;
}
