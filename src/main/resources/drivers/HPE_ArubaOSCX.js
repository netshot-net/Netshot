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

const Info = {
	name: "HPEArubaOSCX",
	description: "HPE ArubaOS CX",
	author: "Netshot Team",
	version: "1.4"
};

const Config = {
	"cxVersion": {
		type: "Text",
		title: "AOS-CX version",
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
};

const Device = {
	"baseMacAddress": {
		type: "Text",
		title: "Base MAC address",
		comparable: true,
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

const CLI = {
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
		prompt: /^.* login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "usernameAgain" ]
			}
		}
	},
	password: {
		prompt: /^Password:\s?$/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "usernameAgain", "disable", "enable" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^.* login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	disable: {
		prompt: /^([A-Za-z\-0-9]+\> )$/,
		pager: {
			avoid: "no page",
			match: /^ -- MORE --, next page.*/,
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
		prompt: /^([A-Za-z\-0-9]+# )$/,
		error: /^(Invalid input: .*|% Command|% Ambiguous)/m,
		pager: {
			avoid: "no page",
			match: /^ -- MORE --, next page.*/,
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
		prompt: /^([A-Za-z\-0-9]+\(conf[0-9\-a-zA-Z]+\)# )$/,
		error: /^(Invalid input: .*|% Command|% Ambiguous)/m,
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

	const configCleanUp = function(config) {
		return config.replace(/^Current configuration:\r?\n!\r?\n/, "");
	}

	cli.macro("enable");
	const runningConfig = configCleanUp(cli.command("show running-config"));

	config.set("runningConfig", runningConfig);

	const hostnameMatch = runningConfig.match(/^hostname (.+)/m);
	if (hostnameMatch) {
		device.set("name", hostnameMatch[1]);
	}

	const startupConfig = cli.command("show startup-config");
	device.set("configurationSaved", startupConfig === runningConfig);

	const showVersion = cli.command("show version");

	const versionMatch = showVersion.match(/Version\s+:\s+(.+)/);
	let version = "";
	if (versionMatch) {
		version = versionMatch[1];
		device.set("softwareVersion", version);
		config.set("cxVersion", version);
	}

	if (typeof config.computeHash === "function") {
		// Netshot 0.21+
		try {
			// Try to use integrated config hash
			const showHash = cli.command("show running-config hash cli");
			const hashMatch = showHash.match(/^([0-9a-f]{64})\s*$/m);
			if (!hashMatch) {
				throw "Cannot find hash";
			}
			config.computeHash(hashMatch[1]);
		}
		catch (err) {
			cli.debug("Cannot get integrated running config hash (fallback to own computation): " + err);
			// Fallback to computing hash of running config
			config.computeHash(runningConfig, version);
		}
	}

	device.set("networkClass", "SWITCH");
	device.set("family", "ACOS-CX device");

	const showSystem = cli.command("show system");
	const serialMatch = showSystem.match(/^Chassis Serial.*: ([0-9A-Z]+)/m);
	device.set("serialNumber", serialMatch ? serialMatch[1] : "");
	const locationMatch = showSystem.match(/^System Location\s+: (.*?)\s*$/m);
	device.set("location", locationMatch ? locationMatch[1] : "");
	const contactMatch = showSystem.match(/^System Contact\s+: (.*?)\s*$/m);
	device.set("contact", contactMatch ? contactMatch[1] : "");
	const baseMacMatch = showSystem.match(
		/^Base MAC Address\s+: ([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})-([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})/m);
	device.set("baseMacAddress", baseMacMatch ?
		`${baseMacMatch[1]}:${baseMacMatch[2]}:${baseMacMatch[3]}:${baseMacMatch[4]}:${baseMacMatch[5]}:${baseMacMatch[6]}`
		: "");
	const productMatch = showSystem.match(/^Product Name\s+: ([A-Z0-9]+) ([0-9]{4,5}[A-Za-z]?)/m);
	if (productMatch) {
		const platform = productMatch[2];
		device.set("family", `Aruba ${platform}`)
	}

	try {
		const showModule = cli.command("show module");
		const linePattern = /^([0-9/]+)\s+([A-Z0-9]+)\s+(.+?)\s+([A-Z0-9]+)\s+([A-Z][a-z]+?)( \(local\))?\r?$/mg;
		const modules = {};
		while (true) {
			const match = linePattern.exec(showModule);
			if (!match) break;
			const module = {
				slot: match[1],
				partNumber: match[2],
				serialNumber: match[4],
				description: match[3], // Not used by Netshot, used in code below
				status: match[5],
				isLocal: !!match[6],
			};
			// Ignore duplicates
			if (modules[module.slot]) {
				continue;
			}
			modules[module.slot] = module;
			device.add("module", module);
		}
	}
	catch (err) {
		cli.debug("'show module' might not be supported on this platform: " + err);
	}

	try {
		const showInventory = cli.command("show system inventory");
		const linePattern = /^(.+?)\s+([0-9]+)\s+([A-Z0-9]+)\s+(.+)\s+([A-Z0-9]+)\s+([0-9.]+)\s*$/mg;
		while (true) {
			const match = linePattern.exec(showInventory);
			if (!match) break;
			const module = {
				type: match[1],
				slot: match[2],
				partNumber: match[3],
				description: match[4],
				serialNumber: match[5],
			};
			device.add("module", module);
		}
	}
	catch (err) {
		cli.debug("'show system inventory' might not be supported on this platform: " + err);
	}

	const vrfPattern = /^vrf (.+)/mg;
	while (true) {
		const match = vrfPattern.exec(runningConfig); 
		if (!match) break;
		device.add("vrf", match[1]);
	}

	const interfaces = cli.findSections(runningConfig, /^interface (.+)/m);
	for (const intf of interfaces) {
		const networkInterface = {
			name: intf.match[1],
			ip: [],
		};
		const description = intf.config.match(/^\s*description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		if (networkInterface.name.match(/^mgmt/)) {
			networkInterface.vrf = "mgmt";
		}
		const vrfMatch = intf.config.match(/^\s*vrf attach (.+)$/m);
		if (vrfMatch) {
			networkInterface.vrf = vrfMatch[1];
		}
		if (intf.config.match(/^\s*no routing$/m)) {
			networkInterface.level3 = false;
		}
		const ipPattern = /^\s*ip (?:address|static) (\d+\.\d+\.\d+\.\d+)\/(\d+)( secondary)?/mg;
		while (true) {
			const ipMatch = ipPattern.exec(intf.config);
			if (!ipMatch) break;
			const ip = {
				ip: ipMatch[1],
				mask: ipMatch[2],
				usage: "PRIMARY"
			};
			if (ipMatch[3]) {
				ip.usage = "SECONDARY";
			}
			networkInterface.ip.push(ip);
		}
		const ipv6Pattern = /^\s*ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg;
		while (true) {
			const ipMatch = ipv6Pattern.exec(intf.config);
			if (!ipMatch) break;
			const ip = {
				ipv6: ipMatch[1],
				mask: parseInt(ipMatch[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		const vrrpInstances = cli.findSections(intf.config, /^\s*vrrp ([0-9]+) address-family ipv(4|6)/m);
		for (const vrrpInstance of vrrpInstances) {
			const ipFamily = vrrpInstance.match[2];
			const vipPattern = /^\s*address (\d+\.\d+\.\d+\.\d+) (primary|secondary)/mg;
			while (true) {
				const vipMatch = vipPattern.exec(vrrpInstance.config);
				if (!vipMatch) break;
				const ip = {
					[ipFamily === 6 ? "ipv6" : "ip"]: vipMatch[1],
					mask: (ipFamily === 6 ? 128 : 32),
					usage: vipMatch[2] === "secondary" ? "SECONDARY VRRP" : "VRRP",
				};
				networkInterface.ip.push(ip);
			}
		}

		try {
			const showInterface = cli.command(`show interface ${networkInterface.name}`);
			const macMatch = showInterface.match(/M(?:AC|ac) Address\s*: ([0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2})/);
			if (macMatch) {
				networkInterface.mac = macMatch[1];
			}
			if (showInterface.match(/^\s*Admin state is down/m) ||
			    showInterface.match(/^\s*Management interface is disabled/)) {
				networkInterface.enabled = false;
			}
		}
		catch (e) {
			cli.debug(`Error while examining interface ${networkInterface.name}`)
		}
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	if (message.match(/hpe-config/)) {
		return true;
	}
	return false;
}

/**
 * SNMPv2 trap configuration example:
 *   snmp-server trap configuration-changes
 *   snmp-server host x.y.z.t trap version v2c community Netsh01 vrf mgmt
 */
function analyzeTrap(trap, debug) {
	for (const t in trap) {
		if (t === "1.3.6.1.6.3.1.1.4.1.0" && trap[t] === "1.3.6.1.4.1.47196.4.1.1.3.20.0.1") {
			// arubaWiredConfigurationChangeNotification
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.47196.4.1.1.") && sysDesc.match(/^(Aruba|HPE)/)) {
		return true;
	}
	return false;
}
