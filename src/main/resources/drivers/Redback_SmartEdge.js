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
	name: "SmartEdgeOS",
	description: "Redback Networks SmartEdge OS",
	author: "Netshot Team",
	version: "1.2"
};

var Config = {
	"osVersion": {
		type: "Text",
		title: "SmartEdge OS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! SmartEdge OS version:",
			preLine: "!!  "
		}
	},
	"configuration": {
		type: "LongText",
		title: "Configuration",
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
	ssh: {
		macros: {
			disable: {
				options: [ "disable" ],
				target: "disable"
			}
		}
	},
	disable: {
		prompt: /^(\[.*?\][A-Za-z\-_0-9\.]+\>)$/,
		pager: {
			avoid: "terminal length 0",
			match: /^---\(more\)---$/,
			response: " "
		},
		macros: {
		}
	}
};

function snapshot(cli, device, config) {
	
	var configCleanup = function(config) {
		config = config.replace(/^Building configuration.*$/m, "");
		config = config.replace(/^Current configuration.*$/m, "");
		var p = config.search(/^[a-z]/m);
		if (p > 0) {
			config = config.slice(p);
		}
		return config;
	};
	
	cli.macro("disable");
	var configuration = cli.command("show configuration");
	
	var author = configuration.match(/^\! Configuration last changed by user '(.*?)' at/m);
	if (author != null) {
		config.set("author", author[1]);
	}
	configuration = configCleanup(configuration);
	config.set("configuration", configuration);
	
	var showVersion = cli.command("show version");
	
	var hostname = configuration.match(/^ *system hostname (.*)$/m);
	if (hostname != null) {
		device.set("name", hostname[1]);
		hostname = hostname[1];
	}
	else {
		hostname = "";
	}
	
	var version = showVersion.match(/SmartEdge OS Version (.*)$/m);
	if (version != null) {
		device.set("softwareVersion", version[1]);
		config.set("osVersion", version[1]);
	}

	var showChassis = cli.command("show chassis");
	var chassis = showChassis.match(/Current platform is (.*)/);

	if (chassis) {
		device.set("family", "Ericsson " + chassis[1]);
	}
	else {
		device.set("family", "Unknown SEOS device");
	}
	device.set("networkClass", "ROUTER");
	
	var location = configuration.match(/^ system location (.*)/m);
	if (location && location[1] != "<location>") {
		device.set("location", location[1]);
	}
	else {
		device.set("location", "");
	}
	var contact = configuration.match(/^ system contact (.*)/m);
	if (contact && contact[1] != "<contact>") {
		device.set("contact", contact[1]);
	}
	else {
		device.set("contact", "");
	}

	var showHardware = cli.command("show hardware");
	var hardwarePattern = /^([0-9]+|N\/A) +(.*?) +([A-Z0-9]+) +[A-Z0-9]+ +[0-9]+ +[0-9]+\-[A-Z][A-Z][A-Z]\-[0-9][0-9][0-9][0-9]/mg;
	var match;
	while (match = hardwarePattern.exec(showHardware)) {
		var module = {
			slot: match[1],
			partNumber: match[2],
			serialNumber: match[3]
		};
		device.add("module", module);
	}

	var showPorts = cli.command("show port detail");
	var ports = [];
	var portPattern = /ethernet (.+) state is (Up|Down)(.|\n|\r)*?Admin state +: (Up|Down)(.|\n|\r)*?MAC address +: ([0-9a-z:]+)/g;
	var match;
	while (match = portPattern.exec(showPorts)) {
		var port = {
			port: match[1],
			adminUp: match[4] == "Up",
			mac: match[6]
		};
		ports.push(port);
	}


	var contexts = configuration.split(/^context /m);
	for (var c in contexts) {
		var contextConfiguration = contexts[c];
		var contextName = contextConfiguration.match(/^(.*)/);
		if (contextName) {
			contextName = contextName[1];
		}
		else {
			contextName = "";
		}
		device.add("virtualDevice", contextName);
		var interfaces = cli.findSections(contextConfiguration, /^ interface (.+)/m);
		for (var i in interfaces) {
			var networkInterface = {
				name: interfaces[i].match[1],
				ip: [],
				virtualDevice: contextName
			};
			var description = interfaces[i].config.match(/^ *description (.+)/m);
			if (description) {
				networkInterface.description = description[1];
			}
			var ipPattern = /^ *ip address (\d+\.\d+\.\d+\.\d+)\/(\d+)( secondary)?/mg;
			while (match = ipPattern.exec(interfaces[i].config)) {
				var ip = {
					ip: match[1],
					mask: parseInt(match[2]),
					usage: "PRIMARY"
				};
				if (match[3] == " secondary") {
					ip.usage = "SECONDARY";
				}
				networkInterface.ip.push(ip);
			}
			var ipv6Pattern = /^ *ipv6 address ([0-9A-Fa-f:]+)\/(\d+)/mg;
			while (match = ipv6Pattern.exec(interfaces[i].config)) {
				var ip = {
					ipv6: match[1],
					mask: parseInt(match[2]),
					usage: "PRIMARY"
				};
				networkInterface.ip.push(ip);
			}
			var port = networkInterface.name.replace(/\..*/, "");
			for (var p in ports) {
				if (ports[p].port == port) {
					networkInterface.mac = ports[p].mac;
					if (!ports[p].adminUp) networkInterface.enabled = false;
				}
			}
			if (interfaces[i].config.match(/^ *shutdown$/m)) {
				networkInterface.enabled = false;
			}
			device.add("networkInterface", networkInterface);
		}
	}

};

function snmpAutoDiscover(sysObjectID, sysDesc) {
	if (sysObjectID.substring(0, 19) == "1.3.6.1.4.1.2352.1."
		&& sysDesc.match(/.*SmartEdge OS.*/)) {
		return true;
	}
	return false;
}
