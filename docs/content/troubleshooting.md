# Snapshot troubleshooting

## Enabling full trace debug

If snapshots keep failing on a specific device, you may want to collect a full trace of the commands and replies exchanged in the background between Netshot and the device. A hidden option makes this easy.

1. Select the device, and click the **Snapshot** button to open the **Take snapshot** dialog.
2. Double-click the icon (the blue arrow) to reveal the checkbox **Enable full debug of the CLI session (only for troubleshooting)**. Tick the box and click **Save** to start the snapshot.
3. After the snapshot has failed, click the **Download debug logs** link to get the full trace.

## Capturing SSH traces

1. Add the following line to `/etc/netshot.conf`:

   ```ini
   netshot.log.class.onl.netfishers.netshot.device.access.Ssh$JschLogger = ALL
   ```

2. Reload Netshot to apply:

   ```bash
   systemctl reload netshot.service
   ```

3. Start a snapshot and extract the logs from `/var/log/netshot/netshot.log`.
4. Remove the config line and reload Netshot to disable the temporary debug.
