# Reports

The _Reports_ section gives an overview of the network status by aggregating data collected by the other modules.

## Configuration changes

The _Configuration changes_ report shows the configuration changes detected by Netshot over the last hours or days, or on a precise day.

Note that the change date/time is the moment when Netshot took the snapshot, not the precise time of the change itself. Even if the snapshot was automatically taken after detection of the change, it occurred several minutes after the actual change. Also, if several changes were made to the configuration of a device within a few minutes, Netshot probably saw a single change only.

## Device access failures

The _Device access failures_ report lists the devices which haven't been successfully backed up by a snapshot task for the last X days, where X can be selected using the numeric field (3 by default). Change the number of days and click the _Update_ button to refresh the list.

The disabled devices are excluded from the list.

The purpose of this report is to easily identify which devices are not responding to Netshot snapshot attempts anymore.

## Configuration compliance

This report gives the compliance percentage for device groups. A device is flagged as non-compliant as soon as it fails at least one rule.

If you click on a group, you'll see the list of non-conforming devices.

If a group of devices doesn't appear in the compliance reports, this is probably because it was marked as _hidden_. Edit it in the Devices section to change this.

## Software compliance

This report gives you, for each group of devices, the percentage of Gold, Silver, Bronze and non-compliant devices, resulting from the software rules defined in the Compliance section.

Click on a category in the legend to display the matching devices at the bottom of the page.

## Hardware support status

This report gives the trend of hardware support over time. This is based on end-of-sale and end-of-life dates that you have defined for part numbers in the Compliance section. When a hardware module becomes end-of-sale (or end-of-life), any device with such a module becomes itself end-of-sale (or end-of-life). The graph in the _Hardware support_ report gives the number of end-of-sale and end-of-life devices, increasing over time. The milestones (dates when batches of devices become end-of-sale or end-of-life) are listed below the table. If you click on the number of devices, you'll get the actual corresponding devices.

## Data export

You can export data collected by Netshot into an Excel file.

The options are self-explanatory. Click on _Download the result_ to generate and get the file. The generation of the file could take a few minutes if there are many devices in the database.
