# Upgrading Netshot

## Common instructions

!!! warning
    Downgrades are only supported by restoring a backup of the database.

1. Get the version from the [Netshot Release Page](https://github.com/netshot-net/Netshot/releases), and unzip it on your Netshot server, in a temporary folder:

   ```bash
   unzip netshot_x.y.z.zip
   ```

2. Backup your data. Backup the following:
    * Your installation folder, e.g. `/usr/local/netshot`
    * Your drivers (if you have customized any), e.g. `/usr/local/netshot/drivers`
    * Your database (using `pg_dump` for example)
    * Your configuration file, e.g. `/etc/netshot.conf`

3. Stop Netshot

   ```bash
   sudo systemctl stop netshot
   ```

4. Copy the updated package

   ```bash
   sudo cp netshot.jar /usr/local/netshot
   ```

5. Check the release notes for any required additional actions. You might have to upgrade the Java version on the system. See the special update notes section in the `UPDATE.txt` file distributed with the release.

6. Ensure compatibility of your own drivers. If you have made local changes to your drivers, or have written your own drivers, you should ensure that they are compatible with the new version, and you might want to adapt them.

7. Start Netshot

   ```bash
   sudo systemctl start netshot
   ```

## Specific update steps

### Any version to the next release

* Stopping Netshot (`SIGTERM`: `systemctl stop`, `docker stop`, Kubernetes) is now graceful: the services are stopped in the reverse order of their start, and the running tasks get `netshot.shutdown.timeout` seconds (default `20`) to end; the tasks still running then are marked as failed, with "Netshot was stopped while the task was running." in their log. They were killed, and at the next start re-run or skipped as too late. With Docker, allow Netshot more time than its default 10 seconds to stop (`stop_grace_period: 30s` in the provided `compose.yaml`, or `docker stop -t 30`).
* Configuration reload (`HUP` signal, `systemctl reload netshot`): the [configuration reference](configuration-reference.md) now tells which parameters a reload applies (**Reload** column). If the configuration can't be read (missing or empty file, unreadable secret file), the current configuration is kept; it was replaced by an empty one, which disabled RADIUS, TACACS+ and OIDC and reset the password policy. A reload now also: removes the TACACS+ servers removed from the configuration, disables OIDC when `netshot.aaa.oidc.idp.url` is removed (and fetches the IdP metadata again when the IdP or the client changes), applies `netshot.aaa.maxidletime` to the new sessions (it was only applied at the next start), and reads the secret files again.
* Configuration: a warning is logged at startup and on each reload for every `netshot.*` parameter (or `NETSHOT_*` environment variable) which doesn't match any known parameter, typically a typo, and for every changed parameter which is only applied at the next start.
* Startup: the Syslog server, the SNMP trap receiver and the SSH server are started after the task manager, so that a message received during the startup can trigger an automatic snapshot. The Syslog server no longer stops for good after an error on its socket: it keeps trying to open its port again.
* REST API: `GET /hooks`, `GET /users`, `GET /apitokens`, `GET /groups`, `GET /rules`, `GET /policies/{id}/rules`, `GET /hardwarerules`, `GET /scripts`, `GET /vaultinstances` and `GET /diagnostics` list the items by ID, `GET /rules/{id}/exempteddevices` by device ID, `GET /devices/{id}/complianceresults` by policy and rule ID, the target groups of a policy are listed by ID, and the compliance reports (`GET /reports/configcompliancedevicestatuses`, `GET /reports/groupconfignoncompliantdevices/{id}`) list the results by device, policy and rule ID (the order was the one the database chose).
* REST API: `PUT /tasks/{id}` with `"cancelled": true` now returns the task as stored once the cancellation is done (`CANCELLED` with the cancellation logged for a scheduled task, `cancelRequested` set for a running one), instead of the task as it was before. `GET /devices/{id}/tasks` lists the tasks with the same status and date by descending ID (the order was the one the database chose).
* REST API: `GET /devices`, `POST /devices/search` and `GET /devicefamilies` list the devices by ID (the order was the one the database chose, so a page given by `offset`/`limit` may now hold other devices), and `GET /devices/{id}/configs` lists the configurations with the same date by descending ID.
* Devices: a snapshot, a script or diagnostics no longer keep a database transaction open while they talk to the device; the device is saved at the end, only if it was not changed meanwhile. If the device was edited (for example through the web UI or the API) while the task ran, the task now fails instead of silently overwriting that edit: run it again.
* Search: `[IP] contains <address>` (devices with an interface subnet containing the address) is now accepted; it was always refused.
* Search: the `%` and `_` characters of a value compared with `is`, `contains`, `containsnocase`, `startswith` or `endswith` now match literally; they were used as SQL wildcards, so `[Name] contains "a_b"` also found `axb`. Check the dynamic groups (and other saved searches) which relied on them as wildcards, and use `matches` with a regular expression instead.
* Purge: the configuration size filter of the database purge now also takes the size of the binary files of the configurations into account; only the long texts (such as the running configuration) were compared to it, so a configuration with only big binary files was never purged by size.
* Diagnostics: deleting a diagnostic, or changing its target group, deleted its results but left their long texts in the database (`long_text_configuration` table). They are now deleted with the results, and the upgrade deletes those left by earlier versions, which may take a while on a large database.
* PostgreSQL 15 or later is required (the provided `compose.yaml` uses PostgreSQL 17). Upgrade older database servers before upgrading Netshot.
* Database: the foreign keys, unique constraints and indexes of foreign keys are renamed after their table and columns (`fk_device_mgmt_domain`, `uk_domain_name`, `ix_module_device`...) instead of generated names (`FK_6w0sgusprwk0sum7kho1yl9n7`); update any external script or tool referring to them by name. Deleting the last configuration of a device now clears the reference to it, instead of deleting the device.
* Database: the rules, diagnostics and device groups are each stored in one table, with a `type` column (`TEXT`, `JAVASCRIPT`, `PYTHON`; `SIMPLE`, `JAVASCRIPT`, `PYTHON`; `STATIC`, `DYNAMIC`): the `text_rule`, `java_script_rule`, `python_rule`, `simple_diagnostic`, `java_script_diagnostic`, `python_diagnostic`, `static_device_group` and `dynamic_device_group` tables are merged into `rule`, `diagnostic` and `device_group`, and dropped. A row of these tables without a matching subtable row (which Netshot couldn't load) is deleted. The enumerations stored as numbers are stored as names (for example `device.status` holds `INPRODUCTION` instead of `1`; also the network class and software level of the devices, the task status, schedule type and schedule mode, the compliance results, the software rule levels, the diagnostic result types and the IP address usages). Update any external SQL query or report on these tables.
* REST API: a request body which can't be read (malformed JSON or XML, value of the wrong type, unknown property) now answers 400 (`NETSHOT_INVALID_REQUEST_PARAMETER`, with the reason in the message) instead of an empty 500 response.
* REST API: `GET /reports/hardwaresupportdevices/{type}/{date}` with a type other than `eos` or `eol` now answers 400 (`NETSHOT_INVALID_REQUEST_PARAMETER`) instead of a database access error (500).
* REST API: webhooks: a relative target URL is now refused with a 400 error (`NETSHOT_INVALID_HOOK_WEB_URL`) instead of 500, and `"triggers": null` in `PUT /hooks/{id}` now removes all the triggers (as on creation) instead of failing with a 500 error.
* REST API: an `id` posted with a new credential set (`POST /credentialsets`) is now ignored, as for the other resources; it used to make the request fail with a 500 error.
* REST API: a `null` name in a domain or group request now answers 400 (`NETSHOT_INVALID_DOMAIN_NAME`, `NETSHOT_INVALID_GROUP_NAME`) instead of 500, and a `null` domain description is stored as empty.
* REST API: `POST /domains` and `PUT /domains/{id}` with a malformed `ipAddress` now answer 400 (`NETSHOT_MALFORMED_IP_ADDRESS`), and without `ipAddress` 400 (`NETSHOT_INVALID_IP_ADDRESS`), instead of 500.
* REST API: an unknown ID now answers 404 on `GET /tasks/{id}` (`NETSHOT_TASK_NOT_FOUND`) and `GET /scripts/{id}` (`NETSHOT_SCRIPT_NOT_FOUND`), which used to answer 204 with an empty body, and on `PUT /tasks/{id}` (`NETSHOT_TASK_NOT_FOUND`), which used to answer 400 (`NETSHOT_INVALID_TASK`).
* REST API: the `userInputDefinitions` posted with a script (`POST /scripts`) are now ignored, and the script is always evaluated to extract them. Posting them used to skip the evaluation, so a script with a syntax error could be saved; object entries (as returned by `GET /scripts`) were refused.
* REST API: a target group which doesn't exist is now refused (400, `NETSHOT_INVALID_GROUP`) when creating or updating a policy, a software rule, a hardware rule or a diagnostic. It used to be silently dropped: the policy had one group less, and the rule or diagnostic applied to all the devices.
* REST API: `POST /apitokens` now refuses a token value which is already registered (409, `NETSHOT_DUPLICATE_API_TOKEN`). A token value registered several times never authenticated anyone; if you have such duplicates, delete them and register the token again.
* Password policy: the `netshot.aaa.passwordpolicy.*` rules now also apply when an administrator creates a local user (`POST /users`, or the user dialog of the web UI); they used to apply only when a password was changed.
* REST API: `PUT /credentialsets/{id}` with an unknown `mgmtDomain` is now refused (`NETSHOT_INVALID_DOMAIN`, as `POST /credentialsets` does), instead of silently removing the domain restriction of the credential set.
* **Search by IP (0.25.x)**: since the upgrade to 0.25.0, the devices created with an earlier version were missing their resolved management IP address until their next successful snapshot. Until then, a search by IP (such as typing an address in the device search box) couldn't find them by their management address, and an SNMP trap or a syslog message from that address didn't trigger an automatic snapshot. A device that is never successfully snapshotted, such as a `[NONAME]` device, stayed in that state. The upgrade sets the missing address of all the devices whose management address is an IP address.
* **Static groups (0.25.x)**: since 0.25.0, a device was removed from all its static groups each time its dynamic groups were refreshed, that is after a snapshot, a compliance check, diagnostics, a script run, a device type discovery, or a device creation or update through the API ([#413](https://github.com/netshot-net/Netshot/issues/413)). This is fixed, but the lost memberships can't be restored automatically: check your static groups after the upgrade, and add the missing devices again (or restore them from a database backup taken before the upgrade to 0.25).
* XML request bodies (`Content-Type: application/xml`) used to be rejected with a 500 error on all `POST` and `PUT` endpoints, and are now accepted, with the same properties as the JSON bodies (the name of the root element is ignored). Clients which sent JSON are not affected. See [XML requests](api/rest-api.md#xml-requests).

### Any version to 0.25.1+

**Container image**: the image no longer has built-in secrets (previous images had `netshot` as DB password and encryption password, and `Netsh01` as SNMP trap community). If you run the image without setting them yourself (the provided `compose.yaml` sets the passwords):

* Set the encryption password to `netshot` (`NETSHOT_DB_ENCRYPTIONPASSWORD`, or better `NETSHOT_DB_ENCRYPTIONPASSWORD_FILE`): the image now refuses to start without it, and a different value would make the stored device credentials unreadable.
* Set `NETSHOT_SNMPTRAP_COMMUNITY` to `Netsh01` if your devices send SNMP v1/v2c traps with this community: no community is accepted by default anymore. The provided `compose.yaml` now sets it.
* The DB password still defaults to `netshot`.

**Kubernetes**: the sample manifest now requires a `dbencryption` secret. If you deployed Netshot without it, set it to `netshot`.

**Reverse proxy**: the session cookie is now marked as `Secure` (only sent over HTTPS) when `netshot.http.trustxforwardedfor` is enabled (as in the provided `compose.yaml`), in addition to when Netshot serves HTTPS itself. If users access Netshot over plain HTTP through a reverse proxy, set the new `netshot.http.securecookie` option to `false`, otherwise they won't be able to log in. If Netshot is accessed over HTTPS through a reverse proxy without `trustxforwardedfor`, set `netshot.http.securecookie` to `true`.

**Secrets**: secret parameters (DB password, encryption password, AAA secrets...) can now be read from files, see [Secrets in files](configuration-reference.md#secrets-in-files). This is recommended over environment variables, which are visible to anyone who can inspect the process or the container (and were visible to user-written Python scripts in previous versions). See [Running Netshot in Docker](installation/docker.md#using-secret-files-recommended) to switch an existing Compose deployment.

**Java/GraalVM**: Netshot now runs on GraalVM for JDK 25 (25.0 LTS line), with the 25.0 GraalVM polyglot libraries (GraalJS, GraalPy). Upgrade the Java environment as described in the [install guide](installation/linux.md#2-install-the-graalvm-jre) (the container image already ships it). Oracle GraalVM for JDK 21 updates are no longer provided under the free GraalVM license from October 2026.

**Python**: the embedded Python version moves from 3.11 to 3.12. If you use a [Python virtualenv](extending/python-virtualenv.md), recreate it with the matching GraalPy version (see [GraalPy](installation/linux.md#10-optional-graalpy)) and reinstall your packages. Python scripts can no longer see the environment variables of the Netshot process, and Python compliance rules can now import the modules of the Python standard library (such as `json`) with the default file system filter.

**Logging**: the Java classes were moved to a new package layout, so `netshot.log.class.<ClassName>` settings referring to Netshot classes (`net.netshot.netshot.*`) must be updated to the new package names.

**Clustering**: when `netshot.cluster.id` is not set, the member ID generated from the MAC address now ends with the HTTP port (it previously always ended with `0000`). Each such member therefore joins the cluster under a new ID after the upgrade; the tasks assigned to its former ID are handled like those of a lost member. Set `netshot.cluster.id` to keep a stable ID.

**Webhooks**: the YAML payload of a webhook now has the same content as the JSON one (it used to be the REST API representation of the task, without the embedded device and some other fields), including the `type` properties, which used to be written as YAML tags (`!<TakeSnapshotTask>`; the type ids with a space, such as `!<SNMP v1>`, were invalid YAML). Update the receivers which parse YAML payloads.

### Any version to 0.20+

Java environment must be upgraded to GraalVM for Java 21. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.19+

Java environment must be upgraded to GraalVM for Java 17. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.16+

Starting with version 0.16, Netshot relies on GraalVM capabilities to execute JavaScript and Python driver, compliance and diagnostic scripts. Thus the standard Oracle or OpenJDK JRE must be replaced by a GraalVM JRE.

Please refer to the [install guide](installation/linux.md) to set up GraalVM.

!!! note
    For each version, check the [install guide](installation/linux.md) for the proper GraalVM version to use.
