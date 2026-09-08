# Installing Netshot on a Linux server

This guide walks through installing Netshot on a Linux server: database setup, the GraalVM runtime, the Netshot package itself, and the systemd service.

## 1. Database server

Install the database engine:

```bash
sudo apt-get install postgresql
# OR
sudo yum install postgresql15-server postgresql15
# or any other way depending on your package manager
```

Create the database and user for Netshot:

```bash
sudo /usr/pgsql-15/bin/postgresql-15-setup initdb # For RHEL/CentOS
sudo systemctl enable postgresql-15.service       # For RHEL/CentOS
sudo systemctl start postgresql-15.service        # For RHEL/CentOS
sudo -u postgres psql
```

```sql
CREATE USER netshot WITH ENCRYPTED PASSWORD 'netshot';
CREATE DATABASE netshot01 WITH OWNER 'netshot' ENCODING 'UTF8' TEMPLATE template0;
\q
```

!!! note
    See the [Security considerations](#8-security-considerations) section below regarding the database access password.

For RHEL/CentOS, ensure local MD5-based connections to the PostgreSQL DB are allowed, i.e. the presence of the following lines in `/var/lib/pgsql/15/data/pg_hba.conf`:

```ini
host    all             all             127.0.0.1/32            md5
host    all             all             ::1/128                 md5
```

## 2. Install the GraalVM JRE

Netshot requires the [GraalVM](https://www.graalvm.org) JRE (in order to run JavaScript and Python scripts).

Compatibility matrix:

| Netshot | Graal | Polyglot |
|---------|-------|----------|
| 0.16.0 - 0.16.3 | 21.1.0 (former numbering) | - |
| 0.16.4 - 0.18.x | 21.3.0 (former numbering) | - |
| 0.19.x | 17.0.8-graal | - |
| 0.20.x | 21.0.4-graal | 23.1.5 |
| 0.21.x - 0.23.x | 21.0.6-graal | 24.1.2 |
| 0.24.0 and newer | 21.0.9-graal | 24.1.2 |

For modern GraalVM versions:

```bash
GRAALVM_VERSION="21.0.9"
wget --quiet https://download.oracle.com/graalvm/${GRAALVM_VERSION%%.*}/archive/graalvm-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz && \
    tar xvzf graalvm-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz && \
    rm -f graalvm-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz && \
    JDIR=$(ls -d graalvm-jdk-${GRAALVM_VERSION}* | tail -n 1) && \
    mkdir ${JDIR}/languages && \
    sudo mkdir -p /usr/lib/jvm && \
    sudo mv ${JDIR} /usr/lib/jvm/${JDIR}
sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/${JDIR}/bin/java 92200
sudo update-alternatives --set java /usr/lib/jvm/${JDIR}/bin/java
# OR
sudo alternatives --install /usr/bin/java java /usr/lib/jvm/graalvm/bin/java 92200
```

Check that `java` now points to the GraalVM JRE:

```bash
java -version 2>&1 | grep Environment | grep GraalVM
```

It should return something like:

```text
Java(TM) SE Runtime Environment Oracle GraalVM 21.0.4+8.1 (build 21.0.4+8-LTS-jvmci-23.1-b41)
```

For GraalVM 17, install the JS and Python languages from GraalVM (ignore for GraalVM 21):

```bash
sudo /usr/lib/jvm/graalvm/bin/gu install js python
```

## 3. Other prerequisites

Some basic packages are required to be installed under RHEL/CentOS:

```bash
sudo yum install tar unzip freetype fontconfig dejavu-sans-fonts
```

For Debian/Ubuntu:

```bash
sudo apt install fontconfig
```

## 4. Add a dedicated system user

```bash
sudo adduser --system --home /usr/local/netshot --disabled-password --disabled-login netshot
# OR
sudo useradd --system -k /dev/null --create-home --home /usr/local/netshot -s /bin/false netshot
```

## 5. Create the SSL certificate

This certificate will be used by the embedded HTTP server. See [Security considerations](#8-security-considerations) below for more information.

```bash
sudo /usr/lib/jvm/graalvm/bin/keytool -genkey -keyalg RSA -alias selfsigned -keystore /usr/local/netshot/netshot.pfx -storepass password -validity 820 -keysize 4096 -storetype pkcs12 -ext san=dns:localhost -dname "CN=localhost, OU=Netshot, O=Netshot, L=A, ST=OCC, C=FR" -ext KeyUsage=nonRepudiation,digitalSignature,keyEncipherment -ext ExtendedKeyUsage=serverAuth
```

Press Enter when asked for a password (we don't use an additional password to protect the key in the keystore).

```bash
sudo chmod o-r /usr/local/netshot/netshot.pfx
```

## 6. Download and install Netshot

* Select your version on the [Release page](https://github.com/netshot-net/Netshot/releases).
* Download that file onto your server and unzip it (replace X.Y.Z with the actual version, e.g. 0.16.0):

```bash
NETSHOT_VERSION="X.Y.Z"
mkdir netshot_${NETSHOT_VERSION} && cd netshot_${NETSHOT_VERSION}
wget https://github.com/netshot-net/Netshot/releases/download/v${NETSHOT_VERSION}/netshot_${NETSHOT_VERSION}.zip
# OR
curl -L https://github.com/netshot-net/Netshot/releases/download/v${NETSHOT_VERSION}/netshot_${NETSHOT_VERSION}.zip --remote-name
unzip netshot_${NETSHOT_VERSION}.zip
sudo cp netshot.jar /usr/local/netshot
sudo mkdir /usr/local/netshot/drivers
sudo chown -R netshot /usr/local/netshot
sudo mkdir /var/local/netshot
sudo chown -R netshot /var/local/netshot
sudo mkdir /var/log/netshot
sudo chown -R netshot /var/log/netshot
sudo cp netshot.conf /etc/netshot.conf
sudo chown netshot /etc/netshot.conf
sudo chmod 400 /etc/netshot.conf
sudo cp systemd-netshot /etc/systemd/system/netshot.service
sudo systemctl daemon-reload
sudo systemctl enable netshot.service
```

## 7. Start the Netshot service

```bash
sudo systemctl start netshot
```

Now you should be able to access Netshot with a browser, on [https://localhost:8443/](https://localhost:8443/) on the machine itself. Use the account `admin` (password `netshot`) for the initial login (then you can create the real users in the Admin section).

## 8. Security considerations

### SSL certificate

For production purposes it is recommended to request and install a certificate approved by an authority you trust, and to use a strong password to protect the keystore (see the `netshot.http.ssl.keystore.pass` line in `netshot.conf`).

### Reverse proxy

Another option is to run Netshot behind a reverse proxy, such as NGINX.

In this case, if the reverse proxy and Netshot are located on the same machine, it *might* be (arguably) acceptable to run non-SSL HTTP between the reverse proxy and Netshot. Use `netshot.http.ssl.enabled = false` in `netshot.conf` for this purpose.

### Database access password

It is highly recommended to set up another password than the default `password` to access the Netshot DB. Use the `netshot.db.password` option in `netshot.conf` to change the password.

### Sensitive information password

In the DB, sensitive information such as device access CLI accounts and SNMP communities are encrypted using a password. It is recommended to change this password: please see the `netshot.db.encryptionpassword` option in `netshot.conf`.

### Database backup

!!! warning
    For production servers, it is highly recommended to schedule automatic backups of the DB and of the binary snapshot folder (`/var/local/netshot` by default). You might also want to back up the sensitive information password (see above) in a different place.

## 9. Optional - Syslog and SNMP trap UDP ports

By default, Netshot listens to non-privileged ports UDP/1162 (for SNMP traps) and UDP/1514 (for Syslog messages) instead of (respectively) UDP/162 and UDP/514.

If you want Netshot to detect changes from Syslog and/or SNMP messages sent by the devices without changing the default target ports on devices, you can edit `netshot.conf` and set the default ports, using the following parameters:

```ini
netshot.syslog.port = 514
netshot.snmptrap.port = 162
```

Although running as a non-root user, Netshot should be able to listen to privileged UDP ports thanks to the `CAP_NET_BIND_SERVICE` capability set by the systemd launch file.

## 10. Optional - GraalPy

If you are willing to use Python scripts within Netshot along with extra packages, you may want to install GraalPy locally:

```bash
GRAALPY_VERSION="24.1.2" # That's the 'Polyglot' column of the compatibility matrix above
wget --quiet https://github.com/oracle/graalpython/releases/download/graal-${GRAALPY_VERSION}/graalpy-jvm-${GRAALPY_VERSION}-linux-amd64.tar.gz && \
    tar xvzf graalpy-jvm-${GRAALPY_VERSION}-linux-amd64.tar.gz && \
    mv graalpy-${GRAALPY_VERSION}-linux-amd64 /usr/lib/graalpy
```

Once GraalPy is installed, you can create a virtualenv to install external packages: see the [Python virtualenv](../extending/python-virtualenv.md) page.
