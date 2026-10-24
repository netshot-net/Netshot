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
 * This driver talks to the Cisco APIC (ACI fabric controller) REST API over
 * HTTPS only, using Netshot's HTTP client. It logs in via a session cookie,
 * (re)creates a fixed-name "config export to remote host" policy (an ACI
 * fileRemotePath + configExportP pair, linked by a configRsRemotePath child)
 * pointing at a one-time SFTP drop point on Netshot, triggers it, and waits
 * for APIC to push the resulting archive back over SFTP (the same
 * upload-ticket mechanism used by SSH/Telnet-based drivers). The archive is
 * stored as-is (still encrypted/whatever format ACI produces) as a
 * BinaryFile config attribute.
 *
 * The fileRemotePath/configExportP/configRsRemotePath object model and the
 * configJob status attributes (operSt/details/fileName) are taken from an
 * actual captured request/response trace, not guessed.
 *
 * Unlike the historical tool this replaces (which left its export policy in
 * place across runs), this driver deletes both objects it creates - the
 * export policy (configExportP) and the remote path (fileRemotePath, which
 * also carries the one-time SFTP ticket's credentials) - at the end of a
 * successful snapshot, in addition to the idempotent delete-then-recreate at
 * the start of every run. Nothing Netshot-specific is left in the fabric
 * config between runs.
 *
 * PREREQUISITE: the fabric must have Global AES Encryption enabled (Fabric >
 * Fabric Policies > Policies > Global AES Passphrase and Keys Encryption,
 * "Enable Encryption" ticked, with a passphrase set) before a config export
 * job can succeed. Without it, the configExportP POST below is rejected with
 * HTTP 400 and APIC error code 105, "Strong encryption key is mandatory to
 * the export the configuration."
 */

const Info = {
	name: "CiscoAPIC",
	description: "Cisco APIC (ACI fabric controller)",
	author: "Netshot Team",
	version: "0.1"
};

const Config = {
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
	"apicVersion": {
		type: "Text",
		title: "APIC version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
};

const Device = {
};

/**
 * A single HTTPS access, authenticated with a session cookie obtained by
 * POSTing local credentials to /api/aaaLogin.json (the client transparently
 * logs in on the first request and replays the resulting APIC-cookie on
 * every later one).
 */
const HTTP = {
	https: {
		auth: {
			type: "cookie",
			method: "post",
			path: "/api/aaaLogin.json",
			data: {
				aaaUser: {
					attributes: {
						name: "$$NetshotUsername$$",
						pwd: "$$NetshotPassword$$",
					}
				}
			},
			contentType: "json",
		}
	}
};

// The fixed name under which Netshot (re)creates its own config-export and
// remote-path policies in the fabric, every run (delete-then-recreate).
const EXPORT_NAME = "netshot";

/**
 * APIC reports API errors as a 4xx/5xx response whose body looks like
 * {"totalCount":"1","imdata":[{"error":{"attributes":{"code":"105","text":"..."}}}]}.
 * Dig that human-readable text out of a caught HTTP error so task failure
 * messages say *why* APIC rejected the request, not just the HTTP status.
 */
function apicErrorMessage(e) {
	try {
		const data = e && e.response && e.response.data;
		const body = typeof data === "string" ? JSON.parse(data) : data;
		const item = body && Array.isArray(body.imdata) && body.imdata.find((i) => i.error);
		if (item && item.error && item.error.attributes && item.error.attributes.text) {
			return item.error.attributes.text;
		}
	}
	catch (parseError) {
		// Body wasn't the expected APIC error shape - fall through.
	}
	return String((e && e.message) || e);
}

/**
 * POST wrapper that re-throws any HTTP error with the APIC-provided error
 * text (see apicErrorMessage) instead of the generic "status code NNN".
 */
function apicPost(http, path, data) {
	try {
		return http.post(path, data);
	}
	catch (e) {
		throw `APIC request to ${path} failed: ${apicErrorMessage(e)}`;
	}
}

/**
 * Add a management network interface, from an APIC-style dotted address +
 * prefix-length-as-string pair (topSystem's oobMgmtAddr/oobMgmtAddrMask or
 * inbMgmtAddr/inbMgmtAddrMask). Skips silently if unset (e.g. "0.0.0.0").
 */
function addMgmtInterface(device, name, address, mask) {
	if (!address || address === "0.0.0.0" || !mask) {
		return;
	}
	const prefixLength = parseInt(mask, 10);
	if (isNaN(prefixLength)) {
		return;
	}
	device.add("networkInterface", {
		name: name,
		enabled: true,
		level3: true,
		ip: [{ ip: address, mask: prefixLength, usage: "PRIMARY" }],
	});
}

/**
 * Fetch and record hardware inventory (chassis/board/CPU/memory/storage/PSU/
 * fans) for a single controller node, strictly scoped to that node's own
 * "ch" (chassis) subtree via its dn - never a fabric-wide class query, so
 * only this controller's own hardware is ever reported, not the rest of the
 * fabric (other controllers, leafs, spines).
 *
 * NOTE: the eqpt* attribute names used below (model/pid/ser/rn) are the
 * conventional ones across ACI's equipment MIT, but haven't been confirmed
 * against a real APIC capture for controller nodes specifically - check the
 * "Found hardware component" debug lines the first time this runs and adjust
 * the attribute fallbacks below if the real field names differ.
 */
function addHardwareComponents(http, device, nodeDn) {
	const chDn = `${nodeDn}/ch`;
	const inventory = http.get(`/api/node/mo/${chDn}.json`, {
		query: {
			"query-target": "subtree",
			"target-subtree-class": "eqptCh,eqptBoard,eqptCPU,eqptDimm,eqptStorage,eqptPsu,eqptFan,eqptFt",
		}
	}).json();
	const items = (inventory && Array.isArray(inventory.imdata)) ? inventory.imdata : [];
	items.forEach((item, index) => {
		const className = Object.keys(item)[0];
		const attrs = (item[className] && item[className].attributes) || {};
		device.add("module", {
			slot: attrs.rn || `${className}-${index + 1}`,
			partNumber: attrs.model || attrs.pid || attrs.type || "",
			serialNumber: attrs.ser || attrs.serial || attrs.sn || "",
		});
	});
}

/**
 * Delete the fileRemotePath and configExportP objects Netshot creates under
 * EXPORT_NAME - the only objects this driver ever creates in the fabric.
 * Called both before (re)creating them (idempotent cleanup of a possible
 * leftover from a previous run) and after a successful run, so nothing
 * Netshot-specific is left behind in the fabric config.
 */
function cleanupExportObjects(http) {
	apicPost(http, `/api/node/mo/uni/fabric/configexp-${EXPORT_NAME}.json`, {
		configExportP: {
			attributes: { dn: `uni/fabric/configexp-${EXPORT_NAME}`, status: "deleted" },
			children: [],
		}
	});
	apicPost(http, `/api/node/mo/uni/fabric/path-${EXPORT_NAME}.json`, {
		fileRemotePath: {
			attributes: { dn: `uni/fabric/path-${EXPORT_NAME}`, status: "deleted" },
			children: [],
		}
	});
}

function snapshot(client, device, config) {

	const http = client.create("http");

	device.set("networkClass", "SERVER");
	device.set("family", "Cisco APIC");

	// Best-effort platform facts (name/version/serial of the controller Netshot
	// is actually talking to) - not critical to the backup itself, so any
	// failure here is only logged, never fatal to the snapshot.
	try {
		const topSystem = http.get("/api/node/class/topSystem.json", {
			query: { "query-target-filter": 'eq(topSystem.role,"controller")' }
		}).json();
		const controllers = (topSystem && Array.isArray(topSystem.imdata)) ? topSystem.imdata : [];
		const mgmtIp = device.get("managementIpAddress");
		let self = controllers.find((item) => item.topSystem && item.topSystem.attributes
			&& (item.topSystem.attributes.oobMgmtAddr === mgmtIp || item.topSystem.attributes.inbMgmtAddr === mgmtIp));
		if (!self) {
			self = controllers[0]; // Fallback: any controller, better than nothing.
		}
		if (self) {
			const attributes = self.topSystem.attributes;
			if (attributes.name) {
				device.set("name", attributes.name);
			}
			if (attributes.version) {
				device.set("softwareVersion", attributes.version);
				config.set("apicVersion", attributes.version);
			}
			if (attributes.serial) {
				device.set("serialNumber", attributes.serial);
			}

			addMgmtInterface(device, "oob-mgmt", attributes.oobMgmtAddr, attributes.oobMgmtAddrMask);
			addMgmtInterface(device, "inb-mgmt", attributes.inbMgmtAddr, attributes.inbMgmtAddrMask);

			if (attributes.dn) {
				try {
					addHardwareComponents(http, device, attributes.dn);
				}
				catch (e) {
					http.debug(`Could not retrieve hardware inventory: ${apicErrorMessage(e)}`);
				}
			}
		}
	}
	catch (e) {
		http.debug(`Could not retrieve controller information: ${apicErrorMessage(e)}`);
	}

	// Ask Netshot for a one-time SFTP drop point, so APIC can push the backup
	// archive back to Netshot once the config export job is done.
	const ticket = config.requestUpload({ method: "sftp" });

	// Delete any pre-existing policy objects under our fixed name (idempotent
	// cleanup before recreating them) - ACI deletes objects via a POST of the
	// object's dn with status "deleted", there is no HTTP DELETE verb for MOs.
	cleanupExportObjects(http);

	// (Re)create the remote path pointing at Netshot's SFTP drop point.
	apicPost(http, `/api/node/mo/uni/fabric/path-${EXPORT_NAME}.json`, {
		fileRemotePath: {
			attributes: {
				dn: `uni/fabric/path-${EXPORT_NAME}`,
				name: EXPORT_NAME,
				status: "created",
				host: ticket.host,
				protocol: "sftp",
				remotePath: "/",
				remotePort: String(ticket.port),
				userName: ticket.username,
				userPasswd: ticket.password,
			},
			children: [],
		}
	});

	// (Re)create the export policy, linked to the remote path above, and
	// trigger it immediately (adminSt "triggered").
	apicPost(http, `/api/node/mo/uni/fabric/configexp-${EXPORT_NAME}.json`, {
		configExportP: {
			attributes: {
				dn: `uni/fabric/configexp-${EXPORT_NAME}`,
				name: EXPORT_NAME,
				adminSt: "triggered",
			},
			children: [
				{
					configRsRemotePath: {
						attributes: { status: "created,modified", tnFileRemotePathName: EXPORT_NAME },
						children: [],
					}
				}
			],
		}
	});

	// Poll the job status - each triggered export creates a child "configJob"
	// (named after its execution timestamp) under this container.
	const jobsPath = `/api/node/mo/uni/backupst/jobs-[uni/fabric/configexp-${EXPORT_NAME}].json`;
	let completed = false;
	let maxPolls = 40; // ~20 minutes at 30s
	while (maxPolls > 0) {
		http.sleep(30000);
		maxPolls -= 1;
		let runs;
		try {
			const jobs = http.get(jobsPath, { query: { "query-target": "children" } }).json();
			runs = (jobs && Array.isArray(jobs.imdata)) ? jobs.imdata : [];
		}
		catch (e) {
			http.debug(`Error while polling the export job status: ${apicErrorMessage(e)}`);
			continue;
		}
		if (runs.length === 0) {
			continue;
		}
		runs.sort((a, b) => {
			const ta = (a.configJob && a.configJob.attributes && a.configJob.attributes.executeTime) || "";
			const tb = (b.configJob && b.configJob.attributes && b.configJob.attributes.executeTime) || "";
			return tb.localeCompare(ta);
		});
		const latest = runs[0].configJob.attributes;
		http.debug(`Export job status: operSt=${latest.operSt}, details=${latest.details}`);
		if (latest.operSt === "success") {
			completed = true;
			break;
		}
		if (latest.operSt === "fail" || latest.operSt === "failed") {
			throw `APIC config export job failed: ${latest.details || latest.descr || "unknown error"}`;
		}
	}
	if (!completed) {
		http.debug("Timed out waiting for the export job to report success; still waiting for the archive upload.");
	}

	const uploadResult = config.awaitUpload(ticket.id, 600000);
	if (uploadResult.files.length !== 1) {
		throw `Invalid number of files (${uploadResult.files.length}) received by Netshot server`;
	}
	const file = uploadResult.files[0];
	config.commitUpload(ticket.id, file.id, "backupArchive");

	// Remove the export policy and remote path now that the archive has been
	// retrieved, rather than leaving Netshot-specific objects (including the
	// remote path's one-time SFTP credentials, pointing nowhere afterwards) in
	// the fabric config. Best-effort: the snapshot itself already succeeded,
	// so a cleanup failure is only logged.
	try {
		cleanupExportObjects(http);
	}
	catch (e) {
		// apicPost() already formats HTTP errors into a readable string.
		http.debug(`Could not remove the '${EXPORT_NAME}' export policy objects: ${e}`);
	}
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return false;
}
