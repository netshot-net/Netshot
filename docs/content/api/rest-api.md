# REST API

Netshot exposes most of its functionality through a REST API. The Web UI itself is built entirely on top of it — any feature available in the Web UI has a corresponding API endpoint. The API supports both JSON and XML encoding.

## Exploring the API

The OpenAPI definition is available on any Netshot deployment at:

```
https://<server:port>/api/openapi.json
```

An interactive API browser is built into the Web UI — open it from the **Help** menu — for exploring and testing endpoints without leaving the application.

## Authentication

### API tokens

Generate a token from the Admin page's [API Tokens](../user-guide/administration.md#api-tokens) section. Copy it immediately; it can't be retrieved again once created.

Pass it as the `X-Netshot-API-Token` header on every request — this is the recommended method for scripts and integrations, and doesn't require session/cookie handling.

### Cookie-based (legacy)

`POST /api/user` with a username and password to obtain a `NetshotSessionID` session cookie, which can then be passed on subsequent calls. `DELETE /api/user` logs out.

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
