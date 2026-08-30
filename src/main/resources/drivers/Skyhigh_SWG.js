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

/**
 * NOTES ON THIS DRIVER:
 * This driver triggers a backup of the target Checkpoint appliance and
 * download the file using SCP.
 * In order for SCP to work, the following is required:
 *  1. The SSH user must have /bin/bash as default shell
 *  2. The SSH user must be in the /etc/scpusers file
 */

var Info = {
	name: "SkyhighSWG",
	description: "Skyhigh Secure Web Gateway",
	author: "Netshot Team",
	version: "2.1"
};

var Config = {
	"mwgVersion": {
		type: "Text",
		title: "MWG Version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
};

var Device = {
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
			bash: {
				options: [ "bash" ],
				target: "bash"
			},
		},
	},
	bash: {
		prompt: /(^.+(\$|#) )/,
	},
};

function snapshot(cli, device, config) {
	const addMatchSet = function(e) {
		e.matchSet = function(data, re, field, defaultValue) {
			const r = data.match(re);
			if (r) {
				e.set(field, r[1]);
			}
			else if (defaultValue) {
				e.set(field, defaultValue);
			}
		} 
	}
	addMatchSet(config);
	addMatchSet(device);
	
	cli.macro("bash");

	const hostname = cli.command("hostname");
	device.matchSet(hostname, /^([A-Za-z\-_0-9\.]+)/m, "name");

	const snmpdConf = cli.command("cat /etc/snmp/snmpd.conf");
	device.matchSet(snmpdConf, /^syslocation (.+)/mi, "location", "");
	device.matchSet(snmpdConf, /^syscontact (.+)/mi, "contact", "");
	const descrMatch = snmpdConf.match(/^sysdescr (.+) Secure Web Gateway ([0-9]+)/m);
	if (descrMatch) {
		device.set("family", `${descrMatch[1]} Secure Web Gateway ${descrMatch[2]}`);
	}
	else {
		device.set("family", "Skyhigh/McAfee Secure Web Gateway");
	}

	const mwgVersion = cli.command("/opt/mwg/bin/mwg-core -v");
	device.matchSet(mwgVersion, /Core version: ([0-9\.]+)/m, "softwareVersion");
	config.matchSet(mwgVersion, /Core version: ([0-9\.]+)/m, "mwgVersion");
	
	device.set("networkClass", "SERVER");

	const dmiSystemSerial = cli.command("dmidecode -s system-serial-number");
	device.matchSet(dmiSystemSerial, /^(.+)/m, "serialNumber");

	// Parse iproute ip addr output
	const ipAddressShow = cli.command("ip address show");
	const interfaces = cli.findSections(ipAddressShow,
			/^([0-9]+): ([-\.a-zA-Z0-9]+): <([A-Z_,]+)> .*/m);
	for (const intf of interfaces) {
		const flags = intf.match[3].split(",");
		const networkInterface = {
			name: intf.match[2],
			ip: [],
			enabled: flags.includes("UP"),
		};
		const macMatch = intf.config.match(/link\/ether ([0-9a-f:]+)/);
		if (macMatch && macMatch[1] !== "00:00:00:00:00:00") {
			networkInterface.mac = macMatch[1];
		}

		const ipPattern = /^\s+inet (\d+\.\d+\.\d+\.\d+)\/(\d+)/mg;
		while (true) {
			const ipMatch = ipPattern.exec(intf.config);
			if (!ipMatch) break;
			networkInterface.ip.push({
				ip: ipMatch[1],
				mask: parseInt(ipMatch[2]),
				usage: "PRIMARY"
			});
		}

		const ip6Pattern = /^\s+inet6 ([a-f0-9:]+)\/(\d+)/mg;
		while (true) {
			const ipMatch = ip6Pattern.exec(intf.config);
			if (!ipMatch) break;
			networkInterface.ip.push({
				ipv6: ipMatch[1],
				mask: parseInt(ipMatch[2]),
				usage: "PRIMARY"
			});
		}

		device.add("networkInterface", networkInterface);
	}

	
	if (!device.options || device.options.fullBackup) {
		const backupBaseFile = "/var/tmp/netshot";

		try {
			// Generate backup archive file and download
			// Clean up just in case
			cli.command(`rm -f ${backupBaseFile}*`);

			// Initiate backup
			const backupCommand = `/opt/mwg/bin/mwg-coordinator -B 'file:in=ACTIVE,out=${backupBaseFile};options:recreate=yes'`;
			const backupOutput = cli.command(backupCommand, {
				timeout: 10 * 1000 * 60, // 10 minutes
			});

			/** Example
			 * [root@mcafix ~]# /opt/mwg/bin/mwg-coordinator -B 'file:in=ACTIVE,out=/var/tmp/netshot;options:recreate=yes'
			 * successfully sent backup request "file:in=ACTIVE,out=/var/tmp/netshot;options:recreate=yes" to running Coordinator process.
			 * Job queued with id: 10
			 * Job progress: .
			 * Job finished.
			 * Coordinator responded:
			 * OK - file copied to '/var/tmp/netshot.backup'.
			 */

			const outputMatch = backupOutput.match(/^OK - file copied to '(.+)'/m);
			if (!outputMatch) {
				throw `Error while generating backup file: ${backupOutput}`;
			}
			const backupFile = outputMatch[1];
			if (!backupFile.startsWith(backupBaseFile)) {
				throw `Unexpected backup file '${backupFile}': doesn't contain '${backupBaseFile}'`;
			}
			cli.command(`gzip ${backupFile}`);
			const backupGzFile = `${backupFile}.gz`;
			const checksum = cli.command(`sha256sum ${backupGzFile}`);
			const hashMatch = checksum.match(/^([0-9a-f]{64})\s+(.+)/m);
			if (!hashMatch || hashMatch[2] !== backupGzFile) {
				throw `Unable to compute hash of backup file:\n${checksum}`;
			}
			const backupHash = hashMatch[1];
			config.download("backupArchive", backupGzFile, { method: "scp", checksum: backupHash });
		}
		finally {
			// Final cleanup
			cli.command(`rm -f ${backupBaseFile}*`);
		}
	}
};

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if ((sysObjectID === "1.3.6.1.4.1.1230.2.7.1.1" ||   // McAfee
			sysObjectID === "1.3.6.1.4.1.59732.2.7.1.1") &&  // Skyhigh
			sysDesc.match(/Secure Web Gateway/)) {
		return true;
	}
	return false;
}
