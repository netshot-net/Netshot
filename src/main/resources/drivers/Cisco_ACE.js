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
	name: "CiscoACE",
	description: "Cisco ACE",
	author: "Netshot Team",
	version: "1.6"
};

var Config = {
	"systemImage": {
		type: "Text",
		title: "System image",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! System image:",
			preLine: "!!  "
		}
	},
	"aceVersion": {
		type: "Text",
		title: "ACE version",
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
	},
	"aceLicense": {
		type: "LongText",
		title: "Software Licenses",
		comparable: true,
		searchable: false,
		checkable: true,
		dump: {
			pre: "!! ACE Software Licenses:",
			preLine: "!! "
		}
	},
};

var Device = {
};

var CLI = {
	telnet: {
		macros: {
			exec: {
				options: [ "username", "password", "exec" ],
				target: "exec"
			},
			configure: {
				options: [ "username", "password", "exec"  ],
				target: "configure"
			}
		}
	},
	ssh: {
		macros: {
			exec: {
				options: [ "exec" ],
				target: "exec"
			},
			configure: {
				options: [ "exec" ],
				target: "configure"
			}
		}
	},
	username: {
		prompt: /login: $/,
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
				options: [ "usernameAgain", "exec" ]
			}
		}
	},
	usernameAgain: {
		prompt: /login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	exec: {
		prompt: /^([A-Za-z0-9_\-\.]+\/[A-Za-z0-9_\-\.]+# )$/,
		error: /^% (invalid .*)/m,
		pager: {
			avoid: "terminal length 0",
			match: /--More--$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "exec", "configure" ],
				target: "configure"
			},
			save: {
				cmd: "copy running-config startup-config",
				options: [ "exec" ],
				target: "exec"
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z0-9_\-\.]+\/[A-Za-z0-9_\-\.]+\(conf[0-9\-a-zA-Z]+\)#) $/,
		error: /^% (.*)/m,
		clearPrompt: true,
		macros: {
			end: {
				cmd: "end",
				options: [ "exec", "configure" ],
				target: "exec"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	cli.macro("exec");

	var showVersion = cli.command("show version");

	var hostname = showVersion.match(/^(.*) kernel uptime/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	var version = showVersion.match(/^ *system: *Version ([A-Za-z0-9\(\)\.]+)/m);
	version = (version ? version[1] : "Unknown");
	device.set("softwareVersion", version);
	config.set("aceVersion", version);

	var image = showVersion.match(/system image file: *(\[LCP\] )?(.*)/m);
	if (image) {
		config.set("systemImage", image[2]);
	}
	config.set("author", "Unknown");
	device.set("networkClass", "LOADBALANCER");
	device.set("family", "Cisco ACE");

	var showInventory = cli.command("show inventory");
	var inventoryPattern = / *NAME: \"(.*)\", +DESCR: \"(.*)\"[\r\n]+ *PID: (.*?) *, +VID: (.*), +SN: (.*)/g;
	var match;
	while (match = inventoryPattern.exec(showInventory)) {
		var module = {
			slot: match[1],
			partNumber: match[3],
			serialNumber: match[5]
		};
		device.add("module", module);
		var description = match[2];
		var family = description.match(/.*Application Control Engine.*/);
		if (family) {
			device.set("family", family[0].replace(/Application Control Engine/, "ACE"));
			device.set("serialNumber", module.serialNumber);
		}
	}
	
	var license = "";
	try {
		license += cli.command("show license usage");
		license += cli.command("show license");
	}
	catch (error) {
	}
	config.set("aceLicense", license);


	var configCleanup = function(config) {
		config = config.replace(/^Generating configuration.*$/m, "");
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};
	
	var showContext = cli.command("show context | i Name");
	var contextPattern = /^Name: (.+) , Id/mg;
	var match;
	var runningConfig = "";
	while (match = contextPattern.exec(showContext)) {
		var context = match[1];
		var contextConfig = cli.command("invoke context " + context + " show running-config");
		if (context == "Admin") {
			var location = contextConfig.match(/^snmp-server location "(.*)"/m);
			device.set("location", (location ? location[1] : ""));
			var contact = contextConfig.match(/^snmp-server contact "(.*)"/m);
			device.set("contact", (contact ? contact[1] : ""));
		}
		else {
			runningConfig += "\r\nchangeto " + context + "\r\n";
		}
		contextConfig = configCleanup(contextConfig);
		runningConfig += contextConfig;
	
		var interfaces = cli.findSections(runningConfig, /^interface (.+)/m);
		for (var i in interfaces) {
			var networkInterface = {
				name: interfaces[i].match[1],
				ip: [],
				virtualDevice: context
			};
			var description = interfaces[i].config.match(/^ *description (.+)/m);
			if (description) {
				networkInterface.description = description[1];
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
			try {
				cli.command("changeto " + context, { clearPrompt: true });
				var showInterface = cli.command("show interface " + networkInterface.name + " | inc is");
				cli.command("changeto Admin", { clearPrompt: true });
				var macAddress = showInterface.match(/MAC address is ([0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2})/);
				if (macAddress) {
					networkInterface.mac = macAddress[1];
				}
				if (!showInterface.match(/administratively up/)) {
					networkInterface.enabled = false;
				}
			}
			catch (e) {
				cli.debug("Unable to get interface details for " + networkInterface.name);
			}
			device.add("networkInterface", networkInterface);
		}
	}
	config.set("runningConfig", runningConfig);

};

function analyzeSyslog(message) {
	if (message.match(/%ACE-5-111008: User '(.*)' executed the .*/)) {
		return true;
	}
	return false;
}

// Note: The Cisco ACE doesn't seem to send any trap upon configuration change.

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 16) == "1.3.6.1.4.1.9.1."
		&& sysDesc.match(/Application Control Engine/)) {
		return true;
	}
	return false;
}
