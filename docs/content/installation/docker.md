# Running Netshot in Docker

## Using Compose to run all components

The fastest way to run Netshot is to use Docker Compose to start all required components.

```bash
wget https://raw.githubusercontent.com/netshot-net/Netshot/master/dist/compose.yaml
# Edit compose.yaml to change the passwords (change_this_pass...) before the first start
docker compose up -d
```

### Using secret files (recommended)

By default, `compose.yaml` passes the passwords as environment variables. These are visible to user-written Python scripts (compliance rules and diagnostics) and to anyone who can inspect the containers. To pass them as [secret files](../configuration-reference.md#secrets-in-files) instead:

1. Create the secret files next to `compose.yaml`. If Netshot was already started, use the **same values** as in `compose.yaml`: a different encryption password would make the device credentials stored in the database unreadable.

    ```bash
    mkdir -m 700 secrets
    printf '%s' 'the DB password' > secrets/db_password
    printf '%s' 'the encryption password' > secrets/db_encryption_password
    chmod 644 secrets/*
    ```

    The `secrets` folder (mode `700`) protects the files on the host; the files themselves must be readable by the `postgres` user of the database container.

2. In `compose.yaml`, uncomment the lines marked `Secret files`, and remove the `NETSHOT_DB_PASSWORD`, `NETSHOT_DB_ENCRYPTIONPASSWORD` and `POSTGRES_PASSWORD` variables.
3. Restart the stack: `docker compose up -d`.

Keep a backup of the encryption password: without it, the device credentials stored in the database can't be decrypted.

### Running the container without the Compose file

The Netshot container image has no built-in secrets: at least the encryption password must be passed (`NETSHOT_DB_ENCRYPTIONPASSWORD_FILE` or `NETSHOT_DB_ENCRYPTIONPASSWORD`), otherwise Netshot refuses to start. See the [configuration reference](../configuration-reference.md) for the other parameters.

You can try different versions of the Netshot container:

* `ghcr.io/netshot-net/netshot:latest` for the last release
* `ghcr.io/netshot-net/netshot:master` for the latest code
* `ghcr.io/netshot-net/netshot:sha-...` for a specific commit

## Building from the code repository

### Compose stack

To fetch the current code, build and start Netshot (along with the DB and reverse proxy containers):

```bash
git clone https://github.com/netshot-net/Netshot
cd Netshot
docker compose up -d
```

### Local build of the container

```bash
# Select a specific version
VERSION=0.19.1
git clone https://github.com/netshot-net/Netshot
cd Netshot
git checkout v$VERSION
docker build -t netshot:$VERSION .
```
