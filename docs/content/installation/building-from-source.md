# Building Netshot from source

## Prerequisites

You need:

* GraalVM JDK (check the [installation guide](linux.md) for the proper version)
* Git

## Instructions

```bash
git clone https://github.com/netshot-net/Netshot.git
cd netshot
./mvnw clean
./mvnw package
```

The resulting JAR, `netshot.jar`, should be found under the `target` folder.

## Check CVE alerts

```bash
./mvnw verify
```
