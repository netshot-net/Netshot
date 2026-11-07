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
	name: "RiverbedRiOS",
	description: "Riverbed RiOS",
	author: "A. Gandarias",
	version: "1.0"
};

var Config = {
	"riverbedVersion": {
		type: "Text",
		title: "RiOS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## RiOS version: ",
			preLine: "##  "
		}
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
	}
};

var Device = {
	"status": {
		type: "Text",
		title: "Status",
		searchable: true
	},
	"manageByCMC": {
		type: "Text",
		title: "Managed by CMC",
		searchable: true
	}
};

var CLI = {
	telnet: {
		macros: {
			enable: {
				options: ["username", "password", "enable", "disable"],
				target: "enable"
			},
			configure: {
				options: ["username", "password", "enable", "disable"],
				target: "configure"
			}
		}
	},
	ssh: {
		macros: {
			enable: {
				options: ["enable", "disable"],
				target: "enable"
			},
			configure: {
				options: ["enable", "disable"],
				target: "configure"
			}
		}
	},
	username: {
		prompt: /^login as: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: ["password", "usernameAgain"]
			}
		}
	},
	password: {
		prompt: /^[A-Za-z0-9_:]+@([0-9]{1,3}.){3}.([0-9]{1,3})'s password:[ ]*$/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: ["usernameAgain", "disable", "enable"]
			}
		}
	},
	usernameAgain: {
		prompt: /^login as: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	disable: {
		prompt: /^[A-Za-z\-_0-9.\/]+ (\(config\)[ ]*)?([>#])[ ]*$/,
		pager: {
			avoid: "terminal length 0",
			match: /^Lines [0-9]+-[0-9]+\s+$/,
			response: " "
		},
		macros: {
			enable: {
				cmd: "enable",
				options: ["enable", "disable"],
				target: "enable"
			}
		}
	},
	enable: {
		prompt: /^[A-Za-z\-_0-9.\/]+ (\(config\)[ ]*)?([>#])[ ]*$/,
		error: /^% (.*)/m,
		pager: {
			avoid: "terminal length 0",
			match: /^Lines [0-9]+-[0-9]+\s+$/,
			response: " "
		},
		macros: {}

	}
};

function snapshot(cli, device, config) {

	cli.macro("enable");
	cli.command("enable");

	var configuration = cli.command("show configuration running");
	config.set('runningConfig', configuration);

	var info = cli.command("show info");
	var status_riverbed = info.match(/Status: (.*)/);
	if (status_riverbed) {
		status_riverbed = status_riverbed[1];
		device.set("status", status_riverbed);
	}


	var cmc = info.match(/Managed by CMC: (.*)/);
	if (cmc && (cmc[1].match(/^[ ]*yes[ ]*$/) || cmc[1].match(/^[ ]*no[ ]*$/)))
		device.set("manageByCMC", cmc[1].replace(/[ ]*/, ""))

	var status = cli.command("show version");
	var hostname = status.match(/Product name: (.*)/);
	if (hostname) {
		hostname = hostname[1];
		device.set("name", hostname.replace(/\s/g, ""));
	}
	var version = status.match(/Product release:[\s]*([0-9]+.*)/m);
	version = (version ? version[1] : "Unknown");
	device.set("softwareVersion", version);
	config.set("osVersion", version);
	var revision = info.match(/Revision: (.*)/);
	device.set('softwareVersion', version + (revision ? " Revision " + revision[1] : ""))

	var tmpfamily = status.match(/Product model: (.*)/);
	var family = (tmpfamily ? tmpfamily[1] : "RIVOS device");
	device.set("family", family);
	device.set("softwareVersion", version);
	config.set("osVersion", version);
	device.set("networkClass", "UNKNOWN");

	var serial = info.match(/Serial: (.*)/);
	if (serial) {
		var module = {
			slot: (family === "vm"? "VM" : "Chassis"),
			partNumber: family,
			serialNumber: serial[1]
		};
		device.add("module", module);
		device.set("serialNumber", serial[1]);
	}
	else {
		device.set("serialNumber", "");
	}

	var infoStatus = cli.command("show interfaces brief");
	var tmpListInterfaces = cli.findSections(infoStatus, /Interface (.*) state/);
	var listInterface = [];
	for (var elt in tmpListInterfaces)
		listInterface.push(tmpListInterfaces[elt].match[1]);
	for (var j in listInterface) {
		var s = listInterface[j];
		var tmp = cli.command("show interface " + s);
		var ip = tmp.match(/IP address: (.*)/m);
		var netmask = tmp.match(/Netmask: (.*)/);
		var mac = tmp.match(/HW address: (.*)/);
		var up = tmp.match(/Up: (.*)/);
		var networkInterface = {
			name: s,
			ip: [],
			virtualDevice: "",
			mac: (mac ? mac[1].replace(/[\s]*/, "") : "0000.0000.0000"),
			disabled: (up ? (up[1].replace(/[\s]*/, "") !== "yes") : false)
		};

		if (ip && netmask) {
			var sp = ip[1].replace(/[\s]*/, "").split(' ');
			var tmpIP = {
				ip: sp[0],
				mask: netmask[1].replace(/[\s]*/, ""),
				usage: "PRIMARY"
			};
			networkInterface.ip.push(tmpIP);
		}
		device.add("networkInterface", networkInterface);
	}


}


function analyzeTrap(trap, debug) {
	//TODO : Set the trap Riverbed
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return (sysObjectID.substring(0, 22) == "1.3.6.1.4.1.17163.1.1") &&
		sysDesc.match(/^Linux (.*)$/);
}
