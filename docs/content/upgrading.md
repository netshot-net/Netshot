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

**Container image**: the image no longer has built-in secrets (previous images had `netshot` as DB password and encryption password, and `Netsh01` as SNMP trap community). If you run the image without setting them yourself (the provided `compose.yaml` sets the passwords):

* Set the encryption password to `netshot` (`NETSHOT_DB_ENCRYPTIONPASSWORD`, or better `NETSHOT_DB_ENCRYPTIONPASSWORD_FILE`): the image now refuses to start without it, and a different value would make the stored device credentials unreadable.
* Set `NETSHOT_SNMPTRAP_COMMUNITY` to `Netsh01` if your devices send SNMP v1/v2c traps with this community: no community is accepted by default anymore. The provided `compose.yaml` now sets it.
* The DB password still defaults to `netshot`.

**Kubernetes**: the sample manifest now requires a `dbencryption` secret. If you deployed Netshot without it, set it to `netshot`.

**Reverse proxy**: the session cookie is now marked as `Secure` (only sent over HTTPS) when `netshot.http.trustxforwardedfor` is enabled (as in the provided `compose.yaml`), in addition to when Netshot serves HTTPS itself. If users access Netshot over plain HTTP through a reverse proxy, set the new `netshot.http.securecookie` option to `false`, otherwise they won't be able to log in. If Netshot is accessed over HTTPS through a reverse proxy without `trustxforwardedfor`, set `netshot.http.securecookie` to `true`.

**Secrets**: secret parameters (DB password, encryption password, AAA secrets...) can now be read from files, see [Secrets in files](configuration-reference.md#secrets-in-files). This is recommended over environment variables, which are visible to anyone who can inspect the process or the container (and were visible to user-written Python scripts in previous versions). See [Running Netshot in Docker](installation/docker.md#using-secret-files-recommended) to switch an existing Compose deployment.

**Java/GraalVM**: Netshot now runs on GraalVM for JDK 25 (25.0 LTS line), with the 25.0 GraalVM polyglot libraries (GraalJS, GraalPy). Upgrade the Java environment as described in the [install guide](installation/linux.md#2-install-the-graalvm-jre) (the container image already ships it). Oracle GraalVM for JDK 21 updates are no longer provided under the free GraalVM license from October 2026.

**Python**: the embedded Python version moves from 3.11 to 3.12. If you use a [Python virtualenv](extending/python-virtualenv.md), recreate it with the matching GraalPy version (see [GraalPy](installation/linux.md#10-optional-graalpy)) and reinstall your packages. Python scripts can no longer see the environment variables of the Netshot process, and Python compliance rules can now import the modules of the Python standard library (such as `json`) with the default file system filter.

### Any version to 0.20+

Java environment must be upgraded to GraalVM for Java 21. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.19+

Java environment must be upgraded to GraalVM for Java 17. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.16+

Starting with version 0.16, Netshot relies on GraalVM capabilities to execute JavaScript and Python driver, compliance and diagnostic scripts. Thus the standard Oracle or OpenJDK JRE must be replaced by a GraalVM JRE.

Please refer to the [install guide](installation/linux.md) to set up GraalVM.

!!! note
    For each version, check the [install guide](installation/linux.md) for the proper GraalVM version to use.
