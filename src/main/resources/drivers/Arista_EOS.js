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
	name: "AristaEOS",
	description: "Arista EOS",
	author: "Netshot Team",
	version: "1.1"
};

var Config = {
	"eosBootImageFile": {
		type: "Text",
		title: "EOS boot image file",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! EOS boot image file:",
			preLine: "!!  "
		}
	},
	"eosVersion": {
		type: "Text",
		title: "EOS version",
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
	"totalMemorySize": {
		type: "Numeric",
		title: "Total memory size (MB)",
		searchable: true
	},
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true
	},
	"systemMacAddress": {
		type: "Text",
		title: "System MAC address",
		searchable: true
	}
};

var CLI = {
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
	
	var match;
	
	var configCleanup = function(config) {
		config = config.replace(/^! Command: .*\r?\n/m, "");
		config = config.replace(/^! Startup-config .*\r?\n/m, "");
		return config;
	};
	
	cli.macro("enable");
	var runningConfig = cli.command("show running-config");
	runningConfig = configCleanup(runningConfig);
	config.set("runningConfig", runningConfig);
	
	var configLogs = cli.command("show logging last 1 hours | include SYS-5-CONFIG_I");
	var author;
	var configByPattern = /Configured from (.+?) by (.+?) on/g;
	while (match = configByPattern.exec(configLogs)) {
		author = match[2];
	}
	if (author) {
		config.set("author", author);
	}
	
	var startupConfig = cli.command("show startup-config");
	startupConfig = configCleanup(startupConfig);
	device.set("configurationSaved", startupConfig == runningConfig);
	
	var hostname = runningConfig.match(/^hostname (.+)/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	var showVersion = cli.command("show version");
	var version = showVersion.match(/^Software image version: (.+)/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("eosVersion", version[1]);
	}
	
	var systemMac = showVersion.match(/^System MAC address: +([0-9\.]+)/m);
	if (systemMac) {
		device.set("systemMacAddress", systemMac[1]);
	}
	
	var memory = showVersion.match(/^Total memory: +([0-9]+) kB/m);
	if (memory) {
		device.set("totalMemorySize", Math.round(parseInt(memory[1]) / 1024));
	}
	
	var showBoot = cli.command("show boot");
	var image = showBoot.match(/^Software image: (.+)/m);
	if (image) {
		config.set("eosBootImageFile", image[1]);
	}
	
	var family = showVersion.match(/^Arista (.+)/m);
	device.set("family", family ? family[0] : "Unknown Arista device");
	device.set("networkClass", "SWITCH");
	
	var serialNumber = showVersion.match(/^Serial number: +(.+)/m);
	if (serialNumber) {
		device.set("serialNumber", serialNumber[1]);
	}
	
	var location = runningConfig.match(/^snmp-server location (.*)/m);
	device.set("location", location ? location[1] : "");
	var contact = runningConfig.match(/^snmp-server contact (.*)/m);
	device.set("contact", contact ? contact[1] : "");

	try {
		var showInventory = cli.command("show inventory | json");
		var inventory = JSON.parse(showInventory);
		for (var inventoryType in inventory) {
			var slots = inventory[inventoryType];
			if (typeof slots === "object") {
				for (var slotId in slots) {
					var slot = slots[slotId];
					if (typeof slot === "object") {
						var partNumber = slot.name || slot.modelName || "";
						var serialNumber = slot.serialNum;
						if (serialNumber) {
							var module = {
								slot: slotId,
								partNumber: partNumber,
								serialNumber: serialNumber,
							};
							device.add("module", module);
						}
					}
				}
			}
		}
		var systemInformation = inventory.systemInformation;
		if (systemInformation) {
			var module = {
				slot: "Chassis",
				partNumber: systemInformation.name,
				serialNumber: systemInformation.serialNum,
			};
			device.add("module", module);
		}
		
	}
	catch (e) {
		cli.debug("Error while reading the inventory. Does the device support 'json' pipe modifier?");
	}
	
	var vrfPattern = /^(?:ip vrf|vrf definition) (.+)/mg;
	while (match = vrfPattern.exec(runningConfig)) {
		device.add("vrf", match[1]);
	}
	
	var interfaces = cli.findSections(runningConfig, /^interface ([^ ]+)/m);
	for (var i in interfaces) {
		var networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		var description = interfaces[i].config.match(/^ *description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		var vrf = interfaces[i].config.match(/^ *(?:ip )?vrf forwarding (.+)$/m);
		if (vrf) {
			networkInterface.vrf = vrf[1];
		}
		if (interfaces[i].config.match(/^ *switchport$/m)) {
			networkInterface.level3 = false;
		}
		var ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+)\/(\d+)( secondary)?/mg;
		while (match = ipPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			};
			if (match[3] == " secondary") {
				ip.usage = "SECONDARY";
			}
			networkInterface.ip.push(ip);
		}
		var fhrpPattern = /^ *(standby|vrrp|glbp)( [0-9]+)? ip (\d+\.\d+\.\d+\.\d+)( secondary)?/mg;
		while (match = fhrpPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[3],
				mask: 32,
				usage: "HSRP"
			};
			if (match[1] == "vrrp") {
				ip.usage = "VRRP";
			}
			if (match[4] == " secondary") {
				ip.usage = "SECONDARY" + ip.usage;
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
		try {
			var showInterface = cli.command("show interface " + networkInterface.name + " | inc address|line protocol");
			var macAddress = showInterface.match(/address is ([0-9a-fA-F]{4}\.[0-9a-fA-F]{4}\.[0-9a-fA-F]{4})/);
			if (macAddress) {
				networkInterface.mac = macAddress[1];
			}
			if (showInterface.match(/ is administratively down/)) {
				networkInterface.enabled = false;
			}
		}
		catch (e) {
		}
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	if (message.match(/%SYS\-5\-CONFIG_I: Configured from (.*) by (.*)/)) {
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
	return !!(sysObjectID.substring(0, 18) == "1.3.6.1.4.1.30065."
		&& sysDesc.match(/^Arista Networks EOS/));
}
