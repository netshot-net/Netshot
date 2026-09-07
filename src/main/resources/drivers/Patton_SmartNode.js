/**
 * Copyright 2013-2026 Netshot
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
	name: "PattonSmartNode",
	description: "Patton SmartNode (SmartWare, Trinity)",
	author: "Netshot Team",
	version: "1.2"
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
	"cliVersion": {
		type: "Text",
		title: "CLI version",
		searchable: true,
		checkable: true,
	},
	"swVersion": {
		type: "Text",
		title: "Software version",
		searchable: true,
		checkable: true,
	},
};

var Device = {
	"swPlatform": {  /* Trinity or SmartWare */
		type: "Text",
		title: "Software platform",
		searchable: true,
		checkable: true,
	},
	"hwVersion": {
		type: "Text",
		title: "Hardware version",
		searchable: true,
		checkable: true,
	},
	"swImage1": {
		type: "Text",
		title: "Software image 1",
		searchable: true,
		checkable: true,
	},
	"swImage2": {
		type: "Text",
		title: "Software image 2",
		searchable: true,
		checkable: true,
	},
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
		prompt: /^login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "usernameAgain" ]
			}
		}
	},
	password: {
		prompt: /^[Pp]assword: ?$/,
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
		prompt: /^([A-Za-z\-_0-9\.\/~]+\>)$/,
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
		prompt: /^([A-Za-z\-_0-9\.\/~]+#)$/,
		error: /^% (.*)/m,
		macros: {
			configure: {
				cmd: "configure",
				options: [ "enable", "configure" ],
				target: "configure"
			},
			save: {
				cmd: "copy running-config startup-config",
				options: [ "enable", "saveTarget" ],
				target: "enable"
			}
		}
	},

	configure: {
		prompt: /^([A-Za-z\-_0-9\.\/~]+\([0-9\-a-zA-Z]+\)(\[[0-9\-a-zA-Z]+\])?#)$/,
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
	const matchSet = function(e, data, re, field, defaultValue) {
		const r = data.match(re);
		if (r) {
			e.set(field, r[1]);
		}
		else if (defaultValue) {
			e.set(field, defaultValue);
		}
	};

	cli.macro("enable");
	const runningConfig = cli.command("show running-config")
		// Remove command echo (normally suppressed by ANSI code)
		.replace(/^.*show running-config.*\n/, "");
	
	config.set("runningConfig", runningConfig);

	matchSet(config, runningConfig, /^cli version ([0-9.]+)/m, "cliVersion");
	
	// On SmartWare flavor, the current date appears in the config header
	// Remove it to compute the config hash
	const runningConfigNoDate = runningConfig.replace(/^# [0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}.*\r?\n/m, "");
	config.computeHash(runningConfigNoDate);

	device.set("networkClass", "VOICEGATEWAY");

	let swPlatform = "Trinity";
	try {
		cli.command("show system version");
	}
	catch (err) {
		swPlatform = "SmartWare";
	}
	cli.debug(`Detected software platform: ${swPlatform}`);
	device.set("swPlatform", swPlatform);

	if (swPlatform === "Trinity") {
		const showSystem = cli.command("show system").replace(/\r/g, "");
		matchSet(device, showSystem, /^ +Hardware Version: +([A-Za-z0-9.-]+)/m, "hwVersion");
		matchSet(config, showSystem, /^ +Software Version: +([A-Za-z0-9.-]+)/m, "swVersion");
		matchSet(device, showSystem, /^ +Software Version: +([A-Za-z0-9.-]+)/m, "softwareVersion");
		matchSet(device, showSystem, /^ +System Location: +(.+)/m, "location", "");
		matchSet(device, showSystem, /^ +System Contact: +(.+)/m, "contact", "");
		matchSet(device, showSystem, /^ +Host Name: +(.+)/m, "name");

		const imagePattern = /^Software Image #([0-9]+)\n=+\n *\n +Image State:( +(.+))?\n +Build Version:( +(.+))?/mg;
		while (true) {
			const match = imagePattern.exec(showSystem);
			if (!match) break;
			const index = parseInt(match[1]);
			const swImage = `${match[5] || "-"} [${match[3]}]`;
			if (index >= 1 && index <= 2) {
				device.set(`swImage${index}`, swImage);
			}
		}

		const serialMatch = showSystem.match(/^ +Serial Number: +(.+)/m);
		const modelMatch = showSystem.match(/^ +Model: +(.+)/m);
		if (modelMatch) {
			const model = modelMatch[1].replace(/\/.*/, "");
			device.set("family", `Patton ${model}`);
		}
		else {
			device.set("family", "Patton SmartNode");
		}
		if (serialMatch && modelMatch) {
			device.add("module", {
				slot: "Mainboard",
				partNumber: modelMatch[1],
				serialNumber: serialMatch[1],
			});
			device.set("serialNumber", serialMatch[1]);
		}

		const contexts = {};
		for (const contextSection of cli.findSections(runningConfig, /^context ip (.+)/m)) {
			const contextName = contextSection.match[1];
			if (typeof contexts[contextName] === "undefined") {
				contexts[contextName] = {
					name: contextName,
					intfs: {},
				};
			}
			for (const intfSection of cli.findSections(contextSection.config, /^ +interface (.+)/m)) {
				const intf = {
					name: intfSection.match[1],
					ip: [],
				};
				const ipPattern = /^ +ipaddress (.+) ([0-9a-f.:]+)\/([0-9]+)/mg;
				while (true) {
					const ipMatch = ipPattern.exec(intfSection.config);
					if (!ipMatch) break;
					if (ipMatch[1].includes(":")) {
						intf.ip.push({
							ipv6: ipMatch[2],
							mask: parseInt(ipMatch[3]),
							usage: "PRIMARY",
						});
					}
					else {
						intf.ip.push({
							ip: ipMatch[2],
							mask: parseInt(ipMatch[3]),
							usage: "PRIMARY",
						});
					}
				}
				context.intfs[intf.name] = intf;
			}
		}

		for (const portSection of cli.findSections(runningConfig, /^port (ethernet .+)/m)) {
			const ni = {
				name: portSection.match[1],
				ip: [],
			};
			if (portSection.config.match(/^ +shutdown/)) {
				ni.enabled = false;
			}
			const bindMatch = portSection.config.match(/bind interface (.+) (.+)/);
			if (bindMatch) {
				const contextName = bindMatch[1];
				const intfName = bindMatch[2];
				ni.description = intfName;
				ni.vrf = contextName;
				try {
					const intf = contexts[contextName].intfs[intfName];
					ni.ip = intf.ip;
				}
				catch (err1) {
					// Ignore
				}
			}
			const showPort = cli.command(`show port ${ni.name}`);
			const macMatch = showPort.match(/^ +Hardware Address: +([0-9a-f:]+)/m);
			if (macMatch) {
				ni.mac = macMatch[1];
			}
			device.add("networkInterface", ni);
		}

	}
	else if (swPlatform === "SmartWare") {
		const showVersion = cli.command("show version");
		const softwareMatch = showVersion.match(/^Software Version +: +([A-Z0-9.]+)/m);
		if (softwareMatch) {
			device.set("softwareVersion", softwareMatch[1]);
			config.set("swVersion", softwareMatch[1]);
		}
		const hardwareMatch = showVersion.match(/^Hardware Version +: +(.+)/m);
		if (hardwareMatch) {
			device.set("hwVersion", hardwareMatch[1]);
		}
		const serialMatch = showVersion.match(/^Serial number +: +(.+)/m);
		if (serialMatch) {
			device.set("serialNumber", serialMatch[1]);
		}
		const productMatch = showVersion.match(/^Productname +: +(.+)/m);
		if (productMatch) {
			const model = productMatch[1].replace(/\/.*/, "");
			device.set("family", `Patton ${model}`);
		}
		if (serialMatch && productMatch) {
			device.add("module", {
				slot: "Mainboard",
				partNumber: productMatch[1],
				serialNumber: serialMatch[1],
			});
		}

		matchSet(device, runningConfig, /^system location (.+)/m, "location", "");
		matchSet(device, runningConfig, /^system contact (.+)/m, "contact", "");
		matchSet(device, runningConfig, /^system hostname (.+)/m, "name");
		device.set("swImage1", "");
		device.set("swImage2", "");



		const contexts = {};
		for (const contextSection of cli.findSections(runningConfig, /^context ip (.+)/m)) {
			const contextName = contextSection.match[1];
			if (typeof contexts[contextName] === "undefined") {
				contexts[contextName] = {
					name: contextName,
					intfs: {},
				};
			}
			const context = contexts[contextName];
			for (const intfSection of cli.findSections(contextSection.config, /^ +interface (.+)/m)) {
				const intf = {
					name: intfSection.match[1],
					ip: [],
					vrf: context.name,
				};
				const ipPattern = /^ +ipaddress ([0-9.]+) ([0-9.]+)/mg;
				while (true) {
					const ipMatch = ipPattern.exec(intfSection.config);
					if (!ipMatch) break;
					intf.ip.push({
						ip: ipMatch[1],
						mask: ipMatch[2],
						usage: "PRIMARY",
					});
				}
				contexts[context.name].intfs[intf.name] = intf;
				if (intfSection.config.match(/^ +loopback/m)) {
					device.add("networkInterface", intf);
				}
			}
		}

		for (const portSection of cli.findSections(runningConfig, /^port (ethernet .+)/m)) {
			const ni = {
				name: portSection.match[1],
				ip: [],
			};
			if (portSection.config.match(/^ +shutdown/)) {
				ni.enabled = false;
			}
			const bindMatch = portSection.config.match(/bind interface (.+) (.+)/);
			if (bindMatch) {
				const intfName = bindMatch[1];
				const contextName = bindMatch[2];
				ni.description = intfName;
				ni.vrf = contextName;
				try {
					const intf = contexts[contextName].intfs[intfName];
					ni.ip = intf.ip;
				}
				catch (err1) {
					// Ignore
				}
			}
			const showPort = cli.command(`show port ${ni.name}`);
			const macMatch = showPort.match(/^MAC Address +: +([0-9A-F:]+)/m);
			if (macMatch) {
				ni.mac = macMatch[1];
			}
			device.add("networkInterface", ni);
		}
	}
	else {
		throw `Unsupported software platform ${swPlatform}`;
	}


};

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.1768.100.4.")) {
		return true;
	}
	return false;
}
