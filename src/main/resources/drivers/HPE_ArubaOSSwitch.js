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
	name: "HPEArubaOSSwitch",
	description: "HPE ArubaOS-Switch",
	author: "Netshot Team",
	version: "1.7"
};

var Config = {
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
	"romVersion": {
		type: "Text",
		title: "ROM version",
		searchable: true
	},
	"primaryVersion": {
		type: "Text",
		title: "Primary image version",
		searchable: true
	},
	"secondaryVersion": {
		type: "Text",
		title: "Secondary image version",
		searchable: true
	},
	"bootImage": {
		type: "Text",
		title: "Boot image",
		searchable: true
	}
};

var CLI = {
	telnet: {
		macros: {
			enable: {
				options: [ "continue", "username", "stackSelect", "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "continue", "username", "stackSelect", "enable", "disable" ],
				target: "configure"
			}
		}
	},
	ssh: {
		macros: {
			enable: {
				options: [ "continue", "stackSelect", "enable", "disable" ],
				target: "enable"
			},
			configure: {
				options: [ "continue", "stackSelect", "enable", "disable" ],
				target: "configure"
			}
		}
	},
	continue: {
		prompt: /Press any key to continue/,
		macros: {
			auto: {
				cmd: "",
				options: [ "username", "stackSelect", "disable", "enable" ]
			}
		}
	},
	// HP Stack Management Commander: the Commander presents a member
	// selection menu ("Enter switch number to connect to or <CR>:") before
	// reaching the CLI. Sending an empty command (CR) connects to the
	// Commander itself, i.e. the device whose IP Netshot polls. This avoids
	// having to disable Stack Management (no stack) on every Commander.
	stackSelect: {
		prompt: /Enter switch number to connect to or <CR>: *$/,
		macros: {
			auto: {
				cmd: "",
				options: [ "username", "continue", "disable", "enable" ]
			}
		}
	},
	username: {
		prompt: /^Username:/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "incorrectCredentials" ]
			}
		}
	},
	password: {
		prompt: /^Password:/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "incorrectCredentials", "continue", "stackSelect", "disable", "enable" ]
			}
		}
	},
	incorrectCredentials: {
		prompt: /Incorrect Credentials/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	disable: {
		prompt: /([A-Za-z\-_0-9\.]+ ?>) $/,
		error: /^Invalid input: .*/m,
		pager: {
			avoid: [ "no pag" ],
			match: /-- MORE --, next page: Space, next line: Enter/,
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
		prompt: /([A-Za-z\-_0-9\.]+ ?#) $/,
		error: /^Invalid input: .*/m,
		pager: {
			avoid: [ "no pag" ], // page or paging
			match: /-- MORE --, next page: Space, next line: Enter/,
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
		prompt: /([A-Za-z\-_0-9\.]+\(.+\) ?#) $/,
		error: /^Invalid input: .*/m,
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
	
	cli.macro("enable");

	const showRunning = cli.command("show running-config");
	const runningConfig = showRunning.replace(/^Running configuration:\s*\n*/m, "");
	config.set("runningConfig", runningConfig);

	if (typeof config.computeHash === "function") {
		config.computeHash(runningConfig);
	}

	const showVersion = cli.command("show version");
	const showSystem = cli.command("show system");

	const nameMatch = showSystem.match(/^\s*System Name *: *(.+)/m);
	if (nameMatch) {
		device.set("name", nameMatch[1]);
	}

	const bootImageMatch = showVersion.match(/Boot Image *: *(.+?)(\s|$)/m);
	device.set("bootImage", bootImageMatch ? bootImageMatch[1] : "");
	const romVersionMatch = showSystem.match(/ROM Version *: *(.+?)(\s|$)/m);
	device.set("romVersion", romVersionMatch ? romVersionMatch[1] : "");
	const softwareMatch = showSystem.match(/Software revision *: *(.+?)(\s|$)/m);
	if (softwareMatch) {
		device.set("softwareVersion", softwareMatch[1]);
		config.set("swVersion", softwareMatch[1]);
	}
	
	const contactMatch = showSystem.match(/System Contact *: *(.+)/m);
	device.set("contact", contactMatch ? contactMatch[1] : "");
	const locationMatch = showSystem.match(/System Location *: *(.+)/m);
	device.set("location", locationMatch ? locationMatch[1] : "");

	device.set("networkClass", "SWITCH");

	const showFlash = cli.command("show flash");
	const primaryMatch = showFlash.match(/^Primary Image *: *(\d+) *([\d\/]+) *([A-Z0-9\.]+) *$/m);
	device.set("primaryVersion", primaryMatch ? primaryMatch[3] : "");
	const secondaryMatch = showFlash.match(/^Secondary Image *: *(\d+) *([\d\/]+) *([A-Z0-9\.]+) *$/m);
	device.set("secondaryVersion", secondaryMatch ? secondaryMatch[3] : "");

	device.set("family", "Avaya switch");

	const chassis = {
		partNumber: null,
		serialNumber: null,
		family: null,
		model: null,
	};

	try {
		const showModules = cli.command("show modules");
		// Chassis: 3800-24G-PoE+-2SFP+ J9573A Serial Number: xxxxxxxxxx
		const chassisMatch = showModules.match(/Chassis: (.+) +(J.+) +Serial Number: +(.+)/);
		if (chassisMatch) {
			const model = chassisMatch[1].trim();
			const platform = model.replace(/-.*/, "");
			chassis.family = `Avaya ${platform}`;
			chassis.partNumber = chassisMatch[2].trim();
			chassis.serialNumber = chassisMatch[3].trim();
			chassis.model = model;
		}

		// [v1.5] Parse management module (5400zl, E5412zl)
		// e.g. "  Management Module: J8726A      Serial Number:  xxxxxxxxxx   Core Dump: YES"
		const mgmtMatch = showModules.match(
			/Management Module:\s+(J[A-Z0-9]+)\s+Serial Number:\s+([A-Z0-9]+)/
		);
		if (mgmtMatch) {
			device.add("module", {
				slot: "Management",
				partNumber: mgmtMatch[1],
				serialNumber: mgmtMatch[2],
			});
		}

		// [v1.5] Parse slot/member module lines.
		// Covers all known output formats:
		//   "  A     HP J8706A 24p SFP zl Module            xxxxxxxxxx     Up"
		//   "  MM1   HP J9827A Management Module 5400Rzl2   xxxxxxxxxx     Active"
		//   "  STK   Aruba JL084A 4-port Stacking Module    xxxxxxxxxx     Up"
		//   "  St... Aruba JL084A 4-port Stacking Module    xxxxxxxxxx     Up"
		//   "  1      STK      HP J9733A 2-port Stacking... xxxxxxxxxx     Up"
		//   "  1     Stacking ... HP J9577A 4-port ...       xxxxxxxxxx"
		const moduleLinePattern = /^\s+(.+?)\s+(?:HP|Aruba)\s+(J[A-Z0-9]+)\s+\S.+?\s+([A-Z][A-Z0-9]{7,})(?:\s|$)/mg;
		while (true) {
			const moduleLineMatch = moduleLinePattern.exec(showModules);
			if (!moduleLineMatch) break;
			const prefix = moduleLineMatch[1].trim();
			const partNumber = moduleLineMatch[2];
			const serialNumber = moduleLineMatch[3];

			let slotLabel;
			const prefixParts = prefix.match(/^(\d+)\s+(.+)$/);
			if (prefixParts) {
				slotLabel = `Member ${prefixParts[1]} / ${prefixParts[2].trim()}`;
			}
			else {
				slotLabel = prefix;
			}

			device.add("module", {
				slot: slotLabel,
				partNumber,
				serialNumber,
			});
		}
	}
	catch (err) {
		cli.debug("show modules doesn't seem to be supported");
	}

	if (!chassis.serialNumber) {
		const serialMatch = showSystem.match(/Serial Number +: +([A-Z0-9]+)/);
		if (serialMatch) {
			chassis.serialNumber = serialMatch[1];
		}
	}

	if (!chassis.serialNumber) {
		try {
			// On older firmwares (e.g. Q.11.x), this field may be absent; fall back to
			// 'show system-information' which exposes it on those platforms.
			const showSystemInformation = cli.command("show system-information");
			const serialMatch = showSystemInformation.match(/Serial Number +: +([A-Z0-9]+)/);
			if (serialMatch) {
				chassis.serialNumber = serialMatch[1];
			}
		}
		catch (err) {
			cli.debug("show system-information doesn't seem to be supported");
		}
	}

	if (!chassis.partNumber) {
		try {
			const showDhcpClient = cli.command("show dhcp client vendor-specific");
			// Vendor Class Id = HP J9773A 2530-24G-PoEP Switch
			// Vendor Class Id = Aruba JL075A 3810M-16SFP+-2-slot Switch
			const classMatch = showDhcpClient.match(/Vendor Class Id = (HP|Aruba) (J[A-Z0-9]+) (.+?)-/m);
			if (classMatch) {
				chassis.partNumber = classMatch[2];
				const model = classMatch[3];
				const platform = model.replace(/-.*/, "");
				chassis.family = `Avaya ${platform}`;
			}
		}
		catch (err) {
			cli.debug("show dhcp client vendor-specific doesn't seem to be supported");
		}
	}

	device.set("family", chassis.family || "Unknown Avaya switch");

	if (chassis.serialNumber) {
		device.set("serialNumber", chassis.serialNumber);
		if (chassis.partNumber) {
			const chassisPN = chassis.model
				? `${chassis.partNumber} ${chassis.model}`
				: chassis.partNumber;
			device.add("module", {
				slot: "Chassis",
				partNumber: chassisPN,
				serialNumber: chassis.serialNumber,
			});
		}
	}

	// [v1.5] Track member serials found by show stacking detail, to avoid
	// duplicates when show system information is parsed later.
	const stackingMemberSerials = {};

	try {
		const showStackingDetail = cli.command("show stacking detail");

		// [v1.5] FIX: v1.4 used undeclared 'foundSerial' and 'serial' variables,
		// causing a ReferenceError that silently aborted this entire try block.
		let stackFoundSerial = false;
		const memberPattern = /^Member ID *: *([0-9]+)\s*\n(.*\s*\n)*?Type *: *(J[A-Z0-9]+)\s*\n(.*\s*\n)*?Model *: *((Aruba|HP) )?(J[A-Z0-9]+) +(.+?)\s*\n(.*\s*\n)*?Serial Number *: *(.+?)\s*\n/mg;
		while (true) {
			const memberMatch = memberPattern.exec(showStackingDetail);
			if (!memberMatch) break;
			const id = memberMatch[1];
			const partNumber = memberMatch[3];
			const model = memberMatch[8];
			const serialNumber = memberMatch[10];
			const platform = model.replace(/-.*/, "");
			stackingMemberSerials[id] = serialNumber;
			if (!stackFoundSerial) {
				stackFoundSerial = true;
				device.set("serialNumber", serialNumber);
				device.set("family", `Avaya ${platform}`);
			}
			device.add("module", {
				slot: `Switch ${id}`,
				partNumber: `${partNumber} ${model}`,
				serialNumber,
			});
		}
	}
	catch (err) {
		cli.debug("show stacking doesn't seem to be supported");
	}

	// [v1.5] Parse 'show system information' (without dash) for per-member serial numbers.
	// This is the ONLY source of chassis serials for 2930F VSF stacks (where show stacking
	// detail is not supported). Also serves as fallback for 3810M/2920/2930M/3800 stacks
	// if show stacking detail didn't capture them.
	// Parses sections like:
	//   " Member :1"       (3810M, 2920, 2930M, 3800)
	//   " VSF-Member :1"   (2930F)
	// followed by:
	//   "  Serial Number      : xxxxxxxxxx"
	try {
		const showSysInfo = cli.command("show system information");
		const sysInfoLines = showSysInfo.split("\n");
		let currentMemberId = null;
		let currentMemberSerial = null;
		const sysInfoMembers = [];

		for (const line of sysInfoLines) {
			const memberHeaderMatch = line.match(/^\s*(?:VSF-)?Member\s*:(\d+)/);
			if (memberHeaderMatch) {
				if (currentMemberId !== null && currentMemberSerial !== null) {
					sysInfoMembers.push({ id: currentMemberId, serial: currentMemberSerial });
				}
				currentMemberId = memberHeaderMatch[1];
				currentMemberSerial = null;
			}
			else if (currentMemberId !== null) {
				const serialLineMatch = line.match(/Serial Number\s+:\s+([A-Z0-9]+)/);
				if (serialLineMatch) {
					currentMemberSerial = serialLineMatch[1];
				}
			}
		}
		if (currentMemberId !== null && currentMemberSerial !== null) {
			sysInfoMembers.push({ id: currentMemberId, serial: currentMemberSerial });
		}

		for (const mem of sysInfoMembers) {
			// Skip if already captured by show stacking detail
			if (!Object.values(stackingMemberSerials).includes(mem.serial)) {
				device.add("module", {
					slot: `Switch ${mem.id}`,
					partNumber: chassis.partNumber || "",
					serialNumber: mem.serial,
				});
			}
			// Set device serial to first member if not already set
			if (mem.id === "1" && !chassis.serialNumber) {
				device.set("serialNumber", mem.serial);
			}
		}
	}
	catch (err) {
		cli.debug("show system information doesn't seem to be supported");
	}

	// Switch ports
	const interfaceDescriptions = {};
	const interfaces = cli.findSections(showRunning, /^interface ([A-Za-z0-9\/]+)/m);
	for (const intf of interfaces) {
		const ifName = intf.match[1];
		const description = intf.config.match(/^\s*name "(.+)"/m);
		if (description) {
			interfaceDescriptions[ifName] = description[1];
		}
	}

	const showIntfConfig = cli.command("show interfaces config");
	const portPattern = /^ *([A-Za-z0-9\/]+?)(-.+?)? +.* | (Yes|No) .*/mg;
	while (true) {
		const portMatch = portPattern.exec(showIntfConfig);
		if (!portMatch) break;
		const ifName = portMatch[1];
		if (ifName === "Port") continue;
		const networkInterface = {
			name: ifName,
			level3: false,
			disabled: portMatch[2] !== "Yes",
		};
		if (interfaceDescriptions[ifName]) {
			networkInterface.description = interfaceDescriptions[ifName];
		}
		device.add("networkInterface", networkInterface);
	}

	// VLAN interfaces
	const vlans = cli.findSections(showRunning, /^vlan ([0-9]+)/m);
	for (const intf of vlans) {
		const ifName = `Vlan${intf.match[1]}`
		const networkInterface = {
			name: ifName,
			ip: [],
		};
		const description = intf.config.match(/^\s*name "(.+)"/m);
		if (description) {
			networkInterface.description = description[1];
		}
		const ipPattern = /^\s*ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/mg;
		while (true) {
			const ipMatch = ipPattern.exec(intf.config);
			if (!ipMatch) break;
			const ip = {
				ip: ipMatch[1],
				mask: ipMatch[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		const ipv6Pattern = /^\s*ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mig;
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
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	return !!message.match(/Running configuration changed by/);
}

/*
 * Trigger a snapshot upon configuration save.
 */
function analyzeTrap(trap, debug) {
	// 1.3.6.1.4.1.11.2.14.11.5.1.7.1.29.1.0.7 = hpSwitchRunningConfigChange
	// 1.3.6.1.4.1.11.2.14.11.5.1.7.1.29.1.12.6.1.3 = hpSwitchRunningCfgChgEventMethod
	return trap["1.3.6.1.6.3.1.1.4.1.0"] === "1.3.6.1.4.1.11.2.14.11.5.1.7.1.29.1.0.7" ||
	  (typeof trap["1.3.6.1.4.1.11.2.14.11.5.1.7.1.29.1.12.6.1.3"] !== "undefined");
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.11.2.3.7.11.") && sysDesc.match(/HP /)) {
		return true;
	}
	return false;
}
