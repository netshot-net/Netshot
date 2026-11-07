/**
 * Copyright 2013-present Netshot
 * Copyright 2021 Remi Locherer
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
	name: "A10ACOS",
	description: "A10 ACOS",
	author: "R. Locherer",
	version: "0.1"
};

var Config = {
	"acosVersion": {
		type: "Text",
		title: "ACOS version",
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
			pre: "!! Configuration (taken on %when%):",
			post: "!! End of configuration"
		}
	}
};

var Device = {
	"memory": {
		type: "Numeric",
		title: "Memory (MB)",
		searchable: true
	}
};

var CLI = {
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
		prompt: /^([A-Za-z\-_0-9\.]+\>)$/,
		pager: {
			match: /^--MORE--$/,
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
		prompt: /^([A-Za-z\-_0-9\.]+#)$/,
		error: /^% (.*)/m,
		pager: {
			match: /^--MORE--$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "config",
				options: [ "enable", "configure" ],
				target: "configure"
			},
			save: {
				cmd: "write memory",
				options: [ "enable" ],
				target: "enable"
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z\-_0-9\.\/]+\(config[0-9\-a-zA-Z]+\)#)$/,
		error: /^% (.*)/m,
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
	
	cli.macro("enable");
	var runningConfig = cli.command("show partition-config all");
	config.set("runningConfig", runningConfig);
	
	var showVersion = cli.command("show version");
	
	var hostname = runningConfig.match(/^hostname (.*)$/m);
	if (hostname != null) {
		device.set("name", hostname[1]);
	}
	
	var version = showVersion.match(/Advanced Core OS \(ACOS\) version (.*), build/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("acosVersion", version[1]);
	}

	device.set("networkClass", "LOADBALANCER");
	device.set("family", "A10 Thunder Series");

	var memory = showVersion.match(/^ *Total System Memory (\d+) Mbytes/m);
	if (memory) {
		device.set("memory", parseInt(memory[1]));
	}

	var serial = showVersion.match(/^ *Serial Number: (.*)$/m);
	device.set("serialNumber", serial[1]);
	
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
		var description = interfaces[i].config.match(/^  name (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		var ipPattern = /^  ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/mg;
		while (match = ipPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[1],
				mask: match[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		var ipv6Pattern = /^  ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg;
		while (match = ipv6Pattern.exec(interfaces[i].config)) {
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

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.match(/^1\.3\.6\.1\.4\.1\.22610\.1\.3\.13$/)
		&& sysDesc.match(/^Thunder Series Unified.*/)) {
		return true;
	}
	return false;
}
