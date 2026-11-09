/**
 * Copyright 2013-2025 Netshot
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
	name: "CiscoIOS12",
	description: "Cisco IOS and IOS-XE",
	author: "Netshot Team",
	version: "2.7"
};

var Config = {
	"iosImageFile": {
		type: "Text",
		title: "IOS image file",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! IOS image file:",
			preLine: "!!  "
		}
	},
	"iosVersion": {
		type: "Text",
		title: "IOS version",
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
	"sdwanRunningConfig": {
		type: "LongText",
		title: "SDWAN Running configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! SDWAN running configuration (taken on %when%):",
			post: "!! End of SDWAN running configuration"
		}
	}
};

var Device = {
	"mainMemorySize": {
		type: "Numeric",
		title: "Main memory size (MB)",
		searchable: true,
		checkable: true,
	},
	"configRegister": {
		type: "Text",
		title: "IOS config register",
		searchable: true,
		checkable: true,
	},
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true,
		checkable: true,
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
		prompt: /^[Uu]sername: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "usernameAgain" ]
			}
		}
	},
	password: {
		prompt: /^[Pp]assword:\s?$/,
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
				options: [ "enable", "saveTarget", "saveOverwrite" ],
				target: "enable"
			}
		}
	},
	saveTarget: {
		prompt: /Destination filename \[startup-config\]/,
		macros: {
			auto: {
				cmd: "",
				options: [ "enable", "saveOverwrite" ]
			}
		}
	},
	saveOverwrite: {
		prompt: /Overwrite the previous NVRAM configuration/,
		macros: {
			auto: {
				cmd: "",
				options: [ "enable" ]
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

	var configCleanup = function(config) {
		config = config.replace(/^ntp clock-period [0-9]+$/m, "");
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};

	cli.macro("enable");
	var runningConfig = cli.command("show running-config");

	var changePattern = /^\! Last configuration change at (.+?)( by (.+))?$/m;
	var runningChangeMatch = runningConfig.match(changePattern);
	if (runningChangeMatch && runningChangeMatch[3]) {
		config.set("author", runningChangeMatch[3]);
	}
	runningConfig = configCleanup(runningConfig);
	config.set("runningConfig", runningConfig);

	var configSaved = false;
	if (runningChangeMatch) {
		var startupComments = cli.command("show startup-config | i ^! .*");
		var startupChangeMatch = startupComments.match(changePattern);
		if (startupChangeMatch && runningChangeMatch[1] === startupChangeMatch[1]) {
			configSaved = true;
		}
	}
	else {
		// Fallback to comparing full text configurations
		var startupConfig = cli.command("show startup-config");
		startupConfig = configCleanup(startupConfig);
		if (startupConfig == runningConfig) {
			configSaved = true;
		}
	}
	device.set("configurationSaved", configSaved);

	var sdwanRunningConfig = null;
	try {
		sdwanRunningConfig = cli.command("show sdwan running-config");
		config.set("sdwanRunningConfig", sdwanRunningConfig);
	}
	catch (e) {
		sdwanRunningConfig = null;
		// Ignore SDWAN config
	}

	var showVersion = cli.command("show version");

	var hostname = showVersion.match(/^ *(.*) uptime is/m);
	if (hostname != null) {
		device.set("name", hostname[1]);
	}

	var version = showVersion.match(/(?:Cisco )?IOS.*Software.*Version ([0-9\.A-Za-z\(\):]+),/m);
	if (!version) {
		version = showVersion.match(/(?:Cisco )?IOS.*Software.*Version ([0-9\.A-Za-z\(\):]+)/m);
	}
	if (version) {
		version = version[1];
		device.set("softwareVersion", version);
		config.set("iosVersion", version);
	}
	var image = showVersion.match(/^System image file is "(.*)"/m);
	if (image != null) {
		image = image[1];
		config.set("iosImageFile", image);
	}

	if (typeof config.computeHash === "function") {
		// Netshot 0.21+
		config.computeHash(runningConfig, sdwanRunningConfig, version, image);
	}

	var versionDetails = showVersion.match(/^(.*) with (\d+)K(\/(\d+)K)? bytes of /m);
	device.set("networkClass", "ROUTER");
	device.set("family", "Unknown IOS device");
	if (versionDetails) {
		var memory = parseInt(versionDetails[2]);
		if (typeof(versionDetails[4]) != "undefined") {
			memory += parseInt(versionDetails[4]);
		}
		memory = Math.round(memory / 1024);
		device.set("mainMemorySize", memory);
		var system = versionDetails[1];
		if (system.match(/.*CSC4.*/)) {
			device.set("family", "Cisco AGS+");
		}
		else if (system.match(/.*1900.*/)) {
			device.set("family", "Cisco Catalyst 1900");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/(^cisco )?(AS)?25[0-9][0-9].*/)) {
			device.set("family", "Cisco 2500");
			device.set("networkClass", "CONSOLESERVER");
		}
		else if (system.match(/.*26[12][01].*/)) {
			device.set("family", "Cisco 2600");
		}
		else if (system.match(/.*Cisco C?8\d\d[^\d]/)) {
			device.set("family", "Cisco ISR 800");
		}
		else if (system.match(/^Cisco IR?8\d\d[^\d]/)) {
			device.set("family", "Cisco IR 800");
		}
		else if (system.match(/.*Cisco C9\d\d[^\d]/)) {
			device.set("family", "Cisco ISR 900");
		}
		else if (system.match(/^cisco C11\d\d[^\d]/)) {
			device.set("family", "Cisco ISR 1000");
		}
		else if (system.match(/.*cisco IR11\d\d[^\d]/)) {
			device.set("family", "Cisco IR 1100");
		}
		else if (system.match(/^cisco C10\d\d-/)) {
			device.set("family", "Cisco Catalyst 1000");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*Cisco 18\d\d .*/)) {
			device.set("family", "Cisco ISR 1800");
		}
		else if (system.match(/.*Cisco 28\d\d .*/)) {
			device.set("family", "Cisco ISR 2800");
		}
		else if (system.match(/.*WS-C29.*/)) {
			device.set("family", "Cisco Catalyst 2900");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*CISCO19\d\d.*/)) {
			device.set("family", "Cisco ISR-G2 1900");
		}
		else if (system.match(/.*CISCO29\d\d.*/)) {
			device.set("family", "Cisco ISR-G2 2900");
		}
		else if (system.match(/.*WS-C355.*/)) {
			device.set("family", "Cisco Catalyst 3550");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*WS-C356.*/)) {
			device.set("family", "Cisco Catalyst 3560");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*WS-C35.*/)) {
			device.set("family", "Cisco Catalyst 3500XL");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/^36[0246][0-9].*/)) {
			device.set("family", "Cisco 3600");
		}
		else if (system.match(/^([Cc]isco )?37.*/)) {
			device.set("family", "Cisco 3700");
		}
		else if (system.match(/^([Cc]isco )?17[0-9][0-9]/)) {
			device.set("family", "Cisco 1700");
		}
		else if (system.match(/.*WS-C3650.*/)) {
			device.set("family", "Cisco Catalyst 3650");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*WS-C3750.*/)) {
			device.set("family", "Cisco Catalyst 3750");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/cisco ME-3400.*/)) {
			device.set("family", "Cisco ME3400");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*WS-C3850.*/)) {
			device.set("family", "Cisco Catalyst 3850");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/^(Cisco )?38\d\d.*/)) {
			device.set("family", "Cisco ISR 3800");
		}
		else if (system.match(/.*CISCO39\d\d.*/)) {
			device.set("family", "Cisco ISR-G2 3900");
		}
		else if (system.match(/cisco ISR4\d\d\d/)) {
			device.set("family", "Cisco ISR 4000");
		}
		else if (system.match(/.*WS-C4[59].*/)) {
			device.set("family", "Cisco Catalyst 4500");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*6000.*/)) {
			device.set("family", "Cisco Catalyst 6000");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/.*WS-C65.*/)) {
			device.set("family", "Cisco Catalyst 6500");
			device.set("networkClass", "SWITCHROUTER");
		}
		else if (system.match(/cisco ME-C65\d\d/)) {
			device.set("family", "Cisco ME6500");
			device.set("networkClass", "SWITCH");
		}
		else if (system.match(/Cisco C68\d\d-/)) {
			device.set("family", "Cisco Catalyst 6800");
			device.set("networkClass", "SWITCHROUTER");
		}
		else if (system.match(/.*720[246].*/)) {
			device.set("family", "Cisco 7200");
		}
		else if (system.match(/[Cc]isco 7300/)) {
			device.set("family", "Cisco 7300");
		}
		else if (system.match(/.*(OSR-|CISCO)76.*/)) {
			device.set("family", "Cisco 7600");
		}
		else if (system.match(/.* ASR100[0-9].*/)) {
			device.set("family", "Cisco ASR 1000");
		}
		else if (system.match(/cisco ASR-920-.*/)) {
			device.set("family", "Cisco ASR 920");
		}
		else if (system.match(/cisco OS-CIGESM.*/)) {
			device.set("family", "Cisco CIGESM Blade");
		}
		else if (system.match(/Cisco IOSv/)) {
			device.set("family", "Cisco IOSv");
		}
		else if (system.match(/[Cc]isco CSR1000V/)) {
			device.set("family", "Cisco CSR1000V");
		}
		else if (system.match(/[Cc]isco C8000V/)) {
			device.set("family", "Cisco Catalyst 8000V");
		}
		else if (system.match(/WS-CBS3/)) {
			device.set("family", "Cisco CBS");
		}
		else if (showVersion.match(/IOS .* RSP Software/) && system.match(/cisco RSP/)) {
			device.set("family", "Cisco 7500");
		}
		else if (system.match(/Cisco VG2\d\d/)) {
			device.set("family", "Cisco VG200");
			device.set("networkClass", "UNKNOWN");
		}
		else if (system.match(/cisco C8200/)) {
			device.set("family", "Cisco Catalyst 8200");
		}
		else if (system.match(/cisco C8300/)) {
			device.set("family", "Cisco Catalyst 8300");
		}
		else if (system.match(/cisco C8500/)) {
			device.set("family", "Cisco Catalyst 8500");
		}
		else if (system.match(/cisco C9200/)) {
			device.set("family", "Cisco Catalyst 9200");
		}
		else if (system.match(/cisco C9300/)) {
			device.set("family", "Cisco Catalyst 9300");
		}
		else if (system.match(/cisco C94\d\d/)) {
			device.set("family", "Cisco Catalyst 9400");
			device.set("networkClass", "SWITCHROUTER");
		}
		else if (system.match(/cisco C95\d\d/)) {
			device.set("family", "Cisco Catalyst 9500");
			device.set("networkClass", "SWITCHROUTER");
		}
		else if (system.match(/cisco C98\d\d/)) {
			device.set("family", "Cisco Catalyst 9800");
			device.set("networkClass", "WIRELESSCONTROLLER");
		}
	}
	var configRegister = showVersion.match(/^Configuration register is (.*)/m);
	if (configRegister != null) {
		device.set("configRegister", configRegister[1]);
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

	var processorId = showVersion.match(/^Processor board ID (.+)/m);
	if (processorId) {
		device.set("serialNumber", processorId[1]);
	}
	try {
		var showInventory = cli.command("show inventory");
		var inventoryPattern = /NAME: \"(.*)\", +DESCR: \"(.*)\"[\r\n]+PID: (.*?) *, +VID: (.*), +SN: (.*)/g;
		var match;
		while (match = inventoryPattern.exec(showInventory)) {
			var module = {
				slot: match[1],
				partNumber: match[3],
				serialNumber: match[5]
			};
			device.add("module", module);
			if (module.slot.match(/[Cc]hassis/)) {
				device.set("serialNumber", module.serialNumber);
			}
		}
	}
	catch (e) {
		cli.debug("show inventory not supported on this device?");
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
			if (interfaces[i].config.match(/^ *ip address (negotiated|dhcp)/m)) {
				// Dynamic address
				var ipPattern = /^ *Internet address is (\d+\.\d+\.\d+\.\d+)\/(\d+)/mg;
				while (match = ipPattern.exec(showInterface)) {
					networkInterface.ip.push({
						ip: match[1],
						mask: parseInt(match[2]),
						usage: "PRIMARY"
					});
				}
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

/**
 * SNMPv2 trap configuration example:
 *   snmp-server enable traps config
 *   snmp-server host x.y.z.t version 2c Netsh01 udp-port 11621  config
 * SNMPv3 trap configuration example:
 *   snmp-server group trapper v3 priv
 *   snmp-server user trapper trapper v3 auth sha authpass priv aes 128 privpass
 *   snmp-server host x.y.z.t version 3 priv trapper udp-port 1162  config
 */
function analyzeTrap(trap, debug) {
	for (var t in trap) {
		if (trap[t] === "3" && t.startsWith("1.3.6.1.4.1.9.9.43.1.1.6.1.5")) {
			return true;
		}
		if (t === "1.3.6.1.6.3.1.1.4.1.0" && trap[t] === "1.3.6.1.4.1.9.9.43.2.0.2") {
			// ccmCLIRunningConfigChanged
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.match(/^1\.3\.6\.1\.4\.1\.9\.1(\..*|$)/)
		&& sysDesc.match(/^Cisco (IOS|Internetwork Operating System) Software.*/)) {
		return true;
	}
	return false;
}
