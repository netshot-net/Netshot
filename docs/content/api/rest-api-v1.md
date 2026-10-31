# REST API v1

The v1 REST API is served under `/api/v1`, beside the [legacy API](rest-api.md) (`/api`), which is unchanged. It shares the authentication of the legacy API (API tokens, `Authorization: Bearer` with an API token or a [JWT access token](rest-api.md#jwt-access-tokens-oidc), session cookie) and its permission levels.

!!! note "Preview"
    The v1 API covers the features of the legacy API (the resource list below); the Web UI still uses the legacy API. The OpenAPI document describes every endpoint: `https://<server:port>/api/v1/openapi.json` (or `.yaml`).

## Formats

Requests and responses are JSON (default), XML or YAML, chosen with the `Content-Type` and `Accept` headers. The three formats carry the same representation:

- Dates are ISO-8601 strings in UTC, with milliseconds: `2026-10-30T09:12:44.000Z`. Dates in requests and filters may have any offset (`2026-10-30T10:00:00+01:00`); a date without offset is refused.
- Every property is written, `null` included (`xsi:nil="true"` in XML).
- In XML, the root element is the kind of object (`<domain>`, `<device>`, `<list>` for a page, `<problem>` for an error), and a list is the repetition of its element, without wrapper (`<tags>core</tags><tags>edge</tags>`), in requests as in responses.

## Objects

Every object has an `id`, a `url` (the path of the object, e.g. `/api/v1/domains/3`) and a `display` name. The versioned objects have a `version`, which is also their `ETag`.

A reference to another object (the domain of a device, its groups...) is always an object, never a bare ID: by default its brief view (`id`, `url`, `display` and a few identifying properties), its full view with `expand`. In requests, a reference is given by its ID (`"mgmtDomain": 3`) or by an object with its ID (`"mgmtDomain": {"id": 3}`, so the object read can be sent back).

Secrets (passwords, keys, communities...) are write-only: they are never returned, not even masked; `hasPassword`-like properties tell whether they are set. In a `PUT` or a `PATCH`, an absent secret is unchanged and a `null` one is cleared (where it is optional).

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

`/api/v1/devices`: list and get (read-only level); add, replace (`PUT`), update (`PATCH`) and delete (read-write level), with `If-Match`.

Filters: `id`, `name`, `mgmtAddress`, `status`, `mgmtDomain` (domain ID), `family`, `driver`, `networkClass`, `softwareVersion`, `softwareLevel`, `serialNumber`, `location`, `contact`, `comments`, `creator`, `cachedIpAddress`, `createdDate`, `changeDate`, `eolDate`, `eosDate`, `group` (member of the group of this ID), `tag` (has this tag; `tag__empty=true` for the devices without tag), `compliant` (`true`: no non-conforming compliance result). `q` searches the name, management address, family, software version, serial number, location, contact and comments. `query` takes a device search query in the [Netshot query language](../user-guide/devices.md#searching-for-devices), combined with the other filters:

```bash
curl -H "X-Netshot-API-Token: <token>" \
  'https://<server:port>/api/v1/devices?query=%5BFamily%5D%20is%20%22Cisco%20ISR%22&location__isw=paris&fields=name,mgmtAddress'
```

**Adding a device** is asynchronous: `POST /api/v1/devices` answers `202 Accepted` with the task and its `Location: /api/v1/tasks/{id}`. Without `driver`, a discovery task finds the type of the device (an SNMP community must exist for the domain), and the device is only created if the discovery succeeds (`accesses` and `options` are then refused). With `driver`, the device is created at once, with a snapshot task. Properties: `mgmtAddress` and `mgmtDomain` (required), `driver`, `comments`, `tags`, `options`, `accesses`. A device with the same address in the domain is a 409.

**Replacing a device** (`PUT`) takes its editable properties: `mgmtAddress`, `mgmtDomain`, `status`, `comments`, `tags`, `options`, `accesses`. The rest (family, serial number, software...) is read-only and ignored when sent back, so a device read can be modified and sent back. As any `PUT`, it is a full replacement: absent `accesses`, `tags` or `options` are emptied. `PATCH` changes the given properties only (`accesses` is replaced as a whole). The version of a device only changes when its own properties change, not its accesses alone.

**Accesses**: each access has `accessName` (as declared by the driver: `ssh`, `telnet`, `snmp`...), `address`, `port`, `sshHostKeyVerification`, `sshTrustedHostKeys`, `httpsCaTrustMode`, `httpsCustomCaCertificate`, and at most one of `globalCredentialSet` (a reference to a global credential set) and `specificCredentialSet` (credentials of this device only: a `type` and the properties of a credential set, without name nor domain; secrets write-only, as for the credential sets). An access which isn't listed anymore is removed, with its specific credentials.

**Actions** create a task and answer `202` with it (and its `Location`):

| Action | Level | Body |
|---|---|---|
| `POST /api/v1/devices/{id}/snapshot` | operator | `dontRunDiagnostics`, `dontCheckCompliance` |
| `POST /api/v1/devices/{id}/compliance-check` | operator | |
| `POST /api/v1/devices/{id}/diagnostics-run` | operator | `dontCheckCompliance` |
| `POST /api/v1/devices/{id}/script-runs` | execute-read-write | `savedScript` (a reference) or `script` and `deviceDriver`; `userInputValues`, `runSnapshot`, `runDiagnostics`, `checkCompliance` |

The body is optional (except for the scripts), and takes the scheduling properties of the tasks too: `comments`, `scheduleType` (`ASAP` by default, `AT`, `HOURLY`, `DAILY`, `WEEKLY`, `MONTHLY`), `scheduleReference`, `scheduleFactor`, `priority` (1 to 9, 5 by default), `debugEnabled`.

**Sub-resources** (read-only level; 404 for an unknown device):

- `GET /api/v1/devices/{id}/interfaces`: the network interfaces (`name`, `description`, `enabled`, `macAddress`, `ipAddresses`, `vrf`, `virtualDevice`, `level3`...). Filters: `name`, `description`, `vrf`, `virtualDevice`, `enabled`, `level3`, `macAddress`, `ipAddress` (`ipAddress__net=10.0.0.0/24`); `q` searches the name and description.
- `GET /api/v1/devices/{id}/modules`: the modules (`slot`, `partNumber`, `serialNumber`, `firstSeenDate`, `lastSeenDate`, `removed`), the removed ones included: `removed=false` for the current ones.
- `GET /api/v1/devices/{id}/tasks`, `/configs`, `/diagnostic-results`, `/compliance-results`: the tasks, configurations and results of the device (see these resources).

The interfaces and modules have an `id` and a `display`, but no `url` (they aren't addressable on their own).

### Configurations

`GET /api/v1/configs` (read-only level) lists the configurations of all the devices, the latest first, in their brief view by default (`device` reference, `changeDate`, `author`). Filters: `id`, `device`, `changeDate`, `author`, `domain`, `group`; `q` searches the author. `GET /api/v1/devices/{id}/configs` is the same list for a device.

- `GET /api/v1/configs/{id}` gives a configuration with its `attributes` (`name`, `type` and the short values; for the long texts and files, the `url` to download them: they are never inlined).
- `GET /api/v1/configs/{id}/items/{item}` downloads a long text (`text/plain`) or a file (`application/octet-stream`) item, as an attachment.
- `GET /api/v1/configs/{id}/diff?with={otherId}` compares the configuration (the revised one) with `with` (the original one; by default, the previous configuration of the device): `original` and `revised` references, `deltas` by compared item.

### Catalogs

Read-only lists of the values found in the inventory (read-only level), with filters on every property, sort and `q`; these aggregates have no `id` nor `url`:

- `GET /api/v1/device-families`: `driver`, `family`, `deviceCount`.
- `GET /api/v1/device-tags`: `name`, `deviceCount`.
- `GET /api/v1/part-numbers`: `partNumber`, `moduleCount`.
- `GET /api/v1/software-versions`: `version`, `deviceCount`.
- `GET /api/v1/device-types`: the loaded drivers (`name`, `description`, `version`, `author`, `priority`, `protocols`, `cliMainModes`, `attributes`, `options`, `accesses`...).

### Groups

`/api/v1/groups`: list and get (read-only level), create, replace, update and delete (read-write level). `type` is `STATIC` or `DYNAMIC` and can't change. The devices of a static group are given by `devices` (their IDs, in order), which is write-only: it isn't part of the group representation, and when it is absent from a `PUT` or a `PATCH` the members are unchanged. The members of a dynamic group follow its `query`.

`GET /api/v1/groups/{id}/devices` lists the devices of a group, with the parameters of the device list (it is the device list filtered on `group={id}`).

Filters: `id`, `name`, `folder`, `type`, `hiddenFromReports`, `query`, `changeDate`, `device` (the groups a device is a member of); sort: `id`, `name`, `folder`, `type`, `changeDate` (default: folder, then name); `q` searches the name, folder and query.

### Tasks

`/api/v1/tasks`: list and get (read-only level), the latest first. A task has a `type` (`TakeSnapshotTask`, `RunDeviceScriptTask`...), a `status`, its target `device` or `group` and its `parentTask` (for the per-device tasks of a group task) as references, its schedule, its `log` and the `parameters` of its type (the options of a snapshot, the script of a script task...).

- `POST /api/v1/tasks/{id}/cancel` (read-write level) cancels a scheduled task, or requests the cancellation of a running group task, or of a device task still waiting for its device (`cancelRequested`); a task in another status can't be cancelled (409).
- `POST /api/v1/tasks` (operator level) creates a task (`202` with the task): `type` (as the `type` of the tasks; the discovery tasks can't be created), the target `device`, `group` or `deviceList` (references) depending on the type, its `parameters` (the keys the `parameters` of a task show), and the scheduling properties of the device actions, `scheduleMode` and `stopOnFailure`. Running scripts needs the execute-read-write level, scanning subnets the read-write level, purging the database the admin level.
- `GET /api/v1/tasks/{id}/debug-log` (read-write level, `text/plain`) gives the debug log of a task run with debug enabled.
- `GET /api/v1/tasks/stats?after=&before=&buckets=`: the finished tasks (`SUCCESS`, `FAILURE`, `CANCELLED`) over a range (ISO-8601 dates; by default from the epoch to now), split into `buckets` (1 to 366, 48 by default): `{"after", "before", "bins": [{"from", "to", "countByStatus"}], "statusCounts", "typeCounts"}`.
- `GET /api/v1/tasks/status-counts?parentTask=`: `{"countByStatus" (every status), "threadCount"}`; with `parentTask`, only the children of that task.

Filters: `id`, `type`, `status`, `author`, `target`, `comments`, `priority`, `scheduleType`, `scheduleReference`, `creationDate`, `changeDate`, `executionDate`, `device`, `group`, `parentTask`, `childOrder`, `runnerId`, `debugEnabled`; `q` searches the author, target and comments.

### Diagnostics

`/api/v1/diagnostics`: list and get (read-only level); create, replace (`PUT`), update (`PATCH`) and delete (execute-read-write level). A diagnostic collects a piece of data from the devices of a group, and has a `type`: `SIMPLE` (a CLI command, with `deviceDriver`, `cliMode`, `command` and an optional `modifierPattern`/`modifierReplacement` applied to the output), `JAVASCRIPT` or `PYTHON` (a `script`). The properties of the other types are null. `resultType` is `TEXT`, `LONGTEXT`, `NUMERIC` or `BINARY`. The target group is the `targetGroup` reference; in requests it is given by its ID (`"targetGroup": 3`, or an object with an `id`, such as the one read), and cleared with `null`. The type of a diagnostic can't change. Changing the target group deletes the results of the diagnostic; deleting a diagnostic deletes its results. Diagnostics have no version: there is no `ETag`, and `If-Match` isn't supported.

Filters: `id`, `name`, `type`, `resultType`, `enabled`, `targetGroup`, `deviceDriver`; `q` searches the name. Sort: `name`, `type`, `resultType`, `enabled`, `deviceDriver` (default `name`).

### Diagnostic results of a device

`GET /api/v1/devices/{id}/diagnostic-results` (read-only level): the last result of each diagnostic run on the device (404 for an unknown device), in their full view by default. A result has the `diagnostic` (reference, `expand=diagnostic` for the full diagnostic), its `type` (`TEXT`, `LONGTEXT`, `NUMERIC` or `BINARY`), its typed `value` (a string, a number or a boolean), and the `creationDate` (when the diagnostic first gave this value) and `lastCheckDate`.

Filters: `diagnostic`, `diagnosticName`, `type`, `creationDate`, `lastCheckDate`; `q` searches the diagnostic name. Sort: `diagnosticName` (default), `type`, `creationDate`, `lastCheckDate`.

### Scripts

`/api/v1/scripts`: list, get, create, delete and validation (execute-read-write level). A script is a JavaScript command script written for a device driver (`deviceDriver`), kept in a `folder`; its `author` is the user who created it. Scripts can't be edited (as in v0): create a new one. The list is in its brief view by default (`name`, `folder`, `deviceDriver`, without the code). `userInputDefinitions` (the user inputs the script asks for, extracted by evaluating it) are written when a script is read, created or validated, and in a list only when asked for.

`POST /api/v1/scripts/validation` checks a script definition (driver, name, code) without saving it: 200 with the script as it would be created (`id` 0, no `url`) and its user inputs, or 400 with the problem.

Filters: `id`, `name`, `folder`, `deviceDriver`, `author`; `q` searches the name, folder and author. Sort: `name` (default), `folder`, `deviceDriver`, `author`.

### Credential sets

`/api/v1/credential-sets`: list and get (read-only level); create, replace (`PUT`), update (`PATCH`) and delete (admin level), with `If-Match`. The global credential sets are the accounts and SNMP communities Netshot tries on the devices.

- `type`: `SSH`, `SSH_KEY`, `TELNET`, `HTTP`, `SNMP_V1`, `SNMP_V2C` or `SNMP_V3`; it is chosen at creation and can't change. A property which doesn't apply to the type is refused (400).
- The secrets (`password`, `superPassword`, `privateKey`, `community`, `authKey`, `privKey`) are write-only; the representation has `hasPassword`, `hasSuperPassword`, `hasPrivateKey`, `hasCommunity`, `hasAuthKey`, `hasPrivKey` (`null` where they don't apply). A required secret can't be cleared.
- Required at creation: `username` and `password` for `SSH`, `TELNET` and `HTTP`; `username` and `privateKey` for `SSH_KEY` (`password` is then the optional key passphrase); `community` for `SNMP_V1` and `SNMP_V2C`; `username` for `SNMP_V3`, with `authKey` and `privKey` unless `authType` / `privType` is `NONE` (the default).
- `mgmtDomain` is a reference (none for all domains).
- A field can be read from a Vault instance instead of being given by value: `usernameVault`, `passwordVault`, `superPasswordVault`, `privateKeyVault`, `communityVault`, `authKeyVault`, `privKeyVault`, each `{"vaultInstance": <reference>, "path": "app/creds/key"}`. Give the value or the location, not both. A location isn't a secret: it is returned.
- The device-specific credential sets (owned by the access of a device) aren't listed by default: `deviceSpecific=true` lists them. They can be read, not changed or deleted here (409).

Filters: `id`, `name`, `type`, `mgmtDomain` (`mgmtDomain__empty=true` for none), `deviceSpecific`, `username`, `changeDate`; `q` searches the name and username.

### Vault instances

`/api/v1/vault-instances`: list and get (read-only level); create, replace, update and delete (admin level), with `If-Match`. A Vault instance is an external secret store (HashiCorp Vault KV v2, `type` `HASHICORP_KV2`) the credential fields can be read from; it can't be deleted while a credential field refers to it (409).

- `authMethod` `APPROLE` needs `appRoleId` and `appRoleSecretId`; `JWT` needs `jwtIdpTokenEndpoint`, `jwtClientId`, `jwtVaultRole` and `jwtClientSecret`. The secrets `appRoleSecretId` and `jwtClientSecret` are write-only (`hasAppRoleSecretId`, `hasJwtClientSecret`).
- Defaults: `httpsCaTrustMode` `SYSTEM_TRUSTSTORE` (`CUSTOM_CA` needs a certificate), `kvMountPath` `secret`, `appRoleMountPath` `approle`, `jwtMountPath` `jwt`.
- `POST /api/v1/vault-instances/{id}/test` tests a saved instance, `POST /api/v1/vault-instances/test` a complete definition (secrets included) which isn't saved. Both answer 200 with `{"success": true|false, "message": ...}`: a failed connection isn't an error status.

Filters: `id`, `name`, `type`, `baseUrl`, `namespace`, `authMethod`, `changeDate`; `q` searches the name and base URL.

### Compliance

The compliance resources have no version: there is no `ETag`, and `If-Match` doesn't apply. They are read with the read-only level, changed with the read-write level.

**Policies** (`/api/v1/compliance/policies`): list, get, create, replace, update, delete. A policy has a `name` and `targetGroups` (references to groups). Removing a group from a policy deletes the compliance results of the devices of that group. `GET /api/v1/compliance/policies/{id}/rules` lists the rules of the policy, with the parameters of the rule list. Filters: `id`, `name`, `targetGroup`; `q` searches the name.

**Rules** (`/api/v1/compliance/rules`): list, get, create, replace, update, delete. A rule has a `type`: `TEXT` (`field`, `context`, `text`, `regExp`, `invert`, `anyBlock`, `matchAll`, `normalize`, `deviceDriver`), `JAVASCRIPT` or `PYTHON` (`script`, the template script if absent), and in every case a `name`, `enabled` (false if absent) and its `policy` (required at creation). The type and the policy of a rule can't change. Filters: `id`, `name`, `type`, `enabled`, `policy`, `field`, `deviceDriver`, `text`; `q` searches the name and text.

- `GET /api/v1/compliance/rules/{id}/exemptions`: the devices exempted from the rule (`device` reference, `expirationDate`); filters `device`, `expirationDate`.
- `PUT /api/v1/compliance/rules/{id}/exemptions` with `{"exemptions": [{"device": 12, "expirationDate": "2030-01-01T00:00:00.000Z"}]}` replaces the whole set (an empty list removes it) and answers the new list.
- `POST /api/v1/compliance/rules/test` with `{"rule": {<rule definition>}, "device": 12}` checks a rule definition (not stored) against a device, and answers `{"result": ..., "log": ..., "comment": ...}` (`CONFORMING`, `NONCONFORMING`, `NOTAPPLICABLE`, `INVALIDRULE`...). A device without configuration gives `NOTAPPLICABLE`; an unknown device is a 404.

**Software rules** (`/api/v1/compliance/software-rules`): list, get, create, replace, update, delete. Properties: `priority`, `level` (`GOLD`, `SILVER`, `BRONZE`), `group`, `driver`, `family`, `familyRegExp`, `version`, `versionRegExp`, `partNumber`, `partNumberRegExp`. The rules are checked in order and the first match wins: `priority` is the position of the rule, from 1. A new rule is the last one unless `priority` is given; writing `priority` moves the rule to that position and shifts the others; without it, a rule keeps its position. Filters on all the properties; default order: the priority.

**Hardware rules** (`/api/v1/compliance/hardware-rules`): list, get, create, replace, update, delete. Properties: `partNumber`, `partNumberRegExp`, `endOfSale`, `endOfLife`, `group`, `driver`, `family`, `familyRegExp`; filters on all of them. An unknown group or driver is a 400.

**Compliance results of a device**: `GET /api/v1/devices/{id}/compliance-results` lists the rules of the policies applying to the device (through its groups): `rule` and `policy` references, `result` (null if never checked), `comment`, `checkDate`, `exemptionExpirationDate`. Filters: `rule`, `ruleName`, `policy`, `policyName`, `result`, `checkDate`, `exemptionExpirationDate`; `q` searches the rule and policy names. An unknown device is a 404.

### Reports

`/api/v1/reports/...` (read-only level). The filters are query parameters: `domain`, `group` and `policy` take IDs, repeated or comma-separated (none means any). An unknown parameter or an invalid value is a 400; an unknown required group is a 404. The device of a row is a reference (brief unless `expand=device`). The statistics are written in the list envelope but never paginated (`limit` and `offset` are refused); the lists of devices are paginated.

| Report | Parameters | Rows |
|---|---|---|
| `group-config-compliance-stats` | `domain`, `group`, `policy` | `group`, `folder`, `compliantDeviceCount`, `deviceCount` |
| `group-software-compliance-stats` | `domain` | `group`, `goldDeviceCount`, `silverDeviceCount`, `bronzeDeviceCount`, `deviceCount` |
| `hardware-support-stats` | `domain`, `group` | `type` (`EOS`, `EOL`), `date` (null: no date), `deviceCount` |
| `changes-by-day` | `days` (1 to 366, 7 by default), `timeZone` (IANA name) | `date` (start of the day), `changeCount`, the oldest first |
| `access-failure-devices` | `days` (3 by default), `domain` | `device`, `lastSuccess`, `lastFailure`: the devices without a successful snapshot for that many days |
| `config-compliance-device-statuses` | `domain`, `group`, `policy`, `result` | `device`, `policy`, `rule`, `checkDate`, `result` |
| `group-config-non-compliant-devices` | `group` (required), `domain`, `policy` | the non-conforming results of the devices of a group (same rows) |
| `group-devices-by-software-level` | `group` and `level` (`GOLD`, `SILVER`, `BRONZE`, `UNKNOWN`) required, `domain` | `device`, `softwareVersion` |
| `hardware-support-devices` | `type` (`EOS` or `EOL`, required), `date` (as given by the statistics; absent: the devices without date), `domain`, `group` | devices |

`GET /api/v1/reports/export?domain=&group=&include=&format=xlsx` exports the devices as an Excel workbook (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, as an attachment); `include` lists the optional sections: `interfaces`, `inventory`, `inventoryHistory` (implies `inventory`), `locations`, `compliance`, `groups`, `driverAttributes`.

### Authentication

`POST /api/v1/auth/session` logs in and creates a session (the `NetshotSessionID` cookie, HttpOnly): `{"username": "...", "password": "..."}`, or `{"authorizationCode": "...", "redirectUri": "..."}` for OIDC (one form only); `newPassword` changes the password while logging in (required when it has expired). Answer: `201` with `Location: /api/v1/auth/session` and the session (`url`, `creationDate`, `maxIdleTime` in seconds, `user`); the session ID is only in the cookie. Logging in always drops the session the client sent, if any, and creates a new one on success. Errors: 401 wrong credentials (the same answer for an unknown user), 403 expired password (`NETSHOT_EXPIRED_PASSWORD`), 400 invalid body or new password refused by the password policy. The answer is not cacheable (`Cache-Control: no-store`).

`GET /api/v1/auth/session` describes the current session (401 without authentication, 404 for a request authenticated otherwise, by an API token for instance). `DELETE /api/v1/auth/session` logs out: always `204`, even without a session; for a user who isn't local, when OIDC is available, the `X-OIDC-EndSessionEndpoint` header gives the endpoint of the identity provider to end the session there too.

`GET /api/v1/auth/me` (read-only level) describes who the request is authenticated as, whatever the method: `kind` (`USER` or `API_TOKEN`), `authMethod` (`SESSION`, `JWT` or `API_TOKEN`), `username` and `local` (null for an API token), `level` and `role`. `PATCH /api/v1/auth/me` with `{"currentPassword": "...", "newPassword": "..."}` changes the password of the current user, who must be a local one (400 otherwise, for a wrong current password, or for a password refused by the policy).

### Users

`/api/v1/users`: list, get, create, replace, update and delete (admin level). A user is local (`local: true`, authenticated by its password) or remote (RADIUS, TACACS+ or OIDC: Netshot only gives it its `level`). `username`, `local` and `level` are required; `password` is write-only (required to create a local user, absent to keep the current one, ignored for a remote user), `hasPassword` tells whether there is one. Users have no version (no `ETag`). A user can't edit nor delete itself (403). Filters: `id`, `username`, `local`, `level`; `q` searches the username.

### API tokens

`/api/v1/api-tokens`: list, get, create and delete (admin level). `POST` takes `{"description": "...", "level": 100}`, and optionally the `token` value to register (32 alphanumeric characters; a random one is generated otherwise). The `201` answer holds the value in `token`: it is the only time it is given (Netshot only keeps a hash of it), and the answer is not cacheable. Filters: `id`, `description`, `level`.

### Hooks

`/api/v1/hooks`: list, get, create, replace, update and delete (admin level). A hook has a `type` (`WEB`: posts the event to a URL), a `name`, `enabled` (true if absent), its `triggers` (`[{"type": "POST_TASK", "item": "TakeSnapshotTask"}]`, replaced as a whole) and, for a `WEB` hook, `action` (`POST_JSON`, `POST_XML` or `POST_YAML`), `targetUrl`, `httpsCaTrustMode` and `httpsCustomCaCertificate`. The `targetUrl` is returned: it shouldn't carry credentials. The type of a hook can't change. No `ETag`. Filters: `id`, `name`, `type`, `enabled`, `url`.

### System

- `GET /api/v1/system/info` (read-only level): `version`, `serverTime`, `sessionMaxIdleTime`.
- `GET /api/v1/system/cluster/members` (admin level): the members of the cluster (empty without clustering).
- `GET /api/v1/system/cluster/master` (no authentication): `clusterEnabled`, `master`, `currentMasterId`; `200` on the master (or without clustering), `503` on another member, so a load balancer can use it to reach the master only.
- `GET /api/v1/system/services` (admin level): the services of the server (task manager, Syslog server, web server...) with their `state`, `healthy` and `stats`.
- `GET /api/v1/health` (no authentication): `{"status": "UP"}` (`200`) when all the services run and are healthy, `{"status": "DOWN"}` (`503`) otherwise, including while the server starts or stops. It tells nothing else. For a Kubernetes probe: `httpGet: {path: /api/v1/health, port: 8443, scheme: HTTPS}`.
