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
	name: "OracleAcmePacketOS",
	description: "Oracle Acme Packet OS",
	author: "Netshot Team",
	version: "1.1"
};

var Config = {
	"acmeVersion": {
		type: "Text",
		title: "Acme Packet OS version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"kernelVersion": {
		type: "Text",
		title: "Linux kernel version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"runningConfigVersion": {
		type: "Numeric",
		title: "Running configuration version",
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

var Device = {
	"platform": {
		type: "Text",
		title: "Platform",
		searchable: true,
		checkable: true,
	},
	"hypervisor": {
		type: "Text",
		title: "Hypervisor",
		searchable: true,
		checkable: true,
	},
	"datapath": {
		type: "Text",
		title: "Datapath",
		searchable: true,
		checkable: true,
	},
	"totalMemorySize": {
		type: "Numeric",
		title: "Total memory size (MB)",
		searchable: true,
		checkable: true,
	},
};

var CLI = {
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
	disable: {
		prompt: /^(\*{0,2}[A-Za-z\-_0-9\.\/]+>) $/,
		error: /^% (.*)/m,
		macros: {
			enable: {
				cmd: "enable",
				options: [ "enable", "disable" ],
				target: "enable"
			},
			configure: {
				cmd: "enable",
				options: [ "enable", "disable" ],
				target: "configure"
			}
		}
	},
	enable: {
		prompt: /^(\*{0,2}[A-Za-z\-_0-9\.\/]+#) $/,
		error: /^% (.*)/m,
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "enable", "configure" ],
				target: "configure"
			},
		}
	},
	configure: {
		prompt: /^(\*{0,2}[A-Za-z\-_0-9\.\/]+\([0-9\-a-zA-Z]+\)#) $/,
		error: /^% (.*)/m,
		clearPrompt: true,
		macros: {
			exit: {
				cmd: "exit",
				options: [ "enable", "configure" ],
				target: "enable"
			}
		}
	}
};

function snapshot(cli, device, config) {

	const ipv6MaskToPrefix = (mask) => {
		// Expand ::
		const parts = mask.split("::");
		const left = parts[0] ? parts[0].split(":") : [];
		const right = parts[1] ? parts[1].split(":") : [];
		
		const missing = 8 - (left.length + right.length);
		const full = [
			...left,
			...Array(missing).fill("0"),
			...right
		];

		// Count bits
		let len = 0;
		for (let h of full) {
			let num = parseInt(h || "0", 16);
			let bin = num.toString(2).padStart(16, "0");
			for (let b of bin) {
				if (b === "1") len++;
				else return len; // stop at first 0
			}
		}
		return len;
	}

	cli.macro("enable");

	const runningConfig = cli.command("show running-config");
	config.set("runningConfig", runningConfig);

	for (const intf of cli.findSections(runningConfig, /^system-config/m)) {
		const nameMatch = intf.config.match(/^[ \t]+hostname +(.+)/m);
		device.set("name", nameMatch ? nameMatch[1] : "noname");
		const locationMatch = intf.config.match(/^[ \t]+(?:location|mib-system-location) +(.+)/m);
		device.set("location", locationMatch ? locationMatch[1] : "");
		const contactMatch = intf.config.match(/^[ \t]+mib-system-contact +(.+)/m);
		device.set("contact", contactMatch ? contactMatch[1] : "");
	}

	const showLinuxUname = cli.command("show linux uname");
	const hnameMatch = showLinuxUname.match(/Linux ([A-Za-z0-9.-_]+) [0-9]+/);
	if (hnameMatch) {
		// Could be more accurate than the one from the config
		device.set("name", hnameMatch[1]);
	}

	const changePattern = /^[ \t]+last-modified-by +(.+)\r?\n +last-modified-date +([0-9]{4}-[0-9]{2}-[0-9]{2}) ([0-9]{2}:[0-9]{2}:[0-9]{2})/mg;
	const lastChange = {
		author: null,
		date: 0,
	};
	while (true) {
		const match = changePattern.exec(runningConfig);
		if (!match) break;
		const dateStr = `${match[2]}T${match[3]}`;
		const date = Date.parse(dateStr);
		if (isNaN(date)) {
			cli.debug(`Unable to parse date '${dateStr}', ignoring`);
			continue;
		}
		if (date > lastChange.date) {
			lastChange.date = date;
			lastChange.author = match[1];
		}
	}
	if (lastChange.author) {
		config.set("author", lastChange.author);
	}

	device.set("networkClass", "VOICEGATEWAY");
	device.set("family", "Acme Packet device");

	const displayRunningCfgVersion = cli.command("display-running-cfg-version");
	const cfgVersionMatch = displayRunningCfgVersion.match(/version is ([0-9]+)/m);
	const runningConfigVersion = cfgVersionMatch ? parseInt(cfgVersionMatch[1]) : 0;
	config.set("runningConfigVersion", runningConfigVersion);

	const showVersion = cli.command("show version");
	const versionMatch = showVersion.match(/Acme Packet ([0-9]+|OS VM) ([A-Z0-9.]+( Patch [0-9]+)?)/m);
	const version = versionMatch ? versionMatch[2] : "Unknown";
	device.set("softwareVersion", version);
	config.set("acmeVersion", version);

	const family = versionMatch ? versionMatch[1] : "device";
	device.set("family", `Acme Packet ${family}`);

	config.computeHash(runningConfig, "" + runningConfigVersion, version);

	
	const showPlatform = cli.command("show platform");
	const platformMatch = showPlatform.match(/^Platform +:[ \t]+(.+)/m);
	device.set("platform", platformMatch ? platformMatch[1] : "");
	const hypervisorMatch = showPlatform.match(/^Hypervisor +:[ \t]+(.+)/m);
	device.set("hypervisor", hypervisorMatch ? hypervisorMatch[1] : "");
	const datapathMatch = showPlatform.match(/^Datapath +:[ \t]+(.+)/m);
	device.set("datapath", datapathMatch ? datapathMatch[1] : "");

	const showMemoryUsage = cli.command("show memory usage");
	const memoryMatch = showMemoryUsage.match(/Total Memory:[ \t]+([0-9]+) MB/m);
	if (memoryMatch) {
		device.set("totalMemorySize", parseInt(memoryMatch[1]));
	}

	const showVersionBoot = cli.command("show version boot");

	const idpromMatch = showVersionBoot.match(/^Contents of Main Board IDPROM\r?\n(([ \t]+.*\r?\n)*)/m);
	if (idpromMatch) {
		// Hardware platform
		const idpromInfo = idpromMatch[1];
		const serialNumberMatch = idpromInfo.match(/^[ \t]+Serial Number:[ \t]+(.+)/m);
		const partNumberMatch = idpromInfo.match(/^[ \t]+Acme Packet Part Number:[ \t]+(.+)/m);
		const module = {
			slot: "Main Board",
			serialNumber: serialNumberMatch ? serialNumberMatch[1] : "",
			partNumber: partNumberMatch ? partNumberMatch[1] : "",
		};
		device.add("module", module);
		device.set("serialNumber", module.serialNumber);
	}
	else if (family === "OS VM") {
		// VM
		const serialNumberMatch = showVersionBoot.match(/^[ \t]+Serial Number:[ \t]+(.+)/m);
		const module = {
			slot: "VM",
			serialNumber: serialNumberMatch ? serialNumberMatch[1] : "",
			partNumber: "Acme Packet OS VM",
		};
		device.add("module", module);
		device.set("serialNumber", module.serialNumber);
	}

	// Extract interface descriptions from running config
	const intfDescs = {};
	for (const intf of cli.findSections(runningConfig, /^network-interface/m)) {
		const nameMatch = intf.config.match(/^[ \t]+name +(.+)/m);
		const descMatch = intf.config.match(/^[ \t]+description +(.+)/m);
		if (nameMatch && descMatch) {
			intfDescs[nameMatch[1]] = descMatch[1];
		}
	}

	const showInterfaces = cli.command("show interfaces");
	const intfs = cli.findSections(showInterfaces, /^([a-zA-Z0-9]+)(:| ).*/m);
	for (const intf of intfs) {
		const flagMatch = intf.config.match(/^[ \t]+Flags: (\(0x.+\) )?(.+)/m);
		if (!flagMatch) continue;
		const networkInterface = {
			name: intf.match[1],
			ip: [],
		};
		if (intfDescs[networkInterface.name]) {
			networkInterface.description = intfDescs[networkInterface.name];
		}
		const adminMatch = intf.config.match(/^[ \t]+Admin State:[ \t]+(.+)/m);
		if (adminMatch && adminMatch[1] !== "enabled") {
			networkInterface.enabled = false;
		}

		const macMatch = intf.config.match(/^[ \t]+Ethernet address is ([0-9a-f:]+)/m);
		if (macMatch) {
			networkInterface.mac = macMatch[1];
		}

		const knownIps = [];

		const ipPattern = /^[ \t]+(?:Internet address|inet is): ([0-9a-f.:]+) +Vlan: [0-9]+\r?\n +(?:Broadcast Address: .*\r?\n)? +Netmask: ([0-9a-f.:]+)/mg;
		while (true) {
			const ipMatch = ipPattern.exec(intf.config);
			if (!ipMatch) break;
			if (ipMatch[1].match(/:/)) {
				networkInterface.ip.push({
					ipv6: ipMatch[1],
					mask: ipv6MaskToPrefix(ipMatch[2]),
					usage: "PRIMARY",
				});
			}
			else {
				networkInterface.ip.push({
					ip: ipMatch[1],
					mask: ipMatch[2],
					usage: "PRIMARY",
				});
			}
			knownIps.push(ipMatch[1]);
		}

		const hipPattern = /^[ \t]+Hip IP: (.+)/mg;
		while (true) {
			const hipMatch = hipPattern.exec(intf.config);
			if (!hipMatch) break;
			const hipList = hipMatch[1].split(/, /);
			for (hip of hipList) {
				if (knownIps.includes(hip)) {
					// Avoid duplicates
					continue;
				}
				knownIps.push(hip);
				const ipV = hip.match(/:/) ? "ipv6" : "ip";
				networkInterface.ip.push({
					[ipV]: hip,
					mask: ipV === "ipv6" ? 128 : 32,
					usage: "SECONDARY",
				});
			}
		}
		device.add("networkInterface", networkInterface);
	}


};

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.9148.1.") && sysDesc.match(/^Acme Packet .*/)) {
		return true;
	}
	return false;
}
