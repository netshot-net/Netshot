# Devices

The Devices section is where you browse the devices Netshot manages, search across them, and start tasks to refresh their status or configure them.

A *device* is a network device as seen by Netshot. What Netshot can see and do with a device — vendor, family, data collected, automatic discovery and snapshots — depends entirely on the [device driver](device-drivers.md) loaded for it.

## Adding devices

Before adding a device, you must have created the required [credential sets](administration.md#device-credentials) in the Admin page. Adding devices requires a read-write role.

To add a single device:

- On the Devices page, click **Add device...**.
- Select the [domain](administration.md#device-domains) for the device.
- Enter the device's management IP address, or a hostname the Netshot server can resolve.
- Check **Autodiscover device type** to have Netshot identify the device via SNMP (requires a valid SNMP credential set); otherwise select the device type manually.
- Once the device type is known, the matching driver is assigned and Netshot starts a snapshot task to capture the device's data, including its current configuration.

To add many devices at once:

- **Add device...** dropdown → **Scan subnet(s) for devices...**
- Select the domain for the discovered devices.
- Enter one IP address (e.g. `1.2.3.4`) or subnet (e.g. `1.2.3.0/24`) per line, then click **Scan**.
- Netshot polls each address with the SNMP credential sets known for the domain and creates devices from the responses.

## Searching for devices

Type a device's name (or virtual name, e.g. a VDC) or IP address in the **Search...** box and press Enter. Click the clear (✕) button to remove the filter.

For more complex queries, open **Advanced search** and build a logical search expression with the provided buttons. Selecting a device type lets you filter on fields specific to that type.

## Device groups

Groups let you organize devices for filtering, for running tasks over many devices at once, as the target of [compliance policies](compliance.md), and to break down [reports](reports.md). Managing groups requires a read-write role.

To create a group: **Add devices...** dropdown → **Add a group...**, then choose its type — **static** (devices selected manually) or **dynamic** (devices matched by a saved search expression, automatically re-evaluated whenever the group definition or a device changes). The type can't be changed after creation.

Selecting a group in the group tree filters the device list to its members and reveals **Edit**/**Delete** buttons.

- Arrange groups hierarchically by entering a folder path, e.g. `Backbone A/Core devices/P routers` — this only affects how groups are displayed.
- Check **Hide this group in reports** to keep a group usable for filtering/policies without it showing up in reports.
- Deleting a group also deletes any policy or scheduled task associated with it.

## Device information

Selecting a device in the list opens its detail view. The **General** tab shows:

| Field | Source |
|---|---|
| Name | Hostname, captured by the driver during snapshots |
| Management address | The address (IP or hostname) Netshot uses to reach the device |
| Management domain | The device's assigned domain |
| Location / Contact | Captured by the driver, often from SNMP location/contact |
| Network class | ROUTER, SWITCH, FIREWALL, LOAD-BALANCER, etc., set by the driver |
| Device type | The driver used to talk to the device — cannot be changed without deleting the device |
| Family / Software version / Serial number | Captured by the driver |
| Creation date / Last change | Set by Netshot |
| Comments | Free text, set manually or by a device script |
| Member of | Groups the device belongs to |

Additional fields may appear depending on the driver. The **Interfaces** and **Modules** tabs are populated by the driver during snapshots, with accuracy depending on what the driver's CLI parsing can extract.

## Configuration history

The **Configurations** tab lists the device's configuration history. What's stored in a configuration entry — and what counts as *comparable* — is defined by the driver; typically anything that changes over time and is worth tracking.

Click a date to view that configuration's content. Click **Compare** to see the differences between two successive configurations in a new window (allow popups). Comparable items are diffed automatically. Use **Previous**/**Next** to step through history, or drag two non-successive dates onto the **Drop to Diff** targets and click **Compare**.

Long text items (e.g. a full running configuration) can be viewed in a new window via **View**, or downloaded via **Download**.

## Device properties

Editable via the wrench icon in the device toolbar (requires read-write role):

- **Name** — read-only, set by the driver.
- **Management address** — the IP or hostname Netshot uses to reach the device; can be changed if needed.
- **Domain** — the device's [management domain](administration.md#device-domains).
- **Credential sets** — which set(s) to try, in order, when connecting.
- **In case of failure, also try all known credentials** — falls back to every credential set associated with the device's domain (or with no domain) if the selected set(s) fail.
- **Comments** — free text.

Depending on the device's driver, extra **driver-defined options** may also be editable here — admin-configurable settings the driver reads back at runtime (for example, a "take full backup archive" toggle). Which options exist, and their type, is defined by the driver — see [Device drivers](device-drivers.md).

A device can have several **network accesses** (management addresses), each independently configurable — see [Connection security](#connection-security) below for the per-access settings. Disabling a device (toolbar button) stops all snapshot tasks against it until re-enabled.

## Connection security

Each network access on a device — not just the primary one — carries its own connection-security settings, independent of the credential set used.

**SSH host key verification**, for SSH-based access:

- **Trust any** — accept any host key presented by the device (legacy/default behavior; offers no protection against interception).
- **Trust known keys** — trust-on-first-use: the first key seen for a given algorithm is learned and pinned automatically; afterwards, only a matching key is accepted, and a key for a new algorithm is rejected rather than silently trusted. Known keys can also be pasted in manually (one per line, `<algorithm> <base64-key>` format, `known_hosts`-style without a hostname prefix).

**HTTPS certificate trust**, for HTTP(S)-based access:

- **Trust any** — accept any server certificate, no hostname check. Historical/default behavior.
- **System truststore** — validate against the JVM's default trust store; the connection hostname is verified against the certificate.
- **Custom CA** — validate against a CA certificate you paste in (PEM); the connection hostname is verified against the certificate.

!!! note
    Hostname verification isn't offered as an independent toggle: outside of **Trust any**, it's always enabled together with certificate validation — checking the hostname alone, without validating the certificate chain, wouldn't add real protection.

A device's management address can be an IP address or a resolvable hostname.

## Snapshots

Scheduling snapshots requires a read-write role.

During a snapshot, Netshot connects to the device — over SSH or Telnet, depending on the credentials available and what the [driver](device-drivers.md) supports — then delegates the actual data collection to the driver.

- Click **Snapshot** in the device toolbar to force one immediately.
- Like any other [task](tasks.md), snapshots can be scheduled to repeat.
- Snapshot a group of devices via **Schedule task...** in the main toolbar.
- If a device sends SNMP traps or Syslog messages on configuration change (and its driver supports detecting this), Netshot can trigger an automatic snapshot a few minutes later. This requires the device to be configured to send those messages to Netshot, and the [SNMP trap receiver](../configuration-reference.md#snmp-trap-receiver) or [Syslog server](../configuration-reference.md#syslog-server) to be enabled.

## Running scripts on devices

Custom JavaScript scripts can be written and run against a device (or a group of devices) — for example to push a configuration change. Open the run-script dialog via the **Play** button in the device toolbar.

A script must define a `run` function, its entry point, receiving two arguments:

- `cli` — runs commands on the device and collects output. `cli.macro("name")` runs a macro defined by the device driver (typically used to switch CLI mode); `cli.command("command")` runs a single command.
- `device` — represents the device, and can be used to read data collected by the last snapshot.

Scripts can declare custom input parameters, requested from the user at run time:

```js
const Input = {
    snmpLocation: {
        label: "Location",
        description: "E.g. FR - Toulouse",
        regExp: /[A-Z]{2} - .+/,
    },
    snmpContact: {
        label: "Contact",
        optional: true,
    },
};

function run(cli, device) {
    // Change SNMP location and contact
    const { snmpLocation, snmpContact } = cli.userInputs;
    cli.macro("configure");
    cli.command(`snmp-server location ${snmpLocation}`);
    if (snmpContact) {
        cli.command(`snmp-server contact ${snmpContact}`);
    }
    else {
        cli.command(`no snmp-server contact`);
    }
    cli.macro("end");
    cli.macro("save");
}
```

When the script runs, Netshot shows a form for the declared inputs (here `snmpLocation` and `snmpContact`), and the values become available in `cli.userInputs`.

Scripts can be organized into folders for easier management, and saved/reloaded with the **Load**/**Save** buttons. When running a script against a device, you can also ask Netshot to automatically take a snapshot afterwards, and/or chain a diagnostics run and a compliance check once the script completes.
