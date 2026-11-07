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

const Info = {
	name: "CiscoIOSXR",
	description: "Cisco IOS-XR",
	author: "Netshot Team",
	version: "2.0"
};

const Config = {
	"xrVersion": {
		type: "Text",
		title: "IOS-XR version",
		comparable: true,
		searchable: true
	},
	"xrPackages": {
		type: "LongText",
		title: "XR packages",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! IOS-XR active packages:",
			preLine: "!!  ",
			post: "!! End of XR packages"
		}
	},
	"configuration": {
		type: "LongText",
		title: "Configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Configuration (taken on %when%):",
			post: "!! End of configuration"
		}
	},
	"adminConfiguration": {
		type: "LongText",
		title: "Admin configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Admin configuration (taken on %when%):",
			post: "!! End of admin configuration"
		}
	},
};

const Device = {
	"xrArchitecture": {
		type: "Text",
		title: "IOS-XR architecture",
		searchable: true,
		checkable: true
	},
};

const CLI = {
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
		},
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
				options: [ "usernameAgain", "exec" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^Username: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	exec: {
		prompt: /^([A-Z0-9\/]+:[A-Za-z\-_0-9\.]+#)$/,
		error: /^% (.*)/m,
		pager: {
			avoid: [ "terminal length 0", "terminal no-timestamp", "terminal exec prompt no-timestamp" ],
			match: /^ --More--$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "exec", "configure" ],
				target: "configure"
			}
		}

	},
	configure: {
		prompt: /^([A-Z0-9\/]+:[A-Za-z\-_0-9\.]+\(conf[0-9\-a-zA-Z]+\)#)$/,
		error: /^% (.*)/m,
		clearPrompt: true,
		macros: {
			abort: {
				cmd: "abort",
				options: [ "exec", "configure" ],
				target: "exec"
			},
			commit: {
				cmd: "commit",
				options: [ "configure" ],
				target: "configure"
			}
		}
	}
};

/**
 * Guess the device family from a model or a chassis PID, as found in
 * 'show version' (e.g. "8812", "NCS-57C1-48Q6-SYS", "ASR9K Series") or in
 * 'show inventory' (e.g. "8712-MOD-M", "NCS-57B1-5DSE-SYS", "ASR-9010-AC").
 * @param model the model or PID
 * @return the family, or null if unknown
 */
function familyFromModel(model) {
	// Cisco 8000 series: 8101-32H, 8201, 8201-SYS, 8202-32FH-M, 8608, 8712-MOD-M, 8808...
	const c8000 = model.match(/^8([0-9])[0-9]{2}(-|$)/);
	if (c8000) {
		return ["1", "2", "6", "7", "8"].includes(c8000[1])
			? `Cisco 8${c8000[1]}00`
			: "Cisco 8000";
	}
	const families = [
		[/^NCS-?57[0-9A-Z]{2}/, "Cisco NCS5700"],
		[/^NCS-?55[0-9A-Z]{2}/, "Cisco NCS5500"],
		[/^NCS-?5[0-9A-Z]{3}/, "Cisco NCS5000"],
		[/^N540X?-/, "Cisco NCS540"],
		[/^NCS-?6[0-9A-Z]{3}/, "Cisco NCS6000"],
		[/^ASR-?9/, "Cisco ASR9000"],
		[/^CRS/, "Cisco CRS"],
		[/^12[0-9]{3}/, "Cisco 12000"],
		[/^IOS XRv Series/, "Cisco XRv"],
	];
	for (const [pattern, family] of families) {
		if (model.match(pattern)) {
			return family;
		}
	}
	return null;
}

function snapshot(cli, device, config) {

	const configCleanup = (config) => {
		const p = config.search(/^[a-z]/m);
		return (p > 0) ? config.slice(p) : config;
	};

	cli.macro("exec");

	const showVersion = cli.command("show version");

	device.set("networkClass", "ROUTER");

	const version = showVersion.match(/^(Cisco )?IOS XR Software.*Version ([0-9.A-Z():]+)/m);
	if (version) {
		device.set("softwareVersion", version[2]);
		config.set("xrVersion", version[2]);
	}

	device.set("family", "IOS-XR device");
	// First line of the hardware section, e.g.
	//   cisco 8000 (Intel(R) Xeon(R) CPU D-1530 @ 2.40GHz)
	//   cisco NCS5700 (D-1633N @ 2.50GHz)
	const platform = showVersion.match(/^cisco (8000|NCS5700) \(/m);
	if (platform) {
		device.set("family", `Cisco ${platform[1]}`);
	}
	// Processor line, e.g.
	//   cisco ASR9K Series (MPC8641D) processor with 4194304K bytes of memory.
	//   cisco 8812 (Intel(R) Xeon(R) CPU D-1530 @ 2.40GHz) processor with 32GB of memory
	//   cisco NCS-57C1-48Q6-SYS (D-1633N @ 2.50GHz) processor with 16GB of memory
	//   cisco NCS-5500 () processor
	const system = showVersion.match(/^cisco (.*?) \(.*\) processor\b/m);
	if (system) {
		const family = familyFromModel(system[1]);
		if (family) {
			device.set("family", family);
		}
	}
	if (showVersion.match(/IOS-XRv 9000 /)) {
		device.set("family", "Cisco XRv 9000");
	}

	// IOS-XR architecture (generation):
	// - LNT (aka XR7): 'Version 7.9.2 LNT' (Cisco 8000 is LNT only)
	// - eXR (64-bit, Linux based, with admin/sysadmin VM): 'Build Information:' block
	// - cXR (32-bit, QNX based, aka classic XR): 'ROM: System Bootstrap', 'System image file is'
	let architecture = null;
	if (showVersion.match(/^(Cisco )?IOS XR Software.*Version \S+ LNT/m) || platform?.[1] === "8000") {
		architecture = "LNT";
	}
	else if (showVersion.match(/^Build Information:/m)) {
		architecture = "eXR";
	}
	else if (showVersion.match(/^ROM: |^System image file is /m)) {
		architecture = "cXR";
	}
	device.set("xrArchitecture", architecture ?? "Unknown");

	// LNT has no admin plane anymore
	const isLnt = (architecture === "LNT");
	const adminCommand = (command) => {
		if (isLnt) {
			return null;
		}
		const output = cli.command(`admin ${command}`);
		if (output.match(/Admin mode has been deprecated/)) {
			return null;
		}
		return output;
	};

	let runningConfig = cli.command("show running-config");
	const author = runningConfig.match(/^\!\! Last configuration change .* by (.*)$/m);
	if (author) {
		config.set("author", author[1]);
	}
	runningConfig = configCleanup(runningConfig);
	config.set("configuration", runningConfig);

	const adminConfig = adminCommand("show running-config");
	if (adminConfig !== null) {
		config.set("adminConfiguration", configCleanup(adminConfig));
	}

	const showInventory = (adminCommand("show inventory") ?? "") + cli.command("show inventory");
	const showInstall = cli.command("show install active");

	const hostname = runningConfig.match(/^hostname (.+)/m) ?? showVersion.match(/^ *(.*) uptime is/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}

	config.set("xrPackages", showInstall);

	const location = runningConfig.match(/^snmp-server location (.*)/m);
	device.set("location", location ? location[1] : "");
	const contact = runningConfig.match(/^snmp-server contact (.*)/m);
	device.set("contact", contact ? contact[1] : "");

	const removeQuotes = (info) => {
		const match = info.match(/^"(.*)"$/);
		return (match ? match[1] : info).trim();
	};
	const inventoryPattern = /NAME: (.*), +DESCR: (.*)[\r\n]+PID: (.*?) *, +VID: (.*), +SN: (.*)/g;
	const knownModules = [];
	for (const match of showInventory.matchAll(inventoryPattern)) {
		const module = {
			slot: removeQuotes(match[1]),
			partNumber: removeQuotes(match[3]),
			serialNumber: removeQuotes(match[5])
		};
		const existing = knownModules.some((m) =>
			m.slot === module.slot &&
			m.partNumber === module.partNumber &&
			m.serialNumber === module.serialNumber);
		if (existing) {
			continue;
		}
		knownModules.push(module);
		device.add("module", module);
		if (module.slot.match(/[Cc]hassis|Rack 0/) && module.partNumber !== "N/A") {
			device.set("serialNumber", module.serialNumber);
			const family = familyFromModel(module.partNumber);
			if (family) {
				device.set("family", family);
			}
		}
	}

	for (const match of runningConfig.matchAll(/^vrf (.+)/mg)) {
		device.add("vrf", match[1]);
	}

	const fhrpAddresses = {};
	const fhrpConfig = cli.findSections(runningConfig, /^ *router (hsrp|vrrp)/m);
	for (const fhrpSection of fhrpConfig) {
		const usage = fhrpSection.match[1].toUpperCase();
		const fhrpIntConfig = cli.findSections(fhrpSection.config, /^ *interface ([^ ]+)/m);
		for (const fhrpInt of fhrpIntConfig) {
			const intName = fhrpInt.match[1];
			fhrpAddresses[intName] ??= [];
			for (const ipMatch of fhrpInt.config.matchAll(/^ *address (\d+\.\d+\.\d+\.\d+)( secondary)?$/mg)) {
				fhrpAddresses[intName].push({
					ip: ipMatch[1],
					mask: 32,
					usage: (ipMatch[2] === " secondary") ? `SECONDARY${usage}` : usage,
				});
			}
			for (const ipv6Match of fhrpInt.config.matchAll(/^ *address (global|linklocal) ([0-9A-Fa-f:]+)$/mg)) {
				fhrpAddresses[intName].push({
					ipv6: ipv6Match[2],
					mask: 128,
					usage: usage,
				});
			}
		}
	}

	const showInterfaces = cli.command("show interface | inc \", address is |line protocol\"");
	const interfaceStatuses = {};
	const ifPattern = /^([A-Za-z0-9\-\.\/]+) is .*, line protocol is (.+)\r?\n( +.*, address is ([0-9a-fA-F]{4}\.[0-9a-fA-F]{4}\.[0-9a-fA-F]{4}))?/mg;
	for (const match of showInterfaces.matchAll(ifPattern)) {
		interfaceStatuses[match[1]] = {
			adminDown: !!match[2].match(/^administratively down/),
			mac: match[4],
		};
	}

	const interfaces = cli.findSections(runningConfig, /^interface (preconfigure )?([^ ]+)( .+)?/m);
	for (const intf of interfaces) {
		const ifName = intf.match[2];
		const networkInterface = {
			name: ifName,
			ip: []
		};
		const description = intf.config.match(/^ *description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		const vrf = intf.config.match(/^ *vrf (.+)$/m);
		if (vrf) {
			networkInterface.vrf = vrf[1];
		}
		if (intf.config.match(/^ *shutdown$/m)) {
			networkInterface.enabled = false;
		}
		for (const match of intf.config.matchAll(/^ *ipv4 address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)( secondary)?/mg)) {
			networkInterface.ip.push({
				ip: match[1],
				mask: match[2],
				usage: (match[3] === " secondary") ? "SECONDARY" : "PRIMARY",
			});
		}
		for (const match of intf.config.matchAll(/^ *ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg)) {
			networkInterface.ip.push({
				ipv6: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY",
			});
		}
		const interfaceStatus = interfaceStatuses[ifName];
		if (interfaceStatus) {
			if (interfaceStatus.adminDown) {
				networkInterface.enabled = false;
			}
			if (interfaceStatus.mac) {
				networkInterface.mac = interfaceStatus.mac;
			}
		}
		for (const address of fhrpAddresses[ifName] ?? []) {
			networkInterface.ip.push(address);
		}
		if (intf.match[3]?.match(/l2transport/) || intf.config.match(/^ *l2transport$/m)) {
			networkInterface.level3 = false;
		}
		device.add("networkInterface", networkInterface);
	}

}

function analyzeSyslog(message) {
	if (message.match(/COMMIT : Configuration committed by user '(.*)'/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
		/*
				                      ccmHistoryEventCommandSource      ccmHistoryEventConfigSource       ccmHistoryEventConfigDestination
				XR 6.9.x  show run   .1.3.6.1.4.1.9.9.43.1.1.6.1.3.x=1 .1.3.6.1.4.1.9.9.43.1.1.6.1.4.x=3 .1.3.6.1.4.1.9.9.43.1.1.6.1.5.x=2
				          conf t     .1.3.6.1.4.1.9.9.43.1.1.6.1.3.x=1 .1.3.6.1.4.1.9.9.43.1.1.6.1.4.x=2 .1.3.6.1.4.1.9.9.43.1.1.6.1.5.x=3
				XR 7.9.x  show run   .1.3.6.1.4.1.9.9.43.1.1.6.1.3.x=1 .1.3.6.1.4.1.9.9.43.1.1.6.1.4.x=2 .1.3.6.1.4.1.9.9.43.1.1.6.1.5.x=1
				          conf t     .1.3.6.1.4.1.9.9.43.1.1.6.1.3.x=1 .1.3.6.1.4.1.9.9.43.1.1.6.1.4.x=1 .1.3.6.1.4.1.9.9.43.1.1.6.1.5.x=2
			HistoryEventMedium = erase(1) commandSource(2) running(3) startup(4) local(5)
			                     networkTftp(6) networkRcp(7) networkFtp(8) networkScp(9)
			=> XR 7.9.x at least is buggy, it shifts the values by one
		*/
	const ccmHistoryEventConfigSource = "1.3.6.1.4.1.9.9.43.1.1.6.1.4";
	const ccmHistoryEventConfigDestination = "1.3.6.1.4.1.9.9.43.1.1.6.1.5";
	const event = {
		[ccmHistoryEventConfigSource]: null,
		[ccmHistoryEventConfigDestination]: null,
	};
	Object.entries(trap).forEach(([k, v]) => {
		Object.keys(event).forEach((type) => {
			if (k.startsWith(type + ".")) {
				event[type] = parseInt(v);
			}
		});
	});

	if (event[ccmHistoryEventConfigSource] === 2 &&
		  event[ccmHistoryEventConfigDestination] === 3) {
		// Normal
		return true;
	}
	if (event[ccmHistoryEventConfigSource] === 1 &&
		  event[ccmHistoryEventConfigDestination] === 2) {
		// Buggy XR 7.9.x
		return true;
	}
	return false;
}


function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 16) == "1.3.6.1.4.1.9.1."
		&& sysDesc.match(/^Cisco IOS XR Software.*/)) {
		return true;
	}
	return false;
}
