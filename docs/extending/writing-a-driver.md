# Writing a new driver for Netshot

## Introduction

Netshot uses drivers to talk to network equipment. A driver is a single JavaScript file — see [Device drivers](../user-guide/device-drivers.md) for the concept and where drivers live. Netshot ships with a number of drivers, but anyone can write and load new ones — see [Loading an alternative driver](loading-a-driver.md) to install one once written.

## Information to collect

Before writing any code, get answers for all of the following questions.

!!! note
    For a better understanding, examples of answers are given for the Cisco IOS driver.

**What is the name of the vendor?**

> Cisco

**What is the name of the operating system?**

> IOS

**Assuming SNMP is configured on the device, what is the result of the following command (executed on an allowed NMS)?**

```bash
snmpwalk -v2c -c [community] [IP of the device] 1.3.6.1.2.1.1
```

```text
iso.3.6.1.2.1.1.1.0 = STRING: "Cisco IOS Software, C3750 Software (C3750-IPSERVICESK9-M), Version 15.0(2)SE9, RELEASE SOFTWARE (fc1)
Technical Support: http://www.cisco.com/techsupport
Copyright (c) 1986-2015 by Cisco Systems, Inc.
Compiled Tue 01-Dec-15 07:02 by prod_rel_team"
iso.3.6.1.2.1.1.2.0 = OID: iso.3.6.1.4.1.9.1.516
iso.3.6.1.2.1.1.3.0 = Timeticks: (279047790) 32 days, 7:07:57.90
iso.3.6.1.2.1.1.4.0 = ""
iso.3.6.1.2.1.1.5.0 = STRING: "switch1.lab.sqdqsd.net"
iso.3.6.1.2.1.1.6.0 = STRING: "observium"
iso.3.6.1.2.1.1.7.0 = INTEGER: 6
iso.3.6.1.2.1.1.8.0 = Timeticks: (0) 0:00:00.00
iso.3.6.1.2.1.1.9.1.2.1 = OID: iso.3.6.1.4.1.9.7.129
iso.3.6.1.2.1.1.9.1.2.2 = OID: iso.3.6.1.4.1.9.7.115
...
```

**If telnet is possible on this kind of device, enable it and capture the full sequence of login (copy/paste of the console).**

```text
toto@bla:~$ telnet 10.1.1.1
Trying 10.1.1.1...
Connected to 10.1.1.1.
Escape character is '^]'.

User Access Verification

Username: mqlskd
Password:
% Login invalid

Username: admin
Password:
switch1.lab#
```

**Capture the full sequence of login via SSH (copy/paste the content of the console).**

```text
toto@bla:~$ ssh user1@10.1.1.1
Password:

switch1.lab#
```

**What are the possible prompts (e.g. exec disable, exec enable, configuration mode) and how to switch from one to another?**

```text
switch1.lab#disable
switch1.lab>enable
Password:
switch1.lab#conf t
Enter configuration commands, one per line.  End with CNTL/Z.
switch1.lab(config)#end
switch1.lab#
```

**Type an unknown command and capture the output. (This is to recognize errors.)**

```text
switch1.lab#lkqjsd
Translating "lkqjsd"
% Unknown command or computer name, or unable to find computer address
switch1.lab#a
% Ambiguous command:  "a"
```

**What is the important information to capture whenever running a snapshot, and what is the associated CLI command?**

> Each config:
>
> * Running-config (`show running-config`)
> * IOS image file (taken from `show version`)
> * IOS version (taken from `show version`)

> Device level (no history):
>
> * Main memory size (taken from `show version`)
> * Config register (taken from `show version`)
> * Configuration saved (compare `show running` and `show startup`)

**Provide a full example of each of these items.**

**Is there any paging system when displaying a long output? Capture it.**

**What is the key to press to go on?**

**Is there any command available to totally disable the paging without altering the configuration?**

> Yes, `--More--` like this:
>
> ```text
> !
> ip vrf VRF270
>  rd 270:270
> !
>  --More--
> ```
>
> Press space to go on.
>
> Type `terminal length 0` to avoid it.

**Is there a way to recognize the name of the user who made the last configuration change to the device?**

> Yes, by looking at the first line in `show run`:
> `! Last configuration change at 18:13:06 UTC Tue Aug 21 2018 by user1`

**Is there any virtualization aspect on the device? (e.g. VDC)**

> No

**How to find out the name of the device?**

> Look for `... Uptime` in `show version`

**How to find out the contact for the device?**

> Look for `snmp-server contact` in the running-config

**How to find out the location of the device?**

> Look for `snmp-server location` in the running-config

**How to find out the software version of the device?**

> Look at `show version`

**How to find out the family of the device?**

> Look at `show version`

**How to retrieve the hardware inventory of the device?**

> Use `show inventory`

**How to find out the main serial number of the device?**

> Processor board ID in `show version`

**How to find out the list of L2/L3 interfaces of the device? With the associated IP address, the associated MAC address, the VRF, the admin status.**

> Look at `interface` blocks in the configuration. Use `show interface X` to retrieve the MAC address.

```text
switch1.lab#show interfaces GigabitEthernet 1/0/1
GigabitEthernet1/0/1 is up, line protocol is up (connected)
  Hardware is Gigabit Ethernet, address is 0021.a16d.9481 (bia 0021.a16d.9481)
...
```

**Upon configuration change, will the device generate a specific Syslog message?**

> Yes, `%SYS-5-CONFIG_I: Configured from console by user1`.

**Upon configuration change, will the device generate a specific SNMP trap?**

> Yes, OID `1.3.6.1.4.1.9.9.43.1.1.6.1.5` with value 3.

## Driver structure

Once you've gathered the answers above, you have what's needed to start writing the driver itself. A driver is a single JavaScript file, and must contain the following global objects:

* `Info`: object describing the driver.
* `Device`: object describing the specific attributes of this type of device.
* `Config`: object describing the attributes of a configuration of a device of this type (a history of these attributes is kept, as opposed to the device attributes).
* `CLI`: object containing the logic to interact with the device's CLI, as a state machine — see [The CLI state machine](#the-cli-state-machine) below.
* `snapshot(cli, device, config, debug)`: a function called each time a snapshot on a device of this type is started.
* `analyzeSyslog(message)`: function automatically called whenever a Syslog message is received. The function must return true if it recognizes a configuration change notification for the type of device it supports.
* `analyzeTrap(trap)`: function automatically called whenever a trap message is received (with the right community). The function must return true if it recognizes a configuration change notification for the specific type of device it supports.
* `snmpAutoDiscover(sysObjectID, sysDesc)`: function called when a device is added to Netshot in autodiscovery mode. The driver looks at the `sysObjectID` and `sysDesc` and returns true if it thinks it can support this device. If it returns true, Netshot will effectively add the device to the database, and assign this driver to the device for future actions.

A driver can optionally also declare:

* `SNMP`: object declaring SNMP-based access, alongside or instead of `CLI`.
* `HTTP`: object declaring one or more REST-API-based accesses (optionally with an authentication scheme), for devices managed entirely over HTTP(S) rather than a CLI — see [Declaring accesses](#declaring-accesses) below.
* `Options`: admin-configurable, per-device settings the driver reads back at runtime (as opposed to `Device`/`Config`, which the driver populates *from* the device) — see [Declaring options](#declaring-options) below.

Use one of the drivers bundled with Netshot as a template, and adapt each section (connection, snapshot, compliance/inventory hooks) based on the answers you collected. The rest of this page is a reference for the API available once you're writing the actual script.

## The CLI state machine

`CLI` describes SSH/Telnet interaction as a state machine: a set of named **modes** (user-exec, privileged-exec, config mode, a login prompt...), and **macros** that move between them. Two special entries, `CLI.ssh` and `CLI.telnet`, declare how a freshly-connected session reaches its first mode; every other key is a mode name.

### Modes

Each mode is an object with:

| Field | Purpose |
|---|---|
| `prompt` | `RegExp` matching this mode's command prompt — how Netshot recognizes the device has finished responding and is sitting in this mode. |
| `macros` | Named transitions out of this mode (see below). |
| `pager` | *(optional)* how to handle paginated output — see [Pagination](#pagination). |
| `error` | *(optional)* `RegExp` checked against command output; a match throws (capture group 1, if present, becomes the reported error text). |
| `fail` | *(optional)* a string to throw as soon as the state machine lands in this mode — used for dead-end/failure states such as a bad-password prompt. |
| `clearPrompt` | *(optional)* `true` to reset "strict prompt" tracking on entry — see [Strict prompt matching](#strict-prompt-matching). |

### Macros

`cli.macro("name")` looks for a macro called `name` in the *current* mode and runs it to reach that macro's declared `target` mode. A macro entry:

| Field | Purpose |
|---|---|
| `cmd` | *(optional)* command string to send. Omit it to just wait and observe (e.g. a login prompt that needs no input from the driver itself). |
| `options` | Ordered list of mode names Netshot might land in after sending `cmd` — each candidate's `prompt` (and pager) is matched against the device's response, in order. |
| `target` | The mode this macro is ultimately trying to reach. |
| `timeout`, `discoverWaitTime`, `waitBefore`, `waitAfter` | *(optional, milliseconds)* timing knobs for slow devices or commands. |
| `noCr` | *(optional)* `true` to suppress the trailing carriage return (for protocols/prompts that don't expect one). |

If sending the macro's `cmd` lands in a mode that isn't yet `target`, Netshot automatically continues the chase: it runs that mode's `auto` macro if it has one (typically a login prompt that needs no explicit `cli.macro()` call from driver code), or re-invokes the same in-progress macro name if the mode declares one, recursing up to 10 hops before giving up.

`cli.command(command, options)` sends one command in the current mode (or an explicitly given one via `options.mode`) and returns its output — no state transition, just a request/response.

### Pagination

```js
pager: {
    avoid: "terminal length 0",  // tried once, best-effort, to disable paging outright
    match: /^--More--$/,          // regex identifying a "press a key to continue" prompt
    response: " ",                 // what to send to fetch the next page
}
```

When `match` fires, Netshot sends `response` and appends the next page to the accumulated output automatically — the driver's `cli.command()` call just gets the fully concatenated result.

### Strict prompt matching

Some devices echo context-dependent detail in their prompt (e.g. `router(config-if)#` inside an interface sub-mode). Netshot can pin a previously-matched piece of the prompt (via a capture group) so it doesn't wrongly match a *different* sub-mode's prompt as if it were the same one. Set `clearPrompt: true` on a mode (or pass `{ clearPrompt: true }` to `cli.command()`) to drop that pinning when it's no longer relevant, typically when leaving such a sub-mode.

### A worked example (Cisco IOS)

This is a trimmed version of the bundled Cisco IOS driver's login/privilege-escalation chain — see `Cisco_IOS.js` for the complete state machine including SSH's simpler path (no username/password modes, since SSH authenticates during the transport handshake) and the `configure`/`save` modes:

```js
var CLI = {
    telnet: {
        macros: {
            enable: { options: [ "username", "password", "enable", "disable" ], target: "enable" },
        }
    },
    username: {
        prompt: /^[Uu]sername: $/,
        macros: {
            auto: { cmd: "$$NetshotUsername$$", options: [ "password", "usernameAgain" ] }
        }
    },
    password: {
        prompt: /^[Pp]assword:\s?$/,
        macros: {
            auto: { cmd: "$$NetshotPassword$$", options: [ "usernameAgain", "disable", "enable" ] }
        }
    },
    usernameAgain: {
        prompt: /^[Uu]sername: $/,
        fail: "Authentication failed - Telnet authentication failure.",
    },
    disable: {
        prompt: /^([A-Za-z\-_0-9\.\/]+\>)$/,
        pager: { avoid: "terminal length 0", match: /^--More--$/, response: " " },
        macros: {
            enable: { cmd: "enable", options: [ "enable", "disable", "enableSecret" ], target: "enable" },
        }
    },
    enable: {
        prompt: /^([A-Za-z\-_0-9\.\/]+#)$/,
        error: /^% (.*)/m,
        pager: { avoid: "terminal length 0", match: /^ --More--$/, response: " " },
        macros: { /* configure, save, ... */ }
    },
};
```

`$$NetshotUsername$$`/`$$NetshotPassword$$` (and `$$NetshotSuperPassword$$` for an enable secret, not shown here) are placeholders substituted with the resolved credential set's values.

Calling `cli.macro("enable")` from `telnet`'s freshly-connected state chases: `username` (auto-sends the username, lands on `password` or `usernameAgain`) → `password` (auto-sends the password, lands on `usernameAgain` on failure — which `fail`s the whole chain — or `disable`/`enable` on success) → if landed on `disable`, the in-progress `enable` macro runs again to send `enable` and reach the `enable` mode, which is the declared `target`.

### `findSections(text, regex)`

A utility available on every CLI client for splitting a chunk of text (typically a configuration) into indented blocks headed by lines matching `regex` — e.g. one block per `interface ...` stanza — without hand-rolling indentation-aware parsing in every driver.

## Declaring accesses

`SNMP` is best learned from an existing driver. `HTTP` is documented in full below, for drivers that manage a device entirely through a REST API instead of a CLI — see the bundled `Cisco_APIC.js`, `Cisco_NDO.js`, and `Infoblox_NIOS.js` drivers for complete examples.

### HTTP access

```js
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
```

The key (`http` or `https`) selects plain HTTP or TLS. The optional `auth` block describes how Netshot authenticates before the driver's own requests go out — modeled after OpenAPI security schemes:

| `type` | Fields | Behavior |
|---|---|---|
| `http` | `scheme`: `basic` (default) or `bearer` | Standard `Authorization` header, built from the resolved HTTP credential set. |
| `apiKey` | `in`: `header` (default), `query`, or `cookie`; `name` (required) | Sends the credential's key/value at the given location. |
| `cookie` | `method`: `post` (default) or `put`; `path` (required); `data` (optional body template); `contentType`: `json` (default) or `form` | Logs in by calling `path` once, then replays the session cookie the device returns on every later request. `$$NetshotUsername$$`/`$$NetshotPassword$$` placeholders in `data` are substituted with the resolved credential set. |
| `oauth2` / `openIdConnect` | — | Recognized but not yet implemented — reserved for a future release. |

Without an `auth` block, requests go out unauthenticated (or however the driver's own script handles auth manually).

The corresponding [connection security settings](../user-guide/devices.md#connection-security) (HTTPS CA trust mode) apply to HTTP accesses the same way as to any other device access.

## Declaring options

`Options` declares admin-configurable, per-device settings — as opposed to `Config`/`Device` attributes, which the driver populates *from* the device. A user sets these values on the device (in the Web UI or via the REST API); the driver only ever reads them back, through `device.options`.

```js
const Options = {
    "fullBackup": {
        type: "Boolean",
        title: "Take full backup archive",
        default: true,
    },
};
```

| Field | Description |
|---|---|
| `type` | `"Boolean"` or `"Text"`. |
| `title` | Label shown in the Web UI. |
| `choices` | *(Text only, optional)* restricts the value to one of a fixed list. |
| `default` | Default value, typed to match `type`. |

## The client, device, and config objects

`snapshot(cli, device, config, debug)`, `run(cli, device, config)`, and each diagnostic function receive these three (the historical `debug` 4th argument on `snapshot` is superseded by `cli.debug(...)` below, kept only for backward compatibility).

### `client.create(access, options)`

The `cli` argument passed in is really the driver's *default* CLI client, bound to its primary SSH/Telnet access. Every client — CLI, SNMP, or HTTP — additionally exposes `create(nameOrArray, options)` to build a client bound to a specific declared access, or to a named *group* of accesses to try in priority order:

```js
const http = cli.create("https");                       // a specific declared access
const anyHttp = cli.create("http");                      // every HTTP/HTTPS access, by priority
const scoped = cli.create("https", { basePath: "/api" }); // prefix every request path
const manual = cli.create("ssh", { autoTryCredentials: false }); // don't auto-fallback credentials
```

`options.autoTryCredentials` defaults to `true` (silently try every candidate credential set on a 401/403 or auth failure); set it to `false` to handle credential fallback manually via `tryNextCredentials()`.

### The `http` client

Available as the object returned by `client.create("http"/"https", ...)`. Axios-inspired:

```js
http.get(path, config)
http.post(path, data, config)
http.put(path, data, config)
http.patch(path, data, config)
http.delete(path, config)
http.head(path, config)
http.options(path, config)
http.request(config)          // { method, url, headers, params, data, validateStatus }
```

Each call returns `{ status, statusText, headers, data, json() }` (`data` is the raw body string; `json()` lazily parses it) or throws if `validateStatus` (default: 2xx) rejects the response. A non-string `data`/body is JSON-encoded automatically, stamping `Content-Type: application/json` unless already set.

```js
const result = http.get("/api/node/class/topSystem.json", {
    query: { "query-target": "self" },
});
const items = result.json().imdata;
```

Binary-safe download, for pulling a file (e.g. a backup archive) instead of decoding the body as text:

```js
const dl = http.download("/export/download", { method: "GET" });
config.commitDownload("backupArchive", dl.file, { storeFileName: "backup.tgz" });
```

Other methods: `tryNextCredentials()` (manual credential fallback, see above), `sleep(millis)` (e.g. while polling an async job), `debug(message)` (writes to the task's debug log).

### `device`

| Method | Purpose |
|---|---|
| `device.options` | Read-only map of the values set by the user for this device's declared `Options`. |
| `device.set(key, value)` | Sets a top-level device field (`name`, `family`, `location`, `contact`, `softwareVersion`, `serialNumber`, `comments`, `networkClass`) or a driver-declared device attribute. |
| `device.add(collection, value)` | Appends to a device collection attribute (e.g. `networkInterface`, `module`). |
| `device.get(key, id)` | Reads back a previously-set value or collection entry. |
| `device.textDownload(fileName, options)` | Pulls a text file off the device over SCP/SFTP (`options.method`, `options.charset`, `options.newSession`). |

### `config`

| Method | Purpose |
|---|---|
| `config.set(key, value)` | Sets a config attribute (or `author`) for the configuration being captured this snapshot. |
| `config.download(key, fileName, options)` | Pulls a file off the device over SCP/SFTP into a `BinaryFile` config attribute. `options`: `method` (`sftp`/`scp`), `storeFileName`, `newSession`, `checksum`. |
| `config.commitDownload(key, tempFilePath, options)` | The HTTP counterpart to `download()`: moves a file already fetched via `http.download(...)` into a `BinaryFile` config attribute. `options`: `storeFileName`, `checksum`. |
| `config.computeHash(...parts)` / `getHash()` / `getLastHash()` / `isChangedHash()` | Build and compare a custom hash across arbitrary text parts, for drivers whose "did anything change" logic isn't a simple field comparison. |
| `config.requestUpload(options)` / `awaitUpload(ticketId, timeout)` / `commitUpload(ticketId, fileId, key, options)` | For devices that push their backup to Netshot themselves (e.g. over the [embedded SSH/SCP/SFTP server](../configuration-reference.md#embedded-ssh-server)) rather than being pulled from: request a one-time upload ticket, wait for the device to use it, then commit the uploaded file into a config attribute. |

## Debugging a driver

Every client (`cli`, `http`, and any built via `client.create(...)`) exposes `debug(message)`, which writes to the task's debug log — visible when [running a task with debug logging enabled](../troubleshooting.md). HTTP requests and responses are additionally written to the task's trace log (with authentication data redacted) when tracing is enabled.
