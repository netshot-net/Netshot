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

### Any version to 0.25.1+

**Clustering**: the tasks working on a device (snapshot, diagnostics, compliance check) are now always assigned to the same runner while the set of available runners doesn't change; they went to a random runner, as the other tasks still do (according to the runner weights). The distribution of the tasks between the runners may change after the upgrade.

**Reverse proxy**: the session cookie is now marked as `Secure` (only sent over HTTPS) when `netshot.http.trustxforwardedfor` is enabled (as in the provided `compose.yaml`), in addition to when Netshot serves HTTPS itself. If users access Netshot over plain HTTP through a reverse proxy, set the new `netshot.http.securecookie` option to `false`, otherwise they won't be able to log in. If Netshot is accessed over HTTPS through a reverse proxy without `trustxforwardedfor`, set `netshot.http.securecookie` to `true`.

**Static groups**: since 0.25.0, a device was removed from all its static groups each time its dynamic groups were refreshed, that is after a snapshot, a compliance check, diagnostics, a script run, a device type discovery, or a device creation or update through the API ([#413](https://github.com/netshot-net/Netshot/issues/413)). This is fixed, but the lost memberships can't be restored automatically: check your static groups after the upgrade, and add the missing devices again (or restore them from a database backup taken before the upgrade to 0.25).

**Search by IP**: since the upgrade to 0.25.0, the devices created with an earlier version were missing their resolved management IP address until their next successful snapshot. Until then, a search by IP (such as typing an address in the device search box) couldn't find them by their management address, and an SNMP trap or a syslog message from that address didn't trigger an automatic snapshot. A device that is never successfully snapshotted, such as a `[NONAME]` device, stayed in that state. The upgrade sets the missing address of all the devices whose management address is an IP address.

**Tasks**: the upgrade indexes the device columns of the task table, which lost their indexes in 0.25.0: listing the tasks of a device (device page) and deleting a device scanned the whole table, which got slower as the task history grew. Building the indexes may take a little while on a large task table.

**HPE ArubaOS-Switch driver**: the family of these switches is now `Aruba <platform>` (e.g. `Aruba 2530`) instead of `Avaya <platform>`, and the disabled ports are now shown as disabled (all the ports were shown as enabled). The family changes at the next snapshot of each device: update the dynamic groups, compliance rules and reports which select these switches by an `Avaya` family.

**REST API**: a `null` name in a domain or group request now answers 400 (`NETSHOT_INVALID_DOMAIN_NAME`, `NETSHOT_INVALID_GROUP_NAME`) instead of 500, and a `null` domain description is stored as empty. Updating a hook with `"triggers": null` (`PUT /hooks/{id}`) now removes all its triggers, as on creation, instead of failing with a 500 error.

### Any version to 0.20+

Java environment must be upgraded to GraalVM for Java 21. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.19+

Java environment must be upgraded to GraalVM for Java 17. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.16+

Starting with version 0.16, Netshot relies on GraalVM capabilities to execute JavaScript and Python driver, compliance and diagnostic scripts. Thus the standard Oracle or OpenJDK JRE must be replaced by a GraalVM JRE.

Please refer to the [install guide](installation/linux.md) to set up GraalVM.

!!! note
    For each version, check the [install guide](installation/linux.md) for the proper GraalVM version to use.
