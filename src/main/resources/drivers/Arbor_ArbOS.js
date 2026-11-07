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
	name: "ArborArbOS",
	description: "Arbor ArbOS 7.2+",
	author: "M. Poussin",
	version: "1.0"
};

var Config = {
	"systemVersion": {
		type: "Text",
		title: "System version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"systemConfiguration": {
		type: "LongText",
		title: "System Configuration",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"systemHardware": {
		type: "LongText",
		title: "System Hardware",
		comparable: true,
		searchable: false,
		checkable: true
	},
	"systemPackages": {
		type: "LongText",
		title: "System Packages",
		comparable: true,
		searchable: false,
		checkable: true

	}
};

var Device = {
	"memorySize": {
		type: "Numeric",
		title: "Memory size (MB)",
		searchable: true
	},
	"configurationCommitted": {
		type: "Binary",
		title: "Configuration committed",
		searchable: true
	}
};

var CLI = {
	ssh: {
		macros: {
			exec: {
				options: ["exec"],
				target: "exec"
			}
		},
		pager: {
			match: /^\[more\]$/,
			response: " "
		},
	},
	exec: {
		prompt: /^([A-Za-z0-9\.\-_]+@[A-Za-z\-_0-9\.]+):\/#/,
	}
};

function snapshot(cli, device, config) {
	const systemHardwareCleanup = function (config) {
		config = config.replace(/^Boot time.*$\n/mg, "");
		config = config.replace(/^Load averages.*$\n/mg, "");
		return config;
	};

	cli.macro("exec");

	// Device type
	device.set("networkClass", "SERVER");
	device.set("family", "Arbor ArbOS");

	// System version
	const systemVersionResult = cli.command("system version");
	systemVersion = systemVersionResult.match(/Version: (.*)/m);
	if (systemVersion != null) {
		config.set("systemVersion", systemVersion[1]);
		device.set("softwareVersion", systemVersion[1]);
	}

	// System packages
	const systemPackagesResult = cli.command("system files show");
	config.set("systemPackages", systemPackagesResult);

	// System hardware
	const systemHardwareResult = systemHardwareCleanup(cli.command("system hardware").replace(/\r\n/g, "\n"));
	config.set("systemHardware", systemHardwareResult);

	// Total memory size
	const memoryPattern = /Memory Device: (.*) MB .*/mg;
	let memory = 0;
	let memoryMatch;
	while (memoryMatch = memoryPattern.exec(systemHardwareResult)) {
		memory += parseInt(memoryMatch[1]);
	}
	device.set("memorySize", memory);

	// Serial number
	const serialMatch = systemHardwareResult.match(/Serial Number: (.*)/m);
	if (serialMatch != null) {
		device.set("serialNumber", serialMatch[1]);
	}

	// System configuration
	const systemConfiguration = cli.command("config show");
	config.set("systemConfiguration", systemConfiguration);

	// Hostname
	const hostname = systemConfiguration.match(/^system name set (.+)$/m);
	if (hostname != null) {
		device.set("name", hostname[1]);
	}

	// System location information
	const location = systemConfiguration.match(/^services sp device edit .* snmp location set (.+)$/m);
	if (location != null) {
		device.set("location", location[1]);
	}

	// System contact information
	const contact = systemConfiguration.match(/^services sp preferences support_email set (.+)$/m);
	if (contact != null) {
		device.set("contact", contact[1]);
	}

	// Check if the configuration has been committed (check for existing diff)
	const configDiffResult = cli.command("config diff").replace(/\r\n/g, "\n");
	if (configDiffResult.split("\n").length === 1) {
		device.set("configurationCommitted", true);
	} else {
		device.set("configurationCommitted", false)
	}

	// Gather network interfaces information
	const interfaces = cli.command("ip interfaces show").replace(/\r\n/g, "\n").trim().split("\n\n");
	for (let interface of interfaces) {
		const ifName = interface.split(" ")[0];
		const ifMac = interface.match(/Hardware: ([0-9a-fA-F]{2}\:[0-9a-fA-F]{2}\:[0-9a-fA-F]{2}\:[0-9a-fA-F]{2}\:[0-9a-fA-F]{2}\:[0-9a-fA-F]{2})/)[1];
		const ifStatus = interface.match(/.* Interface is (\w*),.*/);
		//const ifSpeed = interface.match(/Status: (.*) .*\n/); // not used
		const ifIPv4 = interface.match(/Inet: (\d+\.\d+\.\d+\.\d+) netmask (\d+\.\d+\.\d+\.\d+) .*/);
		const ifIPv6 = interface.match(/Inet6: ([0-9A-Fa-f:]+) prefixlen (\d+)/)

		const networkInterface = {
			name: ifName,
			mac: ifMac,
			ip: []
		};

		if (ifStatus[1] === "UP") {
			networkInterface.enabled = true;
		} else {
			networkInterface.enabled = false;
		}

		if (ifIPv4) {
			const ipv4 = {
				ip: ifIPv4[1],
				mask: ifIPv4[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ipv4);
		}

		if (ifIPv6) {
			const ipv6 = {
				ipv6: ifIPv6[1],
				mask: parseInt(ifIPv6[2]),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ipv6);
		}

		device.add("networkInterface", networkInterface);
	}
}

function runCommands(command) {

}

function analyzeSyslog(message) {
	if (message.match(/CONF-WRITE-END/)) {
		return true;
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 16) == "1.3.6.1.4.1.9694" && sysDesc.match(/^Peakflow.*/)) {
		return true;
	}
	return false;
}
