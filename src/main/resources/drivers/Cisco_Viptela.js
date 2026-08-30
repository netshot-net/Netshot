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
	name: "CiscoViptela",
	description: "Viptela Operating System",
	author: "Netshot Team",
	version: "3.1"
};

var Config = {
	"viptelaVersion": {
		type: "Text",
		title: "Viptela OS version",
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
};

var Device = {
	"personality": {
		type: "Text",
		title: "Personality",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"modelName": {
		type: "Text",
		title: "Model name",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"diskSize": {
		type: "Numeric",
		title: "Disk size (MB)",
		comparable: true,
		checkable: true
	},
	"memorySize": {
		type: "Numeric",
		title: "Memory size (MB)",
		comparable: true,
		checkable: true
	},
};

var Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive (vManage only)",
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
		prompt: /^([A-Za-z\-_0-9\.]+#) $/,
		error: /^syntax error/m,
		pager: {
			avoid: "paginate false",
			match: /^ --More--$/,
			response: " "
		},
		macros: {
			config: {
				cmd: "config",
				options: [ "exec", "config" ],
				target: "config"
			},
			bash: {
				cmd: "vshell",
				options: [ "bash" ],
				target: "bash",
			}
		}
	},
	config: {
		prompt: /^([A-Za-z\-_0-9\.]+\(conf[0-9\-a-zA-Z]+\)#) $/,
		error: /^syntax error/m,
		clearPrompt: true,
		macros: {
			abort: {
				cmd: "abort",
				options: [ "exec", "config" ],
				target: "exec"
			},
			commit: {
				cmd: "commit",
				options: [ "config" ],
				target: "config"
			}
		}
	},
	bash: {
		prompt: /^([A-Za-z\-_0-9\.]+:[\/A–Za–z0–9._-~]+\$ )$/,
		clearPrompt: true,
		macros: {
			execBack: {
				cmd: "exit",
				options: [ "exec" ],
				target: "exec"
			}
		},
	},
};

function snapshot(cli, device, config) {
	
	cli.macro("exec");
	
	device.set("networkClass", "ROUTER");
	device.set("family", "Cisco SD-WAN device");
	
	// For some unfortunate reason, `| display json` is not available for `show system status`
	var showSystemStatus = cli.command("show system status | display xml");
	
	var version = showSystemStatus.match(/<version>(.+)<\/version>/);
	if (version) {
		config.set("viptelaVersion", version[1]);
		device.set("softwareVersion", version[1]);
	}
	
	var doBackup = false;
	var personality = showSystemStatus.match(/<personality>(.+)<\/personality>/);
	if (personality) {
		personality = personality[1];
		device.set("personality", personality);
		if (personality.match(/edge/)) {
			device.set("family", "Cisco SD-WAN vEdge");
		}
		else if (personality.match(/manage/)) {
			device.set("family", "Cisco SD-WAN vManage");
			device.set("networkClass", "SERVER");
			doBackup = true;
		}
		else if (personality.match(/bond/)) {
			device.set("family", "Cisco SD-WAN vBond");
			device.set("networkClass", "SERVER");
		}
		else if (personality.match(/smart/)) {
			device.set("family", "Cisco SD-WAN vSmart");
			device.set("networkClass", "SERVER");
		}
	}
	
	var model = showSystemStatus.match(/<model>(.+)<\/model>/);
	if (model) {
		device.set("modelName", model[1]);
	}
	
	var disk = showSystemStatus.match(/<disk_size>([0-9]+)M<\/disk_size>/);
	if (disk) {
		device.set("diskSize", parseInt(disk[1]));
	}
	
	var memory = showSystemStatus.match(/<mem_total>([0-9]+)<\/mem_total>/);
	if (memory) {
		device.set("memorySize", Math.round(parseInt(memory[1]) / 1024));
	}
	
	try {
		var showInventory = cli.command("show hardware inventory | display json");
		var inventory = JSON.parse(showInventory);
		var items = inventory.data["viptela-hardware:hardware"].inventory;
		for (var i in items) {
			var item = items[i];
			device.add("module", {
				slot: item["hw-type"],
				partNumber: item["part-number"],
				serialNumber: item["serial-number"],
			});
		}
	}
	catch (error) {
		cli.debug("Cannot get or parse inventory: " + error);
	}
	
	
	var runningConfig = cli.command("show running-config");
	config.set("runningConfig", runningConfig);
	
	
	var showRunHostName = cli.command("show running-config system host-name | display json");
	try {
		var hostName = JSON.parse(showRunHostName).data["viptela-system:system"]["host-name"];
		device.set("name", hostName);
	}
	catch (error) {
		cli.debug("Cannot get or parse host-name: " + error);
	}
	

	var showRunSnmp = cli.command("show running-config snmp | display json");
	try {
		var location = JSON.parse(showRunSnmp).data["viptela-snmp:snmp"].location;
		device.set("location", location);
	}
	catch (error) {
		cli.debug("Cannot get or parse location: " + error);
	}
	try {
		var contact = JSON.parse(showRunSnmp).data["viptela-snmp:snmp"].contact;
		device.set("contact", contact);
	}
	catch (error) {
		cli.debug("Cannot get or parse contact: " + error);
	}


	
	var networkInterfaces = {};
	var showInterface = cli.command("show interface | notab");
	var interfaceDetails = cli.findSections(showInterface, /^interface vpn ([0-9]+) interface (.+) af-type ipv4/m);
	for (var i in interfaceDetails) {
		var ifName = interfaceDetails[i].match[2];
		var vpn = interfaceDetails[i].match[1];
		var ifConfig = interfaceDetails[i].config;
		var networkInterface = {
			name: ifName,
			vrf: vpn,
			ip: []
		};
		if (ifConfig.match(/^ +if-admin-status +Down/)) {
			networkInterface.enabled = false;
		}
		var mac = ifConfig.match(/^ +hwaddr +([0-9a-fA-F:]+)/m);
		if (mac) {
			networkInterface.mac = mac[1];
		}
		var ip = ifConfig.match(/^ +ip-address +([0-9\.]+)\/([0-9]+)/m);
		if (ip) {
			networkInterface.ip.push({
				ip: ip[1],
				mask: parseInt(ip[2]),
				usage: "PRIMARY"
			});
		}
		networkInterfaces[ifName] = networkInterface;
	}
	var showIpv6Interface = cli.command("show ipv6 interface | notab");
	var ipv6InterfaceDetails = cli.findSections(showIpv6Interface, /^interface vpn ([0-9]+) interface (.+) af-type ipv6/m);
	for (var i in ipv6InterfaceDetails) {
		var ifName = ipv6InterfaceDetails[i].match[2];
		var ifConfig = ipv6InterfaceDetails[i].config;
		var ip = ifConfig.match(/^ +ipv6-address +([0-9a-fA-F:]+)\/([0-9]+)/m);
		if (networkInterfaces[ifName] && ip) {
			networkInterfaces[ifName].ip.push({
				ipv6: ip[1],
				mask: parseInt(ip[2]),
				usage: "PRIMARY"
			});
		}
	}
	var showInterfaceDesc = cli.command("show interface description | notab");
	var interfaceDescriptions = cli.findSections(showInterfaceDesc, /^interface vpn ([0-9]+) interface (.+) af-type .*/m);
	for (var i in interfaceDescriptions) {
		var ifName = interfaceDescriptions[i].match[2];
		var ifConfig = interfaceDescriptions[i].config;
		var descMatch = ifConfig.match(/^ +desc +(.+)/m);
		if (networkInterfaces[ifName] && descMatch) {
			var description = descMatch[1];
			descMatch = description.match(/^"(.+)"$/);
			if (descMatch) {
				description = descMatch[1];
			}
			networkInterfaces[ifName].description = description;
		}
	}
	for (var i in networkInterfaces) {
		device.add("networkInterface", networkInterfaces[i]);
	}

	if (doBackup && (!device.options || device.options.fullBackup)) {
		try {
			cli.macro("bash");
			// Remove any previous backup
			cli.command("rm -f /opt/data/backup/nsbackup.tar.gz");
			cli.macro("execBack");
		}
		catch (err) {
			// ignore
		}
		var requestBackup = cli.command(
			"request nms configuration-db backup path /opt/data/backup/nsbackup",
			{ timeout: 300000 });
		var pathMatch = requestBackup.match(/^Successfully saved backup to (.+\.tar\.gz)/m);
		var sumMatch = requestBackup.match(/^sha256sum: ([0-9a-f]+)/m);
		if (!pathMatch || !sumMatch) {
			throw "Couldn't perform system backup: " + requestBackup;
		}
		var backupPath = pathMatch[1];
		var checksum = sumMatch[1];
		try {
			config.download("backupArchive", backupPath, { method: "sftp", checksum });
		}
		catch (e) {
			var text = "" + e;
			throw e;
		}
		try {
			cli.macro("bash");
			cli.command("rm -f /opt/data/backup/nsbackup.tar.gz");
			cli.macro("execBack");
		}
		catch (err) {
			// ignore
		}
	}
};

function runCommands(command) {
	
}

/*
 * Trigger a snapshot upon configuration commit syslog message.
 * vEdge configuration:
 * system
 *  logging
 *   server x.y.z.t
 *    vpn 512
 */
function analyzeSyslog(message) {
	if (message.match(/CFGMGR.* system-commit /)) {
		return true;
	}
	return false;
}


/*
 * Trigger a snapshot upon configuration commit SNMP trap.
 *
 * vEdge configuration:
 * snmp
 *  no shutdown
 *  contact  ...
 *  location ...
 *  view community-view
 *   oid 1.3.6
 *  !
 *  community netshot
 *   view          community-view
 *   authorization read-only
 *  !
 *  trap target vpn 512 x.y.z.t 162
 *   group-name     netshot
 *   community-name netshot
 *  !
 *  trap group netshot
 *   system
 *    level critical major minor
 *   exit
 *  exit
 */
function analyzeTrap(trap, debug) {
	for (var t in trap) {
		if (trap[t].substring(0, 24) == "1.3.6.1.4.1.41916.100.2.53") {
			return true;
		}
	}
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 22) == "1.3.6.1.4.1.41916.3.2." && sysDesc.match(/Viptela SNMP agent/)) {
		return true;
	}
	return false;
}
