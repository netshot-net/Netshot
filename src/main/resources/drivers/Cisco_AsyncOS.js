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
	name: "CiscoAsyncOS",
	description: "Cisco AsyncOS",
	author: "Netshot Team",
	version: "0.4"
};

const Config = {
	"aoVersion": {
		type: "Text",
		title: "AsyncOS version",
		comparable: true,
		searchable: true
	},
	"configuration": {
		type: "LongText",
		title: "Configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "<!-- Configuration (taken on %when%): -->",
			post: "<!-- End of configuration -->"
		}
	},
};

const Device = {
	"cpuCount": {
		type: "Numeric",
		title: "Number of CPUs",
		searchable: true
	},
	"memory": {
		type: "Numeric",
		title: "System Memory (MB)",
		searchable: true
	},
};


/**
 * The pager cannot be disabled. To hide the next-page prompt,
 * the device emits \r + spaces + \r, with the prompt being
 * at the beginning of a line based on terminal width wrapping.
 */
const CLI = {
	ssh: {
		config: {
			terminal: {
				pty: true,
				cols: 0xffffff,
				rows: 0xffffff,
			},
		},
		macros: {
			oper: {
				options: [ "oper" ],
				target: "oper"
			},
		},
	},
	oper: {
		prompt: /^([a-zA-Z0-9-_.]+|\((Cluster|Machine) [a-zA-Z0-9-_.]+\) ?)> $/,
		error: /^Unknown command/m,
		pager: {
			match: /-Press Any Key For More-$/,
			response: " "
		},
		macros: {
			commit: {
				cmd: "commit",
				options: [ "oper", "commitSaveConfirm" ],
				target: "oper",
			},
			clear: {
				cmd: "clear",
				options: [ "oper", "clearConfirm" ],
				target: "oper",
			},
		},
	},
	commitSaveConfirm: {
		prompt: /Do you want to save the current configuration for rollback\?/,
		macros: {
			auto: {
				cmd: "y",
				options: [ "oper" ],
			},
		},
	},
	clearConfirm: {
		prompt: /Are you sure you want to clear all changes since the last commit\?/,
		macros: {
			auto: {
				cmd: "y",
				options: [ "oper" ],
			},
		},
	}
};


function snapshot(cli, device, config) {

	cli.macro("oper");

	const getConfig = (passOption) => {
		/**
		 * showconfig example:
		 *   esa> showconfig
		 *  
		 *   Choose the passphrase option:
		 *   1. Mask passphrases (Files with masked passphrases cannot be loaded using loadconfig command)
		 *   2. Encrypt passphrases
		 *   [1]>
		 */
		const option = passOption === "mask" ? "1" : "2";
		cli.command("showconfig", {
			mode: {
				prompt: /^\[1\]>/,
			},
		});
		const output = cli.command(option);
		return output
			.replace(/.*?(<\?xml)/s, "$1");
	};

	const configuration = getConfig();
	config.set("configuration", configuration);

	if (typeof config.computeHash === "function") {
		// Use the configuration without passwords (and without time line) to compute hash
		const configNoPass = getConfig("mask").replace(/^\s*Current Time: .*/mg, "");
		config.computeHash(configNoPass);
	}

	device.set("networkClass", "SERVER");

	const hostnameMatch = configuration.match(/<hostname>(.+?)<\/hostname>/);
	if (hostnameMatch) {
		device.set("name", hostnameMatch[1]);
	}

	const locationMatch = configuration.match(/<syslocation>(.+?)<\/syslocation>/);
	device.set("location", locationMatch ? locationMatch[1] : "");
	const contactMatch = configuration.match(/<syscontact>(.+?)<\/syscontact>/);
	device.set("contact", contactMatch ? contactMatch[1] : "");

	const version = cli.command("version");

	const versionMatch = version.match(/^Version: ([0-9.-]+)/m);
	if (versionMatch) {
		device.set("softwareVersion", versionMatch[1]);
		config.set("aoVersion", versionMatch[1]);
	}

	const cpuMatch = version.match(/^CPUs:.* ([0-9]+) allocated/m);
	if (cpuMatch) {
		device.set("cpuCount", parseInt(cpuMatch[1]));
	}
	const memoryMatch = version.match(/^Memory:.* ([0-9]+) MB allocated/m);
	if (memoryMatch) {
		device.set("memory", parseInt(memoryMatch[1]));
	}

	const modelMatch = version.match(/^Model: (.+)/m);
	device.set("family", modelMatch ? `Cisco ${modelMatch[1]}` : "AsyncOS device");
	const serialMatch = version.match(/^Serial #: ([0-9A-Z-]+)/m);
	if (serialMatch) {
		device.set("serialNumber", serialMatch[1]);
	}
	if (serialMatch && modelMatch) {
		const module = {
			slot: "Appliance",
			partNumber: modelMatch[1],
			serialNumber: serialMatch[1]
		};
		device.add("module", module);
	}

	// Interfaces
	const interfacesMatch = configuration.match(/<interfaces>(.*?)<\/interfaces>/s);
	if (interfacesMatch) {
		const interfacesConfig = interfacesMatch[1];
		const interfacePattern = /<interface>(.*?)<\/interface>/sg;
		while (true) {
			const interfaceMatch = interfacePattern.exec(interfacesConfig);
			if (!interfaceMatch) break;
			const interfaceConfig = interfaceMatch[1];
			const nameMatch = interfaceConfig.match(/<interface_name>(.+?)<\/interface_name>/);
			if (!nameMatch) continue;
			const networkInterface = {
				name: nameMatch[1],
				ip: [],
			};
			const ipMatch = interfaceConfig.match(/<ip>([0-9.]+)<\/ip>/);
			const maskMatch = interfaceConfig.match(/<netmask>([0-9]+)<\/netmask>/);
			if (ipMatch && maskMatch) {
				const ip = {
					ip: ipMatch[1],
					mask: parseInt(maskMatch[1]),
					usage: "PRIMARY"
				};
				networkInterface.ip.push(ip);
			}
			device.add("networkInterface", networkInterface);
		}
	}
};

function runCommands(command) {

}

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	return false;
}


function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.15497.1.")
		&& sysDesc.match(/AsyncOS/)) {
		return true;
	}
	return false;
}
