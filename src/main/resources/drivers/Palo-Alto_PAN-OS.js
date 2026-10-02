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
	name: "PaloAltoPANOS",
	description: "Palo Alto PAN-OS",
	author: "Netshot Team",
	version: "2.0"
};

const Config = {
	"panOsVersion": {
		type: "Text",
		title: "PAN-OS version",
		comparable: true,
		searchable: true
	},
	"configuration": {
		type: "LongText",
		title: "Local configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Configuration (taken on %when%):",
			post: "!! End of configuration"
		}
	},
	"mergedConfiguration": {
		type: "LongText",
		title: "Merged configuration",
		comparable: true,
		searchable: true,
		checkable: true,
	},
};

const Device = {};

const CLI = {
	telnet: {
		macros: {
			operational: {
				options: ["username", "password", "operational"],
				target: "operational"
			}
		}
	},
	ssh: {
		config: {
			auth: {
				interactive: {
					interactions: [{
						prompt: /Do you accept .*yes\/no.*/,
						response: "yes",
					}, {
						echo: false,
						response: "$$NetshotPassword$$",
					}]
				}
			}
		},
		macros: {
			operational: {
				options: ["operational"],
				target: "operational"
			}
		}
	},
	username: {
		prompt: /^login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: ["password", "usernameAgain"]
			}
		}
	},
	password: {
		prompt: /^Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: ["usernameAgain", "operational"]
			}
		}
	},
	usernameAgain: {
		prompt: /^login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	operational: {
		prompt: /^([A-Za-z\-_0-9.]+@[a-zA-Z0-9._-]+(\((.*)*\))*> )$/,
		error: /^(Unknown command: .*|Invalid syntax.)/m,
		pager: {
			avoid: "set cli pager off",
			match: /lines [0-9]+-[0-9]+$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure",
				options: [ "configure" ]
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z\-_0-9.]+@[a-zA-Z0-9._-]+(\((.*)*\))*# )$/,
		error: /^(Unknown command: .*|Invalid syntax.)/m,
		clearPrompt: true,
		macros: {
			quit: {
				cmd: "quit",
				options: [ "operational" ],
				target: "operational"
			},
			revert: {
				cmd: "revert config",
				timeout: 60,
				options: [ "configure" ],
				target: "configure"
			},
			commit: {
				cmd: "commit",
				timeout: 120,
				options: [ "configure" ],
				target: "configure"
			}
		}
	}
};


function snapshot(cli, device, config) {

	const configCleanup = (configuration) => {
		const p = configuration.search(/^config/m);
		if (p > 0) {
			return configuration.slice(p);
		}
		return configuration;
	};

	cli.macro("operational");

	const configuration = cli.command("show config running");
	config.set("configuration", configCleanup(configuration));

	try {
		const mergedConfiguration = cli.command("show config merged");
		config.set("mergedConfiguration", configCleanup(mergedConfiguration));
	}
	catch (err) {
		cli.debug("show config merged not supported on this device");
	}

	const status = cli.command("show system info");
	const hostname = status.match(/^hostname: (.*)$/m);
	if (hostname) {
		device.set("name", hostname[1].trim());
	}

	const versionMatch = status.match(/^sw-version: ([0-9]+.*)$/m);
	const familyMatch = status.match(/^family: (.*)$/m);
	const family = familyMatch ? familyMatch[1].trim() : "PanOS device";
	const version = versionMatch ? versionMatch[1].trim() : "Unknown";
	device.set("family", family);
	device.set("softwareVersion", version);
	config.set("panOsVersion", version);

	device.set("networkClass", "FIREWALL");

	const serial = status.match(/^serial: (.*)$/m);
	if (serial) {
		device.add("module", {
			slot: (family === "vm" ? "VM" : "Chassis"),
			partNumber: family,
			serialNumber: serial[1].trim(),
		});
		device.set("serialNumber", serial[1].trim());
	}
	else {
		device.set("serialNumber", "");
	}

	// Example output:
	// name                      id    vsys zone             forwarding               tag    address
	// ------------------- ----- ---- ---------------- ------------------------ ------ ------------------
	// ethernet1/1               16    1    UNTRUST          vr:default               0      192.0.2.1/29
	// ethernet1/7               22    1                     ha                       0      10.0.0.1/24
	const interfaceLines = cli.command("show interface logical").replace(/\r\n/g, "\n").split("\n");
	const headerIndex = interfaceLines.findIndex(
		(line) => line.match(/^\s*name\s+id\s+vsys\s+zone\s+forwarding\s+tag\s+address\s*$/));
	if (headerIndex < 0) {
		return;
	}
	for (const line of interfaceLines.slice(headerIndex + 1)) {
		if (line.match(/^[\s-]*$/)) {
			// Separator or empty line
			continue;
		}
		// The zone column is empty for interfaces without a zone (e.g. HA links),
		// so read name/id/vsys from the start and forwarding/tag/address from the end.
		const fields = line.trim().split(/\s+/);
		if (fields.length < 6) {
			continue;
		}
		const networkInterface = {
			name: fields[0],
			ip: [],
			virtualDevice: fields[2],
		};
		const address = fields[fields.length - 1];
		const ipv4Match = address.match(/^(\d+\.\d+\.\d+\.\d+)\/(\d+)$/);
		const ipv6Match = address.match(/^([0-9A-Fa-f]*:[0-9A-Fa-f:]*)\/(\d+)$/);
		if (ipv4Match) {
			networkInterface.ip.push({
				ip: ipv4Match[1],
				mask: parseInt(ipv4Match[2]),
				usage: "PRIMARY",
			});
		}
		else if (ipv6Match) {
			networkInterface.ip.push({
				ipv6: ipv6Match[1],
				mask: parseInt(ipv6Match[2]),
				usage: "PRIMARY",
			});
		}

		const macOutput = cli.command(`show interface ${networkInterface.name} | match "Port MAC"`);
		const macMatch = macOutput.match(/Port MAC address ((?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2})/);
		if (macMatch) {
			networkInterface.mac = macMatch[1];
		}
		device.add("networkInterface", networkInterface);
	}
}

function analyzeSyslog(message) {
	return !!message.match(/Commit job succeeded for user (.*)/);

}

function analyzeTrap(trap, debug) {
	const logOid = "1.3.6.1.4.1.25461.2.1.3.1.304";
	return typeof trap[logOid] === "string" && trap[logOid].match(/Commit job succeeded for user (.*)/);
}


function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.substring(0, 22) === "1.3.6.1.4.1.25461.2.3."
		&& sysDesc.match(/^Palo Alto.*/);
}
