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
	name: "GenericSNMP",
	description: "Generic SNMP device",
	author: "Netshot Team",
	version: "2.1",
	priority: 1024, /* Less than default */
};

const Config = {
};

const Device = {
	"sysObjectId": {
		type: "Text",
		title: "SNMP sysObjectID",
		searchable: true
	},
	"sysDescr": {
		type: "Text",
		title: "SNMP sysDescr",
		searchable: true
	},
};

const CLI = {
	/* No mode = no CLI */
};

const SNMP = {
	snmpv1: {},
	snmpv2c: {},
	snmpv3: {},
};

function snapshot(client, device, config) {

	const poller = client.create("snmp");

	device.set("name", poller.get("1.3.6.1.2.1.1.5.0")); /* sysName.0 */
	device.set("contact", poller.get("1.3.6.1.2.1.1.4.0")); /* sysContact.0 */
	device.set("location", poller.get("1.3.6.1.2.1.1.6.0")); /* sysLocation.0 */
	device.set("sysObjectId", poller.get("1.3.6.1.2.1.1.2.0")); /* sysObjectID.0 */
	device.set("sysDescr", poller.get("1.3.6.1.2.1.1.1.0")); /* sysDescr.0 */

	const ifDescr = poller.walk("1.3.6.1.2.1.2.2.1.2", true);
	const ifAlias = poller.walk("1.3.6.1.2.1.31.1.1.1.18", true);
	const ifAdminStatus = poller.walk("1.3.6.1.2.1.2.2.1.7", true);
	const ifPhysAddress = poller.walk("1.3.6.1.2.1.2.2.1.6", true, { format: "hex" });
	/* IPv4 only with this OID :( */
	const ipAdEntAddr = poller.walk("1.3.6.1.2.1.4.20.1.1", true);
	const ipAdEntIfIndex = poller.walk("1.3.6.1.2.1.4.20.1.2", true);
	const ipAdEntNetMask = poller.walk("1.3.6.1.2.1.4.20.1.3", true);

	for (const [ifIndex, name] of Object.entries(ifDescr)) {
		const networkInterface = {
			name,
			description: ifAlias[ifIndex] || undefined,
			enabled: ifAdminStatus[ifIndex] != 2,
			ip: [],
		};
		const mac = ifPhysAddress[ifIndex];
		/* Skip empty or non-Ethernet (not 6-byte) physical addresses */
		if (typeof mac === "string" && mac.match(/^[0-9a-f]{2}(:[0-9a-f]{2}){5}$/i)) {
			networkInterface.mac = mac;
		}
		for (const [a, addrIfIndex] of Object.entries(ipAdEntIfIndex)) {
			const ip = ipAdEntAddr[a];
			const mask = ipAdEntNetMask[a];
			if (addrIfIndex == ifIndex && typeof ip === "string" && typeof mask === "string") {
				networkInterface.ip.push({
					ip,
					mask,
					usage: networkInterface.ip.length > 0 ? "SECONDARY" : "PRIMARY",
				});
			}
		}
		device.add("networkInterface", networkInterface);
	}
}

function analyzeSyslog(message) {
	return false;
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	/* Accept any device which replied to SNMP polls. */
	return true;
}
