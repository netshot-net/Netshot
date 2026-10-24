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
 * This driver talks to the Cisco Nexus Dashboard (ND) REST API - the
 * platform hosting services/apps such as Orchestrator (NDO), Insights (NDI),
 * Fabric Controller (NDFC), Data Broker (NDB) and others - over HTTPS only
 * (there is no useful CLI for this workflow), using Netshot's HTTP client.
 * It logs in via a session cookie, then triggers a config-only backup,
 * has ND push the resulting archive to Netshot over SFTP (the same
 * upload-ticket mechanism used by SSH/Telnet-based drivers), and stores
 * the archive as-is (still encrypted) as a BinaryFile config attribute.
 *
 * ND has two REST API families for backup/restore and infra facts, named
 * below by path prefix (not "old"/"new", which would go stale): "infraV1"
 * (/api/v1/infra/..., confirmed against Cisco's infra-421 spec, ND 4.2.1)
 * and "platformV1" (/nexus/infra/api/platform/v1/... +
 * /api/config|action/class/..., confirmed against Cisco's nexus-dashboard-321
 * spec, ND 3.2.1). Each area (BACKUP_API, fetchClusterNodes,
 * fetchClusterConfig) tries infraV1 first and falls back to platformV1 on a
 * 404. Installed-services has no platformV1 equivalent, so that Config
 * attribute stays empty on ND 3.2.x.
 *
 * The authoritative "backup is done" signal is the archive actually being
 * pushed to Netshot (config.awaitUpload); status polling is just to fail
 * fast and leave a debug trail.
 */

const Info = {
	name: "CiscoND",
	description: "Cisco Nexus Dashboard (ND)",
	author: "Netshot Team",
	version: "0.4"
};

const Config = {
	"backupArchive": {
		type: "BinaryFile",
		title: "Backup Archive",
	},
	"clusterVersion": {
		type: "Text",
		title: "Nexus Dashboard version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"orcVersion": {
		type: "Text",
		title: "Orchestrator (NDO) version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"insVersion": {
		type: "Text",
		title: "Insights (NDI) version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"fcoVersion": {
		type: "Text",
		title: "Fabric Controller (NDFC) version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"dbrVersion": {
		type: "Text",
		title: "Data Broker (NDB) version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"crwVersion": {
		type: "Text",
		title: "Crosswork version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"cwaVersion": {
		type: "Text",
		title: "Crosswork AI version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"splVersion": {
		type: "Text",
		title: "Splunk integration version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"colVersion": {
		type: "Text",
		title: "Collectors version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"emsVersion": {
		type: "Text",
		title: "EMS version",
		comparable: true,
		searchable: true,
		checkable: true,
	},
	"clusterConfig": {
		type: "LongText",
		title: "Cluster Configuration",
		comparable: true,
		searchable: true,
		checkable: true,
	},
};

const Device = {
	"services": {
		type: "Text",
		title: "Installed Services",
	},
};

const Options = {
	"backupEncryptionKey": {
		type: "Text",
		title: "Backup encryption key",
		default: "Netshot123!",
	},
	"fullBackup": {
		type: "Boolean",
		title: "Take backup archive",
		default: true,
	},
};

/**
 * A single HTTPS access, authenticated with a session cookie obtained by
 * POSTing local credentials to /login (the client transparently logs in on
 * the first request and replays the resulting cookie on every later one).
 */
const HTTP = {
	https: {
		auth: {
			type: "cookie",
			method: "post",
			path: "/login",
			data: {
				domain: "local",
				userName: "$$NetshotUsername$$",
				userPasswd: "$$NetshotPassword$$",
			},
			contentType: "json",
		}
	}
};

// Fixed SFTP storage destination name registered on ND, and the fallback
// encryption passphrase (Netshot stores the archive as-is, still encrypted).
const SCP_STORAGE_NAME = "netshot";
const BACKUP_ENCRYPTION_KEY = "Netshot123!";

// Request builders and status-parsing rules per API family (see header
// note). Picked once per snapshot in createStorage()'s 404 fallback and
// reused for the rest of the backup workflow.
const BACKUP_API = {
	infraV1: {
		createStorage(http, ticket) {
			http.post("/remoteStorage", {
				name: SCP_STORAGE_NAME,
				type: "sftp",
				hostname: ticket.host,
				path: "/",
				port: ticket.port,
				authentication: {
					type: "password",
					username: ticket.username,
					password: ticket.password,
					ignoreHostKeyValidation: true,
				},
			});
		},
		createBackup(http, backupName, encryptionKey) {
			http.post("/backups", {
				destination: SCP_STORAGE_NAME,
				encryptionKey: encryptionKey,
				name: backupName,
				type: "configOnly",
			});
		},
		statusPath(backupName) {
			return `/backups/${backupName}`;
		},
		isFailure(data) {
			return /"status"\s*:\s*"failed"/i.test(data);
		},
		isSuccess(data) {
			return /"status"\s*:\s*"completed"/i.test(data);
		},
		deleteStorage(http) {
			http.delete(`/remoteStorage/${SCP_STORAGE_NAME}`);
		},
	},
	platformV1: {
		createStorage(http, ticket) {
			http.post("/api/config/class/securecopystorage", {
				acceptHostKey: true,
				authentication: "password",
				host: ticket.host,
				name: SCP_STORAGE_NAME,
				passphrase: "",
				password: ticket.password,
				path: "/",
				port: ticket.port,
				protocol: "sftp",
				sshKey: "",
				username: ticket.username,
			});
		},
		createBackup(http, backupName, encryptionKey) {
			http.post("/api/action/class/backuprestore/backup", {
				destination: SCP_STORAGE_NAME,
				encryptionKey: encryptionKey,
				name: backupName,
				type: "config-only",
			});
		},
		statusPath(backupName) {
			return `/api/action/class/backuprestore/backup/${backupName}`;
		},
		isFailure(data) {
			return /"status"\s*:\s*"failure"/i.test(data);
		},
		isSuccess(data) {
			return /"status"\s*:\s*"success"/i.test(data);
		},
		deleteStorage(http) {
			http.delete(`/api/config/class/securecopystorage/${SCP_STORAGE_NAME}`);
		},
	},
};

// Maps a systemSoftware/services "serviceName" to the Config attribute
// holding that component's version. "core" and "testapp" aren't user-facing
// so they're unmapped.
const SERVICE_VERSION_ATTRIBUTES = {
	"orchestration": "orcVersion",
	"telemetry": "insVersion",
	"fabric-management": "fcoVersion",
	"data-broker": "dbrVersion",
	"crosswork": "crwVersion",
	"crosswork-ai": "cwaVersion",
	"splunk": "splVersion",
	"collectors": "colVersion",
	"ems": "emsVersion",
};

// True if a caught HTTP error is a 404, meaning "this ND API family doesn't
// have that endpoint" as opposed to a genuine error.
function isNotFoundError(e) {
	return !!(e && e.response && e.response.status === 404);
}

// Add a management or data-network interface for one cluster node, from a
// network block already normalized to { cidr: "ip/prefix", vlanId }.
// Interface names are prefixed with the node name to avoid collisions
// between cluster members on this single Netshot device.
function addNodeInterface(device, nodeName, baseName, network) {
	if (!network || typeof network.cidr !== "string") {
		return;
	}
	const parts = network.cidr.split("/");
	if (parts.length !== 2) {
		return;
	}
	const ip = parts[0];
	const prefixLength = parseInt(parts[1], 10);
	if (!ip || isNaN(prefixLength)) {
		return;
	}
	const iface = {
		name: `${nodeName || "node"}-${baseName}`,
		enabled: true,
		level3: true,
		ip: [{ ip: ip, mask: prefixLength, usage: "PRIMARY" }],
	};
	if (network.vlanId) {
		iface.description = `VLAN ${network.vlanId}`;
	}
	device.add("networkInterface", iface);
}

// Fetch cluster nodes, normalized to { name, model, serialNumber,
// firmwareVersion, mgmtNetwork, dataNetwork } (network: null or { cidr,
// vlanId }) regardless of API family.
function fetchClusterNodes(http) {
	try {
		const response = http.get("/api/v1/infra/cluster/nodes").json();
		const nodes = (response && Array.isArray(response.nodes)) ? response.nodes : [];
		return nodes.map((node) => {
			const toNetwork = (block) => (block && block.network && typeof block.network.cidrv4 === "string")
				? { cidr: block.network.cidrv4, vlanId: block.vlanId || null }
				: null;
			return {
				name: node.name || "",
				model: node.model || "",
				serialNumber: node.serialNumber || "",
				firmwareVersion: node.firmwareVersion || "",
				mgmtNetwork: toNetwork(node.managementNetwork),
				dataNetwork: toNetwork(node.dataNetwork),
			};
		});
	}
	catch (e) {
		if (!isNotFoundError(e)) {
			throw e;
		}
	}

	const response = http.get("/nexus/infra/api/platform/v1/nodes").json();
	const items = (response && Array.isArray(response.items)) ? response.items : [];
	return items.map((item) => {
		const spec = item.spec || {};
		const status = item.status || {};
		const toNetwork = (block) => (block && typeof block.subnet === "string")
			? { cidr: block.subnet, vlanId: block.vlanID || null }
			: null;
		return {
			name: spec.name || "",
			model: status.model || "",
			serialNumber: spec.serialNumber || "",
			firmwareVersion: status.version || "",
			mgmtNetwork: toNetwork(spec.oobNetwork),
			dataNetwork: toNetwork(spec.inbNetwork),
		};
	});
}

// Fetch the plaintext cluster configuration (DNS/NTP/proxy/network
// settings), or null if no cluster was reported.
function fetchClusterConfig(http) {
	try {
		return http.get("/api/v1/infra/cluster/config").json();
	}
	catch (e) {
		if (!isNotFoundError(e)) {
			throw e;
		}
	}

	const response = http.get("/nexus/infra/api/platform/v1/clusters").json();
	const items = (response && Array.isArray(response.items)) ? response.items : [];
	return (items.length > 0) ? (items[0].spec || {}) : null;
}

function snapshot(client, device, config) {

	const http = client.create("http");

	device.set("networkClass", "SERVER");
	device.set("family", "Cisco Nexus Dashboard");

	// Best-effort cluster/node facts - never fatal to the snapshot.
	try {
		const nodes = fetchClusterNodes(http);
		const mgmtIp = device.get("managementIpAddress");
		let self = null;

		nodes.forEach((node) => {
			device.add("module", {
				slot: node.name,
				partNumber: node.model,
				serialNumber: node.serialNumber,
			});
			addNodeInterface(device, node.name, "mgmt", node.mgmtNetwork);
			addNodeInterface(device, node.name, "data", node.dataNetwork);

			if (node.mgmtNetwork) {
				const nodeIp = node.mgmtNetwork.cidr.split("/")[0];
				if (mgmtIp && nodeIp === mgmtIp) {
					self = node;
				}
			}
		});
		if (!self && nodes.length > 0) {
			self = nodes[0]; // Fallback: any node, better than nothing.
		}
		if (self) {
			if (self.name) {
				device.set("name", self.name);
			}
			if (self.firmwareVersion) {
				device.set("softwareVersion", self.firmwareVersion);
				config.set("clusterVersion", self.firmwareVersion);
			}
			if (self.serialNumber) {
				device.set("serialNumber", self.serialNumber);
			}
		}
	}
	catch (e) {
		http.debug(`Could not retrieve cluster node information: ${e}`);
	}

	// Best-effort installed-services list and per-component versions.
	// infraV1 only - no platformV1 equivalent, so this just 404s on ND 3.2.x.
	try {
		const servicesResponse = http.get("/api/v1/infra/systemSoftware/services").json();
		const services = (servicesResponse && Array.isArray(servicesResponse.services)) ? servicesResponse.services : [];
		const summaries = [];
		services.forEach((service) => {
			const name = service.serviceName || "unknown";
			const version = service.version || "";
			// No top-level enabled/disabled flag: operState is only present
			// while deployed, so its absence plus a "disable" op means disabled.
			const disabled = !(service.operState && service.operState.state)
				&& !!(service.deploymentState && service.deploymentState.operation === "disable");
			const state = disabled ? "disabled" : ((service.operState && service.operState.state) || "unknown");
			summaries.push(`${name} ${version} (${state})`.trim());

			const attribute = SERVICE_VERSION_ATTRIBUTES[name];
			if (attribute && !disabled && version) {
				config.set(attribute, version);
			}
		});
		summaries.sort();
		device.set("services", summaries.join(", "));
	}
	catch (e) {
		http.debug(`Could not retrieve installed services: ${e}`);
	}

	// Best-effort plaintext cluster configuration, cheaper than a full backup.
	try {
		const clusterConfig = fetchClusterConfig(http);
		if (clusterConfig) {
			config.set("clusterConfig", JSON.stringify(clusterConfig, null, 2));
		}
	}
	catch (e) {
		http.debug(`Could not retrieve cluster configuration: ${e}`);
	}

	if (!device.options.fullBackup) {
		return;
	}

	const encryptionKey = device.options.backupEncryptionKey || BACKUP_ENCRYPTION_KEY;

	// One-time SFTP drop point so ND can push the backup archive to Netshot.
	const ticket = config.requestUpload({ method: "sftp" });
	const backupName = `nsbackup-${Date.now()}`;

	// Try infraV1, fall back to platformV1 on a 404; reused for the rest of
	// the workflow so it can't mix endpoint sets.
	let backupApi = BACKUP_API.infraV1;
	try {
		try {
			backupApi.createStorage(http, ticket);
		}
		catch (e) {
			if (!isNotFoundError(e)) {
				throw e;
			}
			backupApi = BACKUP_API.platformV1;
			backupApi.createStorage(http, ticket);
		}

		backupApi.createBackup(http, backupName, encryptionKey);

		// Poll just to fail fast and leave a debug trail; the real "done"
		// signal is config.awaitUpload below.
		let maxPolls = 40; // ~20 minutes at 30s
		while (maxPolls > 0) {
			http.sleep(30000);
			try {
				const statusResponse = http.get(backupApi.statusPath(backupName));
				if (backupApi.isFailure(statusResponse.data)) {
					throw `Backup job ${backupName} reported a failure: ${statusResponse.data}`;
				}
				if (backupApi.isSuccess(statusResponse.data)) {
					break;
				}
			}
			catch (e) {
				if (typeof e === "string" && e.indexOf("reported a failure") >= 0) {
					throw e;
				}
				http.debug(`Error while polling backup job status: ${e}`);
			}
			maxPolls -= 1;
		}

		// ND also uploads a small SFTP connectivity-test file (e.g.
		// "nd-copy.test") on the same ticket before the real archive, so more
		// than one file can show up here - match by name instead of count.
		// file.name is always a bare filename, never a path (Java-side it's
		// built from Path.getFileName()), so compare without a leading "/".
		const uploadResult = config.awaitUpload(ticket.id, 600000);
		const archiveName = `${backupName}.tar.gz`;
		const file = uploadResult.files.find((f) => f.name === archiveName);
		if (!file) {
			throw `Backup archive '${archiveName}' not found among ${uploadResult.files.length} file(s) received by Netshot server`;
		}
		config.commitUpload(ticket.id, file.id, "backupArchive");
	}
	finally {
		try {
			backupApi.deleteStorage(http);
		}
		catch (e) {
			http.debug(`Could not clean up the SFTP storage destination: ${e}`);
		}
	}
}

function analyzeTrap(trap, debug) {
	return false;
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
	return false;
}
