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

### Any version to 0.20+

Java environment must be upgraded to GraalVM for Java 21. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.19+

Java environment must be upgraded to GraalVM for Java 17. Check the [install guide](installation/linux.md) for version details.

### Any version to 0.16+

Starting with version 0.16, Netshot relies on GraalVM capabilities to execute JavaScript and Python driver, compliance and diagnostic scripts. Thus the standard Oracle or OpenJDK JRE must be replaced by a GraalVM JRE.

Please refer to the [install guide](installation/linux.md) to set up GraalVM.

!!! note
    For each version, check the [install guide](installation/linux.md) for the proper GraalVM version to use.
