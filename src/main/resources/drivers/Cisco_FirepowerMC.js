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
 * This driver triggers a backup of the target FMC appliance and
 * download the file using SCP.
 * In order for SCP to work, the SSH user must have /bin/sh as login shell.
 */

const Info = {
	name: "CiscoFirepowerMC",
	description: "Cisco Firepower Management Center",
	author: "Netshot Team",
	version: "2.1"
};

const Config = {
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
	"fmcVersion": {
		type: "Text",
		title: "FMC version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"fxosVersion": {
		type: "Text",
		title: "FX-OS version",
		comparable: true,
		searchable: true,
		checkable: true
	},
};

const Device = {
};

const Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive",
		default: true,
	},
};

const CLI = {
	ssh: {
		macros: {
			clish: {
				options: [ "clish", "expert" ],
				target: "clish"
			},
			expert: {
				options: [ "clish", "expert" ],
				target: "expert"
			}
		}
	},
	clish: {
		prompt: /^(> )$/,
		macros: {
			expert: {
				cmd: "expert",
				options: [ "expert" ],
				target: "expert"
			},
			save: {
				cmd: "save config",
				options: [ "clish" ],
				target: "clish"
			}
		}
	},
	expert: {
		prompt: /^([a-z][-a-z0-9]*@[A-Za-z\-_0-9\.]+:.+\$ )$/,
		macros: {
			clish: {
				cmd: "clish",
				options: [ "clish" ],
				target: "clish"
			},
			clishBack: {
				cmd: "exit",
				options: [ "clish" ],
				target: "clish"
			},
			sudo: {
				cmd: "sudo -i",
				options: [ "expertSudo", "sudoPassword" ],
				target: "expertSudo",
			},
		}
	},
	expertSudo: {
		prompt: /^([a-z][-a-z0-9]*@[A-Za-z\-_0-9\.]+:.+# )$/,
		macros: {
			expertBack: {
				cmd: "exit",
				options: [ "expert" ],
				target: "expert"
			},
		}
	},
	sudoPassword: {
		prompt: /^Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "sudoPasswordAgain", "expertSudo" ]
			},
		},
	},
	sudoPasswordAgain: {
		prompt: /^Password: $/,
		fail: "Password rejected when running sudo."
	},
};

function snapshot(cli, device, config) {

	cli.macro("clish");
	const showVersion = cli.command("show version");
	const hostnameMatch = showVersion.match(/^--+\[ ([A-Za-z\-_0-9\.]+) \]--+$/m);
	if (hostnameMatch) {
		device.set("name", hostnameMatch[1]);
	}
	const fmcVersionMatch = showVersion
		.match(/^Model +: +Cisco Firepower Management Center .* Version ([0-9\.]+)/m);
	if (fmcVersionMatch) {
		config.set("fmcVersion", fmcVersionMatch[1]);
		device.set("softwareVersion", fmcVersionMatch[1]);
	}
	const uuidMatch = showVersion.match(/^UUID +: +([-0-9a-f]+)/m);
	if (uuidMatch) {
		device.set("serialNumber", uuidMatch[1]);
		device.add("module", {
			slot: "Main",
			partNumber: "Firepower Management Center",
			serialNumber: uuidMatch[1],
		});
	}
	device.set("family", "Firepower Management Center");
	
	cli.macro("expert");

	const motd = cli.command("cat /etc/motd");
	const fxosVersionMatch = motd.match(/Cisco Firepower Extensible Operating System .* v([0-9\.]+)/);
	if (fxosVersionMatch) {
		config.set("fxosVersion", fxosVersionMatch[1]);
	}

	device.set("networkClass", "SERVER");

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
		// Full backup
		cli.macro("sudo");
		cli.command("mkdir -p /var/sf/backup");
		cli.command("/bin/rm -f /var/sf/backup/netshotbackup*");
		cli.command("perl -MSF -e 'SF::BackupRestore::BackupSensor(\"netshotbackup\",1,[],[],undef,{},\"00000000-0000-0000-0000-000000000000\",undef,undef,0,1);'")

		let maxLoops = 12 * 30; // 30 minutes
		while (true) {
			const backupSum = cli.command("sha256sum /var/sf/backup/netshotbackup*");
			const backupMatch = backupSum.match(/^([0-9a-f]{64})\s+(\/var\/sf\/backup\/netshotbackup.*\.tar)$/m);
			if (backupMatch) {
				const checksum = backupMatch[1];
				const backupPath = backupMatch[2];
				try {
					config.download("backupArchive", backupPath, { method: "sftp", checksum, newSession: true });
				}
				catch (e) {
					var text = "" + e;
					throw e;
				}
				finally {
					cli.command(`rm -f ${backupPath}`);
				}
				break;
			}
			maxLoops -= 1;
			if (maxLoops <= 0) {
				throw "The local backup took too long";
			}
			cli.sleep(5000);
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
	return sysObjectID === "1.3.6.1.4.1.8072.3.2.10" &&
		sysDesc.match(/Linux firepower/);
}
