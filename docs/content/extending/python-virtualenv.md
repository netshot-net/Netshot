# Python virtualenv for additional packages

!!! warning "Experimental"
    This feature is experimental. Requires Netshot 0.16.2 or later.

```bash
su - netshot -s /bin/bash
cd /usr/local/netshot
mkdir python && cd python
# For versions between 0.16.2 and 0.20.0 (excluded):
graalpython -m venv venv
# For versions 0.21.2+
# Please check the GraalPy section of the installation guide if you are missing graalpy
/usr/lib/graalpy/bin/graalpy -m venv venv
source venv/bin/activate
/usr/local/netshot/python/venv/bin/graalpy -m pip install --upgrade pip
pip install [some package]
```

Add the following line to `netshot.conf`:

```ini
netshot.python.virtualenv = /usr/local/netshot/python/venv
```

In your compliance script, you can import the installed package:

```python
import site
import ...
```

For packages which require additional access to the system, the two additional configuration options might be useful, but are insecure:

```ini
netshot.python.allowallaccess = true
netshot.python.filesystemfilter = false
```
