# Configuration file (netshot.conf) reference

Netshot's global settings are configured in a file called `netshot.conf`. On startup, Netshot looks for this file in the current directory, then in `/etc/netshot.conf`. Netshot won't start if no such file is found.

Each line has the form:

```
parameter = value
```

Lines starting with `#` are ignored.

Some parameters are re-read and applied when Netshot receives a `HUP` signal; others require a restart to take effect.

Every parameter can also be passed as an environment variable: replace `.` with `_` and upper-case it. For example, `netshot.log.file` becomes `NETSHOT_LOG_FILE`.

## Secrets in files

Secret parameters can be read from a file instead of being set inline: set the parameter name followed by `.file` (or `_FILE` for the environment variable) to the path of the file containing the secret. For example, `NETSHOT_DB_PASSWORD_FILE=/run/secrets/db_password` makes Netshot read the database password from `/run/secrets/db_password`. This is the recommended way to pass secrets to a container (Docker/Compose secrets, Kubernetes secrets mounted as files).

!!! warning
    Prefer secret files over environment variables: the environment of the Netshot process is visible to anyone who can inspect the process or the container, and to user-written Python scripts if `netshot.python.allowallaccess` is enabled (or with Netshot versions older than 0.25.1).

This applies to the following parameters:

* `netshot.db.url`, `netshot.db.readurl`, `netshot.db.username`, `netshot.db.password`, `netshot.db.encryptionpassword`
* `netshot.http.ssl.keystore.pass`
* `netshot.snmptrap.community`, `netshot.snmptrap.user`
* `netshot.aaa.radiusN.secret`, `netshot.aaa.tacacsN.secret`, `netshot.aaa.oidc.clientsecret`

Rules:

* The file takes precedence over the inline parameter. A warning is logged if the parameter is also set as an environment variable, which should then be removed.
* The whole file content is used as the secret, except trailing line breaks.
* If the file is set but can't be read, is empty, or is larger than 64 KB, Netshot fails to start (it doesn't fall back to the inline value or to the default value).
* Secrets are read once: a restart is required to apply a changed secret.

## Logging

| Parameter | Description |
|---|---|
| `netshot.log.file` | Location of the main log file. Use `CONSOLE` to log to stdout instead (useful for debugging or container deployments). Default: `netshot.log` in the current directory. |
| `netshot.log.maxsize` | Maximum size (MB) of the log file before rotation. Default: `2`. |
| `netshot.log.count` | Number of rotated log files to keep. Default: `5`. |
| `netshot.log.level` | Global log level: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, `ALL`. Default: `WARN`. |
| `netshot.log.class.<ClassName>` | Overrides the log level for a specific class or package logger, e.g. `netshot.log.class.org.apache.sshd = DEBUG`. Only read from the configuration file, not from environment variables. |
| `netshot.log.audit.file` | Enables audit logging (authentication, authorization) to the given file. Also requires `netshot.log.audit.level`. |
| `netshot.log.audit.level` | Log level for audit messages. |
| `netshot.log.audit.count` | Number of rotated audit log files to keep. Default: `5`. |
| `netshot.log.audit.maxsize` | Maximum size (MB) of the audit log file before rotation. Default: `2`. |
| `netshot.log.syslogN.host` | Enables remote Syslog logging to the given host. Increment `N` (`syslog1`, `syslog2`, ...) for multiple destinations. |
| `netshot.log.syslogN.port` | Remote Syslog port. |
| `netshot.log.syslogN.facility` | Remote Syslog facility. |

## Database

| Parameter | Description |
|---|---|
| `netshot.db.url` | JDBC URI of the database, e.g. `jdbc:postgresql://localhost:5432/netshot01?sslmode=disable`. |
| `netshot.db.username` | Database username. |
| `netshot.db.password` | Database password. |
| `netshot.db.encryptionpassword` | Password used to encrypt sensitive data (such as device credentials) stored in the database. Default: `NETSHOT` — **change this in production**. |
| `netshot.db.encryptionpassword.required` | Set to `true` to prevent Netshot from starting if `netshot.db.encryptionpassword` isn't set (instead of using the default value). Enabled in the container image. Default: `false`. |
| `netshot.db.readurl` | JDBC URI of a secondary, read-only database. See [Clustering and High Availability](clustering-ha.md). |
| `netshot.db.driverclass` | JDBC driver class. Default: `org.postgresql.Driver`. |

Advanced connection pool tuning (see the [c3p0 documentation](https://www.mchange.com/projects/c3p0/#configuration) for details):

| Parameter | Default |
|---|---|
| `netshot.db.pooler.minpoolsize` | `5` |
| `netshot.db.pooler.maxpoolsize` | `50` |
| `netshot.db.pooler.maxstatements` | `50` |
| `netshot.db.pooler.maxidletimeout` | `1800` |
| `netshot.db.pooler.maxconnectionage` | `0` |
| `netshot.db.pooler.testconnectiononcheckout` | `true` |
| `netshot.db.pooler.testconnectiononcheckin` | `false` |
| `netshot.db.pooler.idleconnectiontestperiod` | `0` |
| `netshot.db.pooler.connectionisvalidtimeout` | `0` |
| `netshot.db.pooler.unreturnedconnectiontimeout` | `1800` |
| `netshot.db.pooler.checkouttimeout` | `0` (ms to wait for a free connection when the pool is exhausted, `0` = wait indefinitely) |
| `netshot.db.pooler.system.minpoolsize` | `1` |
| `netshot.db.pooler.system.maxpoolsize` | `5` |

A running device task (snapshot, diagnostics, script) holds a connection of the main pool for most of its execution, so `netshot.db.pooler.maxpoolsize` must comfortably exceed `netshot.tasks.threadcount` (at least 10 more; Netshot logs a warning at startup otherwise). The `system` pool is a small, separate pool used for task dispatch and cluster bookkeeping, so that these can't be starved by running tasks. Make sure the total number of connections (each Netshot instance: main pool + system pool + 2, plus the read-only pool if configured) stays below PostgreSQL's `max_connections`.

## Embedded HTTP server

| Parameter | Description |
|---|---|
| `netshot.http.enabled` | Enables the embedded HTTP(S) server (the Web UI/REST API listener). Default: `true`. |
| `netshot.http.ssl.enabled` | Enables HTTPS (vs plain HTTP). Default: `true`. |
| `netshot.http.ssl.keystore.file` | Path to the keystore holding the HTTPS certificate. Default: `netshot.jks` in the current directory — required when SSL is enabled. |
| `netshot.http.ssl.keystore.pass` | Password of the HTTPS keystore. Default: `netshotpass`. |
| `netshot.http.baseurl` | Base URL Netshot advertises itself as, e.g. behind a reverse proxy. Default: `http://localhost:8443` (SSL) or `http://localhost:8080` (plain). |
| `netshot.http.baseport` | TCP port of the embedded server. Default: `8443`. |
| `netshot.http.staticpath` | URL path under which the Web UI static assets are served. Default: `/`. |
| `netshot.http.apipath` | URL path under which the REST API is served. Default: `/api`. |
| `netshot.http.trustxforwardedfor` | Trust the `X-Forwarded-For` header to log the real client IP (used in AAA audit) when behind a proxy. Default: `false`. |
| `netshot.http.sessioncookiename` | Name of the session cookie. |
| `netshot.http.securecookie` | Sets the `Secure` flag on the session cookie, so that browsers only send it over HTTPS. Default: `true` if `netshot.http.ssl.enabled` or `netshot.http.trustxforwardedfor` is enabled (Netshot serving HTTPS, or behind a reverse proxy, expected to serve HTTPS), `false` otherwise. Set to `false` if users access Netshot over plain HTTP through a reverse proxy, otherwise browsers won't keep the session. |

## SSL / TLS

| Parameter | Description |
|---|---|
| `netshot.ssl.truststore.file` | Path to an additional Java truststore to use, e.g. to trust an internal CA when Netshot makes outbound HTTPS calls (Vault, webhooks, device HTTPS access). |

## Syslog server

The embedded Syslog server listens for messages that indicate a device configuration may have changed.

| Parameter | Description |
|---|---|
| `netshot.syslog.port` | UDP port to listen on. Default: `514` (requires root on Linux). |
| `netshot.syslog.disabled` | Disables the embedded Syslog server. Enabled by default. |

To avoid running as root, listen on a high port (e.g. `1514`) and redirect with `iptables`, as described in the [installation guide](installation/linux.md).

## SNMP trap receiver

The embedded SNMP trap receiver listens for traps that indicate a device configuration may have changed.

| Parameter | Description |
|---|---|
| `netshot.snmptrap.port` | UDP port to listen on. Default: `162` (requires root on Linux). |
| `netshot.snmptrap.listenaddress` | IP address to bind to. Default: `0.0.0.0`. |
| `netshot.snmptrap.community` | SNMP community (or space-separated list of communities) accepted from devices. No SNMP v1/v2c trap is accepted unless set. |
| `netshot.snmptrap.engineid` | SNMP engine ID. Default: auto-generated at startup. |
| `netshot.snmptrap.user` | SNMPv3 user(s) allowed to send traps, space-separated for multiple entries. Format: `username protocols [authkey] [privkey]`, where `protocols` is a pipe-separated list of auth (`MD5`, `SHA`, `HMAC128SHA224`, `HMAC192SHA256`, `HMAC256SHA384`, `HMAC384SHA512`) and privacy (`DES`, `3DES`, `AES128`, `AES192`, `AES256`) protocols. No SNMPv3 user is defined by default. |
| `netshot.snmptrap.threadcount` | Number of worker threads processing incoming traps. Default: `2`. |
| `netshot.snmptrap.disabled` | Disables the embedded SNMP trap receiver. Enabled by default. |

As with Syslog, listen on a high port and redirect with `iptables` to avoid running as root.

## Embedded SSH server

Netshot embeds an SSH server, primarily so managed devices can push their backup archive to Netshot over SCP or SFTP (e.g. Fortinet FortiManager/FortiAnalyzer).

| Parameter | Description |
|---|---|
| `netshot.sshserver.enabled` | Enables the embedded SSH server. Default: `true`. |
| `netshot.sshserver.sftp.enabled` | Enables the SFTP server (requires the SSH server). Default: `true`. |
| `netshot.sshserver.scp.enabled` | Enables the SCP server (requires the SSH server). Default: `true`. |
| `netshot.sshserver.port` | TCP port to listen on. Default: `2022`. |
| `netshot.sshserver.externalport` | External TCP port as seen by managed devices, if different from `.port` (e.g. behind NAT). Defaults to `.port`. |
| `netshot.sshserver.listenaddress` | IP address to bind to. Default: `0.0.0.0`. |
| `netshot.sshserver.hostkeypath` | Path to persist SSH host keys. Defaults to the system temp folder (keys regenerate on reboot) — set this explicitly in production so device-side host key pinning stays valid across restarts. |
| `netshot.sshserver.maxconcurrentsessions` | Maximum concurrent SSH server sessions. Default: `10`. |
| `netshot.sshserver.kexalgorithms` | Comma-separated list of key exchange algorithms. |
| `netshot.sshserver.hostkeyalgorithms` | Comma-separated list of host key algorithms. |
| `netshot.sshserver.ciphers` | Comma-separated list of ciphers. |
| `netshot.sshserver.macs` | Comma-separated list of MAC algorithms. |
| `netshot.sshserver.compressionalgorithms` | Comma-separated list of compression algorithms. |

Some devices (e.g. Cisco ISE) require the server on TCP/22. To avoid binding the reserved port directly or conflicting with the system's own OpenSSH server, listen on a high port and redirect with `iptables`:

```
netshot.sshserver.port = 11022
netshot.sshserver.externalport = 22
```

```bash
iptables -t nat -A PREROUTING -p tcp --dport 22 -j REDIRECT --to-port 11022 -s 10.255.255.0/24
```

## Embedded TFTP server

| Parameter | Description |
|---|---|
| `netshot.tftpserver.disabled` | Disables the embedded TFTP server. **Disabled by default** — enable explicitly if a driver relies on TFTP transfer. |
| `netshot.tftpserver.port` | UDP port to listen on. Default: `69`. |

## User authentication

Local authentication / password policy:

| Parameter | Description |
|---|---|
| `netshot.aaa.passwordpolicy.maxhistory` | Number of previous password hashes kept per user, to prevent reuse. |
| `netshot.aaa.passwordpolicy.maxduration` | Days before a local user must change their password. |
| `netshot.aaa.passwordpolicy.mintotalchars` | Minimum password length. |
| `netshot.aaa.passwordpolicy.minspecialchars` | Minimum number of special characters (`` !"#$%&'()*+,-./:;<=>?@\[\]^_{}|~ ``). |
| `netshot.aaa.passwordpolicy.minnumericalchars` | Minimum number of digits. |
| `netshot.aaa.passwordpolicy.minlowercasechars` | Minimum number of lower-case letters. |
| `netshot.aaa.passwordpolicy.minuppercasechars` | Minimum number of upper-case letters. |

RADIUS authentication:

| Parameter | Description |
|---|---|
| `netshot.aaa.radiusN.ip`, `.authport`, `.acctport`, `.secret` | Defines RADIUS server `N` (up to 3: `radius1`, `radius2`, `radius3`). Ports default to `1812` (authentication) and `1813` (accounting). |
| `netshot.aaa.radiusN.timeout` | Timeout (seconds) for that server. Default: `5`. |
| `netshot.aaa.radius.method` | Auth method: `mschapv2` (default), `pap`, `chap`, `eap-md5`, or `eap-mschapv2`. |
| `netshot.aaa.radius.nasidentifier` | NAS-Identifier attribute added to requests. Unset by default. |

TACACS+ authentication:

| Parameter | Description |
|---|---|
| `netshot.aaa.tacacsN.ip`, `.port`, `.secret` | Defines TACACS+ server `N` (up to 3). Port defaults to `49`. Authentication always uses the ASCII method. |
| `netshot.aaa.tacacs.timeout` | Timeout (seconds) for TACACS+ requests. Default: `5`. |
| `netshot.aaa.tacacs.accounting` | Logs all write requests as TACACS+ accounting messages. |
| `netshot.aaa.tacacs.role.attributename` | Name of the server-returned attribute holding the user's role. |
| `netshot.aaa.tacacs.role.readonlyrole` | Role value that maps to the Read-only permission level. Default: `read-only`. |
| `netshot.aaa.tacacs.role.operatorrole` | Role value that maps to the Operator permission level. Default: `operator`. |
| `netshot.aaa.tacacs.role.readwriterole` | Role value that maps to the Read-write permission level. Default: `read-write`. |
| `netshot.aaa.tacacs.role.executereadwriterole` | Role value that maps to the Execute-scripts permission level. Default: `execute-read-write`. |
| `netshot.aaa.tacacs.role.adminrole` | Role value that maps to the Admin permission level. Default: `admin`. |

Single Sign-On / OIDC authentication — see [Single Sign-On (OIDC)](user-guide/administration.md#single-sign-on-oidc) for the full setup walkthrough:

| Parameter | Description |
|---|---|
| `netshot.aaa.oidc.idp.url` | URL of the OIDC Identity Provider. |
| `netshot.aaa.oidc.clientid` | OIDC client ID. |
| `netshot.aaa.oidc.clientsecret` | OIDC client secret. |
| `netshot.aaa.oidc.usernameclaimname` | ID token claim used as the Netshot username. |
| `netshot.aaa.oidc.role.defaultlevel` | Default permission level for an OIDC user when no role claim is present. |
| `netshot.aaa.oidc.role.claimname` | Name of the ID token claim carrying the user's role. |
| `netshot.aaa.oidc.role.readonlyrole` | Role claim value that maps to the Read-only permission level. Default: `read-only`. |
| `netshot.aaa.oidc.role.operatorrole` | Role claim value that maps to the Operator permission level. Default: `operator`. |
| `netshot.aaa.oidc.role.readwriterole` | Role claim value that maps to the Read-write permission level. Default: `read-write`. |
| `netshot.aaa.oidc.role.executereadwriterole` | Role claim value that maps to the Execute-scripts permission level. Default: `execute-read-write`. |
| `netshot.aaa.oidc.role.adminrole` | Role claim value that maps to the Admin permission level. Default: `admin`. |
| `netshot.aaa.oidc.idp.refreshinterval` | How often (ms) Netshot refreshes the IdP's published metadata/keys. Default: `43200000` (12h). |
| `netshot.aaa.oidc.idp.retryinterval` | Retry interval (ms) after a failed metadata fetch. Default: `30000`. |

Other:

| Parameter | Description |
|---|---|
| `netshot.aaa.maxidletime` | Idle time (seconds) before a logged-in user is disconnected. Default: `1800`. |

## Vault integration

Optional integration with a HashiCorp Vault-compatible backend for externally-stored device credentials — see [Vault instances](user-guide/administration.md#vault-instances) for how to configure a Vault instance and point credentials at it. These settings tune the client behavior; Vault instance connection details (URL, auth method, mount paths) are configured through the Web UI, not `netshot.conf`.

| Parameter | Description |
|---|---|
| `netshot.vault.token.renewmarginms` | How long (ms) before expiry Netshot renews a Vault auth token. Default: `30000`. |
| `netshot.vault.secret.cachettlms` | How long (ms) a secret fetched from Vault is cached before being re-fetched. Default: `60000`. |
| `netshot.vault.http.connecttimeoutms` | HTTP connect timeout (ms) for calls to Vault. Default: `5000`. |
| `netshot.vault.http.readtimeoutms` | HTTP read timeout (ms) for calls to Vault. Default: `10000`. |
| `netshot.vault.token.refreshpollintervalseconds` | How often (seconds) the background daemon checks whether Vault auth tokens need renewing. Default: `60` (minimum `5`). |

## Device drivers

| Parameter | Description |
|---|---|
| `netshot.drivers.path` | Extra directory to load [device drivers](user-guide/device-drivers.md) from, in addition to the embedded ones. |

## Snapshots

| Parameter | Description |
|---|---|
| `netshot.snapshots.dump` | Directory to save a copy of each device configuration after every snapshot, in a file named after the device (path separators in the name are replaced by `_`). No dump is written unless set. |
| `netshot.snapshots.auto.interval` | Minutes to wait after a detected change before triggering an automatic snapshot. Default: `10`. |
| `netshot.snapshots.auto.anyip` | Match a device by any of its known IP addresses (not just management IPs) when identifying the source of a trap/Syslog message. Disabled by default. |
| `netshot.snapshots.binary.path` | Directory to save binary file extracts from devices, for drivers that support this. |

## Tasks

| Parameter | Description |
|---|---|
| `netshot.tasks.threadcount` | Number of tasks that can run concurrently. Default: `10`. Keep it well below `netshot.db.pooler.maxpoolsize` (see [Database](#database)). |
| `netshot.tasks.devicelock.pollintervalms` | Interval (ms) at which a task waiting for its turn on a device checks whether the device is free. Default: `3000`. |
| `netshot.tasks.sequentialpollintervalms` | Interval (ms) at which a group task running its devices sequentially checks whether the current device task has finished. Default: `3000` (range `500`–`60000`). |

## CLI (SSH / Telnet) connections to devices

| Parameter | Description |
|---|---|
| `netshot.cli.telnet.connectiontimeout` | Max time (ms) to establish a Telnet session. |
| `netshot.cli.telnet.receivetimeout` | Max time (ms) to wait for data on a Telnet session. |
| `netshot.cli.telnet.commandtimeout` | Max time (ms) to wait for a command's output over Telnet. |
| `netshot.cli.ssh.connectiontimeout` | Max time (ms) to establish an SSH session. |
| `netshot.cli.ssh.receivetimeout` | Max time (ms) to wait for data on an SSH session. |
| `netshot.cli.ssh.commandtimeout` | Max time (ms) to wait for a command's output over SSH. |
| `netshot.cli.ssh.kexalgorithms` | Comma-separated key exchange algorithms for device SSH connections. |
| `netshot.cli.ssh.hostkeyalgorithms` | Comma-separated host key algorithms for device SSH connections. |
| `netshot.cli.ssh.ciphers` | Comma-separated ciphers for device SSH connections. |
| `netshot.cli.ssh.macs` | Comma-separated MAC algorithms for device SSH connections. |
| `netshot.cli.ssh.compressionalgorithms` | Comma-separated compression algorithms for device SSH connections. |
| `netshot.cli.ssh.rekeytimelimit` | Re-key device SSH sessions after this time (ms). Default: `3600000` (1h). `0` disables this limit. |
| `netshot.cli.ssh.rekeydatalimit` | Re-key device SSH sessions after this amount of data (bytes). Default: `1073741824` (1 GiB). `0` disables this limit. |
| `netshot.cli.ssh.rekeyblockslimit` | Re-key device SSH sessions after this number of cipher blocks. Default: `0` (no limit). |
| `netshot.cli.ssh.rekeypacketslimit` | Re-key device SSH sessions after this number of packets. Default: `2147483648` (2³¹). `0` disables this limit. |

## JavaScript VM

| Parameter | Description |
|---|---|
| `netshot.javascript.maxexecutiontime` | Max time (ms) a JavaScript compliance rule may run. Default: `60000`. |

## Python VM

These options apply to Python compliance rules (not to Python diagnostics). To use additional packages, see [Python virtualenv for additional packages](extending/python-virtualenv.md).

| Parameter | Description |
|---|---|
| `netshot.python.virtualenv` | Path to the (Graal) Python virtual environment. |
| `netshot.python.maxexecutiontime` | Max time (ms) a Python compliance rule may run. Default: `60000`. |
| `netshot.python.allowallaccess` | Allow all types of host access from Python scripts. **Insecure** — off by default. |
| `netshot.python.filesystemfilter` | Set to `false` to disable filesystem access restrictions for Python scripts. **Insecure** — restrictions are on by default (read-only access to the GraalPy libraries, the virtualenv, and system-wide `/proc` entries, but not to the Netshot process's own `/proc` entries). |
| `netshot.python.allowcreateprocess` | Allow Python scripts to spawn OS processes. **Insecure** — off by default. |
| `netshot.python.allowcreatethread` | Allow Python scripts to create threads (needed by some packages, see [Python virtualenv](extending/python-virtualenv.md#troubleshooting)). **Insecure** — off by default. |
| `netshot.python.allowhostfileaccess` | Allow Python scripts to access the host filesystem. **Insecure** — off by default. |
| `netshot.python.allowhostsocketaccess` | Allow Python scripts to open host sockets. **Insecure** — off by default. |
| `netshot.python.allownativeaccess` | Allow Python scripts native access. **Insecure** — off by default. |

## Cryptographic libraries

| Parameter | Description |
|---|---|
| `netshot.cryptolibs.load` | Loads additional embedded crypto libraries (Bouncy Castle) needed for certain device SSH algorithms. Default: `true`. |
| `netshot.cryptolibs.tmppath` | Temporary directory used to extract those libraries at startup. |

## Clustering

See [Clustering and High Availability](clustering-ha.md) for the full model.

| Parameter | Description |
|---|---|
| `netshot.cluster.enabled` | Enables clustering mode. Default: `false`. |
| `netshot.cluster.id` | Statically assigns this instance's cluster member ID (20 lowercase letters/digits). Recommended when clustering is enabled. |
| `netshot.cluster.master.priority` | Priority to become cluster master (higher wins). |
| `netshot.cluster.runner.priority` | Priority to be selected as a task runner (higher wins). |
| `netshot.cluster.runner.weight` | Relative weight for task distribution once selected as a runner. |
| `netshot.cluster.domainipoverride` | Overrides the domain IP address advertised by this node (e.g. for the embedded SSH server), formatted as `real_ip\|advertised_ip` pairs separated by spaces. |
