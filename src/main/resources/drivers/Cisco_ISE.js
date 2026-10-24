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
	name: "CiscoISE",
	description: "Cisco ISE",
	author: "Netshot Team",
	version: "1.1"
};

var Config = {
	"iseVersion": {
		type: "Text",
		title: "ISE version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"adeosVersion": {
		type: "Text",
		title: "ADE-OS version",
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
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
	"backupEncryptKey": {
		type: "Text",
		title: "Backup encryption key",
	},
};

var Device = {
	"memorySize": {
		type: "Numeric",
		title: "Memory size (MB)",
		searchable: true,
		checkable: true,
	},
	"cpuCount": {
		type: "Numeric",
		title: "CPU count",
		searchable: true,
		checkable: true,
	},
	"iseRoles": {
		type: "Text",
		title: "ISE roles",
		searchable: true,
		checkable: true,
	},
};

var Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive (Admin nodes only)",
		default: true,
	},
};

var CLI = {
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
	exec: {
		prompt: /^([A-Za-z\-_0-9\.\/]+#)$/,
		error: /^(syntax error: .*)/m,
		pager: {
			avoid: "screen-length 0",
			match: /^--More--$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "exec", "configure" ],
				target: "configure"
			},
		}
	},
	configure: {
		prompt: /^([A-Za-z\-_0-9\.\/]+\(conf[0-9\-a-zA-Z]+\)#)$/,
		error: /^(syntax error: .*)/m,
		clearPrompt: true,
		macros: {
			end: {
				cmd: "end",
				options: [ "exec" ],
				target: "exec"
			}
		}
	}
};

function snapshot(cli, device, config) {

	cli.macro("exec");
	const runningConfig = cli.command("show running-config");
	config.set("runningConfig", runningConfig);

	const showVersion = cli.command("show version");

	const hostnameMatch = showVersion.match(/^Hostname: (.+)/m);
	if (hostnameMatch) {
		device.set("name", hostnameMatch[1]);
	}

	const aosVersionMatch = showVersion.match(/ADE-OS Build Version: (.+)/m);
	if (aosVersionMatch) {
		config.set("adeosVersion", aosVersionMatch[1]);
	}
	const iseVersionMatch = showVersion.match(/Cisco Identity Services Engine\r?\n-+\r?\nVersion *: +(.+)/m);
	if (iseVersionMatch) {
		const version = iseVersionMatch[1];
		config.set("iseVersion", version);
		device.set("softwareVersion", version);
	}

	device.set("networkClass", "SERVER");
	device.set("family", "Unknown ISE");

	// Infer the node roles from the ISE processes: only Admin nodes run the Indexing Engine,
	// only Monitoring nodes run the M&T Session Database; Policy Service nodes run neither.
	// roles stays empty if the process list can't be read (application not running...).
	const roles = [];
	try {
		const showAppStatus = cli.command("show application status ise");
		if (showAppStatus.match(/^Application Server\s+running/m)) {
			if (showAppStatus.match(/^ISE Indexing Engine\s+running/m)) {
				roles.push("Admin");
			}
			if (showAppStatus.match(/^M&T Session Database\s+running/m)) {
				roles.push("Monitoring");
			}
			if (roles.length === 0) {
				roles.push("Policy Service");
			}
			device.set("iseRoles", roles.join(", "));
		}
	}
	catch (err) {
		cli.debug("Couldn't get the ISE application status");
	}

	const locationMatch = runningConfig.match(/^snmp-server location (.*)/m);
	device.set("location", locationMatch ? locationMatch[1] : "");
	const contactMatch = runningConfig.match(/^snmp-server contact (.*)/m);
	device.set("contact", contactMatch ? contactMatch[1] : "");

	const showMemory = cli.command("show memory");
	const memoryMatch = showMemory.match(/memory total memory: +([0-9]+) kB/m);
	if (memoryMatch) {
		device.set("memorySize", Math.round(parseInt(memoryMatch[1]) / 1024));
	}
	const showCpu = cli.command("show cpu");
	let cpuCount = 0;
	const cpuPattern = /processor\s*: +([0-9]+)/mg;
	while (true) {
		const cpuMatch = cpuPattern.exec(showCpu);
		if (!cpuMatch) break;
		cpuCount += 1;
	}
	device.set("cpuCount", cpuCount);

	const showInventory = cli.command("show inventory");
	const inventoryPattern = /NAME: \"(.*)\",\s+DESCR: \"(.*)\"\s+PID: (.*?) *, +VID: (.*),\s+SN: (.*)/g;
	while (true) {
		const match = inventoryPattern.exec(showInventory);
		if (!match) break;
		const module = {
			slot: match[1],
			partNumber: match[3],
			serialNumber: match[5]
		};
		device.add("module", module);
		if (module.slot.match(/[Cc]hassis/)) {
			device.set("serialNumber", module.serialNumber);
		}
		if (module.partNumber.match(/ISE-VM/)) {
			device.set("family", "Cisco ISE VM");
		}
		const pidMatch = module.partNumber.match(/SNS-([0-9][0-9])[0-9][0-9]-/);
		if (pidMatch) {
			device.set("family", `Cisco ISE SNS${pidMatch[1]}00`);
		}
	}

	const interfaces = cli.findSections(runningConfig, /^interface (.+)/m);
	for (const intf of interfaces) {
		const networkInterface = {
			name: intf.match[1],
			ip: [],
		};

		const ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+) (\d+\.\d+\.\d+\.\d+)/mg;
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

		const ipv6Pattern = /^ *ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg;
		while (true) {
			const ipMatch = ipv6Pattern.exec(intf.config);
			if (!ipMatch) break;
			const ip = {
				ipv6: ipMatch[1],
				mask: ipMatch[2],
				usage: "PRIMARY"
			};
			networkInterface.ip.push(ip);
		}

		if (intf.config.match(/^ *shutdown/m)) {
			networkInterface.enabled = false;
		}

		try {
			const showInterface = cli.command(`show interface ${networkInterface.name}`);
			const macMatch = showInterface.match(/ether ([0-9a-f:]+)/);
			if (macMatch) {
				networkInterface.mac = macMatch[1];
			}
		}
		catch (err) {
			cli.debug(`Couldn't get MAC info from interface ${networkInterface.name}`);
		}
		device.add("networkInterface", networkInterface);
	}

	let fullBackupTaken = false;
	if (device.options && !device.options.fullBackup) {
		cli.debug("Full backup disabled by device option, the full backup won't be taken");
	}
	else if (roles.length > 0 && !roles.includes("Admin")) {
		cli.debug("Not an ISE Admin node, the full backup won't be taken");
	}
	else {
		try {
			const ticket = config.requestUpload();
			const repository = "netshot";
			const backupName = "netshotbackup";
			const encryptPass = "NetshotB4ckup_";

			if (ticket.port !== 22) {
				throw "For Cisco ISE, the SSH server port must be 22.";
			}

			cli.command(`crypto host_key add host ${ticket.host}`);

			try {
				cli.debug("Adding Netshot SFTP repository");
				cli.macro("configure")
				cli.command(`no repository ${repository}`);
				cli.command(`repository ${repository}`);
				cli.command(`url sftp://${ticket.host}/`);
				cli.command(`user ${ticket.username} password plain ${ticket.password}`);
				cli.macro("end");


				cli.debug("Starting backup");
				const backupOutput = cli.command(`backup ${backupName} repository ${repository} ise-config encryption-key plain ${encryptPass}`, {
					timeout: 600000,
				});
				const backupError = backupOutput.match(/^%\s*Error:\s*(.+?)\s*$/m);
				if (backupError) {
					throw `ISE backup failed: ${backupError[1]}`;
				}
				const uploadResult = config.awaitUpload(ticket.id);
				if (uploadResult.files.length !== 1) {
					throw `Invalid number of files (${uploadResult.files.length}) received by Netshot server`;
				}
				const file = uploadResult.files[0];
				config.commitUpload(ticket.id, file.id, "backupArchive");
				config.set("backupEncryptKey", encryptPass);
				fullBackupTaken = true;
			}
			finally {
				cli.debug("Removing repository");
				try {
					cli.macro("configure");
					cli.command(`no repository ${repository}`);
					cli.macro("end");
				}
				catch (e2) {
					cli.debug("Error while removing the repository");
				}
			}
		}
		catch (e) {
			const error = String(e);
			if (error.match(/server is not running/)) {
				cli.debug("Netshot SSH server is disabled, the full backup won't be taken");
			}
			else if (error.match(/not supported on this node type/)) {
				cli.debug("Configuration backup not supported on this ISE node, the full backup won't be taken");
			}
			else {
				throw e;
			}
		}
	}

	if (!fullBackupTaken) {
		config.computeHash(runningConfig);
	}

};

function analyzeSyslog(message) {
	if (message.match(/CONFIGURATION MODIFIED.*username=(.+?),/)) {
		return true;
	}
	return false;
}

/**
 * Not supported
 */
function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.9.1.")
		&& sysDesc.match(/Cisco Identity Services Engine/)) {
		return true;
	}
	return false;
}
