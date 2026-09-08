# Configuration file (netshot.conf) reference

Netshot's global settings are configured in a file called `netshot.conf`. On startup, Netshot looks for this file in the current directory, then in `/etc/netshot.conf`. Netshot won't start if no such file is found.

Each line has the form:

```
parameter = value
```

Lines starting with `#` are ignored.

Some parameters are re-read and applied when Netshot receives a `HUP` signal; others require a restart to take effect.

Every parameter can also be passed as an environment variable: replace `.` with `_` and upper-case it. For example, `netshot.log.file` becomes `NETSHOT_LOG_FILE`.

## Logging

| Parameter | Description |
|---|---|
| `netshot.log.file` | Location of the main log file. Use `CONSOLE` to log to stdout instead (useful for debugging or container deployments). Default: `netshot.log` in the current directory. |
| `netshot.log.maxsize` | Maximum size (MB) of the log file before rotation. Default: `2`. |
| `netshot.log.count` | Number of rotated log files to keep. Default: `5`. |
| `netshot.log.level` | Global log level: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, `ALL`. Default: `WARN`. |
| `netshot.log.class.<ClassName>` | Overrides the log level for a specific internal class. |
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
| `netshot.db.encryptionPassword` | Password used to encrypt sensitive data (such as device credentials) stored in the database. Default: `NETSHOT` — **change this in production**. |
| `netshot.db.readurl` | JDBC URI of a secondary, read-only database. See [Clustering and High Availability](clustering-ha.md). |
| `netshot.db.driverclass` | JDBC driver class. Default: `org.postgresql.Driver`. |

Advanced connection pool tuning (see the [c3p0 documentation](https://www.mchange.com/projects/c3p0/#configuration) for details):

| Parameter | Default |
|---|---|
| `netshot.db.pooler.minpoolsize` | `5` |
| `netshot.db.pooler.maxpoolsize` | `30` |
| `netshot.db.pooler.maxstatements` | `50` |
| `netshot.db.pooler.maxidletimeout` | `1800` |
| `netshot.db.pooler.maxconnectionage` | `0` |
| `netshot.db.pooler.testconnectiononcheckout` | `true` |
| `netshot.db.pooler.testconnectiononcheckin` | `false` |
| `netshot.db.pooler.idleconnectiontestperiod` | `0` |
| `netshot.db.pooler.connectionisvalidtimeout` | `0` |
| `netshot.db.pooler.unreturnedconnectiontimeout` | `1800` |

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
| `netshot.snmptrap.community` | SNMP community (or space-separated list of communities) accepted from devices. Default: `NETSHOT`. |
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
| `netshot.aaa.radiusN.ip`, `.authport`, `.secret` | Defines RADIUS server `N` (up to 3: `radius1`, `radius2`, `radius3`). |
| `netshot.aaa.radiusN.timeout` | Timeout (seconds) for that server. Default: `5`. |
| `netshot.aaa.radius.method` | Auth method: `mschapv2` (default), `pap`, `chap`, `eap-md5`, or `eap-mschapv2`. |
| `netshot.aaa.radius.nasidentifier` | NAS-Identifier attribute added to requests. Unset by default. |

TACACS+ authentication:

| Parameter | Description |
|---|---|
| `netshot.aaa.tacacsN.ip`, `.port`, `.secret` | Defines TACACS+ server `N` (up to 3). |
| `netshot.aaa.tacacs.timeout` | Timeout (seconds) for TACACS+ requests. Default: `5`. |
| `netshot.aaa.tacacs.method` | Inner method: `ascii` (default), `chap`, or `pap`. |
| `netshot.aaa.tacacs.accounting` | Logs all write requests as TACACS+ accounting messages. |
| `netshot.aaa.tacacs.role.attributename` | Name of the server-returned attribute holding the user's role. |
| `netshot.aaa.tacacs.role.adminlevelrole` | Role value that maps to the Admin permission level. |
| `netshot.aaa.tacacs.role.executereadwritelevelrole` | Role value that maps to Execute-scripts. |
| `netshot.aaa.tacacs.role.readwritelevelrole` | Role value that maps to Read-write. |

Single Sign-On / OIDC authentication — see [Single Sign-On (OIDC)](user-guide/administration.md#single-sign-on-oidc) for the full setup walkthrough:

| Parameter | Description |
|---|---|
| `netshot.aaa.oidc.idp.url` | URL of the OIDC Identity Provider. |
| `netshot.aaa.oidc.clientid` | OIDC client ID. |
| `netshot.aaa.oidc.clientsecret` | OIDC client secret. |
| `netshot.aaa.oidc.usernameclaimname` | ID token claim used as the Netshot username. |
| `netshot.aaa.oidc.role.defaultlevel` | Default permission level for an OIDC user when no role claim is present. |
| `netshot.aaa.oidc.role.claimname` | Name of the ID token claim carrying the user's role. |
| `netshot.aaa.oidc.role.adminlevelrole` | Role claim value that maps to Admin. |
| `netshot.aaa.oidc.role.executereadwritelevelrole` | Role claim value that maps to Execute-scripts. |
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

## Device drivers

| Parameter | Description |
|---|---|
| `netshot.drivers.path` | Extra directory to load [device drivers](user-guide/device-drivers.md) from, in addition to the embedded ones. |

## Snapshots

| Parameter | Description |
|---|---|
| `netshot.snapshots.dump` | Directory to save a copy of each device configuration after every snapshot. No dump is written unless set. |
| `netshot.snapshots.auto.interval` | Minutes to wait after a detected change before triggering an automatic snapshot. Default: `10`. |
| `netshot.snapshots.auto.anyip` | Match a device by any of its known IP addresses (not just management IPs) when identifying the source of a trap/Syslog message. Disabled by default. |
| `netshot.snapshots.binary.path` | Directory to save binary file extracts from devices, for drivers that support this. |

## Tasks

| Parameter | Description |
|---|---|
| `netshot.tasks.threadcount` | Number of tasks that can run concurrently. Default: `10`. |

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

## JavaScript VM

| Parameter | Description |
|---|---|
| `netshot.javascript.maxexecutiontime` | Max time (ms) a JavaScript compliance/diagnostic script may run. Default: `60000`. |

## Python VM

Requires a configured GraalPy virtual environment — see [Python virtualenv for additional packages](extending/python-virtualenv.md).

| Parameter | Description |
|---|---|
| `netshot.python.virtualenv` | Path to the (Graal) Python virtual environment. |
| `netshot.python.maxexecutiontime` | Max time (ms) a Python compliance/diagnostic script may run. Default: `60000`. |
| `netshot.python.allowallaccess` | Allow all types of host access from Python scripts. **Insecure** — off by default. |
| `netshot.python.filesystemfilter` | Set to `false` to disable filesystem access restrictions for Python scripts. **Insecure** — restrictions are on by default. |
| `netshot.python.allowcreateprocess` | Allow Python scripts to spawn OS processes. **Insecure** — off by default. |
| `netshot.python.allowcreatethread` | Allow Python scripts to create threads. **Insecure** — off by default. |
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
