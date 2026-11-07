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
	name: "HPEComware",
	description: "HPE Comware OS",
	author: "Netshot Team",
	version: "0.4"
};

const Config = {
	"cwVersion": {
		type: "Text",
		title: "Comware version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Comware version:",
			preLine: "##  "
		}
	},
	"bootVersion": {
		type: "Text",
		title: "Boot version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"currentConfig": {
		type: "LongText",
		title: "Current configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Current configuration (taken on %when%):",
			post: "## End of current configuration"
		}
	},
	"release": {
		type: "Text",
		title: "Release",
		comparable: true,
		searchable: true,
		checkable: true
	},
};

const Device = {
};

const CLI = {
	telnet: {
		macros: {
			user: {
				options: [ "username", "password", "user" ],
				target: "user"
			},
			system: {
				options: [ "username", "password", "user", "system" ],
				target: "system"
			}
		}
	},
	ssh: {
		macros: {
			user: {
				options: [ "user" ],
				target: "user"
			},
			system: {
				options: [ "user", "system" ],
				target: "system"
			}
		}
	},
	username: {
		prompt: /^Username:$/,
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
				options: [ "usernameAgain", "user" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^Username:$/,
		fail: "Authentication failed - Telnet authentication failure."
	},

	user: {
		prompt: /^\x00?(<[A-Za-z\-_0-9\.]+>)$/,
		error: /^Error: /m,
		pager: {
			avoid: "screen-length disable",
			match: /^  *---- More ----$/,
			response: " "
		},
		macros: {
			system: {
				cmd: "system-view",
				options: [ "user", "system" ],
				target: "configure"
			},
			save: {
				cmd: "save",
				options: [ "user", "saveConfirm" ],
				target: "user"
			}
		}
	},
	saveConfirm: {
		prompt: /Are you sure to continue\?/,
		macros: {
			auto: {
				cmd: "y",
				options: [ "user" ]
			}
		}
	},
	
	system: {
		prompt: /^\x00*(\[(~)?[A-Za-z\-_0-9\.]+\])$/,
		error: /^Error: (.*)/m,
		clearPrompt: true,
		macros: {
			user: {
				cmd: "quit",
				options: [ "user", "system" ],
				target: "user"
			},
			commit: {
				cmd: "commit",
				options: [ "system" ],
				target: "system"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	const configCleanup = function(config) {
		return config.replace(/^\!Last configuration .*[\r\n]+/m, "");
	};
	
	cli.macro("user");
	let currentConfig = cli.command("display current-config");
	
	const author = currentConfig.match(/^\!Last configuration was updated .* by (.*)$/m);
	if (author) {
		config.set("author", author[1]);
	}
	currentConfig = configCleanup(currentConfig);
	config.set("currentConfig", currentConfig);

	const hostname = currentConfig.match(/^ *sysname (.*)$/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}

	const displayVersion = cli.command("display version");

	const versionMatch = displayVersion.match(/Comware Software, Version ([0-9\.]+)/);
	const version = versionMatch ? versionMatch[1] : "Unknown";
	device.set("softwareVersion", version);
	config.set("cwVersion", version);
	const releaseMatch = displayVersion.match(/Comware Software.* Release (.*)/);
	if (releaseMatch) {
		config.set("release", releaseMatch[1]);
		device.set("softwareVersion", `${version}/${releaseMatch[1]}`);
	}
	else {
		device.set("release", "");
	}
	const bootVersionMatch = displayVersion.match(/Boot image version: ([0-9\.]+)/m);
	const bootVersion = bootVersionMatch ? bootVersionMatch[1] : "Unknown";
	config.set("bootVersion", bootVersion);

	device.set("networkClass", "SWITCHROUTER");

	// Fix: use "weeks?" to match both singular ("1 week") and plural ("2 weeks")
	const platformMatch = displayVersion.match(/^HPE? (.+?)(-| ).*uptime is [0-9]+ weeks?/m);
	const platform = platformMatch ? platformMatch[1] : "Unknown Comware switch";
	device.set("family", platform);

	const locationMatch = currentConfig.match(/^ *snmp-agent sys-info location (.*)/m);
	device.set("location", locationMatch ? locationMatch[1] : "");
	const contactMatch = currentConfig.match(/^ *snmp-agent sys-info contact (.*)/m);
	device.set("contact", contactMatch ? contactMatch[1] : "");

	const displayDeviceManuinfo = cli.command("display device manuinfo");

	// Parse "display device manuinfo" line by line to handle the following observed variants:
	//   - "Slot X:"          Comware 5 (e.g. 3600, A5500): no leading space, colon after number
	//   - " Slot X CPU 0:"   Comware 7 IRF (e.g. 5130 EI): leading space, CPU suffix, colon
	//   - " Slot X"          Some Comware 7 builds (e.g. 5900 old firmware): no trailing colon
	// Additional considerations:
	//   - First slot header may not be preceded by a blank line (single-slot devices)
	//   - Line endings may be "\r\n" depending on SSH/Telnet session settings
	//   - Sub-components (Fan, Power) may expose their own DEVICE_NAME / DEVICE_SERIAL_NUMBER
	//     fields and must not be captured as module serial numbers
	// Slot names are normalized to "Slot X" (CPU suffix stripped) for consistency
	// across Comware versions and IRF/standalone topologies.
	let currentSlot = null;
	let currentDeviceName = null;
	let inSubComponent = false;
	for (const line of displayDeviceManuinfo.split(/\r?\n/)) {
		// Slot or Chassis header — normalize to "Slot X" / "Chassis X"
		const slotMatch = line.match(/^\s*(Slot|Chassis)\s+(\d+)(?:\s+CPU\s+\d+)?\s*:?\s*$/);
		if (slotMatch) {
			currentSlot = `${slotMatch[1]} ${slotMatch[2]}`;
			currentDeviceName = null;
			inSubComponent = false;
			continue;
		}
		// Fan or Power sub-component header — skip their DEVICE_NAME / DEVICE_SERIAL_NUMBER
		if (line.match(/^\s*(Fan|Power)\s+\d+\s*:?\s*$/)) {
			inSubComponent = true;
			continue;
		}
		if (inSubComponent) continue;
		const deviceNameMatch = line.match(/^\s*DEVICE_NAME\s*:\s*(.+?)\s*$/);
		if (deviceNameMatch) {
			currentDeviceName = deviceNameMatch[1];
			continue;
		}
		const serialMatch = line.match(/^\s*DEVICE_SERIAL_NUMBER\s*:\s*(.+?)\s*$/);
		if (serialMatch && currentSlot) {
			device.add("module", {
				slot: currentSlot,
				partNumber: currentDeviceName || "",
				serialNumber: serialMatch[1],
			});
			// Use the first captured slot serial as the device-level serial number
			// (Slot 1 / lowest member in an IRF stack)
			if (!device.get("serialNumber")) {
				device.set("serialNumber", serialMatch[1]);
			}
		}
	}
	
	const vrfPattern = /^ip vpn-instance (.+)/mg;
	while (true) {
		const match = vrfPattern.exec(currentConfig);
		if (!match) break;
		device.add("vrf", match[1]);
	}
	
	const interfaces = cli.findSections(currentConfig, /^interface ([^ ]+)( .+)?/m);
	for (const intf of interfaces) {
		const networkInterface = {
			name: intf.match[1],
			ip: []
		};
		const description = intf.config.match(/^ *description (.+)/m);
		if (description) {
			networkInterface.description = description[1];
		}
		const vrf = intf.config.match(/^ *ip binding vpn-instance (.+)$/m);
		if (vrf) {
			networkInterface.vrf = vrf[1];
		}
		if (intf.config.match(/^ *portswitcht$/m)) {
			networkInterface.level3 = false;
		}
		if (intf.config.match(/^ *shutdown/m)) {
			networkInterface.enabled = false;
		}
		const ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)( sub)?/mg;
		while (true) {
			const match = ipPattern.exec(intf.config);
			if (!match) break;
			const ip = {
				ip: match[1],
				mask: match[2],
				usage: "PRIMARY"
			};
			if (match[3] === " sub") {
				ip.usage = "SECONDARY";
			}
			networkInterface.ip.push(ip);
		}
		const fhrpPattern = /^ *(vrrp) vrid ([0-9]+) virtual-ip (\d+\.\d+\.\d+\.\d+)/mg;
		while (true) {
			const match = fhrpPattern.exec(intf.config);
			if (!match) break;
			const ip = {
				ip: match[3],
				mask: 32,
				usage: "VRRP"
			};
			networkInterface.ip.push(ip);
		}
		const ipv6Pattern = /^ *ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg;
		while (true) {
			const match = ipv6Pattern.exec(intf.config);
			if (!match) break;
			const ip = {
				ipv6: match[1],
				mask: parseInt(match[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}
		const displayInterface = cli.command(`display interface ${networkInterface.name} | inc address|line`);
		const macAddress = displayInterface.match(/address: ([0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4})/);
		if (macAddress) {
			networkInterface.mac = macAddress[1];
		}
		device.add("networkInterface", networkInterface);
	}

};

function analyzeSyslog(message) {
	// To be confirmed
	if (message.match(/ConfigDestination=running;Configuration is changed/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	// Tested on VSR1000, unable to get a trap on config changes
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.startsWith("1.3.6.1.4.1.25506.11.1.") &&
		sysDesc.match(/Comware Platform Software/);
}
