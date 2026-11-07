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
	name: "CiscoNXOS",
	description: "Cisco NX-OS 5+",
	author: "Netshot Team",
	version: "1.9.3"
};

var Config = {
	"systemImageFile": {
		type: "Text",
		title: "System image file",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"kickstartImageFile": {
		type: "Text",
		title: "Kickstart image file",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"nxosVersion": {
		type: "Text",
		title: "NX-OS version",
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
	"nxosLicense": {
		type: "LongText",
		title: "License",
		comparable: true,
		searchable: false,
		checkable: true,
		dump: {
			pre: "!! NX-OS license(s):",
			preLine: "!! "
		}
	},
	"nxosPackages": {
		type: "LongText",
		title: "NX-OS packages",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! NX-OS active packages:",
			preLine: "!!  ",
			post: "!! End of NX-OS packages"
		}
	},
};

var Device = {
	"memorySize": {
		type: "Numeric",
		title: "Memory size (MB)",
		searchable: true
	},
	"flashSize": {
		type: "Numeric",
		title: "Flash size (MB)",
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
			exec: {
				options: [ "username", "password", "exec" ],
				target: "exec"
			},
			configure: {
				options: [ "username", "password", "exec" ],
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
		prompt: /^login: $/,
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
		prompt: /^login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	exec: {
		prompt: /^([A-Za-z\-_0-9\.]+#) $/,
		error: /^% (.*)/m,
		pager: {
			avoid: "terminal length 0",
			match: /^ --More--$/,
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
			},
			saveAll: {
				cmd: "copy running-config startup-config vdc-all",
				options: [ "exec" ],
				target: "exec"
			},
		}

	},
	configure: {
		prompt: /^([A-Za-z\-_0-9\.]+\(conf[0-9\-a-zA-Z]+\)#) $/,
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
	
	var image = showVersion.match(/kickstart image file is: *(.*)/m);
	if (image) {
		config.set("kickstartImageFile", image[1]);
	}
	var image = showVersion.match(/(NXOS|system) image file is: *(.*)/m);
	if (image) {
		config.set("systemImageFile", image[2]);
	}
	var memory = showVersion.match(/with (\d+) kB of memory/m);
	if (memory) {
		memory = parseInt(memory[1]);
		memory = Math.round(memory / 1024 / 4) * 4;
		device.set("memorySize", memory);
	}
	var flash = showVersion.match(/bootflash: *(\d+) kB/m);
	if (flash) {
		flash = parseInt(flash[1]);
		flash = Math.round(flash / 1024 / 4) * 4;
		device.set("flashSize", flash);
	}
	device.set("networkClass", "SWITCH");
	device.set("family", "Unknown NX-OS device");
	var chassis = showVersion.match(/cisco (.*?)( \(.*\))? [cC]hassis/m);
	if (chassis) {
		var chassis = chassis[1];
		chassis = chassis.replace(/(Nexus)([0-9].*)/, "$1 $2");
		device.set("family", chassis);
	}
	
	var version = showVersion.match(/(system|NXOS): *version (.*)/m);
	if (version) {
		device.set("softwareVersion", version[2]);
		config.set("nxosVersion", version[2]);
	}

	var activePackages = showVersion.match(/^Active Package\(s\):\r?\n(( +.*\r?\n)*)/m);
	if (activePackages) {
		config.set("nxosPackages", activePackages[1]);
	}
	
	var showInventory = cli.command("show inventory");
	var inventoryPattern = /NAME: \"(.*)\", +DESCR: \"(.*)\" *[\r\n]+PID: (.*?) *, +VID: (.*), +SN: (.*)/g;
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
	
	var license = "";
	try {
		license += cli.command("show license usage");
		license += cli.command("show license");
		license = license.replace(/Honor Start .*/mg, "Honor Start <Timer>");
	}
	catch (error) {
	}
	config.set("nxosLicense", license);
	
	var configCleanup = function(config) {
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		config = config.replace(/^\!Time.*$/mg, "");
		return config;
	};
	
	
	var runningConfig;
	try {
		runningConfig = cli.command("show running-config vdc-all");
		if (runningConfig.match(/vdc_is_active failed/)) {
			throw "Nexus 4000 error";
		}
		if (runningConfig.match(/Cmd exec error/)) {
			throw "Nexus vdc error";
		}
	}
	catch (error) {
		runningConfig = cli.command("show running-config");
	}
	var runningDate = NaN;
	var runningDateMatch = runningConfig.match(/^!Running configuration last done at: (.+)/m);
	if (runningDateMatch) {
		runningDate = Date.parse(runningDateMatch[1]);
	}
	runningConfig = configCleanup(runningConfig);
	config.set("runningConfig", runningConfig);
	
	var startupConfig;
	try {
		startupConfig = cli.command("show startup-config vdc-all");
	}
	catch (error) {
		startupConfig = cli.command("show startup-config");
	}
	var startupDate = NaN;
	var startupDateMatch = startupConfig.match(/^!Startup config saved at: (.+)/m);
	if (startupDateMatch) {
		startupDate = Date.parse(startupDateMatch[1]);
	}
	startupConfig = configCleanup(startupConfig);

	if (!isNaN(runningDate) && !isNaN(startupDate)) {
		cli.debug("Using dates to compare startup and running configs");
		device.set("configurationSaved", startupDate >= runningDate);
	}
	else {
		device.set("configurationSaved", startupConfig == runningConfig);
	}
	
	
	var hostname = runningConfig.match(/^(hostname|switchname) (.+)$/m);
	if (hostname) {
		device.set("name", hostname[2]);
	}
	
	var vdcConfigs = runningConfig.split(/[\r\n]+\!Running config for vdc: .*[\r\n]+/);
	for (var v in vdcConfigs) {
		var vdcConfig = vdcConfigs[v];
		
		var vdcName = vdcConfig.match(/^switchto vdc (.*)$/m);
		if (vdcName) {
			vdcName = vdcName[1];
			try {
				cli.command("switchto vdc " + vdcName, { clearPrompt: true });
				device.add("virtualDevice", vdcName);
			}
			catch (error) {
				if (typeof error == "string" && error.match(/switchto vdc/)) {
					continue;
				}
				else {
					throw error;
				}
			}
		}
		else {
			vdcName = "";
			var location = vdcConfig.match(/^snmp-server location (.*)/m);
			if (location) {
				device.set("location", location[1]);
			}
			else {
				device.set("location", "");
			}
			var contact = vdcConfig.match(/^snmp-server contact (.*)/m);
			if (contact) {
				device.set("contact", contact[1]);
			}
			else {
				device.set("contact", "");
			}
		}
		
		var vrfPattern = /^^vrf context (.+)$/mg;
		while (match = vrfPattern.exec(vdcConfig)) {
			device.add("vrf", match[1]);
		}
		
		var interfaces = cli.findSections(vdcConfig, /^interface ([^ ]+)/m);
		for (var i in interfaces) {
			if (interfaces[i].match[1].match(/cmp-mgmt|breakout/)) {
				continue;
			}
			var networkInterface = {
				name: interfaces[i].match[1],
				ip: []
			};
			var description = interfaces[i].config.match(/^ *description (.+)/m);
			if (description) {
				networkInterface.description = description[1];
			}
			var vrf = interfaces[i].config.match(/^ *vrf member (.+)$/m);
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
			var fhrpGroups = cli.findSections(interfaces[i].config, /^ *(hsrp|vrrp|glbp) (\d+)/m);
			for (var g in fhrpGroups) {
				var fhrpConfig = fhrpGroups[g].config;
				var fhrpPattern = /^ *ip (\d+\.\d+\.\d+\.\d+)( secondary)?/mg;
				while (match = fhrpPattern.exec(fhrpConfig)) {
					var ip = {
						ip: match[1],
						mask: 32,
						usage: "HSRP"
					};
					if (fhrpGroups[g].match[1] == "vrrp") {
						ip.usage = "VRRP";
					}
					if (match[4] == " secondary") {
						ip.usage = "SECONDARY" + ip.usage;
					}
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
			var showInterface = cli.command("show interface " + networkInterface.name + " | inc \"(address| is down)\"");
			var macAddress = showInterface.match(/([0-9a-fA-F]{4}\.[0-9a-fA-F]{4}\.[0-9a-fA-F]{4})/);
			if (macAddress) {
				networkInterface.mac = macAddress[1];
			}
			if (showInterface.match(/(Administratively down|SFP not inserted)/)) {
				networkInterface.enabled = false;
			}
			if (vdcName != "") {
				networkInterface.virtualDevice = vdcName;
			}
			device.add("networkInterface", networkInterface);
		}
		
		if (vdcName != "") {
			cli.command("switchback", { clearPrompt: true });
		}
		
	}

};

function runCommands(command) {
	
}

function analyzeSyslog(message) {
	if (message.match(/%VSHD-5-VSHD_SYSLOG_CONFIG_I: Configured from (.*) by (.*) on .*/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	for (var t in trap) {
		if (trap[t].substring(0, 24) == "1.3.6.1.4.1.9.9.43.2.0.2" || t.substring(0, 24) == "1.3.6.1.4.1.9.9.43.1.1.1") {
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 17) == "1.3.6.1.4.1.9.12." && sysDesc.match(/^Cisco NX-OS.*/)) {
		return true;
	}
	return false;
}
