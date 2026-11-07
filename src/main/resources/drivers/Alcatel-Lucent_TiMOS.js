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
	name: "AlcatelLucentTiMOS",
	description: "Alcatel-Lucent TiMOS",
	author: "Netshot Team",
	version: "1.3"
};

var Config = {
	"osVersion": {
		type: "Text",
		title: "TiMOS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## TiMOS version:",
			preLine: "##  "
		}
	},
	"bof": {
		type: "LongText",
		title: "BOF",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## BOF:",
			post: "## End of BOF"
		}
	},
	"configuration": {
		type: "LongText",
		title: "Configuration",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "## Running configuration (taken on %when%):",
			post: "## End of running configuration"
		}
	}
};

var Device = {
	"configurationSaved": {
		type: "Binary",
		title: "Configuration saved",
		searchable: true,
		checkable: true
	},
	"configFile": {
		type: "Text",
		title: "Configuration file name",
		comparable: true,
		searchable: true,
		checkable: true
	}
};

var CLI = {
	telnet: {
		macros: {
			prompt: {
				options: [ "login", "password", "prompt" ],
				target: "prompt"
			}
		}
	},
	ssh: {
		macros: {
			prompt: {
				options: [ "prompt" ],
				target: "prompt"
			}
		}
	},
	login: {
		prompt: /^Login: $/,
		macros: {
			auto: {
				cmd: "$$NetshotUsername$$",
				options: [ "password" ]
			}
		}
	},
	password: {
		prompt: /^Password: $/,
		macros: {
			auto: {
				cmd: "$$NetshotPassword$$",
				options: [ "loginAgain", "prompt" ]
			}
		}
	},
	loginAgain: {
		prompt: /^Login: $/,
		fail: "Authentication failed - Telnet authentication failure."
	},
	prompt: {
		prompt: /^((?:\*)?[AB]:[A-Za-z\-_0-9\.]+#) $/,
		pager: {
			avoid: "environment no more",
			match: /Press any key to continue \(Q to quit\)$/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure terminal",
				options: [ "enable", "configure" ],
				target: "configure"
			},
			save: {
				cmd: "admin save",
				options: [ "prompt" ],
				target: "prompt"
			}
		}

	},
	configure: {
		prompt: /^((?:\*)?[AB]:[A-Za-z\-_0-9\.]+>config[a-z0-9\->]*[#\$]) $/,
		clearPrompt: true,
		macros: {
			prompt: {
				cmd: "exit all",
				options: [ "prompt", "configure" ],
				target: "prompt"
			}
		}
	}
};

function snapshot(cli, device, config) {
	
	cli.macro("prompt");
	var information = cli.command("show system information");
	var configuration = cli.command("admin display-config");
	var bof = cli.command("show bof");
	
	configuration = configuration.replace(/^# Generated .*$/m, "");
	configuration = configuration.replace(/^# Finished .*$/m, "");
	config.set("configuration", configuration);
	config.set("bof", bof);
	
	var hostname = information.match(/^System Name *: (.*)$/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	var version = information.match(/System Version *: (.*)$/m);
	if (version) {
		device.set("softwareVersion", version[1]);
		config.set("osVersion", version[1]);
	}
	else {
		device.set("softwareVersion", "Unknown");
		config.set("osVersion", "Unknown");
	}
	
	var author = information.match(/^User Last Modified *: (.*)$/m);
	if (author) {
		config.set("author", author[1]);
	}
	var configFile = information.match(/^Last Saved Config *: (.*)$/m);
	if (configFile) {
		device.set("configFile", configFile[1]);
	}
	if (information.match(/^Changes Since Last Save *: Yes/m)) {
		device.set("configurationSaved", true);
	}
	else {
		device.set("configurationSaved", false);
	}
	
	device.set("networkClass", "ROUTER");
	var family = information.match(/^System Type *: (.*)$/m);
	if (family) {
		family = family[1];
		family = family.replace(/^([^ ]+ [^ ]+) .*$/, "$1");
		family = "ALU " + family;
		device.set("family", family);
	}
	
	var location = information.match(/^System Location *: (.*)$/m);
	if (location) {
		device.set("location", location[1]);
	}
	var contact = information.match(/^System Contact *: (.*)$/m);
	if (contact) {
		device.set("contact", contact[1]);
	}
	
	var showCard = cli.command("show card detail");
	var cardPattern = /\nCard ([0-9A-Z]+)(.|\r|\n)+?  Part number *: (.*)(.|\r|\n)+?  Serial number *: (.*)/g;
	var match;
	while (match = cardPattern.exec(showCard)) {
		var module = {
			slot: match[1],
			partNumber: match[3],
			serialNumber: match[5]
		};
		device.add("module", module);
	}
	
	var showChassis = cli.command("show chassis");
	var chassis = showChassis.match(/\n +Part number *: (.*)(.|\r|\n)+? +Serial number *: (.*)/);
	if (chassis) {
		var module = {
			slot: "chassis",
			partNumber: chassis[1],
			serialNumber: chassis[3]
		};
		device.add("module", module);
	}
	
	var showMda = cli.command("show mda detail");
	var mdaPattern = /\nMDA ([0-9A-Z\/]+) detail(.|\r|\n)+?  Part number *: (.*)(.|\r|\n)+?  Serial number *: (.*)/g;
	var match;
	while (match = mdaPattern.exec(showMda)) {
		var module = {
			slot: match[1],
			partNumber: match[3],
			serialNumber: match[5]
		};
		device.add("module", module);
	}

	var showPorts = cli.command("show port");
	var portListPattern = /(\d+\/\d\/\d+)\s+\S+\s+\S+\s+\S+\s+\d+\s+\d+\s+\S+\s+\S+\s+\S+\s+\S+.+?([0-9A-Z\-]+)/g;
	var match1;
	while (match1 = portListPattern.exec(showPorts)) {
		var showPort = cli.command("show port " + match1[1]);
		var portPattern = /\nInterface\s+:\s+(\S+)(.|\r|\n)+?Transceiver Type\s+:\s+(\S+)(.|\r|\n)+?Model Number\s+:\s+(\S+)(.|\r|\n)+?Serial Number\s+:\s+(\S+)/g;
		var match;
		while (match = portPattern.exec(showPort)) {
			var module = {
				slot: match[1],
				name: match[3],
				partNumber: match[5],
				serialNumber: match[7]
			};
			device.add("module", module);
		}
	}
	
	var parseInterfaces = function(service, showInterface, showVrrp, down) {
		var vrrpInterfaces = [];
		var vrrpPattern = /^(.+?) +[0-9]+ .*[\r\n]+.*[\r\n]+  Backup Addr: ([0-9\.]+)/mg;
		var match;
		while (match = vrrpPattern.exec(showVrrp)) {
			var vrrpInterface = {
					name: match[1],
					ip: match[2]
			};
			vrrpInterfaces.push(vrrpInterface);
		}
		var interfaceDetails = showInterface.split(/\-\-\-\-\-\-\-\-\-+[\r\n]+Interface *[\r\n]+\-\-\-\-\-\-\-\-\-+/);
		for (var i in interfaceDetails) {
			var interfaceDetail = interfaceDetails[i];
			
			var name = interfaceDetail.match(/^If Name *: (.*)$/m);
			if (name) {
				name = name[1];
			}
			else {
				continue;
			}
			var networkInterface = {
				name: name,
				ip: []
			};
			
			var description = interfaceDetail.match(/^Description *: (.*)$/m);
			if (description && description[1] != "(Not Specified)") {
				networkInterface.description = description[1];
			}
			if (service != "") {
				networkInterface.vrf = service;
			}
			
			var port = interfaceDetail.match(/(Port|SAP) Id *: (.*)$/m);
			if (port) {
				networkInterface.name += " [" + port[2] + "]";
			}
			
			networkInterface.enabled = interfaceDetail.match(/Admin State *: Up/) && !down;
			
			var mac = interfaceDetail.match(/MAC Address *: ([0-9a-fA-F:]+)/);
			if (mac) {
				networkInterface.mac = mac[1];
			}
			
			var ipPattern = /IP Addr\/mask *: ([0-9\.]+)\/([0-9]+)/g;
			var usage = "PRIMARY";
			while (match = ipPattern.exec(interfaceDetail)) {
				var ip = {
					ip: match[1],
					mask: parseInt(match[2]),
					usage: usage
				};
				usage = "SECONDARY";
				networkInterface.ip.push(ip);
			}
			
			for (var v in vrrpInterfaces) {
				if (vrrpInterfaces[v].name == name) {
					var ip = {
						ip: vrrpInterfaces[v].ip,
						mask: 32,
						usage: "VRRP"	
					};
					networkInterface.ip.push(ip);
				}
			}
			
			device.add("networkInterface", networkInterface);
		}
	};
	
	// Global interfaces
	var showRouterInterface = cli.command("show router interface detail");
	var showRouterVrrp = cli.command("show router vrrp instance");
	parseInterfaces("", showRouterInterface, showRouterVrrp, false);
	
	// Management interfaces
	var showRouterManagementInterface = cli.command('show router "management" interface detail');
	parseInterfaces("management", showRouterManagementInterface, "", false);
	
	// VPRN interfaces
	var showServiceUsing = cli.command("show service service-using");
	var servicePattern = /^([0-9]+) + ([A-Z]+) +(Up|Down) +(Up|Down) +([0-9]+) +(.*)$/gm;
	var match;
	while (match = servicePattern.exec(showServiceUsing)) {
		var service = match[1];
		var type = match[2];
		var status = match[4];
		var name = match[6];
		if (type == "VPRN") {
			var serviceInterfaces = cli.command("show service id " + service + " interface detail");
			var serviceVrrp = cli.command("show router " + service + " vrrp instance");
			parseInterfaces(name, serviceInterfaces, serviceVrrp, status == "Down");
		}
	}
	
	

};

function analyzeSyslog(message) {
	if (message.match(/(configuration modified|changed administrative state)/)) {
		return true;
	}
	return false;
}

function analyzeTrap(trap, debug) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.6527.3.1.3.1.0.9" ||
		trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.6527.3.1.3.1.0.8";
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 17) == "1.3.6.1.4.1.6527."
		&& sysDesc.match(/^TiMOS.*/)) {
		return true;
	}
	return false;
}
