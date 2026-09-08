# Administration

Administration screens require an Admin permission level and live under the Admin tab.

## Authentication

Netshot supports both local and remote accounts. Local accounts are stored and authenticated in the Netshot database. Remote authentication delegates to a RADIUS server, a TACACS+ server, or an OIDC Identity Provider.

On login, Netshot first checks for a local account with that username. If found, the password is checked directly against it. If no local account exists and at least one RADIUS or TACACS+ server is configured, Netshot tries a challenge against it:

- **RADIUS**: on success, the user is allowed as Visitor by default. A `Service-Type` attribute of `Administrative-User` grants Admin, `Outbound-User` grants Execute-scripts, and `NAS-Prompt-User` grants Read-write. If the user already exists locally as a *remote* user, their locally-assigned permission level overrides the RADIUS-provided one.
- **TACACS+**: the `role` attribute returned by the server is checked against known values (`admin`, `execute-read-write`, `read-write` by default — customizable in `netshot.conf`).

See [Single Sign-On (OIDC)](#single-sign-on-oidc) below for the third option.

To enable local authentication, just create local accounts in the Admin page.

To enable RADIUS, declare up to 3 servers in `netshot.conf`:

```ini
netshot.aaa.radius1.ip = 1.2.3.4
netshot.aaa.radius1.authport = 1812
netshot.aaa.radius1.secret = MyKey#1
netshot.aaa.radius2.ip = 1.2.3.5
netshot.aaa.radius2.authport = 1812
netshot.aaa.radius2.secret = MyKey#1
```

To enable TACACS+, similarly:

```ini
netshot.aaa.tacacs1.ip = 1.2.3.4
netshot.aaa.tacacs1.port = 49
netshot.aaa.tacacs1.secret = MyKey#1
```

See the [configuration reference](../configuration-reference.md#user-authentication) for every available authentication setting.

### Permission levels

Netshot has five permission levels, from least to most privileged:

| Level | Can do |
|---|---|
| **Visitor** (read-only) | Browse everything; make no changes. |
| **Operator** | Visitor, plus trigger standard tasks: run snapshots, diagnostics, compliance checks. |
| **Read-write** | Operator, plus create/edit/delete devices, groups, and policies, and schedule tasks. |
| **Execute-scripts** | Read-write, plus create, edit, and run device scripts and diagnostics. |
| **Admin** | Full access, including user management and everything under the Admin tab. |

The Web UI hides — rather than merely disables — buttons and sections a user doesn't have permission for, so what's shown reflects what's actually usable.

## Single Sign-On (OIDC)

Netshot supports OpenID Connect as a third authentication option, using the Authorization Code flow with an authenticated client. OIDC is used only for the initial login: once authenticated, the session is tracked with a Netshot-issued cookie, independent of the IdP.

Login flow:

1. At startup, and periodically afterward, Netshot fetches the IdP's metadata from its well-known discovery URL.
2. The login page shows an SSO button.
3. Clicking it redirects the browser to the IdP.
4. On successful IdP authentication, the browser is redirected back with a temporary authorization code.
5. The Web UI verifies the returned state and posts the code to the Netshot backend.
6. The backend exchanges the code for a token directly with the IdP, authenticating itself with the configured client ID/secret.
7. Netshot validates the token and reads the username and role from the mapped ID token claims.
8. A session is created and a cookie handed to the browser; subsequent requests authenticate with that cookie.

If a session is idle long enough to expire, the Web UI prompts the user to re-authenticate rather than silently dropping them.

To enable OIDC, set at minimum the IdP URL and client credentials in `netshot.conf`:

```ini
netshot.aaa.oidc.idp.url = https://idp/path/to/realm
netshot.aaa.oidc.clientid = netshot
netshot.aaa.oidc.clientsecret = MySecret
```

Role mapping and IdP metadata refresh behavior are also configurable — see the [OIDC settings](../configuration-reference.md#user-authentication) in the configuration reference for `netshot.aaa.oidc.role.*`, `netshot.aaa.oidc.usernameclaimname`, and the metadata `refreshinterval`/`retryinterval` settings.

## API tokens

API tokens let scripts and external tools call the REST API without a session cookie. Click **Add**, choose a permission level (same levels as user accounts), and give it a description to identify it in the list later. **Copy the token immediately** — it can't be retrieved again once created.

See [REST API](../api/rest-api.md) for how to use a token.

## Device domains

Every device belongs to a management domain, which defines settings that apply to its devices:

- The domain's configured IP address is how devices in it see the Netshot server — relevant for drivers that upload data to Netshot via FTP/TFTP.
- Credential sets can be scoped to a domain, in which case only that domain's devices try them.

Domains are created in the Admin page and can't be deleted while they still contain a device or have a credential set scoped to them.

## Device credentials

Credentials used to reach devices are managed globally from the Admin page: SNMP v1/v2c/v3, Telnet, and SSH (versions 1.5, 1.99, or 2.0).

SSH authentication supports a password or an SSH key pair — paste the contents of `~/.ssh/id_rsa` (private) and `~/.ssh/id_rsa.pub` (public) if using `ssh-keygen`-generated keys.

A credential set can be scoped to a management domain (used only by that domain's devices) or left unscoped (usable by any device). When a device is added, available credential sets are tried in turn; the one that works is remembered as the device's primary set.

Each credential field can independently be **Local** (stored, encrypted, in the Netshot database) or **Vault-backed** (resolved at connection time from an external Vault instance — see below). SNMPv1/v2 community strings are encrypted at rest, and SNMPv3 supports the SHA-2 family of authentication protocols (`HMAC128SHA224`, `HMAC192SHA256`, `HMAC256SHA384`, `HMAC384SHA512`, alongside the legacy `MD5`/`SHA`) and privacy protocols up to `AES256`.

For per-device connection-security settings (SSH host key verification, HTTPS certificate trust), see [Connection security](devices.md#connection-security) in the Devices guide.

## Vault instances

Instead of storing device credential secrets locally, Netshot can resolve them at connection time from a HashiCorp Vault-compatible KV v2 backend. This is configured per Vault **instance**, created in the Admin page:

- **Base URL** — the Vault server's base URL (e.g. `https://vault.example.com:8200`).
- **Namespace** *(optional)* — a Vault Enterprise namespace.
- **KV mount path** — where the KV v2 secrets engine is mounted (defaults to `secret`).
- **Certificate trust** — Trust any / System truststore / Custom CA, same model as [device HTTPS access](devices.md#connection-security).
- **Authentication method** — one of:
    - **AppRole** — a static `role_id`/`secret_id` pair, optionally against a non-default AppRole mount path.
    - **JWT** — Netshot obtains a JWT from an external OAuth2 IdP (client-credentials grant, with client ID/secret and optional scope), then exchanges it with Vault's JWT auth method against a configured Vault role and mount path.

Use **Test connection** after configuring an instance to confirm it can authenticate and reach the KV mount. Instances configured over plain HTTP (rather than HTTPS) are flagged with a warning.

Once an instance exists, individual credential fields on a device credential set can be switched from Local to Vault-backed, pointing at a path within that instance's KV mount (e.g. `app/creds/username`). Vault-issued auth tokens are refreshed automatically in the background before they expire, and resolved secrets are cached briefly to limit load on Vault — both behaviors are tunable via the `netshot.vault.*` settings in the [configuration reference](../configuration-reference.md#vault-integration).

## Device types

The Admin page lets an administrator reload [device driver](device-drivers.md) files at runtime: click **Reload the drivers** to re-scan and pick up any change made to them. This action requires the Admin permission level.

## Web hooks

Netshot can trigger HTTP POST calls on specific events, such as a snapshot task finishing. See [Webhooks](../api/webhooks.md).

## Cluster members

In clustering mode, the Admin page lists cluster members and their state. See [Clustering and High Availability](../clustering-ha.md). Expired members disappear the next time the current server reloads the list.
