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

const Info = {
	name: "VersaNetworksFlexVNF",
	description: "Versa Networks FlexVNF (VOS)",
	author: "Najihel",
	version: "1.0"
};

// --------------------------------------------------------------------------------------------------

const Config = {
	"vosVersion": {
		type: "Text",
		title: "Versa FlexVNF Version",
		comparable: true,
		searchable: true,
		dump: {
			pre: "!! VOS version:",
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

// --------------------------------------------------------------------------------------------------

const Device = {
	"vosOSPackage": { /* This stores VOS OS Package information. */
		type: "Text",
		title: "Versa FlexVNF OS Package Version",
		searchable: true,
		checkable: true,
	},
	"vosSecurityPackage": { /* This stores VOS Security Package information. */
		type: "Text",
		title: "Versa FlexVNF Security Package Version",
		searchable: true,
		checkable: true,
	},
	"vosOSSecurityPackage": { /* This stores VOS OS Security Package information. */
		type: "Text",
		title: "Versa FlexVNF OS Security Package Version",
		searchable: true,
		checkable: true,
	},
	"hostVersion": { /* This stores the Host Version. */
		type: "Text",
		title: "Versa Host OS Version",
		searchable: true,
		checkable: true,
	}
};

// --------------------------------------------------------------------------------------------------

const CLI = {
	ssh: {
		macros: {
			bash: {
				options: [ "bash" ],
				target: "bash"
			},
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
				options: [ "bash" ]
			}
		}
	},
	bash: {
		prompt: /^(\[[A-Za-z0-9\.\-_]+@[A-Za-z\-_0-9\.]+: ~\] [\$|#] )$/m,
		macros: {
			operate: {
				cmd: "cli",
				options: [ "operate" ],
				target: "operate"
			}
		}
	},
	operate: {
		prompt: /^([A-Za-z0-9\.\-_]+@[A-Za-z\-_0-9\.]+\-cli> )$/m,
		error: /^(unknown command|syntax error|error: Unrecognized command)/m,
		pager: {
			match: /^--More--/,
			response: " "
		},
		macros: {
			configure: {
				cmd: "configure",
				options: [ "operate", "configure" ],
				target: [ "configure" ]
			},
			exit: {
				cmd: "exit",
				options: [ "bash", "operate" ],
				target: [ "bash" ]
			}
		}
	},
	configure: {
		prompt: /^([A-Za-z0-9\.\-_]+@[A-Za-z\-_0-9\.]+-cli\(config\)% )$/m,
		error: /^(unknown command|syntax error|invalid )/m,
		clearPrompt: true,
		macros: {
			exit: {
				cmd: "exit",
				options: [ "bash", "operate", "configure" ],
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

// --------------------------------------------------------------------------------------------------

function snapshot(cli, device, config) {

	const configCleanup = function(text) {
		// Remove [ok] and empty lines
		return text
			.replace(/^\[ok\].*$/gm, '')
			.replace(/^\s*$/gm, '');
	};

	// --------------------------------------------------------------------------------------------------

	// CleanUp JSON (no thanks Versa...)
	function parseJsonSafe(jsonString) {
		const lastBraceIndex = jsonString.lastIndexOf('}');
		let cleanJson = jsonString;
		if (lastBraceIndex !== -1) {
			cleanJson = jsonString.substring(0, lastBraceIndex + 1);
		}
		let jsonData;
		try {
			jsonData = JSON.parse(cleanJson);
		} catch (e) {
			cli.debug(`ERROR | JSON Error : ${e}`);
			jsonData = null;
		}
		return jsonData;
	}

	// --------------------------------------------------------------------------------------------------

	// Set networkClass of the Device
	device.set("networkClass", "ROUTER");

	// --------------------------------------------------------------------------------------------------

	// Initiate bash state
	cli.macro("bash");

	// --------------------------------------------------------------------------------------------------

	// Retreive OS version
	const showOSVersion = cli.command("/usr/bin/lsb_release -a");
	const OSVersionLines = showOSVersion.split('\n').filter(line => line.includes(':'));
	const OSVersionInfos ={};
	OSVersionLines.forEach(line => {
		const [key, value] = line.split(':').map(s => s.trim());
		OSVersionInfos[key] = value;
	});
	const hostVersion = `${OSVersionInfos['Description']} (${OSVersionInfos['Codename']})`;

	if (hostVersion) {
		device.set("hostVersion", hostVersion);
	}
	else {
		device.set("Unknown Host version", hostVersion);
	}

	// --------------------------------------------------------------------------------------------------

	// Initiate operate state
	cli.macro("operate");

	// --------------------------------------------------------------------------------------------------

	// Retreive + cleanup configuration in curly output
	let configuration = cli.command("show configuration | nomore");
	configuration = configCleanup(configuration);
	config.set("configuration", configuration);

	// --------------------------------------------------------------------------------------------------

	// Retreive + cleanup configuration in set output
	let configurationAsSet = cli.command("show configuration | display set | nomore");
	configurationAsSet = configCleanup(configurationAsSet);
	config.set("configurationAsSet", configurationAsSet);

	// --------------------------------------------------------------------------------------------------

	// Retrieve the author and the protocol used for the current configuration
	const latestCommit = cli.command("show commit list 1");
	const latestCommitLine = latestCommit.split('\n').find(line => line.trimStart().startsWith('0'));
	if (latestCommitLine != null) {
		const tokens = latestCommitLine.trim().split(/\s+/);
		if (tokens.length >= 4) {
			const authorUser = tokens[2];
			const authorProtocol = tokens[3];
			const author = authorProtocol != null
				? authorUser + ' via ' + authorProtocol
				: authorUser;
			config.set("author", author);
		}
	}

	// --------------------------------------------------------------------------------------------------

	// Retrieve HostName
	const showHostname = cli.command("show configuration system identification name | display set");
	const hostname = showHostname.match(/^set system identification name (\S*)$/m);
	if (hostname != null) {
		device.set("name", hostname[1]);
	}
	else {
		device.set("name", "versa-flexvnf");
	}

	// --------------------------------------------------------------------------------------------------

	// Retrieve vosVersion, vosOSPackage
	const showPackageInfoJSON = cli.command("show system package-info | display json | nomore");	
	const jsonPackageInfo = parseJsonSafe(showPackageInfoJSON);

	if (jsonPackageInfo) {
		const packageInfo = jsonPackageInfo?.data?.["system:system"]?.["package-info"]?.[0] || {};

		const packageBranch = packageInfo["branch"] || "";
		const packageRelType = packageInfo["reltype"] || "";
		const packageId = packageInfo["package-id"] || "Unknown";
		const packageDate = packageInfo["date"] || "";

		// --------------------------------------------------------------------------------------------------

		// vosVersion
		const vosVersion = `Versa FlexVNF software ${packageBranch} ${packageRelType}`.trim();

		if (vosVersion && !vosVersion.includes("undefined")) {
			config.set("vosVersion", vosVersion);
			device.set("softwareVersion", vosVersion);
		} else {
			config.set("vosVersion", "Unknown version");
			device.set("softwareVersion", "Unknown version");
		}

		// --------------------------------------------------------------------------------------------------

		// vosOSPackage
		const vosOSPackage = `ID ${packageId} - Date ${packageDate}`.trim();

		if (vosOSPackage && !vosOSPackage.includes("undefined")) {
			device.set("vosOSPackage", vosOSPackage);
		} else {
			device.set("vosOSPackage", "Unknown package version");
		}

	} else {
		config.set("vosVersion", "Unknown version");
		device.set("softwareVersion", "Unknown version");
		device.set("vosOSPackage", "Unknown package version");
	}

// --------------------------------------------------------------------------------------------------

	// Retrieve Manufacturer, SKU, SerialNumber, vosOSSecurityPackage, vosOSSecurityPackage, etc. of the Device
	const showSystemDetailsJSON = cli.command("show system details | display json | nomore");
	const jsonSystemDetails = parseJsonSafe(showSystemDetailsJSON);

	if (jsonSystemDetails) {
		const systemDetails = jsonSystemDetails?.data?.["system:system"]?.details?.[0] || {};

		// --------------------------------------------------------------------------------------------------

		// Device Family
		const systemHypervisorType = systemDetails["hypervisor"] || "unknown";
		let family;

		if (systemHypervisorType === "baremetal") {
			family = "BareMetal";
		} else if (["vmware", "kvm"].includes(systemHypervisorType.toLowerCase())) {
			family = "VirtualMachine";
		} else {
			family = "Versa FlexVNF generic device";
		}

		device.set("family", family);

		// --------------------------------------------------------------------------------------------------

		// vosOSSecurityPackage and vosOSSecurityPackage
		const spackArray = Array.isArray(systemDetails["spack-info"]) ? systemDetails["spack-info"] : [];
		const ossArray = Array.isArray(systemDetails["osspack-info"]) ? systemDetails["osspack-info"] : [];

		const vosSecurityPackage = spackArray
			.map(s => `Version ${s.version} - API ${s["api-version"]} - Flavour ${s.flavor} - Update type ${s["update-type"]}`)
			.join(", ");

		let vosOSSecurityPackage = "";

		if (ossArray.length > 0) {
			const notInstalled = ossArray.some(o => o.version === "OSSPACK Not Installed");

			if (notInstalled) {
				vosOSSecurityPackage = "OSSPACK Not Installed";
			} else {
				vosOSSecurityPackage = ossArray
					.map(o => `Version ${o.version} - Update type ${o["update-type"]}`)
					.join(", ");
			}
		}

		device.set("vosSecurityPackage", vosSecurityPackage || "Unknown");
		device.set("vosOSSecurityPackage", vosOSSecurityPackage || "Unknown");

		// --------------------------------------------------------------------------------------------------

		// Serial number, Manufacturer, SKU
		const serialNumber = systemDetails["hw-serial"] || "";
		const systemDetailsManufacturer = systemDetails["manufacturer"] || "";
		const systemDetailsSKU = systemDetails["sku"] || "";

		if (serialNumber) {
			device.set("serialNumber", serialNumber);
		} else {
			device.set("serialNumber", "Unknown");
		}

		if (systemDetailsManufacturer && systemDetailsSKU && serialNumber) {
			const module = {
				slot: "Chassis",
				partNumber: `${systemDetailsManufacturer} ${systemDetailsSKU}`,
				serialNumber: serialNumber
			};
			device.add("module", module);
		}
	}

	// --------------------------------------------------------------------------------------------------

	// Retrieve SNMP Information
	const snmpConfig = cli.findSections(configuration, /^snmp /m);
	if (snmpConfig.length > 0) {
		const location = snmpConfig[0].config.match(/^ *location ("(.+)"|(.+));/m);
		if (location) {
			device.set("location", location[2] || location[3]);
		}
		else {
			device.set("location", "");
		}
		const contact = snmpConfig[0].config.match(/^ *contact ("(.+)"|(.+));/m);
		if (contact) {
			device.set("contact", contact[2] || contact[3]);
		}
		else {
			device.set("contact", "");
		}
	}

	// --------------------------------------------------------------------------------------------------

	// Retrieve Interfaces
	const showInterfacesBrief = cli.command("show interfaces brief | display json | nomore");
	const showInterfacesDetail = cli.command("show interfaces detail | display json | nomore");

	const jsonDataBrief = parseJsonSafe(showInterfacesBrief);
	const jsonDataDetail = parseJsonSafe(showInterfacesDetail);
	
	if (jsonDataBrief && jsonDataDetail) {
	
		const briefIfaces = jsonDataBrief?.data?.["interfaces:interfaces"]?.brief || [];
		const detailIfaces = jsonDataDetail?.data?.["interfaces:interfaces"]?.detail || [];
	
		const detailMap = {};
		detailIfaces.forEach(d => {
			if (d.name) detailMap[d.name] = d;
		});
	
		const networkInterfaces = briefIfaces.map(iface => {
			const ifaceObj = {};
		
			// --------------------------------------------------------------------------------------------------

			// Interface Name
			if (iface.name) {
				ifaceObj.name = iface.name;
				ifaceObj.level3 = false;
			}

			// --------------------------------------------------------------------------------------------------

			// Interface Administrative Status
			if (typeof iface["if-admin-status"] !== "undefined") {
				ifaceObj.enabled = iface["if-admin-status"] === "up";
			}

			// --------------------------------------------------------------------------------------------------

			// Interface MAC Address
			if (typeof iface.mac === "string") {
				const macClean = iface.mac.trim().toLowerCase();
				if (macClean && macClean !== "n/a") {
					ifaceObj.mac = iface.mac.trim();
				}
			}
			// --------------------------------------------------------------------------------------------------

			// Interface VRF
			if (iface.vrf) {
				ifaceObj.vrf = iface.vrf;
			}

			// --------------------------------------------------------------------------------------------------

			// Interface Description and Host Interface mapping
			const detail = detailMap[iface.name];
			if (detail) {
				if (detail["host-inf"] && detail["host-inf"].toLowerCase() !== "n/a") {
					ifaceObj.virtualDevice = detail["host-inf"];
				}
				if (detail["intf-descr"]) {
					ifaceObj.description = detail["intf-descr"];
				}
			}
		
			// Interface IP / IPv6
			if (Array.isArray(iface.address) && iface.address.length > 0) {
				ifaceObj.level3 = true;
			
				ifaceObj.ip = [];
			
				let ipv4Index = 0;
				let ipv6Index = 0;
			
				iface.address
					.filter(addr => addr.ip)
					.forEach(addr => {
						const [addrValue, maskStr] = String(addr.ip).split("/");
						const mask = Number(maskStr);
						const isIPv6 = addrValue.includes(":");
					
						if (isIPv6) {
							ifaceObj.ip.push({
								ipv6: addrValue,
								mask: mask,
								usage: ipv6Index === 0 ? "PRIMARY" : "SECONDARY"
							});
							ipv6Index++;
						} else {
							ifaceObj.ip.push({
								ip: addrValue,
								mask: mask,
								usage: ipv4Index === 0 ? "PRIMARY" : "SECONDARY"
							});
							ipv4Index++;
						}
					});
			}
		
			return ifaceObj;
		});
	
		if (networkInterfaces.length) {
			for (const netIf of networkInterfaces) {
				device.add("networkInterface", netIf);
			}
		} else {
			cli.debug(`ERROR | No network interfaces detected !`);
		}
	}

};

function analyzeSyslog(message) {
	//Maybe later
	return false;
};

function analyzeTrap(trap, debug) {
	//Maybe later
	return false;
};

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return sysObjectID.startsWith("1.3.6.1.4.1.42359.2.2.") &&
		sysDesc.match(/Versa Appliance/);
};
