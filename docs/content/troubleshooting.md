# Snapshot troubleshooting

## Enabling full trace debug

If snapshots keep failing on a specific device, you may want to collect a full trace of the commands and replies exchanged in the background between Netshot and the device.

1. Select the device, and click the **Take snapshot** button to open the snapshot dialog.
2. Tick **Enable full trace of the CLI session (only for troubleshooting)** and click **Run** to start the snapshot. The task dialog opens.
3. Once the task is over, click the **Debug logs** button of the task dialog to download the full trace (this needs the read-write permission level or above).

The same option exists in the dialog to run a script on a device.

## Capturing SSH traces

The full trace above shows what is exchanged inside the CLI session. If the SSH connection itself fails (key exchange, algorithms, authentication), the logs of the SSH library (Apache Mina SSHD) give more details.

1. Add the following line to the Netshot configuration file (e.g. `/etc/netshot.conf`). This parameter is only read from the configuration file, not from environment variables:

   ```ini
   netshot.log.class.org.apache.sshd = DEBUG
   ```

2. Reload Netshot to apply:

   ```bash
   systemctl reload netshot.service
   ```

3. Start a snapshot and extract the logs from the Netshot log file (`netshot.log.file`, e.g. `/var/log/netshot/netshot.log`, or the container output when it is set to `CONSOLE`).
4. Remove the config line and reload Netshot to disable the temporary debug.
