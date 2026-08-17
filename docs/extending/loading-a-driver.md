# Loading an alternative driver into Netshot

A driver is a piece of (JavaScript) script which allows Netshot to talk with a specific family of devices — see [Device drivers](../user-guide/device-drivers.md) for the concept. Netshot comes by default with a number of included "official" drivers, generally in their last known version at the moment of the package release. You can find the list of official Netshot driver files in the [`src/main/resources/drivers`](https://github.com/netshot-net/Netshot/tree/master/src/main/resources/drivers) folder of the repository.

If you want to write your own driver — see [Writing a new driver](writing-a-driver.md) — or want to install a patched version of an existing driver, you can load that additional driver into Netshot by following these steps:

1. Copy the driver (`file.js`) to the server where Netshot runs, into the driver folder, `/usr/local/netshot/drivers` by default.
2. In the Netshot web interface, as an admin user, go to the Admin tab and click **Reload the drivers** at the top of the page.
3. You should then see the version of the driver updated in the table of drivers. The next snapshot should use the updated version.

!!! note
    The location of additional drivers can be customized by the administrator using the `netshot.drivers.path` entry in the Netshot configuration file `/etc/netshot.conf`.

!!! note
    In case an updated driver doesn't show up in the table of drivers on the Admin page, look at the Netshot logs (by default `/var/log/netshot/netshot.log`) to find any error reported by Netshot when loading the driver.
