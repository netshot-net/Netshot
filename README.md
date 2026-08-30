# Netshot
Configuration and Compliance Management Software

https://www.netshot.net

## Features

* Configuration backup and history
* Many devices supported
  * Cisco IOS, NX-OS, IOS-XR, ACE, ASA, StarOS
  * Juniper JUNOS
  * Huawei NE
  * Brocade FastIron
  * Fortinet FortiOS
  * Alcatel-Lucent TiMOS
  * Oracle Acme Packet OS
  * Citrix NetScaler SDX
  * ...
  * Check the [complete list of included drivers](main/resources/drivers)
* Network inventory
* Software compliance
* Hardware compliance
* Configuration compliance
* Extensibility
* Change automation
* User authentication
* Free

## How to start

Go to https://docs.netshot.net for installation and other instructions.

## Development with Docker Compose

* Basic dev stack: Netshot built from source, live-reloading web UI, simulated devices.

  ```bash
  docker compose -f compose.dev.yaml up --build
  ```

* With a local OIDC identity provider:

  ```bash
  docker compose -f compose.dev.yaml -f compose.dev.oidc.yaml up --build
  ```

* With clustering enabled (two Netshot nodes):

  ```bash
  docker compose -f compose.dev.yaml -f compose.dev.cluster.yaml up --build
  ```

Once the stack is up, open the web interface at https://localhost:8443/ (self-signed certificate, served through the `rproxy` container).

The `router1` and `router2` simulated devices can be added to the running Netshot instance using their Compose hostname (`router1` / `router2`) and credentials `admin` / `admin`.

## Contact

contact@netshot.net

## Related Projects

* https://github.com/scaleway/netbox2netshot
