---
icon: lucide/rocket
---

# Netshot

Netshot is a free, open-source configuration and compliance management tool for network devices.

With Netshot, you can:

- Keep an operational inventory of your network devices.
- Back up device configurations and track changes over time.
- Track the IP and MAC addresses used across your network.
- Track the software and hardware versions in use, and their support status.
- Check device conformance against best-practice or custom policies and rules — for software, hardware, and configuration.
- Script changes and diagnostics on your devices.
- Interact with Netshot from other tools through its REST API.
- Authenticate users locally, or delegate to RADIUS, TACACS+, or an OIDC identity provider for Single Sign-On.

Many widely-used network devices are supported out of the box, and adding support for a new platform is a matter of writing a driver — a single script file. See [Architecture](architecture.md) for how the pieces fit together, or jump straight to [installing Netshot](installation/docker.md).

## Logging in

Netshot is used through a web application. Once an [authentication method](user-guide/administration.md#authentication) has been configured by an administrator — local accounts, RADIUS, TACACS+, or [OIDC Single Sign-On](user-guide/administration.md#single-sign-on-oidc) — you can log in. The default local account on a freshly-initialized database is `admin` / `netshot`.

After logging in, the main navigation lets you move between devices, compliance, reports, tasks, and administration (depending on your permission level). Click your username in the top bar to see your current permission level or, for local accounts, change your password.

## Where to go next

- [Architecture](architecture.md) — the main components of Netshot and how they fit together
- [Installation](installation/docker.md) — running Netshot in Docker or on a Linux server
- [User guide](user-guide/devices.md) — devices, compliance, diagnostics, reports, tasks, and administration
- [Configuration reference](configuration-reference.md) — every `netshot.conf` setting
- [REST API](api/rest-api.md) and [webhooks](api/webhooks.md) — integrating with Netshot
