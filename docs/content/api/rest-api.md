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
- The values of free-form maps (such as the driver `options` when adding a device) are read as strings in XML, so options which have to be booleans or numbers can't be set that way: use JSON for these requests.

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
