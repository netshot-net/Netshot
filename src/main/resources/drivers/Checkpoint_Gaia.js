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
 * In order for SCP to work, the SSH user must have /bin/bash as login shell.
 * It supports both standard GAiA and Embedded GAiA (SMB platforms).
 */

const Info = {
	name: "CheckpointGaia",
	description: "Checkpoint GAiA OS",
	author: "Netshot Team",
	version: "4.6"
};

const Config = {
	"productVersion": {
		type: "Text",
		title: "Gaia Product Version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			preLine: "## Product version: "
		}
	},
	"kernelVersion": {
		type: "Text",
		title: "Kernel version",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"configuration": {
		type: "LongText",
		title: "Configuration (clish)",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Configuration (taken on %when%):",
			post: "## End of configuration"
		}
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

const Device = {
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true
	},
	"osEdition": {
		type: "Text",
		title: "OS Edition",
		searchable: true
	}
};

const Options = {
	"fullBackup": {
		type: "Boolean",
		title: "Take full backup archive",
		default: true,
	},
};

const CLI = {
	telnet: {
		macros: {
			clish: {
				options: [ "login", "clish", "expert" ],
				target: "clish"
			},
			expert: {
				options: [ "username", "clish", "expert", "noExpertPassword" ],
				target: "expert"
			}
		}
	},
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
	username: {
		prompt: /^login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password", "usernameAgain" ]
			}
		}
	},
	password: {
		prompt: /^Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "loginAgain", "clish", "expert" ]
			}
		}
	},
	loginAgain: {
		prompt: /^login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	clish: {
		prompt: /^([A-Za-z\-_0-9\.:]+> )$/,
		error: /^((CLINFR0329|NMSHNM0679) .*nvalid|Bad parameter starting at)/m,
		pager: {
			avoid: "set clienv rows 0",
			match: /^-- More --$/,
			response: " "
		},
		macros: {
			expert: {
				cmd: "expert",
				options: [ "expert", "noExpertPassword", "expertPassword" ],
				target: "expert"
			},
			save: {
				cmd: "save config",
				options: [ "clish" ],
				target: "clish"
			},
			expertBack: {
				cmd: "exit",
				options: [ "expert" ],
				target: "expert"
			}
		}
	},
	expert: {
		prompt: /^(\[Expert@[A-Za-z\-_0-9\.:]+\]# )$/,
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
			}
		}
	},
	noExpertPassword: {
		prompt: /Expert password has not been defined/,
		fail: "Unable to switch to expert mode - No expert password defined."
	},
	expertPassword: {
		prompt: /^Enter expert password: ?$/,
		macros: {
			auto: {
				cmd: "$$NetshotSuperPassword$$",
				options: [ "expert", "wrongPassword" ]
			}
		}
	},
	wrongPassword: {
		prompt: /Wrong password./,
		fail: "Invalid expert password"
	}
};

function snapshot(cli, device, config) {

	const matchSet = function(e, data, re, field, defaultValue) {
		const r = data.match(re);
		if (r) {
			e.set(field, r[1]);
		}
		else if (defaultValue) {
			e.set(field, defaultValue);
		}
	};
	
	cli.macro("clish");

	const showConfig = cli.command("show configuration");
	const configuration = showConfig.replace(/^(# Exported by .*) on .*/mg, "$1");
	config.set("configuration", configuration);

	matchSet(device, showConfig, /^set snmp contact "(.+)"/m, "contact");
	matchSet(device, showConfig, /^set snmp location "(.+)"/m, "location");

	device.set("networkClass", "FIREWALL");

	const takeFullBackup = !device.options || device.options.fullBackup;

	let isEmbedded = false;

	try {
		const showVersion = cli.command("show version all");
		matchSet(config, showVersion, /^Product version (.+)/m, "productVersion");
		matchSet(config, showVersion, /^OS kernel version (.+)/m, "kernelVersion");
		matchSet(device, showVersion, /^OS edition (.+)/m, "osEdition");
		const version = showVersion.match(/^Product version Check Point Gaia (.+)/m);
		if (version) {
			device.set("softwareVersion", version[1]);
		}
	}
	catch (err) {
		const text = "" + err;
		if (text.match(/Bad parameter/)) {
			isEmbedded = true;
		}
		else {
			throw err;
		}
	}

	if (isEmbedded) {
		// GAiA Embedded

		const showDeviceDetails = cli.command("show device-details");
		// MYFIREWALL-01> show device-details
		// hostname:                     MYFIREWALL-01
		// country:                      other
		// auth-cert:
		matchSet(device, showDeviceDetails, /^hostname: +(.+)/m, "name");

		const showSoftwareVersion = cli.command("show software-version");
		// MYFIREWALL-01> show software-version
		// This is Check Point's 1550 Appliance R81.10.16 - Build 996
		const platformMatch = showSoftwareVersion.match(/Check Point('s)? ([0-9]+)/);
		const platform = platformMatch ? `Check Point ${platformMatch[2]}` : "Check Point SMB";
		device.set("family", platform);
		matchSet(device, showSoftwareVersion, / (R[0-9.]+)/, "softwareVersion");
		device.set("osEdition", "Embedded");

		const showDiag = cli.command("show diag");
		const serialMatch = showDiag.match(/^Serial number: +(.+)/m);
		if (serialMatch) {
			const serialNumber = serialMatch[1];
			device.set("serialNumber", serialNumber);
			device.add("module", {
				slot: "Chassis",
				partNumber: platform,
				serialNumber,
			});
		}

		const intfs = {};
		const intfPattern = /^set interface(-[a-z]+)? "(.+?)" /mg;
		while (true) {
			const match = intfPattern.exec(configuration);
			if (!match) break;
			const name = match[2];
			intfs[name] = {
				name,
				ip: [],
			};
		}

		const intfIpv4Pattern = /^set interface-(type|alias) "(.+?)" .*ipv4-address "([0-9.]+)" (subnet-mask "([0-9.]+)"|mask-length "([0-9]+)")/mg;
		while (true) {
			const match = intfIpv4Pattern.exec(configuration);
			if (!match) break;
			intfs[match[2]].ip.push({
				ip: match[3],
				mask: match[5] ? match[5] : parseInt(match[6]),
				usage: "PRIMARY",
			});
		}

		const intfDescPattern = /^set interface-(type|alias) "(.+?)" .*description "(.+?)"/mg;
		while (true) {
			const match = intfDescPattern.exec(configuration);
			if (!match) break;
			intfs[match[2]].description = match[3];
		}

		const intfStatePattern = /^set interface-(type|alias) "(.+?)" .*state "(on|off)"/mg;
		while (true) {
			const match = intfStatePattern.exec(configuration);
			if (!match) break;
			if (match[3] === "off") {
				intfs[match[2]].enabled = false;
			}
		}

		const showInterfacesAll = cli.command("show interfaces all");
		const intfMacPattern = /^name: +(.+)\r?\n(.+\r?\n)*mac-address: +([0-9a-f:]+)/mg;
		while (true) {
			const match = intfMacPattern.exec(showInterfacesAll);
			if (!match) break;
			const name = match[1];
			const mac = match[3];
			if (intfs[name]) {
				intfs[name].mac = mac;
			}
		}

		Object.values(intfs).forEach((intf) => {
			device.add("networkInterface", intf);
		});

		if (takeFullBackup) {
			cli.macro("expert");
			const uname = cli.command("uname -r");
			matchSet(config, uname, /([0-9]+[0-9a-z\-.]+)/m, "kernelVersion");

			const backupFile = "/storage/netshot_backup.zip";
			cli.command(`rm -f ${backupFile}`);
			// backup_settings.sh points to this base directory which might not exist
			cli.command(`mkdir -p /config_back`);
			// The path is relative to /config_back
			const backupOutput = cli.command(
				`/pfrm2.0/bin/backup_settings.sh -t full -l local -u admin -f ../${backupFile}`,
				{ timeout: 5 * 60 * 1000 });

			let checksum = undefined;
			try {
				// Try to compute backup file's checkum
				// sha256sum not available
				const md5sum = cli.command(`md5sum ${backupFile}`);
				const hashMatch = md5sum.match(/^([0-9a-f]{32})\s+.*\.zip/m);
				if (!hashMatch) {
					throw "No match";
				}
				checksum = hashMatch[1];
			}
			catch (err) {
				cli.debug(`Unable to compute hash of backup file on the device: ${err}`);
			}

			try {
				config.download("backupArchive", backupFile, { method: "scp", checksum });
			}
			catch (e) {
				const text = "" + e;
				if (text.match(/SCP error/)) {
					throw "SCP error: is /bin/bash the user's login shell?";
				}
				throw e;
			}
			finally {
				cli.command(`rm -f ${backupFile}`);
			}
		}

	}
	else {
		// Full GAiA

		matchSet(device, showConfig, /^set hostname (.+)/m, "name");

		const configState = cli.command("show config-state");
		device.set("configurationSaved", !!configState.match(/^saved/));

		const license = cli.command("cplic print");
		config.set("cpLicense", license);


		const showInterfaces = cli.command("show interfaces all");
		const interfaces = cli.findSections(showInterfaces, /^Interface (.+)/m);
		for (const intf of interfaces) {
			const networkInterface = {
				name: intf.match[1],
				ip: []
			};
			const description = intf.config.match(/^ *comments (\{(.+)\}|(.+))$/m);
			if (description) {
				networkInterface.description = description[2] || description[3];
			}
			const ipv4 = intf.config.match(/^ *ipv4-address (\d+\.\d+\.\d+\.\d+)\/(\d+)/m);
			if (ipv4) {
				networkInterface.ip.push({
					ip: ipv4[1],
					mask: parseInt(ipv4[2]),
					usage: "PRIMARY"
				});
				const vrrpPattern = /^set vrrp interface (.+) monitored-circuit vrid (\d+) backup-address (\d+\.\d+\.\d+\.\d+)/mg;
				while (true) {
					const vrrpMatch = vrrpPattern.exec(configuration);
					if (!vrrpMatch) break;
					if (vrrpMatch[1] == networkInterface.name) {
						networkInterface.ip.push({
							ip: vrrpMatch[3],
							mask: parseInt(ipv4[2]),
							usage: "VRRP"
						});
					}
				}
			}
			const ipv6 = intf.config.match(/^ *ipv6-address ([A-Fa-f0-9:]+)\/(\d+)/m);
			if (ipv6) {
				networkInterface.ip.push({
					ipv6: ipv6[1],
					mask: parseInt(ipv6[2]),
					usage: "PRIMARY"
				});
			}
			const macAddress = intf.config.match(/^ *mac-addr (([0-9A-fa-f][0-9A-fa-f]:){5}[0-9A-fa-f][0-9A-fa-f])/m);
			if (macAddress) {
				networkInterface.mac = macAddress[1];
			}
			if (intf.config.match(/^ *state off/m)) {
				networkInterface.enabled = false;
			}
			device.add("networkInterface", networkInterface);
		}

		// Note: "show asset all" may cause a freeze on a system with faulty LOM
		const showAsset = cli.command("show asset system");
		matchSet(device, showAsset, /^Model:? (.+)/m, "family", "Check Point");

		for (const part of ["Motherboard", "Chassis"]) {
			const partNumber = showAsset.match(new RegExp(`^${part} Assembly Part Number: (.+)`, "m"));
			const serialNumber = showAsset.match(new RegExp(`^${part} Serial Number: (.+)`, "m"));
			if (partNumber && serialNumber) {
				device.add("module", {
					slot: part,
					partNumber: partNumber[1],
					serialNumber: serialNumber[1]
				});
			}
			if (serialNumber) {
				device.set("serialNumber", serialNumber[1]);
			}
		}
		{
			// Fallback for some versions
			/**
				TESTFW333> show asset system
				Platform: QS-22-00
				Model: Check Point 6200P
				Serial Number: 6853AB8729
				CPU Model: Intel(R) Pentium(R) Gold G5400 CPU
				CPU Frequency: 3700.000 Mhz
				Number of Cores: 4
				CPU Hyperthreading: Enabled
			**/
			const partNumber = showAsset.match(/^Platform: (.+)/m);
			const serialNumber = showAsset.match(/^Serial Number: (.+)/m);
			if (partNumber && serialNumber) {
				device.add("module", {
					slot: "Chassis",
					partNumber: partNumber[1],
					serialNumber: serialNumber[1]
				});
			}
			if (serialNumber) {
				device.set("serialNumber", serialNumber[1]);
			}
		}

		if (takeFullBackup) {
			const addBackup = cli.command("add backup local");
			if (!addBackup.match(/Creating backup package/)) {
				throw `Can't start backup: ${addBackup}`;
			}

			let maxLoops = 12 * 20;
			while (true) {
				cli.sleep(5000);
				backupStatus = cli.command("show backup status");
				if (backupStatus.match(/backup succeeded/)) {
					break;
				}
				else if (backupStatus.match(/Performing local backup|Performing backup to Local/)) {
					maxLoops -= 1;
					if (maxLoops <= 0) {
						throw "The local backup took too long";
					}
				}
				else {
					throw "Invalid Checkpoint backup status";
				}
			}

			const backupNameMatch = backupStatus.match(/Backup file location: (.+)/);
			if (backupNameMatch) {
				const backupName = backupNameMatch[1];
				let checksum = undefined;
				try {
					// Try to compute backup file's checkum in expert mode
					cli.macro("expertBack");
					const sha256sum = cli.command(`sha256sum ${backupName}`);
					const hashMatch = sha256sum.match(/^([0-9a-f]{64})\s+.*\.tgz/m);
					if (!hashMatch) {
						throw "No match";
					}
					checksum = hashMatch[1];
				}
				catch (err) {
					cli.debug(`Unable to compute hash of backup file on the device: ${err}`);
				}
				finally {
					try {
						cli.macro("clish");
					}
					catch (err) {
						cli.debug(`Unable to switch back to clish mode: ${err}`);
					}
				}
				try {
					config.download("backupArchive", backupName, { method: "scp", checksum });
				}
				catch (e) {
					const text = "" + e;
					if (text.match(/SCP error/)) {
						throw "SCP error: is /bin/bash the user's login shell?";
					}
					throw e;
				}
				finally {
					const toDelete = backupName.replace(/^.*\//, "");
					cli.command(`delete backup ${toDelete}`);
				}
			}
			else {
				throw "Unable to find backup file path";
			}
		}

	}
};

function analyzeSyslog(message) {
	if (message.match(/Configuration changed from (.*) by user (.*) by/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.2620.1.3000.10.1.1";
}

/**
 * Standard Gaia:
 * .1.3.6.1.2.1.1.1.0 = STRING: "Linux gw-040000 3.10.0-1160.15.2cpx86_64 #1 SMP Fri Nov 11 13:49:46 IST 2022 x86_64"
 * .1.3.6.1.2.1.1.2.0 = OID: .1.3.6.1.4.1.2620.1.6.123.1.49
 * Emebedded:
 * .1.3.6.1.2.1.1.1.0 = STRING: "Linux DE610-FW-Waren-01 4.14.76-release-1.3.0 #1 SMP Sun May 5 16:35:03 IDT 2024 aarch64"
 * .1.3.6.1.2.1.1.2.0 = OID: .1.3.6.1.4.1.2620.1.1
 */
function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.startsWith("1.3.6.1.4.1.2620.");
}
