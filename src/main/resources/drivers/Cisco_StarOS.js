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
	name: "CiscoStarOS",
	description: "Cisco StarOS",
	author: "Netshot Team",
	version: "1.3"
};

var Config = {
	"starOsVersion": {
		type: "Text",
		title: "StarOS version",
		comparable: true,
		searchable: true,
		checkable: true,
		dump: {
			pre: "!! StarOS version:",
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
	},
	"licenseInformation": {
		type: "LongText",
		title: "License information",
		comparable: true,
		searchable: true,
		checkable: true
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
			}
		},
	},
	ssh: {
		macros: {
			operate: {
				options: [ "operate" ],
				target: "operate"
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
		prompt: /^password: $/,
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
		prompt: /^(\[[a-zA-Z0-9]+\][A-Za-z0-9_\-]+(?:#|>) )$/,
		pager: {
			avoid: [ "terminal length 0" ],
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

	}
};


function snapshot(cli, device, config) {
	
	cli.macro("operate");
	
	var showVersion = cli.command("show version");
	
	device.set("networkClass", "ROUTER");
	if (showVersion.match(/asr5000\.bin/)) {
		device.set("family", "Cisco ASR5000");
	}
	else if (showVersion.match(/asr5500\.bin/)) {
		device.set("family", "Cisco ASR5500");
	}
	else {
		device.set("family", "Starent device");
	}
	var image = showVersion.match(/Image Version: +(.*?) *$/m);
	if (image) {
		device.set("softwareVersion", image[1]);
		config.set("starOsVersion", image[1]);
	}
	else {
		device.set("softwareVersion", "Unknown");
		config.set("starOsVersion", "Unknown");
	}
	
	
	var configCleanup = function(config) {
		config = config.replace(/(encrypted [a-z\-]+|ssh key|encrypted-url) +(\")?\+[A-Za-z0-9]+(\")?/g, "$1 *****");
		return config;
	};
	
	var configuration = cli.command("show configuration");

	var hostname = configuration.match(/^ *system hostname (.*)$/m);
	if (hostname) {
		device.set("name", hostname[1]);
	}
	
	configuration = configCleanup(configuration);
	config.set("configuration", configuration);
	
	
	var location = configuration.match(/^ *system location (.*)/m);
	if (location) {
		device.set("location", location[1]);
	}
	else {
		device.set("location", "");
	}
	var contact = configuration.match(/^ *system contact (.*)/m);
	if (contact) {
		device.set("contact", contact[1]);
	}
	else {
		device.set("contact", "");
	}
	
	var showPortInfo = cli.command("show port info")
	var macAddresses = {};
	var ports = cli.findSections(showPortInfo, /^Port: (.*)/m);
	for (var p in ports) {
		var mac = ports[p].config.match(/^ *Interface MAC Address *: ([0-9A-F\-]+)/m); 
		if (mac) {
			macAddresses[ports[p].match[1]] = mac[1];
		}
	}
	
	var networkInterfaces = [];
	
	var ports = cli.findSections(configuration, /^  port ethernet (.+)/m);
	for (var p in ports) {
		var portName = ports[p].match[1];
		var networkInterface = {
			name: portName,
			level3: false,
			enabled: false,
			ip: []
		};
		var bind = ports[p].config.match(/^    bind interface (.+) (.+)$/m);
		if (bind) {
			networkInterface.level3 = true;
			networkInterface.description = bind[1];
			networkInterface.vrf = bind[2];
		}
		if (ports[p].config.match(/^    no shutdown$/m)) {
			networkInterface.enabled = true;
		}
		if (typeof(macAddresses[portName]) == "string") {
			networkInterface.mac = macAddresses[portName];
		}
		networkInterfaces.push(networkInterface);
		
		var vlans = cli.findSections(ports[p].config, /^    vlan ([0-9]+)/m);
		for (var v in vlans) {
			var vlanInterface = {
				name: portName + "." + vlans[v].match[1],
				level3: false,
				enabled: false,
				ip: []
			};
			var bind = vlans[v].config.match(/^      bind interface (.+) (.+)$/m);
			if (bind) {
				vlanInterface.level3 = true;
				vlanInterface.description = bind[1];
				vlanInterface.vrf = bind[2];
			}
			if (vlans[v].config.match(/^      no shutdown$/m)) {
				vlanInterface.enabled = true;
			}
			if (typeof(macAddresses[portName]) == "string") {
				vlanInterface.mac = macAddresses[portName];
			}
			networkInterfaces.push(vlanInterface);
		}
		
	}
	
	var contexts = cli.findSections(configuration, /^  context (.*)/m);
	for (var c in contexts) {
		var context = contexts[c].match[1];
		var interfaces = cli.findSections(contexts[c].config, /^    interface (.+?)( loopback)?$/m);
		for (var i in interfaces) {
			var interfaceName = interfaces[i].match[1];
			for (var ni in networkInterfaces) {
				if (interfaceName == networkInterfaces[ni].description &&
						context == networkInterfaces[ni].vrf) {
					var ipPattern = /^      ip address ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+) ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)/gm;
					var match;
					var usage = "PRIMARY";
					while (match = ipPattern.exec(interfaces[i].config)) {
						networkInterfaces[ni].ip.push({
							ip: match[1],
							mask: match[2],
							usage: usage
						});
						usage = "SECONDARY";
					}
					break;
				}
			}
		}
	}
	
	for (var ni in networkInterfaces) {
		device.add("networkInterface", networkInterfaces[ni]);
	}
	
	var showInventory = cli.command("show inventory");
	var inventoryPattern = /NAME: \"(.*)\", +DESCR: \"(.*)\"[\r\n]+PID: (.*?) *, +VID: (.*), +SN: (.*)/g;
	var match;
	while (match = inventoryPattern.exec(showInventory)) {
		var module = {
			slot: match[1],
			partNumber: match[3],
			serialNumber: match[5]
		};
		device.add("module", module);
		if (module.slot.match(/Chassis/)) {
			device.set("serialNumber", module.serialNumber);
		}
	}
	
	var showLicense = cli.command("show license information");
	config.set("licenseInformation", showLicense);

};

function analyzeTrap(trap) {
	return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.8164.2.1100";
}

function snmpAutoDiscover(sysObjectID, sysDesc, debug) {
	return (sysObjectID.substring(0, 17) == "1.3.6.1.4.1.8164." ||
			sysObjectID.substring(0, 20) == "1.3.6.1.4.1.9.1.1457") && sysDesc.match(/staros/i);
}
