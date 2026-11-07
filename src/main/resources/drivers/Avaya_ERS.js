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
	name: "AvayaERS",
	description: "Avaya ERS",
	author: "Netshot Team",
	version: "0.4.4"
};

var Config = {
	"license": {
		type: "Text",
		title: "License",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"fwVersion": {
		type: "Text",
		title: "Firmware version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"swVersion": {
		type: "Text",
		title: "Software version",
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
	"operationMode": {
		type: "Text",
		title: "Operational mode",
		searchable: true
	}
};

var CLI = {
	telnet: {
		macros: {
			enable: {
				options: [ "ctrly", "username", "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "ctrly", "username", "enable", "disable" ],
				target: "configure"
			}
		}
	},
	ssh: {
		macros: {
			enable: {
				options: [ "ctrly", "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "ctrly", "enable", "disable" ],
				target: "configure"
			}
		}
	},
	ctrly: {
		prompt: /Enter Ctrl\-Y to begin\./,
		macros: {
			auto: {
				cmd: String.fromCharCode(0x19),
				noCr: true,
				options: [ "username", "disable", "enable", "enter" ]
			}
		}
	},
	enter: {
		prompt: /Press ENTER to continue/,
		macros: {
			auto: {
				cmd: "",
				options: [ "username", "disable", "enable" ]
			}
		}
	},
	username: {
		prompt: /^ +Enter Username: /,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "incorrectCredentials" ]
			}
		}
	},
	password: {
		prompt: /^ +Enter Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "incorrectCredentials", "disable", "enable" ]
			}
		}
	},
	incorrectCredentials: {
		prompt: /Incorrect Credentials/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	disable: {
		prompt: /^([A-Za-z\-_0-9\.]+(\<level\-[0-9]+\>)?\>)$/,
		pager: {
			avoid: [ "terminal length 0", "terminal width 132" ],
			match: /^----More.*----$/,
			response: " "
		},
		macros: {
			enable: {
				cmd: "enable",
				options: [ "enable" ],
				target: "enable"
			},
			configure: {
				cmd: "enable",
				options: [ "enable" ],
				target: "configure"
			}
		}
	},
	enable: {
		prompt: /^([A-Za-z\-_0-9\.]+(\<level\-[0-9]+\>)?#)$/,
		error: /^% (.*)/m,
		pager: {
			avoid: "terminal length 0",
			match: /^----More.*----$/,
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
				options: [ "enable" ],
				target: "enable"
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z\-_0-9\.]+\(conf[0-9\-a-zA-Z]+\)#)$/,
		error: /^% (.*)/m,
		clearPrompt: true,
		macros: {
			end: {
				cmd: "end",
				options: [ "enable" ],
				target: "enable"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	var configCleanup = function(config) {
		/* Avoids line wrapping. */
		var width = 79;
		var wMatch = config.match(/^terminal width ([0-9]+)/m);
		if (wMatch) {
			width = wMatch;
		}
		var linePattern = new RegExp("^(.{" + width + "})[\r\n]+(.+)", "mg");
		config = config.replace(linePattern, function(match, line1, line2) {
			if (line2.startsWith("!")) return match;
			if (line1.split(" ")[0] == line2.split(" ")[0]) return match;
			return line1 + line2;
		});
		/* Remove the tftp-server line, it changes each time a tool uploads the config via TFTP */
		config = config.replace(/^tftp-server .*/mg, "");
		return config;
	};
	
	cli.macro("enable");

	var runningConfig = cli.command("show running-config");
	
	runningConfig = configCleanup(runningConfig);
	config.set("runningConfig", runningConfig);
	
	var showSystem = cli.command("show system verbose");

	var hostname = showSystem.match(/sysName: +(.+)/);
	if (hostname) {
		device.set("name", hostname[1]);
	}

	var versions = showSystem.match(/HW:([0-9\.]+)\s+FW:([0-9\.]+)\s+SW:v?([0-9\.]+)/);
	if (versions) {
		var swVersion = versions[3];
		var fwVersion = versions[2];
		device.set("softwareVersion", swVersion);
		config.set("swVersion", swVersion);
		config.set("fwVersion", fwVersion);
	}

	var license = showSystem.match(/Operational license: +(.+)/);
	if (license) {
		config.set("license", license[1]);
	}
	var operationMode = showSystem.match(/Operation mode: +(.+)/);
	if (operationMode) {
		device.set("operationMode", operationMode[1]);
	}

	device.set("networkClass", "SWITCHROUTER");

	var family = "Avaya switch";
	var sysDescr = showSystem.match(/sysDescr: +(.+)/);
	if (sysDescr) {
		family = sysDescr[1];
		family = family.replace(/Ethernet Routing Switch/, "ERS");
		family = "Avaya " + family;
	}
	device.set("family", family);

	var location = showSystem.match(/sysLocation: +(.+)/);
	if (location) {
		device.set("location", location[1]);
	}
	else {
		device.set("location", "");
	}
	var contact = showSystem.match(/sysContact: +(.+)/);
	if (contact) {
		device.set("contact", contact[1]);
	}
	else {
		device.set("contact", "");
	}

	var match;
	var unitPattern = /^Unit #([0-9]+).*\:[\r\n]+((\s+.+[\r\n]+)*)/gm;
	var serialSet = false;
	while (match = unitPattern.exec(showSystem)) {
		var unit = match[1];
		var details = match[2];
		var model = details.match(/Switch Model: +(.+)/);
		model = model ? model[1] : "";
		var serialNumber = details.match(/Serial Number: +(.+)/);
		serialNumber = serialNumber ? serialNumber[1] : "";
		device.add("module", { slot: unit, partNumber: model, serialNumber: serialNumber });
		if (!serialSet) {
			device.set("serialNumber", serialNumber);
			serialSet = true;
		}
	}

	var mainMac = showSystem.match(/MAC Address: +([0-9A-Fa-f\-]+)/);
	mainMac = mainMac ? mainMac[1] : "00-00-00-00-00-00";
	var switchIp = runningConfig.match(/ip address switch ([0-9\.]+)/);
	var maskIp = runningConfig.match(/ip address netmask ([0-9\.]+)/);
	maskIp = maskIp ? maskIp[1] : "255.255.255.0";
	var ipPattern = /^ip address (switch|stack|unit ([0-9]+)) ([0-9\.]+)/mg;
	var mgmtVlan = runningConfig.match(/vlan mgmt ([0-9]+)/);
	mgmtVlan = mgmtVlan ? mgmtVlan[1] : "1";
	while (match = ipPattern.exec(runningConfig)) {
		var type = match[1];
		var ip = match[3];
		if (ip == "0.0.0.0") {
			continue;
		}
		var networkInterface = {
			name: "Vlan" + mgmtVlan + " - " + type,
			description: "Management",
			ip: [{
				ip: ip,
				mask: maskIp,
				usage: "PRIMARY"
			}],
			mac: mainMac
		};
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	return !!message.match(/Trap:  bsnConfigurationSavedToNvram/);
}


/*
 * Trigger a snapshot upon configuration save.
 *
 * Switch configuration:
 *  show snmp-server view
 *  (config)# snmp-server community Netsh01 read-view nncli write-view nncli notify-view nncli
 *  (config)# snmp-server host 1.1.1.1 v2c Netsh01
 *  (config)# snmp-server enable
 */

function analyzeTrap(trap, debug) {
	for (var t in trap) {
		if (trap[t] == "1.3.6.1.4.1.45.5.2.2.0.1") {
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 17) == "1.3.6.1.4.1.45.3."
		&& sysDesc.match(/Ethernet Routing Switch/)) {
		return true;
	}
	return false;
}
