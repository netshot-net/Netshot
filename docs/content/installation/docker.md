# Running Netshot in Docker

## Using Compose to run all components

The fastest way to run Netshot is to use Docker Compose to start all required components.

```bash
wget https://raw.githubusercontent.com/netshot-net/Netshot/master/dist/compose.yaml
docker compose up -d
```

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
