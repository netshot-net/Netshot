# Python virtualenv for additional packages

!!! warning "Experimental"
    This feature is experimental. Requires Netshot 0.16.2 or later.

Python compliance rules can import packages which are not part of the Python standard library (e.g. `ciscoconfparse`), installed in a (GraalPy) Python virtual environment.

!!! tip "Parsing configurations"
    To parse device configurations, you don't need any additional package: the built-in [script toolbox](../user-guide/script-toolbox.md) provides a configuration parser (`from netshot.toolbox import ConfigTree`) along with a [ciscoconfparse-compatible class](../user-guide/script-toolbox.md#migrating-from-ciscoconfparse).

!!! note
    The virtual environment (and the other `netshot.python.*` options) only applies to Python **compliance rules**, not to Python diagnostics.

The virtual environment must be created with the GraalPy version matching the one embedded in Netshot (see the [compatibility matrix](../installation/linux.md#2-install-the-graalvm-jre) and the [GraalPy section](../installation/linux.md#10-optional-graalpy) of the installation guide). After upgrading to a Netshot version embedding a different Python version (e.g. 3.11 to 3.12 with Netshot 0.25.1), recreate the virtual environment and reinstall the packages.

## Linux installation

Check the [GraalPy section](../installation/linux.md#10-optional-graalpy) of the installation guide if `/usr/lib/graalpy` is missing.

```bash
su - netshot -s /bin/bash
mkdir -p /usr/local/netshot/python && cd /usr/local/netshot/python
/usr/lib/graalpy/bin/graalpy -m venv venv
venv/bin/graalpy -m pip install --upgrade pip
venv/bin/graalpy -m pip install [some package]
```

Add the following line to `netshot.conf`, then restart Netshot:

```ini
netshot.python.virtualenv = /usr/local/netshot/python/venv
```

## Docker

The Netshot container image ships the matching GraalPy version in `/usr/lib/graalpy`. Create the virtual environment in a persistent volume, such as `/var/local/netshot` with the provided `compose.yaml`:

```bash
docker compose exec netshot /usr/lib/graalpy/bin/graalpy -m venv /var/local/netshot/python/venv
docker compose exec netshot /var/local/netshot/python/venv/bin/graalpy -m pip install --upgrade pip
docker compose exec netshot /var/local/netshot/python/venv/bin/graalpy -m pip install [some package]
```

Then set the virtual environment path in the `environment` section of the `netshot` service in `compose.yaml`, and apply the change with `docker compose up -d`:

```yaml
      NETSHOT_PYTHON_VIRTUALENV: "/var/local/netshot/python/venv"
```

## Using the packages

In your compliance script, import the installed packages as usual:

```python
from ciscoconfparse import CiscoConfParse

def check(device):
  config = device.get('running_config')
  parse = CiscoConfParse(config.splitlines(), syntax='ios')
  for intf_obj in parse.find_objects_w_child(r'^interface', r'^\s+shutdown'):
    debug(intf_obj.text + " is shutdown")
  return result_option.CONFORMING
```

Use the rule test feature to check that a package works within Netshot before relying on it.

## Troubleshooting

**Installation fails with `ModuleNotFoundError` in `setup.py`**: some packages import their own dependencies at build time. Install these dependencies first, then the package with the `--no-build-isolation` option, e.g. for the [ciscoconfparse fork](https://github.com/SCadilhac/ciscoconfparse):

```bash
venv/bin/graalpy -m pip install setuptools wheel loguru==0.5.3
venv/bin/graalpy -m pip install --no-build-isolation git+https://github.com/SCadilhac/ciscoconfparse
```

**`Creating threads is not allowed`**: the package starts threads (e.g. logging libraries with background queues, such as `loguru` with `enqueue=True`). Allow thread creation:

```ini
netshot.python.allowcreatethread = true
```

**Some functions fail although the package works in a GraalPy shell**: within Netshot, Python scripts run sandboxed, with an emulated operating system layer. Some low-level functions are not available or behave differently: for example `socket.inet_pton` fails, so the `netaddr` package can't parse IP addresses. Prefer the standard library when possible (e.g. the `ipaddress` module).

**Last resort**: the following options give Python scripts full access to the host (files, processes, network, Java classes), with the privileges of the Netshot process. Only use them if all the users allowed to edit Python rules are fully trusted. Note that disabling the file system filter alone makes the virtual environment unreadable by the scripts.

```ini
netshot.python.allowallaccess = true
netshot.python.filesystemfilter = false
```

## Older versions

For Netshot versions between 0.16.2 and 0.20.0 (excluded), Python was part of the GraalVM JDK: create the virtual environment with `graalpython -m venv venv` instead.
