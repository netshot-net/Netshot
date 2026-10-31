# REST API v1

The v1 REST API is served under `/api/v1`, beside the [legacy API](rest-api.md) (`/api`), which is unchanged. It shares the authentication of the legacy API (API tokens, `Authorization: Bearer` with an API token or a [JWT access token](rest-api.md#jwt-access-tokens-oidc), session cookie) and its permission levels.

!!! note "Work in progress"
    The v1 API is being built: the domains (`/api/v1/domains`), the devices (read and delete, `/api/v1/devices`), the groups (`/api/v1/groups`) and the tasks (read and cancel, `/api/v1/tasks`) are available so far. The OpenAPI document lists what is available: `https://<server:port>/api/v1/openapi.json` (or `.yaml`).

## Formats

Requests and responses are JSON (default), XML or YAML, chosen with the `Content-Type` and `Accept` headers. The three formats carry the same representation:

- Dates are ISO-8601 strings in UTC, with milliseconds: `2026-10-30T09:12:44.000Z`. Dates in requests and filters may have any offset (`2026-10-30T10:00:00+01:00`); a date without offset is refused.
- Every property is written, `null` included (`xsi:nil="true"` in XML).
- In XML, the root element is the kind of object (`<domain>`, `<device>`, `<list>` for a page, `<problem>` for an error), and a list is the repetition of its element, without wrapper (`<tags>core</tags><tags>edge</tags>`), in requests as in responses.

## Objects

Every object has an `id`, a `url` (the path of the object, e.g. `/api/v1/domains/3`) and a `display` name. The versioned objects have a `version`, which is also their `ETag`.

A reference to another object (the domain of a device, its groups...) is always an object, never a bare ID: by default its brief view (`id`, `url`, `display` and a few identifying properties), its full view with `expand`. In requests, a reference is given by its ID.

## Methods and statuses

| Request | Effect | Success |
|---|---|---|
| `GET /things` | List (see below) | 200 |
| `GET /things/{id}` | Get one object | 200, or 304 with a matching `If-None-Match` |
| `POST /things` | Create | 201, with `Location` and the new object |
| `PUT /things/{id}` | Replace: an absent optional property takes its default value, an absent required one is refused | 200, with the object |
| `PATCH /things/{id}` | Update the given properties only ([JSON merge patch](https://www.rfc-editor.org/rfc/rfc7396), `Content-Type: application/merge-patch+json`; `null` clears a property; a list is replaced as a whole) | 200, with the object |
| `DELETE /things/{id}` | Delete | 204 |

An unknown object is always a 404. `PUT`, `PATCH` and `DELETE` accept an `If-Match` header holding the ETag (version) the object must still be at: if it was changed meanwhile, the request is refused with a 412.

The read-only properties of an object (`id`, `url`, `version`, dates...) are ignored in a request body, so an object read with `GET` can be modified and sent back with `PUT`. An unknown property is refused.

## Lists

A list answers one page, in an envelope:

```json
{
  "count": 1234,
  "offset": 0,
  "limit": 100,
  "next": "/api/v1/devices?offset=100&limit=100",
  "previous": null,
  "results": [ ... ]
}
```

- `offset` and `limit` select the page. Without `limit`, a page holds `netshot.http.api.pagesize.default` items (100); there is no maximum unless `netshot.http.api.pagesize.max` is set, a `limit` above it is then refused (see the [configuration reference](../configuration-reference.md#embedded-http-server)).
- `count` is the total number of matching items; `count=false` skips counting it (faster on large lists, `next` is then given as long as a page is full).
- `sort=name,-changeDate` sorts the list (`-` for a descending order); the ID always ends the order, so that the pages are stable.
- `q=<text>` searches the text in the main text properties (case-insensitive).

### Filters

Any other parameter is a filter on a property, NetBox style: `<property>=<value>` for an exact match, or `<property>__<lookup>=<value>`:

| Lookup | Meaning | Lookup | Meaning |
|---|---|---|---|
| (none) | equal to | `n` | not equal to |
| `ie` | equal to, ignoring case | `nie` | not equal to, ignoring case |
| `ic` | contains, ignoring case | `nic` | doesn't contain, ignoring case |
| `isw` | starts with, ignoring case | `nisw` | doesn't start with, ignoring case |
| `iew` | ends with, ignoring case | `niew` | doesn't end with, ignoring case |
| `regex` | matches a regular expression | `iregex` | matches, ignoring case |
| `lt`, `lte`, `gt`, `gte` | lower / greater than (numbers, dates) | `empty` | `true`: null or empty, `false`: has a value |
| `net` | IP address within a subnet (`10.0.0.0/8`) | | |

A parameter given several times matches any of its values (`status=INPRODUCTION&status=PREPRODUCTION`), and none of them for the negated lookups; different parameters must all match. Wildcards (`%`, `_`) are literal. A date is a date (`2026-10-30`, midnight UTC) or a date and time with offset.

An unknown parameter, an unknown property, a lookup which doesn't apply to the property or an invalid value is refused with a 400 error naming the parameter.

### Views and fields

- `view=brief` or `view=full`: a list answers the brief view of its items by default, a single object its full view.
- `fields=name,mgmtDomain.description`: only these properties (and `id`); a dotted path selects in a nested object.
- `omit=attributes,accesses`: every property but these.
- `expand=mgmtDomain,ownerGroups`: the full view of these referenced objects instead of their brief view.

Omitting the heavy properties also makes the request lighter on the server (for instance, a device list which only needs summary properties doesn't load the attributes and accesses of the devices).

## Errors

Errors are [problem details (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457), as `application/problem+json` (or `application/problem+xml`, or YAML, after the `Accept` header):

```json
{
  "type": "urn:netshot:error:duplicate-domain",
  "title": "Duplicate domain",
  "status": 409,
  "detail": "A domain with this name already exists.",
  "instance": "/api/v1/domains",
  "code": "NETSHOT_DUPLICATE_DOMAIN",
  "errorCode": 111,
  "errors": null
}
```

`code` and `errorCode` are the Netshot error codes of the legacy API. `errors` lists the faulty parameters or properties (`[{"field": "name__ic", "message": "..."}]`). The statuses: 400 invalid request (syntax, parameter, property, validation), 401 authentication required, 403 insufficient permission, 404 unknown object, 409 conflict (duplicate, object in use, task which can't be cancelled), 412 version mismatch (`If-Match`). Unlike the legacy API, a 412 never means anything else.

## Resources

### Domains

`/api/v1/domains`: list (read-only level), create, replace, update and delete (admin level). Filters: `id`, `name`, `description`, `server4Address` (IP address), `changeDate`; sort: `id`, `name`, `changeDate`; `q` searches the name and description.

### Devices

`/api/v1/devices`: list and get (read-only level), delete (read-write level). The interfaces, modules, configurations, tasks and results of a device will be sub-resources (`/api/v1/devices/{id}/...`); the device creation and edition are coming.

Filters: `id`, `name`, `mgmtAddress`, `status`, `mgmtDomain` (domain ID), `family`, `driver`, `networkClass`, `softwareVersion`, `softwareLevel`, `serialNumber`, `location`, `contact`, `comments`, `creator`, `cachedIpAddress`, `createdDate`, `changeDate`, `eolDate`, `eosDate`, `group` (member of the group of this ID), `tag` (has this tag; `tag__empty=true` for the devices without tag), `compliant` (`true`: no non-conforming compliance result). `q` searches the name, management address, family, software version, serial number, location, contact and comments. `query` takes a device search query in the [Netshot query language](../user-guide/devices.md#searching-for-devices), combined with the other filters:

```bash
curl -H "X-Netshot-API-Token: <token>" \
  'https://<server:port>/api/v1/devices?query=%5BFamily%5D%20is%20%22Cisco%20ISR%22&location__isw=paris&fields=name,mgmtAddress'
```

### Groups

`/api/v1/groups`: list and get (read-only level), create, replace, update and delete (read-write level). `type` is `STATIC` or `DYNAMIC` and can't change. The devices of a static group are given by `devices` (their IDs, in order), which is write-only: it isn't part of the group representation, and when it is absent from a `PUT` or a `PATCH` the members are unchanged. The members of a dynamic group follow its `query`.

`GET /api/v1/groups/{id}/devices` lists the devices of a group, with the parameters of the device list (it is the device list filtered on `group={id}`).

Filters: `id`, `name`, `folder`, `type`, `hiddenFromReports`, `query`, `changeDate`, `device` (the groups a device is a member of); sort: `id`, `name`, `folder`, `type`, `changeDate` (default: folder, then name); `q` searches the name, folder and query.

### Tasks

`/api/v1/tasks`: list and get (read-only level), the latest first. A task has a `type` (`TakeSnapshotTask`, `RunDeviceScriptTask`...), a `status`, its target `device` or `group` and its `parentTask` (for the per-device tasks of a group task) as references, its schedule, its `log` and the `parameters` of its type (the options of a snapshot, the script of a script task...).

- `POST /api/v1/tasks/{id}/cancel` (read-write level) cancels a scheduled task, or requests the cancellation of a running group task, or of a device task still waiting for its device (`cancelRequested`); a task in another status can't be cancelled (409).
- `GET /api/v1/tasks/{id}/debug-log` (read-write level, `text/plain`) gives the debug log of a task run with debug enabled.

Filters: `id`, `type`, `status`, `author`, `target`, `comments`, `priority`, `scheduleType`, `scheduleReference`, `creationDate`, `changeDate`, `executionDate`, `device`, `group`, `parentTask`, `childOrder`, `runnerId`, `debugEnabled`; `q` searches the author, target and comments.
