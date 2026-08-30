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
	name: "CitrixNetscaler",
	description: "Citrix NetScaler",
	author: "Netshot Team",
	version: "4.1"
};

const Config = {
	"nsVersion": {
		type: "Text",
		title: "NetScaler version",
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
			pre: "# Running configuration (taken on %when%):",
			post: "# End of running configuration"
		}
	},
	"nsLicense": {
		type: "LongText",
		title: "License",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "# License status:",
			preLine: "# ",
			post: "# End of license"
		}
	},
	"nsConfigBundle": {
		type: "BinaryFile",
		title: "Config Bundle",
		comparable: false,
		searchable: false,
		checkable: false,
	},
};

var Device = {
	"haNode": {
		type: "Text",
		title: "Other HA node IP",
		searchable: true
	},
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true
	}
};

var Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive",
		default: true,
	},
};

var CLI = {
	ssh: {
		macros: {
			cli: {
				options: [ "cli", "shell" ],
				target: "cli"
			},
			shell: {
				options: [ "cli", "shell" ],
				target: "shell"
			}
		}
	},
	cli: {
		prompt: /^([^\s\r\n]*( \((Primary|Secondary)\))?> )$/,
		error: /^ERROR: (.*)/m,
		pager: {
			avoid: "set cli mode -page OFF",
			match: /^.--More--/,
			response: " "
		},
		macros: {
			shell:  {
				cmd: "shell",
				options: [ "shell" ],
				target: "shell"
			},
			save: {
				cmd: "save ns config",
				options: [ "cli" ],
				target: "cli"
			}
		}
	},
	shell: {
		prompt: /^([^\s\r\n]*# )$/,
		macros: {
			cli: {
				cmd: "exit",
				options: [ "cli" ],
				target: "cli"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	const configCleanup = function(cfg) {
		return cfg
			.replace(/[\r\n]+\s+Done[\r\n\s]+$/, "")
			.replace(/^# Last modified .*/m, "");
	};

	const removeEncryptedParts = function(cfg) {
		return cfg
			.replace(/^(.* )([0-9a-f]+)( \-encrypted \-encryptmethod ENCMTHD.*)$/mg, "");
	}

	const parseInterfaces = function(partition, configuration) {
		const vlanInterfaces = {};
		const ipVlans = {};
		const arpTable = {};

		let match;

		const showArp = cli.command("show arp");
		const arpPattern = /([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)\s+([0-9a-f:]+)\s.*([0-9]+)\s*$/mg;
		while (match = arpPattern.exec(showArp)) {
			arpTable[match[1] + "_" + match[3]] = match[2];
		}

		const bindVlanPattern = /^bind vlan ([0-9]+).*\-ifnum (\S+)/mg;
		while (match = bindVlanPattern.exec(configuration)) {
			vlanInterfaces[match[1]] = match[2];
		}

		const bindVlanIpPattern = /bind vlan ([0-9]+) .*\-IPAddress ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+) ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+).*(\-td ([0-9]+))?/mg;
		while (match = bindVlanIpPattern.exec(configuration)) {
			const td = match[5] ? match[5] : "0";
			ipVlans[match[2] + "_" + match[3] + "_" + td] = match[1];
		}
		const addIp = function(ip, mask, description, td) {
			if (!td) td = "0";
			const networkInterface = {
				name: "",
				ip: [{ ip: ip, mask: mask, usage: "PRIMARY" }],
				virtualDevice: partition,
				vrf: "TD " + td,
				description: description
			};
			const vlan = ipVlans[ip + "_" + mask + "_" + td];
			if (vlan) {
				networkInterface.name = `Vlan${vlan}`;
				if (vlanInterfaces[vlan]) {
					networkInterface.name = `${networkInterface.name} (${vlanInterfaces[vlan]})`;
				}
			}
			const mac = arpTable[ip + "_" + td];
			if (mac) {
				networkInterface.mac = mac;
			}
			device.add("networkInterface", networkInterface);
		}


		const addIpPattern = /add ns ip ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+) ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+).*(\-type ([A-Z]+))?.*(\-td ([0-9]+))?/mg;
		while (match = addIpPattern.exec(configuration)) {
			const td = match[6] ? match[6] : "0";
			const type = match[4] ? match[4] : "NSIP";
			const ip = match[1];
			const mask = match[2];
			addIp(ip, mask, type, td);
			
		}
		const nsIp = configuration.match(/^set ns config \-IPAddress ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+) \-netmask ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)/m);
		if (nsIp) {
			const td = 0;
			const ip = nsIp[1];
			const mask = nsIp[2];
			addIp(ip, mask, "NetScaler IP", td);
		}
	}

	cli.macro("cli");

	let runningConfig = cli.command("show ns runningConfig");
	runningConfig = configCleanup(runningConfig);

	const showNsHostname = cli.command("show ns hostname");
	const hostname = showNsHostname.match(/Hostname:\s+(.+)/);
	if (hostname) {
		device.set("name", hostname[1]);
	}

	const showNsLicense = cli.command("show ns license");
	config.set("nsLicense", showNsLicense);

	const showNsConfig = cli.command("show ns config");
	let saved = false;
	const lastChange = showNsConfig.match(/Last Config Changed Time: (.* [0-9][0-9][0-9][0-9])/);
	const lastSaved  = showNsConfig.match(/Last Config Saved Time: (.* [0-9][0-9][0-9][0-9])/);
	if (lastChange && lastSaved) {
		const lastChangeDate = new Date(lastChange[1]);
		const lastSavedDate = new Date(lastSaved[1]);
		saved = lastSavedDate >= lastChangeDate;
	}
	device.set("configurationSaved", saved);

	const showNsVersion = cli.command("show ns version");
	const version = showNsVersion.match(/NetScaler (NS.+?),/i);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("nsVersion", version[1]);
	}

	device.set("networkClass", "LOADBALANCER");

	const showNsHardware = cli.command("show ns hardware");
	const serialNumber = showNsHardware.match(/Serial no: +(.+)/);
	if (serialNumber) {
		device.set("serialNumber", serialNumber[1]);
	}

	let family = "NetScaler";
	let platform = showNsHardware.match(/Platform: +(.+)/);
	if (platform) {
		platform = platformMatch[1];
		if (platform.match(/Virtual Appliance/)) {
			family = "NetScaler VPX";
		}
		else if (platform.match(/MPX/)) {
			family = "NetScaler MPX";
		}
	}
	device.set("family", family);


	if (serialNumber && platform) {
		device.add("module", { slot: "NetScaler", partNumber: platform, serialNumber: serialNumber });
	}

	const location = runningConfig.match(/^set snmp mib .*\-contact "(.+?)"/m);
	if (location) {
		device.set("location", location[1]);
	}
	else {
		device.set("location", "");
	}
	const contact = runningConfig.match(/^set snmp mib .*\-location "(.+?)"/m);
	if (contact) {
		device.set("contact", contact[1]);
	}
	else {
		device.set("contact", "");
	}

	const haNodePattern = /^add HA node ([0-9]+) ([0-9\.]+)/mg;
	const haNodes = [];
	let haNode;
	while (haNode = haNodePattern.exec(runningConfig)) {
		haNodes.push(haNode[2]);
	}
	device.set("haNode", haNodes.join(", "));

	parseInterfaces("", runningConfig);

	let usePartitions = false;
	const partitionPattern = /^add ns partition (\S+)/mg;
	let partition;
	while (partition = partitionPattern.exec(runningConfig)) {
		usePartitions = true;
		const partitionName = partition[1];
		cli.command(`switch ns partition ${partitionName}`, { clearPrompt: true });
		let partRunningConfig = cli.command("show ns runningConfig");
		partRunningConfig = configCleanup(partRunningConfig);
		runningConfig += "\r\n\r\n" + `### Partition ${partitionName}\r\n`;
		runningConfig += `switch ns partition ${partitionName}\r\n\r\n`;
		runningConfig += partRunningConfig;
		parseInterfaces(partitionName, partRunningConfig);
	}
	config.set("runningConfig", runningConfig);

	// Netshot 0.21+
	const runningConfigNoPass = removeEncryptedParts(runningConfig);
	config.computeHash(runningConfigNoPass, showNsLicense, version);

	// Switch back to default partition, if possible
	try {
		if (usePartitions) {
			cli.command("switch ns partition default");
		}
	}
	catch (e) {
		// Ignore
	}

	// Delete any legacy backup archive
	try {
		cli.command("rm system backup netshot.tgz");
	}
	catch (e) {
		if (String(e).match(/Backup file does not exist/)) {
			// ignore
		}
		else {
			throw e;
		}
	}

	if ((!device.options || device.options.fullBackup) && config.isChangedHash()) {
		// Create a backup archive
		const backupOutput = cli.command("create system backup -level full netshot");
		if (!backupOutput.match(/Done/)) {
			cli.debug(`Result of backup command: ${backupOutput}`);
			throw "Couldn't create Netscaler backup";
		}
		try {
			config.download("nsConfigBundle", "/var/ns_sys_backup/netshot.tgz", { method: "sftp", newSession: true });
		}
		catch (e) {
			throw e;
		}
		finally {
			cli.command("rm system backup netshot.tgz");
		}
	}
};

function analyzeSyslog(message) {
	return !!message.match(/netScalerConfigChange/);
}


/*
 * Trigger a snapshot upon configuration change.
 *
 * NetScaler configuration:
 *  > add snmp trap specific 1.1.1.1 -version V2 -destPort 162 -communityName Netsh01 -severity Unknown -allPartitions ENABLED
 *
 */

function analyzeTrap(trap, debug) {
	return !!trap["1.3.6.1.4.1.5951.4.1.10.2.5.0"];
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.startsWith("1.3.6.1.4.1.5951.") && sysDesc.match(/NetScaler/i)) {
		return true;
	}
	return false;
}
