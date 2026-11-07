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
	name: "CiscoAireOS",
	description: "Cisco AireOS",
	author: "Netshot Team",
	version: "1.0"
};

var Config = {
	"runConfig": {
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
	"productVersion": {
		type: "Text",
		title: "Product version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"recoveryVersion": {
		type: "Text",
		title: "Recovery image version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"bootloaderVersion": {
		type: "Text",
		title: "Bootloader version",
		comparable: true,
		searchable: true,
		checkable: true
	},
};

var Device = {
	"memorySize": {
		type: "Numeric",
		title: "Total memory size (MB)",
		searchable: true
	},
	"flashSize": {
		type: "Numeric",
		title: "Flash size (MB)",
		searchable: true
	},
	"macAddress": {
		type: "Text",
		title: "Burned-in MAC address",
		searchable: true
	},
	"maxAccessPointCount": {
		type: "Numeric",
		title: "Maximum number of APs supported",
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
			config: {
				options: [ "username", "password", "exec" ],
				target: "config"
			}
		}
	},
	ssh: {
		macros: {
			exec: {
				options: [ "username", "password", "exec" ],
				target: "exec"
			},
			config: {
				options: [ "username", "password", "exec" ],
				target: "config"
			}
		}
	},
	username: {
		prompt: /^User: ?$/,
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
				options: [ "usernameAgain", "exec" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^User: ?$/,
		fail: "Authentication failed - Telnet/SSH authentication failure."
	},
	exec: {
		prompt: /^(\(.+\)) >$/,
		error: /^(Incorrect usage. .+)/m,
		pager: {
			avoid: "config paging disable",
			match: /^--More-- or \(q\)uit$/,
			response: " "
		},
		macros: {
			config: {
				cmd: "config",
				options: [ "exec", "configure" ],
				target: "config"
			},
			save: {
				cmd: "save config",
				options: [ "saveConfirm", "exec" ],
				target: "exec"
			},
		}
	},
	saveConfirm: {
		prompt: /Are you sure you want to save\? \(y\/n\)/,
		macros: {
			auto: {
				cmd: "y",
				options: [ "exec" ]
			}
		}
	},
	config: {
		prompt: /^(\(.+\)) config>$/,
		error: /^(Incorrect usage. .+)/m,
		clearPrompt: true,
		macros: {
			exit: {
				cmd: "exit",
				options: [ "exec", "config" ],
				target: "exec"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	cli.macro("exec");

	const showSysinfo = cli.command("show sysinfo");

	const productVersionMatch = showSysinfo.match(/^Product Version\.+ (.+)/m);
	if (productVersionMatch) {
		config.set("productVersion", productVersionMatch[1]);
		device.set("softwareVersion", productVersionMatch[1]);
	}
	const recoveryVersionMatch = showSysinfo.match(/^Field Recovery Image Version\.+ (.+)/m);
	if (recoveryVersionMatch) {
		config.set("recoveryVersion", recoveryVersionMatch[1]);
	}
	const emegencyVersionMatch = showSysinfo.match(/^Emergency Image Version\.+ (.+)/m);
	if (emegencyVersionMatch) {
		config.set("recoveryVersion", emegencyVersionMatch[1]);
	}
	const bootloaderVersionMatch = showSysinfo.match(/^Bootloader Version\.+ (.+)/m);
	if (bootloaderVersionMatch) {
		config.set("bootloaderVersion", bootloaderVersionMatch[1]);
	}
	const nameMatch = showSysinfo.match(/^System Name\.+ (.+)/m);
	if (nameMatch) {
		device.set("name", nameMatch[1]);
	}
	const locationMatch = showSysinfo.match(/^System Location\.+ (.*)/m);
	if (locationMatch) {
		device.set("location", locationMatch[1]);
	}
	const contactMatch = showSysinfo.match(/^System Contact\.+ (.*)/m);
	if (contactMatch) {
		device.set("contact", contactMatch[1]);
	}
	const macMatch = showSysinfo.match(/^Burned-in MAC Address\.+ (.*)/m);
	if (macMatch) {
		device.set("macAddress", macMatch[1]);
	}
	const apCountMatch = showSysinfo.match(/^Maximum number of APs supported\.+ (.*)/m);
	if (apCountMatch) {
		device.set("maxAccessPointCount", parseInt(apCountMatch[1]));
	}
	const flashSizeMatch = showSysinfo.match(/^Flash Size\.+ ([0-9]+)/m);
	if (flashSizeMatch) {
		device.set("flashSize", parseInt(flashSizeMatch[1]) / 1024 / 1024);
	}

	const showSystemMeminfo = cli.command("show system meminfo");
	const memTotalMatch = showSystemMeminfo.match(/MemTotal: +(.+) kB/m);
	if (memTotalMatch) {
		device.set("memorySize", parseInt(memTotalMatch[1]) / 1024);
	}

	device.set("networkClass", "WIRELESSCONTROLLER");
	device.set("family", "Cisco WLC");
	
	const showInventory = cli.command("show inventory");
	const inventoryPattern = /NAME: \"(.*)\" *, +DESCR: \"(.*)\"\s+PID: (.*?) *, +VID: (.*) *, +SN: (.*)/g;
	let invMatch;
	while (invMatch = inventoryPattern.exec(showInventory)) {
		const module = {
			slot: invMatch[1],
			partNumber: invMatch[3],
			serialNumber: invMatch[5]
		};
		device.add("module", module);
		if (module.slot.match(/Chassis/)) {
			device.set("serialNumber", module.serialNumber);
		}
		const familyMatch = invMatch[2].match(/Cisco ([0-9]+) .*/);
		if (familyMatch) {
			device.set("family", `Cisco WLC${familyMatch[1]}`);
		}
	}

	let showRunConfigCommands = cli.command("show run-config commands");
	showRunConfigCommands = showRunConfigCommands.replace(/\r/g, "");
	showRunConfigCommands = showRunConfigCommands.replace(/^ +/mg, "");
	showRunConfigCommands = showRunConfigCommands.replace(/ +$/mg, "");
	showRunConfigCommands = showRunConfigCommands.replace(/\n\n?\n?/mg, "\n");
	if (showRunConfigCommands.match(/^rogue adhoc alert/m)) {
		// Remove dynamic lines
		showRunConfigCommands = showRunConfigCommands
			.replace(/^rogue client (alert|rogue client) .*\r?\n/mg, "");
	}
	config.set("runConfig", showRunConfigCommands);

	const networkInterfaces = {};
	const addInterface = (name) => {
		if (!networkInterfaces[name]) {
			networkInterfaces[name] = {
				name,
				ip: [],
			};
		}
		return networkInterfaces[name];
	};
	const interfaceCreatePattern = /^interface create ([A-Za-z0-9-_]+)/mg;
	let interfaceMatch;
	while (interfaceMatch = interfaceCreatePattern.exec(showRunConfigCommands)) {
		addInterface(interfaceMatch[1]);
	}
	const interfaceAddressPattern = /^interface address (dynamic-interface )?([A-Za-z0-9-_]+) ([0-9\.]+) ([0-9\.]+)/mg;
	while (interfaceMatch = interfaceAddressPattern.exec(showRunConfigCommands)) {
		const networkInterface = addInterface(interfaceMatch[2]);
		networkInterface.ip.push({
			ip: interfaceMatch[3],
			mask: interfaceMatch[4],
			usage: "PRIMARY",
		});
	}
	const interfaceVlanPattern = /^interface vlan ([A-Za-z0-9-_]+) ([0-9]+)/mg;
	while (interfaceMatch = interfaceVlanPattern.exec(showRunConfigCommands)) {
		const networkInterface = networkInterfaces[interfaceMatch[1]];
		const vlanId = parseInt(interfaceMatch[2]);
		if (networkInterface) {
			networkInterface.name += ` (VLAN ${vlanId})`;
		}
	}

	Object.values(networkInterfaces).forEach((networkInterface) => {
		try {
			const showInterfaceDetail = cli.command(`show interface detailed ${networkInterface.name}`);
			const macMatch = showInterfaceDetail.match(/^MAC Address\.+ ([0-9a-f:]+)/m);
			if (macMatch) {
				networkInterface.mac = macMatch[1];
			}
		}
		catch (error) {
			cli.debug(`Cannot show interface details for ${networkInterface.name}`);
		}
		device.add("networkInterface", networkInterface);
	});
};

function runCommands(command) {
	
}

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	for (let t in trap) {
		// Config saved trap
		if (trap[t] === "1.3.6.1.4.1.14179.2.6.3.23") {
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.9.") && sysDesc.match(/^Cisco Controller/)) {
		return true;
	}
	return false;
}
