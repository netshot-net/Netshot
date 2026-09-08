# Device drivers

Each supported type of device in Netshot relies on a single JavaScript file, often called a _driver_. This file describes the special attributes of the device type (e.g. running configuration and configuration register for a Cisco IOS device), and contains runnable code that will be executed when interacting with the device (e.g. taking a snapshot of the device, or reading it over a REST API instead of a CLI).

## Driver location

The Netshot package (especially the `.jar` file) contains several built-in drivers for some well-known network device operating systems. These drivers are automatically loaded from the package without further action, which makes Netshot able to manage most router and switch devices out of the box.

Netshot also automatically loads drivers from the file system, which allows you to add your own driver files, by placing them in the directory defined in the Netshot configuration file by `netshot.drivers.path`.

If you want to make changes to an existing built-in driver, you can extract it from the `.jar` file, and copy it to the driver directory. A driver loaded from the file system takes priority over the built-in driver of the same name.

Drivers can be dynamically reloaded while Netshot runs, from the Admin page — see [Device types](administration.md#device-types).

## Writing or extending a driver

Writing a new driver, or modifying an existing one, is a matter of writing JavaScript against Netshot's driver API. See [Writing a new driver](../extending/writing-a-driver.md) for the methodology and the full API reference, and [Loading an alternative driver](../extending/loading-a-driver.md) for installing the result.
