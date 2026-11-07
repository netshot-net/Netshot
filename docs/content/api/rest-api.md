# REST API

This page describes the legacy REST API of Netshot, served on `/api`. It is kept unchanged for the existing integrations, and is **disabled by default**: set `netshot.http.api.v0.enabled` to `true` to serve it (see the [configuration reference](../configuration-reference.md#embedded-http-server)). The API supports both JSON and XML encoding.

!!! tip "REST API v1"
    The current API, which the Web UI is built on, is the [REST API v1](rest-api-v1.md) (`/api/v1`): paginated lists, NetBox-style filters, field selection, statistics, exports and standard HTTP semantics. New integrations should use it.

## Exploring the API

The OpenAPI definition is available on any Netshot deployment at:

```
https://<server:port>/api/openapi.json
```

(or `openapi.yaml`, or `openapi` in the format of the `Accept` header).

An interactive API browser is built into the Web UI — open it from the **Help** menu — for exploring and testing endpoints without leaving the application.

## Authentication

### API tokens

Generate a token from the Admin page's [API Tokens](../user-guide/administration.md#api-tokens) section. Copy it immediately; it can't be retrieved again once created.

Pass it as the `X-Netshot-API-Token` header on every request — this is the recommended method for scripts and integrations, and doesn't require session/cookie handling. The standard `Authorization: Bearer <token>` header is accepted too.

### JWT access tokens (OIDC)

When `netshot.aaa.oidc.idp.url` and `netshot.aaa.oidc.jwt.enabled` are set (see the [configuration reference](../configuration-reference.md#user-authentication)), the API also accepts the JWT access tokens issued by the Identity Provider, for instance obtained by a script or a CI job with the client credentials grant:

```bash
curl -H "Authorization: Bearer <JWT>" -H "Accept: application/json" https://<server:port>/api/v1/domains
```

The token signature is checked against the keys published by the IdP, as well as its issuer, its audience (`netshot.aaa.oidc.jwt.audience`, the OIDC client ID by default) and its expiration. The user name and the permission level come from the same claims as for an OIDC login of the Web UI (the user name falls back to the `sub` claim, a token without known role gets `netshot.aaa.oidc.role.defaultlevel`). As for an OIDC login, a token can't authenticate a user defined as local in Netshot, and the level of a remote user defined in Netshot is the one defined there. Each request is authenticated on its own: no session is created.

The JWT access tokens don't need the OIDC login of the Web UI: without `netshot.aaa.oidc.clientid` and `netshot.aaa.oidc.clientsecret`, the IdP is only used to validate the tokens (`netshot.aaa.oidc.jwt.audience` is then required), and the Web UI keeps its other login methods.

### Cookie-based (legacy)

`POST /api/user` with a username and password to obtain a `NetshotSessionID` session cookie, which can then be passed on subsequent calls. `DELETE /api/user` logs out.

### Precedence

A request is authenticated by the first kind of credentials it carries, in this order: API token (`X-Netshot-API-Token`, or a non-JWT `Authorization: Bearer`), JWT access token (`Authorization: Bearer` with a JWT), session cookie. Invalid credentials make the request anonymous (401 on a protected endpoint) even if a valid session cookie is also present: there is no fallback to another method. Every authentication by an API token or a JWT is recorded in the AAA log with the client address.

```bash
curl -v -X POST -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{ "username": "script", "password": "automation" }' \
  https://<server:port>/api/user
```

Note the returned cookie value, then reuse it:

```bash
curl -v -X POST -H "Content-Type: application/json" -H "Accept: application/json" \
  -H "Cookie: NetshotSessionID=<the ID>" -d '...' \
  https://<server:port>/api/...
```

```bash
curl -v -X DELETE -H "Accept: application/json" \
  -H "Cookie: NetshotSessionID=<the ID>" \
  https://<server:port>/api/user/0
```

## Pagination

List-returning endpoints that can grow large — such as device search — accept `offset` and `limit` query parameters to page through results, e.g.:

```
POST /api/devices/search?offset=0&limit=100
```

Omitting them returns the full result set.

## Device comments and tags

Devices carry free-form `comments` and `tags` (an array of strings, see [Device tags](../user-guide/devices.md#device-tags)):

- `POST /api/devices` (add a device) accepts optional `comments` and `tags` fields; in autodiscovery mode, they are applied to the device once discovered.
- `PUT /api/devices/{id}` accepts `comments` and `tags`; a `tags` array replaces all the tags of the device, while omitting the field (or passing `null`) leaves them unchanged.
- `GET /api/devicetags` lists the distinct tags currently in use, with the number of devices having each one (`[{"name": "core", "deviceCount": 12}, ...]`).
- Devices can be searched by tag with `POST /api/devices/search` and a `[Tag] is "core"` query.

A tag can't be blank and is limited to 255 characters (surrounding whitespace is trimmed); an invalid tag is rejected with a 400 error.

## XML requests

Request bodies can be sent as XML (`Content-Type: application/xml`) as well as JSON, on every `POST` and `PUT` endpoint. The XML shape mirrors the XML responses of the API, which are the JSON properties as child elements:

- The name of the root element is not significant: `<device>`, `<domain>` or anything else is accepted.
- A list is a wrapper element holding one child element of the same name per item, e.g. `<tags><tags>core</tags><tags>edge</tags></tags>`.
- The `type` discriminator of the polymorphic bodies (credential sets...) is an attribute of the root element, e.g. `<credentialSet type="SSH">`.
- Absent elements take the same default values as absent JSON properties.
- The values of free-form maps (such as the driver `options` of a device) are read as text in XML: a boolean driver option is given as `true` or `false`, and stored as a boolean.

```bash
curl -X POST -H "Content-Type: application/xml" -H "Accept: application/xml" \
  -H "X-Netshot-API-Token: <token>" \
  -d '<domain><name>Paris DC</name><description>Paris data center</description><ipAddress>10.255.0.1</ipAddress></domain>' \
  https://<server:port>/api/domains
```

A request body which can't be read (malformed JSON or XML, or a value of the wrong type, such as a list sent without its wrapper element in XML) is refused with a 400 error, `NETSHOT_INVALID_REQUEST_PARAMETER` (code 10), whose message tells what could not be read.

## Diagnostic results

Per-device diagnostic results include a `diagnosticId` field alongside the diagnostic's name and value, so a client can reliably associate a result with its diagnostic definition without matching on name.

## Example: scheduling a scan task

This mirrors the **Scan subnet(s) for devices** option in the Web UI's **Add device...** menu — scanning `10.16.16.0/24` for devices using known SNMP credentials.

First, [generate an API token](../user-guide/administration.md#api-tokens). Then:

```bash
curl -v -X POST -H "Content-Type: application/json" -H "Accept: application/json" \
  -H "X-Netshot-API-Token: <token>" \
  -d '{ "type": "ScanSubnetsTask", "domain": "1", "subnets": "10.16.16.0/24" }' \
  https://<server:port>/api/tasks
```

Netshot scans the subnet with its known SNMP credentials; any newly-found device is added and goes through the usual discovery flow (snapshot, compliance check, etc.).

## Tracking calls made by the Web UI

Open your browser's developer tools (F12) and switch to the **Network** tab while using the Web UI to see the exact REST calls it makes — a quick way to find the right endpoint and payload shape for a given action.
