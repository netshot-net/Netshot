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
	name: "CheckpointSPLAT",
	description: "Checkpoint SPLAT",
	author: "Netshot Team",
	version: "2.1"
};

var Config = {
	"spVersion": {
		type: "Text",
		title: "Secure Platform Version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			preLine: "## Secure Platform version: "
		}
	},
	"kernelVersion": {
		type: "Text",
		title: "Kernel version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"cpLicense": {
		type: "LongText",
		title: "License",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## License status:",
			preLine: "## ",
			post: "## End of license"
		}
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
			cpshell: {
				options: [ "cpshell", "expert" ],
				target: "cpshell"
			},
			expert: {
				options: [ "cpshell", "expert" ],
				target: "expert"
			}
		}
	},
	cpshell: {
		prompt: /^(\[[A-Za-z_0-9\.][A-Za-z\-_0-9\.]*\]# )$/,
		pager: {
			match: /^--More--$/,
			response: " "
		},
		error: /^Unknown command .*/m,
		macros: {
			expert: {
				cmd: "expert",
				options: [ "expert", "expertPassword", "noExpertPassword" ],
				target: "expert"
			}
		}
	},
	expert: {
		prompt: /^(\[Expert@[A-Za-z\-_0-9\.]+\]# )$/,
		macros: {
			cpshell: {
				cmd: "cpshell",
				options: [ "cpshell" ],
				target: "cpshell"
			},
			cpshellBack: {
				cmd: "exit",
				options: [ "cpshell" ],
				target: "cpshell"
			},
			backup: {
				cmd: "backup --file -path /var/log/CPbackup/backups netshot",
				options: [ "expert", "backupConfirm" ],
				target: "expert"
			}
		}
	},
	backupConfirm: {
		prompt: /Are you sure you want to proceed\?/,
		macros: {
			auto: {
				cmd: "y",
				options: [ "expert" ]
			}
		}
	},
	noExpertPassword: {
		prompt: /Expert password has not been defined/,
		fail: "Unable to switch to expert mode - No expert password defined."
	},
	expertPassword: {
		prompt: /^Enter expert password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotSuperPassword$$",
				options: [ "expert", "wrongPassword" ]
			}
		}
	},
	wrongPassword: {
		prompt: /Wrong password/,
		fail: "Invalid expert password"
	}
};

function snapshot(cli, device, config) {
	var addMatchSet = function(e) {
		e.matchSet = function(data, re, field, defaultValue) {
			var r = data.match(re);
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
	
	
	cli.macro("cpshell");
	var scroll = cli.command("scroll");
	var hasPaging = scroll.match(/scrolling is on/);
	if (hasPaging) {
		cli.command("scroll off");
	}

	var hostname = cli.command("hostname");
	device.matchSet(hostname, /^([A-Za-z\-_0-9\.]+)/m, "name");

	var ver = cli.command("ver");
	var version = ver.match(/SecurePlatform (.+)/);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("spVersion", version[1]);
	}

	try {
		var license = cli.command("cplic print");
		config.set("cpLicense", license);
	}
	catch (e) {
		// Go on
	}

	device.set("networkClass", "FIREWALL");

	var ifconfig = cli.command("ifconfig");
	var ifPattern = /^([^ ]+) +(.*[\r\n]+( +.*[\r\n]+)*)/mg;
	var ipPattern = /inet addr:([0-9\.]+) .*Mask:([0-9\.]+)/mg;
	var match;
	var ipMatch;
	while (match = ifPattern.exec(ifconfig)) {
		var networkInterface = {
			name: match[1],
			ip: [],
			enabled: !!match[2].match(/^ +UP /m),
		};
		while (ipMatch = ipPattern.exec(match[2])) {
			networkInterface.ip.push({
				ip: ipMatch[1],
				mask: ipMatch[2],
				usage: "PRIMARY"
			});
		}
		var macMatch = match[2].match(/HWaddr ([A-F0-9:]+)/m);
		if (macMatch) {
			networkInterface.mac = macMatch[1];
		}
		if (match[2].match(/^ *state off/m)) {
			networkInterface.enabled = false;
		}
		device.add("networkInterface", networkInterface);
	}

	if (hasPaging) {
		// Restore scrolling state
		cli.command("scroll on");
	}

	cli.macro("expert");

	var uname = cli.command("uname -r");
	config.matchSet(uname, /^(.+)/m, "kernelVersion");

	var dmiProduct = cli.command("dmiparse System Product");
	match = dmiProduct.match(/^(.+)/m);
	device.set("family", "Check Point" + (match ? " " + match[1] : ""));
	var dmiSystemSerial = cli.command("dmiparse System Serial");
	device.matchSet(dmiSystemSerial, /^(.+)/m, "serialNumber");
	
	var parts = ["Base Board", "Chassis"];
	for (var p in parts) {
		var part = parts[p];
		var module = {
			slot: part,
			partNumber: "",
			serialNumber: "",
		};
		var dmiSerial = cli.command("dmiparse '" + part + "' Product");
		match = dmiSerial.match(/^.+/m);
		if (match) module.serialNumber = match[1];
		var dmiName = cli.command("dmiparse '" + part + "' 'Product Name'");
		match = dmiName.match(/^.+/m);
		if (match) module.partNumber = match[1];

		device.add("module", module);
	}

	if (!device.options || device.options.fullBackup) {
		cli.macro("backup", {
			timeout: 30 * 60 * 1000, // 30 minutes
		});

		try {
			config.download("backupArchive", "/var/log/CPbackup/backups/netshot.tgz", { method: "scp" });
		}
		catch (e) {
			var text = "" + e;
			if (text.match(/SCP error/)) {
				throw "SCP error: is the user in /etc/scpusers, with /bin/bash as login shell?";
			}
			throw e;
		}
		finally {
			cli.command("rm -f /var/log/CPbackup/backups/netshot.tgz")
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
	return !!(sysObjectID === "1.3.6.1.4.1.8072.3.2.10" && sysDesc.match(/[0-9]+cp #1/));
}
