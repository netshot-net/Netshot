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
	name: "JuniperJunos",
	description: "Juniper Junos",
	author: "Netshot Team",
	version: "3.3"
};

var Config = {
	"junosVersion": {
		type: "Text",
		title: "Junos version",
		comparable: true,
		searchable: true,
		dump: {
			pre: "!! Junos version:",
			preLine: "!!  "
		}
	},
	"configuration": {
		type: "LongText",
		title: "Configuration (curly)",
		comparable: true,
		searchable: true,
		checkable: true
	},
	"configurationAsSet": {
		type: "LongText",
		title: "Configuration (set)",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! Configuration (taken on %when%):",
			post: "!! End of configuration"
		}
	}
};

var Device = {
};

var CLI = {
	telnet: {
		macros: {
			operate: {
				options: [ "username", "password", "operate" ],
				target: "operate"
			},
			configure: {
				options: [ "username", "password", "operate" ],
				target: "configure"
			}
		},
	},
	ssh: {
		macros: {
			operate: {
				options: [ "operate" ],
				target: "operate"
			},
			configure: {
				options: [ "operate" ],
				target: "configure"
			}
		}
	},
	username: {
		prompt: /^login: $/,
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
				options: [ "usernameAgain", "operate" ]
			}
		}
	},
	usernameAgain: {
		prompt: /^login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	operate: {
		prompt: /^([A-Za-z0-9\.\-_]+@[A-Za-z\-_0-9\.]+>) $/,
		error: /^(unknown command|syntax error|error: Unrecognized command)/m,
		pager: {
			avoid: [ "set cli complete-on-space off", "set cli screen-length 0" ],
			match: /^---\(more( [0-9]+%)?\)---$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure",
				options: [ "operate", "configure" ],
				target: "configure"
			}
		}

	},
	configure: {
		prompt: /^([A-Za-z0-9\.\-_]+@[A-Za-z\-_0-9\.]+#) $/,
		error: /^(unknown command|syntax error|invalid )/m,
		clearPrompt: true,
		macros: {
			exit: {
				cmd: "exit",
				options: [ "operate", "configure" ],
				target: "operate"
			},
			top: {
				cmd: "top",
				options: [ "configure" ],
				target: "configure"
			},
			commit: {
				cmd: "commit",
				options: [ "configure" ],
				target: "configure"
			}
		}
	}
};


function snapshot(cli, device, config) {

	var configCleanup = function(config) {
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};

	cli.macro("operate");
	var configuration = cli.command("show configuration");
	var configurationAsSet = cli.command("show configuration | display set");

	var author = configuration.match(/^## Last commit: .* by (.*)$/m);
	if (author != null) {
		config.set("author", author[1]);
	}
	configuration = configCleanup(configuration);
	config.set("configuration", configuration);
	configurationAsSet = configCleanup(configurationAsSet);
	config.set("configurationAsSet", configurationAsSet);

	var showVersion = cli.command("show version");

	var hostname = showVersion.match(/^Hostname: (.*)/m);
	if (hostname != null) {
		device.set("name", hostname[1]);
	}

	var version = showVersion.match(/^(JUNOS .* \[(\S+)\]|Junos: (\S+))/m);
	var softVersion = version[2] || version[3] || "Unknown";
	device.set("softwareVersion", softVersion);
	config.set("junosVersion", softVersion);


	device.set("networkClass", "ROUTER");
	var family = showVersion.match(/^Model: (.*)/m);
	if (family) {
		var family = family[1];
		family = family.toUpperCase();
		family = family.replace(/(^[A-Z0-9]+)-.*/, "$1");
		device.set("family", "Juniper " + family);
		if (family.match(/(EX|QFX)/)) {
			device.set("networkClass", "SWITCHROUTER");
		}
		else if (family.match(/SRX/)) {
			device.set("networkClass", "FIREWALL");
		}
	}
	else {
		device.set("family", "Junos device");
	}

	device.set("location", "");
	device.set("contact", "");
	var snmpConfig = cli.findSections(configuration, /^snmp /m);
	if (snmpConfig.length > 0) {
		var location = snmpConfig[0].config.match(/^ *location ("(.+)"|(.+));/m);
		if (location) {
			device.set("location", location[2] || location[3]);
		}
		var contact = snmpConfig[0].config.match(/^ *contact ("(.+)"|(.+));/m);
		if (contact) {
			device.set("contact", contact[2] || contact[3]);
		}
	}

	try {
		var showChassis = cli.command("show chassis hardware");
		var header = showChassis.match(/^Item .*Version .*Part number .*Serial number .*Description/m);
		if (header) {
			var itemIndent = 0;
			var versionIndent = header[0].indexOf("Version");
			var partNumberIndent = header[0].indexOf("Part number");
			var serialNumberIndent = header[0].indexOf("Serial number");
			var descriptionIndent = header[0].indexOf("Description");
			var path = [];
			var modulePattern = /^( *).+$/mg;
			var ipv6Match;
			while (ipv6Match = modulePattern.exec(showChassis)) {
				var preSpaces = ipv6Match[1];
				var line = ipv6Match[0];
				if (line.length < descriptionIndent) {
					continue;
				}
				var item = line.substr(0, versionIndent).trim();
				if (item == "Item") {
					continue;
				}
				var serialNumber = line.substr(serialNumberIndent, descriptionIndent - serialNumberIndent).trim();
				var description = line.substr(descriptionIndent).trim();;
				while (preSpaces.length < path.length) {
					path.pop();
				}
				while (preSpaces.length > path.length) {
					path.push("");
				}
				path.push(item);
				if (description != "") {
					var slot = path.join(" ");
					device.add("module", {
						slot: slot,
						partNumber: description,
						serialNumber: serialNumber
					});
					if (slot.match(/[Cc]hassis/)) {
						device.set("serialNumber", serialNumber);
					}
				}
			}
		}
	}
	catch(e) {
		cli.debug("Error while reading the inventory");
	}

	var interfaceVrfs = {};
	var vrfIntfPattern = /^set routing-instances (\S+) interface (\S+)/mg;
	while (true) {
		var vrfIntfMatch = vrfIntfPattern.exec(configurationAsSet);
		if (!vrfIntfMatch) {
			break;
		}
		interfaceVrfs[vrfIntfMatch[2]] = vrfIntfMatch[1];
	}

	var showInterfaces = cli.command("show interfaces");
	var interfaces = cli.findSections(showInterfaces, /^Physical interface: (.+?), (Enabled|Administratively down),.*/m);
	for (var i in interfaces) {
		var ni = {
			name: interfaces[i].match[1],
			ip: []
		};
		if (ni.name.match(/^(vcp|bme|jsrv|pime|vme|lsi).*/)) {
			continue;
		}
		if (!interfaces[i].match[2].match(/Enabled/)) {
			ni.enabled = false;
		}
		var description = interfaces[i].config.match(/^  Description: (.*)$/m);
		if (description) {
			ni.description = description[1];
		}
		var macAddress = interfaces[i].config.match(/Hardware address: ([0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2})/);
		if (macAddress) {
			ni.mac = macAddress[1];
		}
		device.add("networkInterface", ni);

		var logicalInterfaces = cli.findSections(interfaces[i].config, /^  Logical interface (\S+)/m);
		for (var j in logicalInterfaces) {
			var lni = {
				name: logicalInterfaces[j].match[1],
				ip: [],
			};
			description = logicalInterfaces[j].config.match(/^ +Description: (.*)$/m);
			if (description) {
				lni.description = description[1];
			}
			var ipPattern = /^ +(Destination: [0-9\.]+(?:\/([0-9]+))?(?:[\.0-9]*), )?Local: ([0-9\.]+)/mg;
			var ipMatch;
			while (ipMatch = ipPattern.exec(logicalInterfaces[j].config)) {
				lni.ip.push({
					ip: ipMatch[3],
					mask: (ipMatch[2] === undefined) ? 32 : parseInt(ipMatch[2]),
					usage: "PRIMARY"
				});
			}
			var ipv6Pattern = /^ +(Destination: [0-9a-f\\:]+\/([0-9]+), )+Local: ([0-9a-f\\:]+)/mg;
			var ipv6Match;
			while (ipv6Match = ipv6Pattern.exec(logicalInterfaces[j].config)) {
				lni.ip.push({
					ipv6: ipv6Match[3],
					mask: (ipv6Match[2] === undefined) ? 128 : parseInt(ipv6Match[2]),
					usage: "PRIMARY"
				});
			}
			if (lni.name.match(/^(pp).*/)) {
				// Additional command for PPP interfaces
				var pppIpPattern = /^ +Local address: ([0-9\.]+),/mg;
				var showPppInterface = cli.command("show ppp interface " + lni.name + " extensive");
				var ipMatch;
				while (ipMatch = pppIpPattern.exec(showPppInterface)) {
					lni.ip.push({
						ip: ipMatch[1],
						mask: 32,
						usage: "PRIMARY"
					});
				}
			}
			if (ni.mac) {
				lni.mac = ni.mac;
			}
			if (logicalInterfaces[j].config.match(/^ +Protocol (eth-switch|ethernet|vlan)/)) {
				lni.level3 = false;
			}
			if (interfaceVrfs[lni.name]) {
				lni.vrf = interfaceVrfs[lni.name];
			}
			device.add("networkInterface", lni);
		}
	}
};

function analyzeSyslog(message) {
	if (message.match(/UI_COMMIT: User '(.*)' requested 'commit' operation/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.2636.4.5.0.1";
}


function snmpAutoDiscover(sysObjectID, sysDesc, debug) {
	return sysObjectID.substring(0, 17) == "1.3.6.1.4.1.2636." && sysDesc.match(/JUNOS/);
}
