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
	name: "HuaweiNE",
	description: "Huawei NE Router",
	author: "Netshot Team",
	version: "1.2"
};

var Config = {
	"vrpVersion": {
		type: "Text",
		title: "VRP version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## VRP version:",
			preLine: "##  "
		}
	},
	"currentConfig": {
		type: "LongText",
		title: "Current configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Current configuration (taken on %when%):",
			post: "## End of current configuration"
		}
	},
	"patch": {
		type: "Text",
		title: "Patch version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"moduleInfo": {
		type: "LongText",
		title: "Module information",
		comparable: true,
		searchable: true,
		checkable: true
	}
};

var Device = {
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true
	}
};

var CLI = {
	telnet: {
		macros: {
			user: {
				options: [ "username", "password", "user" ],
				target: "user"
			},
			system: {
				options: [ "username", "password", "user", "system" ],
				target: "system"
			}
		}
	},
	ssh: {
		macros: {
			user: {
				options: [ "user" ],
				target: "user"
			},
			system: {
				options: [ "user", "system" ],
				target: "system"
			}
		}
	},
	username: {
		prompt: /^Username:$/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password" ]
			}
		}
	},
	password: {
		prompt: /^Password:$/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "usernameAgain", "user" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^Username:$/,
		fail: "Authentication failed - Telnet authentication failure."
	},

	user: {
		prompt: /^(<[A-Za-z\-_0-9\.]+>)$/,
		error: /^Error: /m,
		pager: {
			avoid: "screen-length 0 temporary",
			match: /^  *---- More ----$/,
			response: " "
		},
		macros: {
			system: {
				cmd: "system-view",
				options: [ "user", "system" ],
				target: "configure"
			},
			save: {
				cmd: "save",
				options: [ "user", "saveConfirm" ],
				target: "user"
			}
		}
	},
	saveConfirm: {
		prompt: /Are you sure to continue\?/,
		macros: {
			auto: {
				cmd: "y",
				options: [ "user" ]
			}
		}
	},
	
	system: {
		prompt: /^(\[(~)?[A-Za-z\-_0-9\.]+\])$/,
		error: /^Error: (.*)/m,
		clearPrompt: true,
		macros: {
			user: {
				cmd: "quit",
				options: [ "user", "system" ],
				target: "user"
			},
			commit: {
				cmd: "commit",
				options: [ "system" ],
				target: "system"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	var configCleanup = function(config) {
		config = config.replace(/^\!Last configuration .*[\r\n]+/m, "");
		return config;
	};
	
	cli.macro("user");
	var currentConfig = cli.command("display current-config");
	
	var author = currentConfig.match(/^\!Last configuration was updated .* by (.*)$/m);
	if (author) {
		config.set("author", author[1]);
	}
	currentConfig = configCleanup(currentConfig);
	config.set("currentConfig", currentConfig);
	
	var savedConfig = cli.command("display saved-configuration");
	savedConfig = configCleanup(savedConfig);
	device.set("configurationSaved", currentConfig == savedConfig);


	var hostname = currentConfig.match(/^ *sysname (.*)$/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}

	var displayVersion = cli.command("display version");

	var version = displayVersion.match(/VRP .* software, Version (.*)/);
	if (version) {
		version = version[1];
	}
	else {
		version = "Unknown";
	}
	device.set("softwareVersion", version);
	config.set("vrpVersion", version);
	var patch = displayVersion.match(/Patch Version: (.*)/);
	if (patch) {
		config.set("patch", patch[1]);
		device.set("softwareVersion", version + "/" + patch[1]);
	}
	else {
		device.set("patch", "");
	}


	device.set("networkClass", "SWITCHROUTER");
	
	var platform = displayVersion.match(/^(.*) version information/m);
	if (platform) {
		device.set("family", platform[1]);
	}
	else {
		device.set("family", "Unknown NE device");
	}

	var displayEsn = cli.command("display esn");
	var serialNumber = displayEsn.match(/: (.*)/);
	if (serialNumber) {
		device.set("serialNumber", serialNumber[1]);
	}
	else {
		device.set("serialNumber", "");
	}

	try {
		var moduleInfo = cli.command("display module-information");
		config.set("moduleInfo", moduleInfo);
	}
	catch(err) {
		config.set("moduleInfo", "");
	}


	var location = currentConfig.match(/^ *snmp-agent sys-info location (.*)/m);
	if (location) {
		device.set("location", location[1]);
	}
	else {
		device.set("location", "");
	}
	var contact = currentConfig.match(/^ *snmp-agent sys-info contact (.*)/m);
	if (contact) {
		device.set("contact", contact[1]);
	}
	else {
		device.set("contact", "");
	}


	var boards = cli.findSections(displayVersion, /^(.PU) (?:\(.*\) )?([0-9]+).*uptime/m);
	for (var b in boards) {
		var slot = boards[b].match[1] + " " + boards[b].match[2];
		var info = boards[b].config.match(/^ *(.PU )?([0-9A-Z]+) version information/m);
		if (info) {
			device.add("module", {
				slot: slot,
				partNumber: info[2],
				serialNumber: "-"
			});
		}
		var picPattern = /^ *PIC([0-9]+): (.+) version information/gm;
		var match;
		while (match = picPattern.exec(boards[b].config)) {
			device.add("module", {
				slot: "PIC" + " " + boards[b].match[2] + "/" + match[1],
				partNumber: match[2],
				serialNumber: "-"
			});
		}
	}
	
	var vrfPattern = /^ip vpn-instance (.+)/mg;
	while (match = vrfPattern.exec(currentConfig)) {
		device.add("vrf", match[1]);
	}
	
	var interfaces = cli.findSections(currentConfig, /^interface ([^ ]+)( .+)?/m);
	for (var i in interfaces) {
		var networkInterface = {
			name: interfaces[i].match[1],
			ip: []
		};
		var description = interfaces[i].config.match(/^ *description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		var vrf = interfaces[i].config.match(/^ *ip binding vpn-instance (.+)$/m);
		if (vrf) {
			networkInterface.vrf = vrf[1];
		}
		if (interfaces[i].config.match(/^ *portswitcht$/m)) {
			networkInterface.level3 = false;
		}
		if (interfaces[i].config.match(/^ *shutdown/m)) {
			networkInterface.enabled = false;
		}
		var ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)( sub)?/mg;
		while (match = ipPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[1],
				mask: match[2],
				usage: "PRIMARY"
			};
			if (match[3] == " sub") {
				ip.usage = "SECONDARY";
			}
			networkInterface.ip.push(ip);
		}
		var fhrpPattern = /^ *(vrrp) vrid ([0-9]+) virtual-ip (\d+\.\d+\.\d+\.\d+)/mg;
		while (match = fhrpPattern.exec(interfaces[i].config)) {
			var ip = {
				ip: match[3],
				mask: 32,
				usage: "VRRP"
			};
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
		var displayInterface = cli.command("display interface " + networkInterface.name + " | inc address|line protocol");
		var macAddress = displayInterface.match(/address is ([0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4})/);
		if (macAddress) {
			networkInterface.mac = macAddress[1];
		}
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	if (message.match(/The user chose Y when deciding whether to save the configuration to the device/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.2011.5.25.191.3.1";
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.substring(0, 22) == "1.3.6.1.4.1.2011.2.62." &&
		sysDesc.match(/Huawei Versatile Routing Platform Software/);
}
