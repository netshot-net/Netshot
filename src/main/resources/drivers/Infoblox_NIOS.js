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
 * This driver talks to an Infoblox NIOS Grid (Grid Master) over HTTPS only,
 * using Netshot's HTTP client and the public WAPI REST API - not the
 * internal "ibap" SOAP endpoint (POST .../ui/ then POST .../ibap/foo) that
 * the NIOS Grid Manager web UI itself uses to build a "Backup" download
 * (this is what a captured browser trace of Grid Manager > Backup shows:
 * login, a GetGridData SOAP call returning a one-time download URL under
 * /http_direct_file_io/..., a plain HTTPS GET of that URL, then a
 * DataGetComplete SOAP call). WAPI's fileop/getgriddata function is the
 * documented, versioned equivalent of that same GetGridData call.
 *
 * Authentication is plain HTTP Basic auth (every WAPI request), which NIOS
 * accepts on every call - no separate login/cookie step is required.
 *
 * BACKUP TRANSPORT: getgriddata (type=BACKUP) hands back a one-time token +
 * a local download URL, which the caller fetches with a plain HTTPS GET and
 * then acknowledges via a "downloadcomplete" call - the exact two-step
 * handshake the Grid Manager web UI's "Backup" button performs under the
 * hood (see above). This driver uses Netshot's `http.download(key, path, ...)`
 * (the HTTP client's binary-safe counterpart to `http.get(...)`/`.data`,
 * which always decodes the response as a charset-decoded String and would
 * corrupt a binary archive) to fetch that URL and land it straight into the
 * `backupArchive` BinaryFile config attribute in one call, byte-exact.
 *
 * getgriddata's "url" is an absolute URL (scheme+host+path); only the path
 * is kept for the actual `http.download(...)` call, since Netshot's HTTP
 * client always targets the host/port of the access it's already connected
 * to (the same Grid Master, here) rather than an arbitrary absolute URL.
 *
 * INVENTORY: a Netshot device is reached at one management IP address, which
 * here is one Grid member's own address (its vip_setting.address - "VIP" is
 * historical naming, it applies to standalone members too) - so this driver
 * represents that one member, not the whole Grid (a Grid with several
 * members being monitored would have one Netshot device per member, each
 * pointed at its own IP, same as any other clustered platform). The Grid's
 * own name is only recorded as the "gridName" config attribute, for
 * correlating members that belong to the same Grid - it is not the device
 * name (confirmed against a live Grid: the device name must be the member's
 * own "host_name", not the Grid's name, which is shared by every member and
 * so useless for telling them apart).
 *
 * An HA member has two node_info entries (one per physical node) sharing
 * that same VIP, so vip_setting alone can't tell which physical node
 * currently owns it. The node actually answering API calls right now is
 * whichever one node_info reports "ha_status":"ACTIVE" (a standalone,
 * non-HA member - as seen in the only Grid this was tested against - has a
 * single node_info entry with "ha_status":"NOT_CONFIGURED", so the "first
 * (only) entry" and "the ACTIVE one" coincide there). Hardware modules are
 * still recorded for every node_info entry (both physical nodes of an HA
 * pair are real hardware, worth listing), but the device-level serial
 * number/hardware facts come specifically from the ACTIVE one.
 *
 * NIOS's running software version is deliberately not exposed on the
 * member/node_info objects (confirmed against the WAPI reference) - it has
 * to be read from the separate "upgradestatus" object (type=VNODE), keyed
 * by member host name.
 */

const Info = {
	name: "InfobloxNIOS",
	description: "Infoblox NIOS (Grid)",
	author: "Netshot Team",
	version: "0.1"
};

const Config = {
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
	"niosVersion": {
		type: "Text",
		title: "NIOS version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"gridName": {
		type: "Text",
		title: "Grid name",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"platform": {
		type: "Text",
		title: "Platform",
		comparable: true,
		searchable: true,
		checkable: true,
	},
};

const Device = {
};

/**
 * A single HTTPS access, authenticated with plain HTTP Basic auth on every
 * request (NIOS's WAPI has no separate login call when using Basic auth).
 */
const HTTP = {
	https: {
		auth: {
			type: "http",
			scheme: "basic",
		}
	}
};

/**
 * WAPI reports API errors as a 4xx/5xx response whose JSON body looks like
 * {"Error":"AdmConProtoError: ...","code":"Client.Ibap.Proto","text":"..."}.
 * Dig the human-readable text out of a caught HTTP error so task failure
 * messages say *why* NIOS rejected the request, not just the HTTP status.
 */
function wapiErrorMessage(e) {
	try {
		const data = e && e.response && e.response.data;
		const body = typeof data === "string" ? JSON.parse(data) : data;
		if (body && (body.text || body.Error)) {
			return body.text || body.Error;
		}
	}
	catch (parseError) {
		// Body wasn't the expected WAPI error shape - fall through.
	}
	return String((e && e.message) || e);
}

/**
 * POST wrapper that re-throws any HTTP error with the WAPI-provided error
 * text (see wapiErrorMessage) instead of the generic "status code NNN").
 * `query` (e.g. WAPI's `_function` parameter for calling an object's
 * function, as in `fileop?_function=getgriddata`) is passed as an actual
 * query parameter, never appended to `path` as a literal "?..." string:
 * Netshot's HTTP client treats the request path as a literal path (it
 * doesn't parse a query string out of it), so a NIOS Grid instead sees a
 * single, bogus "object type" like "fileop?_function=getgriddata" and
 * rejects it with a 400 "Unknown object type" error.
 */
function wapiPost(http, path, data, query) {
	try {
		return http.post(path, data, query ? { query: query } : undefined);
	}
	catch (e) {
		throw `WAPI request to ${path} failed: ${wapiErrorMessage(e)}`;
	}
}

function wapiGet(http, path, query) {
	try {
		return http.get(path, { query: query });
	}
	catch (e) {
		throw `WAPI request to ${path} failed: ${wapiErrorMessage(e)}`;
	}
}

/**
 * Numeric (not lexicographic) comparison of dotted WAPI version strings
 * ("1.0", "2.13.1", ...), so the highest one can be picked reliably
 * regardless of the number of dotted segments.
 */
function compareVersions(a, b) {
	const pa = String(a).split(".").map(Number);
	const pb = String(b).split(".").map(Number);
	const len = Math.max(pa.length, pb.length);
	for (let i = 0; i < len; i++) {
		const na = pa[i] || 0;
		const nb = pb[i] || 0;
		if (na !== nb) {
			return na - nb;
		}
	}
	return 0;
}

/**
 * Discover the highest WAPI version this Grid supports, per Infoblox's
 * documented method (GET the always-present v1.0 endpoint with ?_schema,
 * which reports every version the appliance actually supports).
 */
function discoverWapiVersion(http) {
	const schema = wapiGet(http, "/wapi/v1.0/", { _schema: "1" }).json();
	const versions = (schema && Array.isArray(schema.supported_versions) && schema.supported_versions.length > 0)
		? schema.supported_versions : ["1.0"];
	return versions.slice().sort(compareVersions).pop();
}

/**
 * WAPI's member host_name is the member's FQDN (e.g.
 * "infoblix.lab.armorique.gau", confirmed against a live NIOS VM) - Netshot
 * device names are meant to be the short hostname, matching every other
 * driver, so only the leftmost label is kept.
 */
function shortHostname(fqdn) {
	return typeof fqdn === "string" ? fqdn.split(".")[0] : fqdn;
}

/**
 * Convert a dotted-decimal subnet mask ("255.255.255.0") into its prefix
 * length (24). Returns null if the mask isn't a valid dotted-decimal value.
 */
function maskToPrefixLength(mask) {
	if (typeof mask !== "string") {
		return null;
	}
	const parts = mask.split(".").map(Number);
	if (parts.length !== 4 || parts.some((p) => isNaN(p) || p < 0 || p > 255)) {
		return null;
	}
	let bits = 0;
	for (const part of parts) {
		let n = part;
		while (n > 0) {
			bits += n & 1;
			n >>= 1;
		}
	}
	return bits;
}

/**
 * Add the member's LAN1 interface - its own primary/management IP
 * (vip_setting; "VIP" naming is historical, this applies to standalone
 * members too), named after the physical port it actually is in the NIOS
 * GUI (Grid Manager > Members > ... > Network), not after the member's
 * hostname or any per-node identifier.
 */
function addLan1Interface(device, vipSetting) {
	if (!vipSetting || !vipSetting.address) {
		return;
	}
	const prefixLength = maskToPrefixLength(vipSetting.subnet_mask);
	if (prefixLength === null) {
		return;
	}
	device.add("networkInterface", {
		name: "LAN1",
		enabled: true,
		level3: true,
		ip: [{ ip: vipSetting.address, mask: prefixLength, usage: "PRIMARY" }],
	});
}

/**
 * Record one hardware module per physical node of this member (an HA pair
 * has two node_info entries/physical nodes, a standalone member has one).
 * Slots are labeled by node index + ha_status, never by hostname - shared
 * by both nodes of an HA pair, so useless to tell them apart.
 */
function addNodeModules(device, nodeInfos) {
	nodeInfos.forEach((node, index) => {
		device.add("module", {
			slot: nodeInfos.length > 1 ? `Node ${index + 1} (${node.ha_status || "unknown"})` : "Node 1",
			partNumber: node.hwmodel || node.hwplatform || node.hwtype || "",
			serialNumber: node.hwid || "",
		});
	});
}

/**
 * Pick the node_info entry for the physical node actually answering API
 * calls right now (see NOTES ON THIS DRIVER above for why vip_setting alone
 * can't tell, on an HA member): the one reporting "ha_status":"ACTIVE", or
 * the only entry when there's just one (a standalone, non-HA member).
 */
function findActiveNode(nodeInfos) {
	if (nodeInfos.length <= 1) {
		return nodeInfos[0];
	}
	return nodeInfos.find((node) => node.ha_status === "ACTIVE") || nodeInfos[0];
}

/**
 * WAPI's snmp_setting.syscontact/syslocation (RFC 1213 MIB-II sysContact/
 * sysLocation) are, unusually, lists rather than plain strings - not
 * documented why (possibly historical/multi-value support), and not
 * confirmed against a live Grid with a non-empty value, so this takes the
 * first entry only.
 */
function firstSnmpValue(list) {
	return (Array.isArray(list) && list.length > 0 && list[0]) ? String(list[0]) : null;
}

function snapshot(client, device, config) {

	const http = client.create("http");

	device.set("networkClass", "SERVER");
	device.set("family", "Infoblox NIOS");

	let wapiBase = "/wapi/v1.0";
	try {
		wapiBase = `/wapi/v${discoverWapiVersion(http)}`;
	}
	catch (e) {
		http.debug(`Could not discover the supported WAPI version, defaulting to ${wapiBase}: ${wapiErrorMessage(e)}`);
	}

	// Best-effort platform facts (Grid name, this member's own hardware/
	// version) - not critical to the backup itself, so any failure here is
	// only logged, never fatal to the snapshot.
	let gridSnmpSetting = null;
	try {
		const grids = wapiGet(http, `${wapiBase}/grid`, { _return_fields: "name,snmp_setting" }).json();
		if (Array.isArray(grids) && grids.length > 0) {
			if (grids[0].name) {
				config.set("gridName", grids[0].name);
			}
			gridSnmpSetting = grids[0].snmp_setting;
		}
	}
	catch (e) {
		http.debug(`Could not retrieve Grid information: ${wapiErrorMessage(e)}`);
	}

	try {
		const members = wapiGet(http, `${wapiBase}/member`, {
			_return_fields: "host_name,platform,vip_setting,node_info,snmp_setting",
		}).json();

		let versionByMember = {};
		try {
			const statuses = wapiGet(http, `${wapiBase}/upgradestatus`, {
				type: "VNODE",
				_return_fields: "current_version,member",
			}).json();
			(Array.isArray(statuses) ? statuses : []).forEach((status) => {
				if (status && status.member) {
					versionByMember[status.member] = status.current_version;
				}
			});
		}
		catch (e) {
			http.debug(`Could not retrieve member software versions: ${wapiErrorMessage(e)}`);
		}

		// Find the one member (out of the whole Grid's member list) that this
		// device actually is - the one reachable at the device's own
		// management IP (see NOTES ON THIS DRIVER above: one Netshot device
		// = one Grid member, not the whole Grid).
		const mgmtIp = device.get("managementIpAddress");
		const memberList = Array.isArray(members) ? members : [];
		let selfMember = memberList.find((member) => member.vip_setting && member.vip_setting.address === mgmtIp);
		if (!selfMember && memberList.length > 0) {
			selfMember = memberList[0]; // Fallback: unexpected, but better than nothing.
		}

		if (selfMember) {
			if (selfMember.host_name) {
				device.set("name", shortHostname(selfMember.host_name));
			}
			if (selfMember.platform) {
				config.set("platform", selfMember.platform);
			}

			// This member's own SNMP contact/location, falling back to the
			// Grid-wide default (member-level snmp_setting only overrides it
			// when explicitly set - most members are expected to inherit it).
			const memberSnmpSetting = selfMember.snmp_setting || {};
			const contact = firstSnmpValue(memberSnmpSetting.syscontact)
				|| firstSnmpValue(gridSnmpSetting && gridSnmpSetting.syscontact);
			const location = firstSnmpValue(memberSnmpSetting.syslocation)
				|| firstSnmpValue(gridSnmpSetting && gridSnmpSetting.syslocation);
			if (contact) {
				device.set("contact", contact);
			}
			if (location) {
				device.set("location", location);
			}

			addLan1Interface(device, selfMember.vip_setting);

			const nodes = Array.isArray(selfMember.node_info) ? selfMember.node_info : [];
			addNodeModules(device, nodes);
			if (nodes.length > 0) {
				const activeNode = findActiveNode(nodes);
				if (activeNode.hwid) {
					device.set("serialNumber", activeNode.hwid);
				}
			}

			const version = versionByMember[selfMember.host_name]
				|| Object.values(versionByMember)[0];
			if (version) {
				device.set("softwareVersion", version);
				config.set("niosVersion", version);
			}
		}
	}
	catch (e) {
		http.debug(`Could not retrieve Grid member information: ${wapiErrorMessage(e)}`);
	}

	// Ask NIOS to generate the Grid backup archive server-side; getgriddata
	// hands back a one-time token + download URL (see NOTES ON THIS DRIVER
	// above), which is fetched with a binary-safe HTTPS GET and committed as
	// the backupArchive BinaryFile attribute. NIOS is told the download is
	// done (downloadcomplete) whether or not the fetch itself succeeded, so
	// it can release the one-time download resource either way.
	const backup = wapiPost(http, `${wapiBase}/fileop`, {
		type: "BACKUP",
		nios_data: true,
		discovery_data: false,
	}, { _function: "getgriddata" }).json();
	if (!backup || !backup.url || !backup.token) {
		throw "NIOS did not return a download URL/token for the Grid backup";
	}
	// getgriddata's "url" is an absolute URL (scheme+host+path[?query]); only
	// the path is kept for http.download() (see NOTES ON THIS DRIVER above) -
	// and, in case it carries a query string, that's split off and passed
	// separately rather than left embedded in the path, for the same reason
	// _function is passed as an actual query parameter above.
	const downloadUrl = backup.url.replace(/^https?:\/\/[^/]+/, "");
	const queryIndex = downloadUrl.indexOf("?");
	const downloadPath = queryIndex === -1 ? downloadUrl : downloadUrl.substring(0, queryIndex);
	const downloadQuery = {};
	if (queryIndex !== -1) {
		downloadUrl.substring(queryIndex + 1).split("&").forEach((pair) => {
			const [k, v] = pair.split("=");
			if (k) {
				downloadQuery[decodeURIComponent(k)] = decodeURIComponent(v || "");
			}
		});
	}
	const storeFileName = downloadPath.split("/").pop() || "grid-backup.tar.gz";

	try {
		// This endpoint (unlike the /wapi/ ones) has been observed to reject a
		// GET with neither header with a 415 "Unsupported Media Type" - so
		// both are sent explicitly here, matching what a real browser/curl
		// (used by every other capture/example of this same download) sends
		// by default and Netshot's HTTP client otherwise wouldn't.
		http.download("backupArchive", downloadPath, {
			storeFileName: storeFileName,
			query: downloadQuery,
			headers: { "Accept": "*/*", "Content-Type": "application/octet-stream" },
		});
	}
	finally {
		try {
			wapiPost(http, `${wapiBase}/fileop`, { token: backup.token }, { _function: "downloadcomplete" });
		}
		catch (e) {
			http.debug(`Could not signal download completion to NIOS: ${wapiErrorMessage(e)}`);
		}
	}
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	// Infoblox's IANA-assigned enterprise number is 7779, confirmed against a
	// live NIOS VM (sysObjectID 1.3.6.1.4.1.7779.1.1004). Its sysDescr turned
	// out to be a plain Linux kernel string (NIOS is Linux-based underneath)
	// with no "Infoblox" wording at all - e.g. "Linux <host> 5.8.0-63-generic
	// #71~20.04.1-Ubuntu SMP ... x86_64" - so unlike most other drivers'
	// snmpAutoDiscover, the sysObjectID prefix alone is the only reliable
	// signal here; requiring anything from sysDesc would reject a real box.
	return sysObjectID.startsWith("1.3.6.1.4.1.7779.");
}
