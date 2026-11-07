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
	name: "GigamonGigaVUE",
	description: "Gigamon GigaVUE",
	author: "Netshot Team",
	version: "1.3"
};

var Config = {
	"runningConfig": {
		type: "LongText",
		title: "Running configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Running configuration (taken on %when%):",
			post: "## End of running configuration"
		}
	},
	"gvRelease": {
		type: "Text",
		title: "GigaVUE release",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			preLine: "## GigaVUE release: "
		}
	},
};

var Device = {
	"mainMemorySize": {
		type: "Numeric",
		title: "Main memory size (MB)",
		searchable: true
	}
};

var CLI = {
	telnet: {
		fail: "Telnet access is not supported."
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
	disable: {
		prompt: /^([A-Za-z\-_0-9\.\/]+( \[[^\]\r\n]+\])? \> )$/,
		pager: {
			avoid: "no cli session paging enable",
			match: /^lines [0-9]+-[0-9]+$/,
			response: "\r"
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
		prompt: /^([A-Za-z\-_0-9\.\/]+( \[[^\]\r\n]+\])? # )$/,
		error: /^% (.*)/m,
		pager: {
			avoid: "no cli session paging enable",
			match: /^lines [0-9]+-[0-9]+$/,
			response: "\r"
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "enable", "configure" ],
				target: "configure"
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z\-_0-9\.\/]+ \(config\) # )$/,
		error: /^% (.*)/m,
		clearPrompt: true,
		macros: {
			end: {
				cmd: "exit",
				options: [ "enable", "configure" ],
				target: "enable"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	
	var configCleanup = function(config) {
		config = config.replace(/^(## Generated at ).*/m, "$1...");
		return config;
	};

	cli.macro("enable");
	var runningConfig = cli.command("show running-config full");
	runningConfig = configCleanup(runningConfig);
	config.set("runningConfig", runningConfig);
	
	
	var showVersion = cli.command("show version");
	
	var hostname = runningConfig.match(/^## Hostname: +(.+)/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	var version = showVersion.match(/^Product release: +(.+)/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("gvRelease", version[1]);
	}
	
	var memory = showVersion.match(/^System memory: .*([0-9]+) MB total/m);
	if (memory) {
		device.set("mainMemorySize", memory[1]);
	}
	
	device.set("networkClass", "SWITCH");
	
	var productName = showVersion.match(/^Product name: +(.+)/m);
	if (productName) {
		device.set("family", productName[1]);
	}
	
	var location = runningConfig.match(/^snmp-server location ("(.+)"|(.+))/m);
	if (location) {
		device.set("location", location[2] || location[3]);
	}
	else {
		device.set("location", "");
	}
	var contact = runningConfig.match(/^snmp-server contact ("(.+)"|(.+))/m);
	if (contact) {
		device.set("contact", contact[2] || contact[3]);
	}
	else {
		device.set("contact", "");
	}

	var productModel = showVersion.match(/^Product model: +(.+)/m);
	var productHw = showVersion.match(/^Product hw: +(.+)/m);
	var hostId = showVersion.match(/^Host ID: +(.+)/m);
	if (productModel && productHw && hostId) {
		device.add("module", {
			slot: "Chassis",
			partNumber: productModel[1] + " (" + productHw[1] + ")",
			serialNumber: hostId[1],
		});
	}
	
	var interfaces = cli.findSections(runningConfig, /^interface ([^ ]+)/m);
	var mergedInterfaceConfigs = {};
	for (var i in interfaces) {
		var name = interfaces[i].match[1];
		if (!mergedInterfaceConfigs[name]) {
			mergedInterfaceConfigs[name] = "";
		}
		mergedInterfaceConfigs[name] += interfaces[i].config;
		mergedInterfaceConfigs[name] += "\n";
	}
	for (var i in mergedInterfaceConfigs) {
		var networkInterface = {
			name: i,
			ip: [],
		};
		var description = mergedInterfaceConfigs[i].match(/^ *comment "(.+)"/m);
		if (description) {
			networkInterface.description = description[1];
		}
		var ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+) *\/(\d+)/mg;
		while (match = ipPattern.exec(mergedInterfaceConfigs[i])) {
			networkInterface.ip.push({
				ip: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			});
		}
		var ipv6Pattern = /^ *ipv6 address ([0-9A-Fa-f:]+) *\/(\d+)/mg;
		while (match = ipv6Pattern.exec(mergedInterfaceConfigs[i])) {
			var ip = {
				ipv6: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		networkInterface.enabled = !!mergedInterfaceConfigs[i].match(/^ *no shutdown/m);
		device.add("networkInterface", networkInterface);
	}
	
	var portPattern = /^port (\d+\/\d+\/[a-z]\d+) +(.+)/mg;
	var portConfigs = {};
	while (match = portPattern.exec(runningConfig)) {
		var name = match[1];
		if (!portConfigs[name]) {
			portConfigs[name] = "";
		}
		portConfigs[name] += match[2];
		portConfigs[name] += "\n";
	}
	for (var p in portConfigs) {
		var networkInterface = {
			name: p,
			ip: [],
		};
		networkInterface.enabled = !!portConfigs[p].match(/^ *params admin enable/m);
		var alias = portConfigs[p].match(/^ *alias (.+)/m);
		if (alias) {
			networkInterface.description = alias[1];
		}
		device.add("networkInterface", networkInterface);
	}
};

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID == "1.3.6.1.4.1.26866" && sysDesc.match(/GigaVUE/)) {
		return true;
	}
	return false;
}
