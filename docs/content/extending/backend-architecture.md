# Backend architecture

This page is for developers working on the Netshot core server (Java). It describes the layering of the code base, the rules that protect it, and the conventions to follow when adding or moving code. For the map of the components, see [Architecture](../architecture.md).

!!! note "History"
    The REST API used to be one monolithic class, `RestService`. It has been moved, one feature at a time, to the layered structure described here, and the class is gone: `api.rest.RestServer` only starts the web server, and every endpoint is a resource of `api.rest.v0.<feature>`. The last section is the step-by-step guide that was followed, with the device endpoints (`/devices`) as the worked example: use it when you add or change an endpoint, and for the same kind of work on another API.

## Layers and packages

Everything lives under `net.netshot.netshot`.

```text
Netshot            entry point only (main)
boot/              composition root (Wiring), persistent model (EntityModel)
platform/          technical building blocks; depends on no feature
├── database/      Hibernate set-up, Database, Transactions, Liquibase migrators
├── error/         ErrorCode, NetshotException
├── event/         DomainEvents: synchronous in-process publish/subscribe
├── crypto/ tls/ rest/
api/               API channels: how the outside world talks to the core
├── rest/          web server (RestServer), Jersey application, filters, exception mapper, shared REST bits
│   └── v0/        the current (legacy, unversioned) REST API, served on /api
├── hook/          webhook channel: HookDispatcher (reacts to domain events), WebHookSender (HTTP), payload/ (payload DTOs and mapper)
└── grpc/          (later)
inventory/ driver/ access/ credentials/ vault/ devicescript/ userscript/
compliance/ diagnostic/ task/ report/ hook/ aaa/ cluster/ system/ tools/
                   features: entities, repositories, services, engines
```

- **Feature packages** (`inventory`, `task`, `compliance`...) hold the entities (single JPA model, no separate domain model), and over time the repositories and services of the feature.
- **Entities** carry the JPA mapping and the invariants on their own state only. No `Session`, no I/O, no static registry lookup, and no Jackson/JAXB serialization annotation (no API channel serializes an entity: the DTOs and the webhook payloads are written by the mappers).
- **Repositories** contain queries only; the `Session` is passed explicitly as first parameter.
- **Services** implement the use cases and own the transaction boundaries (see [Transactions](#transactions)). They throw neutral exceptions (see [Errors](#errors)), and know nothing of HTTP.
- **`api.*`** are the API channels. Each channel owns its DTOs, even when two channels have similar ones (the REST task representation is not the webhook task payload). A channel translates between the wire format and the services. `api.rest` and `api.hook` do not depend on each other; they share services and entities, not DTOs.
- **`platform`** knows nothing about the features. The persistent model (the list of entity classes) is therefore given to `Database.init()` by `boot`.
- **`boot`** is the only place where everything is assembled.

The REST API must stay byte-for-byte equivalent (JSON, XML, YAML) while it is restructured.

## Dependency rules

The rules are enforced by `ArchitectureTest` (ArchUnit): `platform` depends on no feature; only `boot` (and `Netshot`) depend on `api`, and `api` does not depend on `boot`; `api.rest` and `api.hook` do not depend on each other; `api` does not use the `Session`; JAX-RS server classes are used only by the REST layer, and the JAX-RS client and `core` types only by the packages making outgoing calls; entities, mapped superclasses and embeddables carry no `@JsonView` or JAXB annotation; entities do not use `Session` or `Database`; and there is no cycle between top-level packages.

Most rules are *strict*: any violation fails the build. Three rules still have violations, to be removed with the planned ORM change, and are *frozen*: entities using the `Session`, entities using `Database`, and the cycles between top-level packages (mostly due to the reverse collections of `Device`). Their existing violations are recorded in `src/test/resources/archunit_store/` and tolerated, only new ones fail. That store must only shrink. After a planned move or a cleanup, refreeze and review the diff, which must only remove entries (or follow renames):

```bash
./mvnw test -Dtest=ArchitectureTest -DargLine=-Darchunit.freeze.refreeze=true
```

## Transactions

Services open their transactions through `platform.database.Transactions` (a thin wrapper over Hibernate sessions), never by calling `Database.getSession()` themselves:

| Method | Session | Transaction |
|---|---|---|
| `<T> T read(Function<Session, T>)` | from the read-only pool (`Database.getSession(true)`) | none |
| `<T> T write(Function<Session, T>)` | read-write | begun, committed on success |
| `void run(Consumer<Session>)` | read-write | same as `write`, without result |

On any exception (including a failing commit) the transaction is rolled back and the exception is rethrown unchanged; the session is always closed. Note that the read-only pool only points to a different database when `netshot.db.readurl` is set: otherwise it is the read-write database and nothing prevents a `read` from writing.

```java
public Domain rename(long id, String name) {
    return transactions.write(session -> {
        Domain domain = repository.get(session, id);   // throws NetshotException if not found
        domain.setName(name);
        return domain;
    });
}
```

!!! warning
    Do not hold a transaction across device I/O, hooks or other long operations.

## Errors

`platform.error.ErrorCode` lists the errors reported to API clients: each code has a stable numeric value and the HTTP status the REST API answers with. Services signal them with `platform.error.NetshotException(ErrorCode, message)`, a runtime exception that knows nothing of the transport. The REST exception mapper renders it exactly like the legacy `NetshotBadRequestException`: HTTP status of the code, body `{ "errorMsg": ..., "errorCode": ... }` (`RsErrorBean`). Other API channels translate it to their own error format.

The former REST code threw `NetshotBadRequestException(message, ErrorCode)`, a JAX-RS `WebApplicationException` (it only survives in the tests, as the reference the rendering of the errors is compared with): services must not use JAX-RS types. To detect a duplicate key on persist, use `Database.isDuplicateException()`, unless the rule of an existing use case has to be kept (see the [pitfalls](#pitfalls-met-on-the-credential-set-vault-instance-and-hook-slices)).

A helper shared by several services (for example `credentials.CredentialSets`) throws `NetshotException` too.

Inside a service, a failing query is a `HibernateException`: catch it around the `Transactions` call and report it as `NETSHOT_DATABASE_ACCESS_ERROR` (or a more specific code when the cause is known, like a duplicate key), keeping the message the clients know. Check the failure of the business rules *inside* the transaction (the exception rolls it back) and keep the order of the checks, as it decides which error a client gets when several apply.

When adding an error: add the `ErrorCode` (never change an existing numeric value or status; the web UI and the API clients depend on them).

## Repositories and services

The device use cases (`inventory.device`) show how the layers fit together.

- **`DeviceRepository`** only holds queries. Every method takes the `Session` first, none opens a transaction or catches an exception. It documents its *fetch plan*: `findWithDetails` loads exactly what the full device representation reads (`left join fetch` for the to-one associations, `Hibernate.initialize` for the collections), and nothing else. Lookups of other aggregates which the use case needs (domain, credential set) are there too, until those get their own repository.
- **`DeviceService`** has one public method per use case, named after it (`list`, `get`, `create`, `update`, `delete`). It is constructed with its collaborators (`Transactions`, repository, ports): no static lookup, no `Wiring.get()`. Inputs are *command records* (`DeviceCreation`, `DeviceUpdate`): plain data, no `api` type. In a command, a `null` component means "not provided / unchanged".
- **What a service returns** is a *detached entity loaded with a documented fetch plan* (the caller may read what the plan covers, and nothing else), a service-level record (`DeviceSummary`, a projection), or a primitive (`delete` returns whether there was a device). Never a DTO, never a `Response`.
- **Side effects** which must not happen if the transaction fails (refresh of the dynamic groups, scheduling of tasks, removal of files) are explicit statements *after* the `Transactions` call. A service reaches the static task engine only through a port, here `task.TaskSubmitter`, which `Wiring` implements with `TaskManager::addTask` and the tests with a recording lambda.
- **Pagination**: a list use case takes a `platform.database.Page` (offset and limit, validated by its constructor); `PaginationParams.toPage()` builds it in the REST layer.
- **Input objects which are entities**: some inputs are too rich to duplicate as service records (the accesses of a device and their credential sets). The API mapper builds *transient entities* from the request (`DeviceAccess`, `DeviceSshAccount`...), the command carries them, and the service validates them and resolves them against the persistent ones (global credential set by ID, Vault instance by ID...). They are never persisted as they are.
- **Validation** (`DeviceValidation`) is made of static, side-effect free functions which throw `NetshotException`. Other services which need the same checks call them.

```java
// DeviceService.update, abridged
Device device = transactions.write(session -> {
    Device existing = repository.find(session, id);
    if (existing == null) {
        throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
    }
    applyUpdate(session, existing, update);       // validation and changes, in the order of the legacy code
    // ... duplicate address check ...
    return existing;
});
DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);   // after the commit
return get(id);                                           // re-read with the documented fetch plan
```

## DTO conventions

DTOs are the types written to and read from the wire by an API channel: Java records, mapped from entities (or projections) by mappers in the same channel package. These facts hold today (Jackson 2.22 and swagger-core 2.2; the DTO tests and the contract goldens pin the representation):

- There are no Jackson views: the providers of the REST API (`NetshotWebApplication`) and of the webhook sender (`WebHookSender`) are plain `JsonMapper`/`XmlMapper`/`YAMLMapper` ones, and a record serializes all its components. What must not be written is left out of the record (or marked `@JsonIgnore`). `RestViews` only holds the views of the cluster wire format (`ClusterMessage`, `ClusterMember`), which is not an API channel.
- `@XmlRootElement`, `@XmlElement` and `@XmlAccessorType` are no-ops with the real providers (the custom mapper has no XML-bind introspector). The XML root element is the simple class name: a DTO whose name differs from today's class or entity needs `@JacksonXmlRootElement(localName = "<old name>")` (e.g. `DeviceDto` is published as `Device`).
- A "light" type that used to be a subclass of the full type is a separate record composing the common part with `@JsonUnwrapped` (JSON and YAML are byte-identical, XML identical given the root element name).
- Polymorphic DTOs use `@JsonTypeInfo(use = Id.NAME, property = "type")` with explicit `@JsonSubTypes.Type(name = "<old simple name>")`, never `SIMPLE_NAME`. List endpoints must declare the concrete generic type (`List<TaskDto>`, not `List<Object>`), otherwise the type id is lost.
- `Date` values are serialized as epoch milliseconds, like on the entities.
- OpenAPI: the generated document has no component schemas for the DTOs (`/openapi.json` is pinned by the API goldens); declaring them is a backlog item.
- **Nested polymorphic types** (device attributes, credential sets, groups) are a `sealed interface` with the `@JsonTypeInfo`/`@JsonSubTypes` annotations and its subtypes as nested records (`DeviceAttributeDto.Text`, `CredentialSetDto.Ssh`...). The `type` property (an attribute in XML) keeps today's values, written explicitly in `@JsonSubTypes.Type(name = ...)`.
- **Request records** (`NewDeviceRequest`, `UpdateDeviceRequest`, `DeviceAccessDto`) read exactly the JSON the former request classes read: same property names, and an unknown property is refused (a record is strict like the beans were). `@JsonUnwrapped` doesn't work on the properties which are *read*, so a shape which is both read and written (`CredentialSetDto`) repeats the common components in each subtype. The default a bean field had when the property was absent is applied in a compact constructor. When an absent property and an explicit `null` must differ (as they did for a primitive or for a field with an initial value), use a `JsonDeserializer` overriding `getAbsentValue()` and `getNullValue()` (see `DeviceAccessDto.SshHostKeyVerificationDeserializer`); `DeviceDtoTest` pins these defaults.
- **A record read from XML is read by Jackson, provided it is not annotated with `@XmlRootElement`.** A class annotated with it is read by Jersey's JAXB provider instead, which fails with a 500 since there is no JAXB runtime (that was the behavior of the former request classes). Request classes (and any class read as a request body) must therefore not carry it: the XML root element name of a request is ignored. The XML shape of a request is the one of the responses (wrapped lists, `type` attribute of the polymorphic bodies); the values of `Map<String, Object>` properties are strings in XML.
- A DTO is mapped from a *detached* entity or a projection by a mapper in the same package (`DeviceMapper`, `DeviceAccessMapper`: final classes of static functions). The mapper only touches what the fetch plan of the service loaded: a `LazyInitializationException` means the plan is incomplete, not that the mapper should open a session. Use `Hibernate.unproxy()` before an `instanceof` on an association, as it can hold a proxy.
- Entity simple class names are part of the contract (HQL entity names, default discriminators, the `type` values and the XML root names the DTOs reproduce): do not rename entities.

Webhook payloads are the DTOs of `api.hook.payload` (a sealed `TaskPayload` family, one record per task type, with the device, groups and credential sets it embeds), written by the plain providers of `WebHookSender`. They hold the same properties as the `Task` entity did when it was serialized; `HookPayloadTest` compares them with stored files. The payload never holds a secret: the CLI/HTTP credential sets only carry their common properties, and the other secrets are written as `=`.

### Task handlers

A `Task` entity holds the data of a task (target, schedule, status, log, result attributes) and its scheduling metadata (`schedule`, `clone`, `prepare`, `deviceLockPolicy`, `onSchedule`, `onCancel`); it does not run itself. Each concrete task type in `task.type` has one `task.TaskHandler<T>` in `task.handler` (`taskType()` and `run(T task)`). A handler sets the status, the log and the result on the task exactly as the engine expects, and takes its collaborators by constructor (`DriverRegistry`, `ComplianceChecker`, `DiagnosticAdapters`, `TaskSubmitter`, `ChildTaskOrchestrator`). `task.TaskHandlers` is the registry: it refuses to be built when a concrete `Task` subclass found by the package scan has no handler (or has two), and `TaskHandlers.run(task)` dispatches on the real class of the task.

`TaskJob` is built by Quartz through `TaskJobFactory` (handed to `TaskManager.init(taskJobFactory)` by `Netshot.main`, built by `Wiring`), which gives it the registry and `DomainEvents`; a test runs a task with `Wiring.get().taskJobFactory().newTaskJob().runTask(id)`. The group tasks (`TakeGroupSnapshotTask`, `RunGroupDiagnosticsTask`, `RunDeviceGroupScriptTask`, `ScanSubnetsTask`) delegate the pre-creation, promotion and polling of their child tasks to `task.engine.ChildTaskOrchestrator`; the pure decision functions stay in `Task.SequentialScheduling`. To add a task type: the entity in `task.type`, its handler, its DTO and payload (with their `@JsonSubTypes` entries), and its line in `Wiring`.

### Domain events

`platform.event.DomainEvents` is a synchronous, in-process publish/subscribe: `publish(event)` calls the listeners of the event type, in subscription order, in the publisher's thread; an exception thrown by a listener is logged and does not stop the others nor reach the publisher. A feature publishes an event of its own package (`task.event.TaskFinished(taskId, taskType)`, published by `TaskJob` once the result is saved, before the next occurrence is scheduled); a channel subscribes to it in `Wiring` (`HookDispatcher` runs the enabled `POST_TASK` hooks of the task type). `TaskJob` receives the publisher from the `TaskJobFactory`. A listener loads what it needs itself, through services: `TaskService.mapTask(id, mapper)` loads and prepares the task in a read transaction and applies the mapper inside it, so an `api` class maps the entity without touching a `Session`.

## Safety nets

- `ApiContractTest`: golden files of every REST endpoint (`src/test/resources/api-contract/`), with `ApiContractCoverageTest` failing when a resource method has no case. A diff means the change broke the API. Regenerate with `-Dnetshot.contract.update=true` only for a deliberate API change.
- `WebHookContractTest`: golden files of the webhook payloads (`src/test/resources/hook-contract/`), regenerated with `-Dnetshot.hook.contract.update=true`. It goes through the whole path: `TaskFinished` published, `HookDispatcher`, the payload mapper and the HTTP sender, against a local receiver.
- `HookPayloadTest`: for each task type (and the credential set kinds the goldens don't reach), the payload DTO is written in JSON, XML and YAML and compared with the stored files of `src/test/resources/dto-contract/`.
- The `*DtoTest` classes (`DeviceDtoTest`, `ComplianceDtoTest`...): the representation of objects made of every kind of attribute, credential set, group, rule... (JSON and XML), and the way the requests are read (values, defaults, refused bodies), are compared with stored files under `src/test/resources/dto-contract/<test>/`. They are regenerated with `-Dnetshot.dto.contract.update=true`, only for a deliberate change. The contract goldens only cover the kinds of objects the seed data holds: add a case here for the types they don't reach.
- `DeviceServiceTest`: the use cases against a real database (error codes, update semantics, side effects), with a recording `TaskSubmitter`.
- `DatabaseMigrationTest`: schema upgrades.
- `ArchitectureTest`: the dependency rules.

### Test layout

A test lives in the package of the code it tests: `inventory.device.DeviceServiceTest`, `compliance.rules.JsRuleTest`, `api.rest.v0.device.DeviceDtoTest` and `DeviceResourceTest` (the REST resource over HTTP), `driver.CiscoIOS12DriverTest`... Write one class per subject; keep `@Nested` classes only for facets of the same subject sharing a fixture. The root package `net.netshot.netshot` holds the shared helpers and the tests of the whole application (`ArchitectureTest`, `NetshotConfigTest`):

- base classes: `WithDatabaseTest` (database configuration of a schema), `WithRestServerTest` (starts Netshot and the REST server on a free port for the class, stops it afterwards, gives each test an admin `apiClient`), `WithDeviceSimulatorTest` (configuration, drivers and device simulator containers, `dev/device-simulator`), `HookSeededTest`;
- utilities: `TestConfig.init` (replaces the configuration, as `Netshot.initConfig` is not public), `TestDrivers`, `TestNetwork` (free ports), the `Fake*` doubles, `NetshotApiClient`, and the contract helpers (`ApiContract*`, `DtoContract`).

`TestLayoutTest` fails when a test class is left in the root package, or when two test classes share a simple name (the expected files of `DtoContract` are stored under the simple name of the class).

## Wiring

`boot.Wiring` is the composition root. `Wiring.init()` initializes the database access (with the persistent model from `boot.EntityModel`) and builds the long-lived objects: `Transactions`, then each repository and service (today `DeviceRepository` and `DeviceService`), in dependency order. `Netshot.main` calls it right after the schema update; tests needing a database call the same method.

- **Adding an entity**: register it in `boot.EntityModel` (task, rule and diagnostic subclasses are discovered by scanning).
- **Adding a service**: build it in `Wiring.init()`, give it an accessor, and bind it in `boot.WiringBinder`.
- **REST resources** get their dependencies by injection. The dependency goes from `boot` to `api`, never the other way: `Wiring.binder()` returns the HK2 binder, `Netshot.main` hands it to `RestServer.init(binder)` which gives it to `NetshotWebApplication`, which registers it into Jersey. A resource declares its services in an `@Inject` constructor (`DeviceResource(DeviceService)`). A rule of `ArchitectureTest` forbids `api` to depend on `boot`.
- **Code outside Jersey** receives its dependencies explicitly from `Netshot.main`, which keeps the `Wiring` returned by `Wiring.init()`: `SyslogServer.init(driverRegistry, autoSnapshotScheduler)`, `SnmpTrapReceiver.init(...)`, `ClusterManager.init(...)`, `TaskManager.init(taskJobFactory)`. No static facade is left: the device drivers are looked up in the injected `DriverRegistry` (`forDevice(device)`, `getDriverByName(name)`), which the handlers pass to the device scripts and helpers they create, and the automatic snapshots are scheduled through `AutoSnapshotScheduler`. The static `Wiring.get()` has no caller in `src/main` any more; it remains for the tests, and `Wiring.reloadTaskConfigs()` / `reloadRuleRuntimeConfigs()` (called by `Netshot.loadModuleConfigs()`, which runs before the wiring exists in tests) use the same static instance.
- **Descriptions of the drivers belong to the mappers.** The description of the driver of a rule, a diagnostic, a script, a discovered type or a device is computed by the mapper (`api/rest/v0`, and `TaskPayloads` for the webhooks) from the `DriverRegistry` it receives as first argument; the resources get the registry by injection and the hook dispatcher by constructor. Entities have no getter that looks a driver up.
- **Remaining static state.** `Finder` parses its queries with static methods, so it reads the drivers from a static `DriverRegistry` set by `Wiring` (`Finder.setDriverRegistry`); `AutoSnapshotScheduler` holds the process-wide set of the devices with a queued automatic snapshot (marked and cleared by the task lifecycle, which has no access to the scheduler). Both go with the ORM change.

Resources are discovered by Jersey package scanning of `net.netshot.netshot.api.rest` (sub-packages included): a new `@Path` class is picked up with no registration. Filters, providers and the exception mapper carry no `@Provider` annotation and are registered explicitly in `NetshotWebApplication`.

## Adding or moving a v0 endpoint

This is the procedure that was followed to move every group of endpoints out of the former `RestService`, starting with `GET /devices`, `GET /devices/{id}`, `POST /devices`, `PUT /devices/{id}` and `DELETE /devices/{id}`. It also tells how to add an endpoint (skip what only concerns the removal of legacy code). Follow the steps in this order, and keep the API contract tests green at each step: **a golden file must never change** (never run the tests with the update flags to make them pass).

The files of the worked example:

| File (under `net.netshot.netshot`) | Role |
|---|---|
| `inventory/device/DeviceRepository` | queries and fetch plan |
| `inventory/device/DeviceService` | use cases, transactions, side effects |
| `inventory/device/DeviceCreation`, `DeviceUpdate` | commands (inputs of the use cases) |
| `inventory/device/DeviceSummary` | projection of the list query |
| `inventory/device/DeviceValidation` | validation helpers (they used to be in `RestService`) |
| `credentials/CredentialSets`, `platform/tls/CaCertificates`, `platform/database/Page` | helpers shared by the service and other code |
| `task/TaskSubmitter` | port to the task engine |
| `boot/Wiring`, `boot/WiringBinder` | construction and injection |
| `api/rest/v0/device/DeviceResource` | the JAX-RS resource |
| `api/rest/v0/device/DeviceDto`, `DeviceSummaryDto`, `NewDeviceRequest`, `UpdateDeviceRequest` | the DTOs of the endpoints |
| `api/rest/v0/device/DeviceAccessDto`, `CredentialSetDto`, `DeviceAttributeDto`, `DeviceGroupDto`, `DomainDto`, `ModuleDto` | the DTOs of the nested types |
| `api/rest/v0/device/DeviceMapper`, `DeviceAccessMapper` | entity/projection to DTO, request to command |
| `DeviceServiceTest`, `DeviceDtoTest` (tests) | service tests, entity/DTO differential test |

### 1. Check the safety nets

The endpoints must be covered by `ApiContractTest` (JSON and XML, the error cases, the status codes: look at `_status.properties`), and `ApiContractCoverageTest` must pass. Add the missing cases *first*, on the unchanged code, to create their goldens. Read the goldens: they tell what the representation holds (this is the list of DTOs to write) and which quirks you must preserve (a 500 recorded for a XML request is a quirk, see [DTO conventions](#dto-conventions)). Also list which other code serializes the entities you are about to replace by DTOs (other endpoints, `WebHookContractTest` for the webhook payloads): nothing else must write them any more (step 10).

### 2. Read the legacy code like a specification

For each endpoint note the status codes and the way they are chosen (`suggestReturnCode`), the error codes and the order in which the checks happen, the transaction boundaries (what is read in which session), the side effects after the commit, the audit log lines (`AAA` logger), the quirks (swallowed exceptions, fallbacks). The migrated code must do the same, in the same order. Mark the helpers that other endpoints use too.

### 3. Repository

`<feature>/<Entity>Repository`: one method per query, `Session` first, no transaction, no exception handling. For a query building a light object (`select new ...`), project into a record of the feature package (`DeviceSummary`), *never* an `api` type, and use the fully qualified name in the HQL (`DeviceSummary.class.getName()`). For a query whose result is serialized, write the fetch plan in the Javadoc and implement it explicitly (`findWithDetails`): this replaces the `.size()` calls the legacy code made to initialize collections before closing the session.

### 4. Commands and service

`<feature>/<Entity>Service`, constructed with `Transactions`, the repository and the ports it needs (see [Repositories and services](#repositories-and-services)). One public method per endpoint. Commands are records (`DeviceCreation`); the author of a change (user name) is a component of the command, since the service knows nothing of the security context. Copy the legacy logic *including its order*; replace `NetshotBadRequestException` by `NetshotException` with the same `ErrorCode` and message; replace each `Database.getSession()` block by one `Transactions` call; put the side effects after it. Move the validation helpers the use cases need to a `<Entity>Validation` class (static, throwing `NetshotException`); if other code still has its own copy of one, delete the copy and call the new one. A service method never receives `Session`, a DTO or a JAX-RS type.

### 5. Wiring

In `boot.Wiring.init()` build the repository and the service (a `new` chain, dependencies first), add an accessor, and bind the service in `boot.WiringBinder`.

### 6. Service tests

Write `<Entity>ServiceTest` in the package of the service, extending `WithDatabaseTest` (see `inventory.device.DeviceServiceTest`: own schema, `TestConfig.init`, `TestDrivers.reload()` (the drivers), `Database.update()`, `Wiring.init(TestDrivers.registry())`, a recording `TaskSubmitter`). Cover each error code with its trigger, the order of the checks, the semantics of the null components, that a failing use case leaves nothing behind, the side effects, and that what the service returns can be read without a session. This is where the logic of the use cases is tested; the contract tests only prove that the HTTP surface did not move.

### 7. DTOs and mapper

In `api.rest.v0.<feature>`, following the [DTO conventions](#dto-conventions):

- one record per shape of the goldens, with the components named like the properties (the property names are the contract; the order does not matter, the goldens are normalized). A representation made of several nested types needs one record per nested type: let the golden of every case (and the entity annotations, for the cases the seed data doesn't hold) tell what is there;
- the full device is `DeviceDto` with `@JacksonXmlRootElement(localName = "Device")` (the XML root element is the simple class name otherwise); a summary is a separate record, not a subclass;
- the request records replace the request classes: same property names, same defaults (compact constructor or deserializer, see the conventions), without `@XmlRootElement` (see above);
- the mapper: `toDto(...)` from the detached entity or the projection, `to<Command>(request, author)` for the requests. Compare against the entities, not only against the goldens: write the DTO test (`DeviceDtoTest`) building an in-memory entity with every variant, mapping it to its DTO, writing it with a plain `JsonMapper`/`XmlMapper` and comparing the canonical form (`ApiContractGolden`) with a stored file (`DtoContract`).

### 8. Resource

A class in `api.rest.v0.<feature>` annotated `@Path("/")`, with the **full path on each method**, and the service injected by an `@Inject` constructor. Copy for every method: `@RolesAllowed`, `@Consumes`/`@Produces`, `@Operation`, `@Tag`, `@Parameter` (also on the parameters the method ignores, like `details` of `GET /devices`) and the method *name* (it is the OpenAPI `operationId`). Declare concrete generic return types (`List<DeviceSummaryDto>`). The resource only: reads the principal (`SecurityContext`) for the author, builds the command, calls the service, maps the result, and chooses a status code other than 200 with `ResponseCodeFilter.suggest(request, status)`. It has no `Session`, no repository, no `try`/`catch` of its own (a `NetshotException` is rendered by the exception mapper exactly like a `NetshotBadRequestException`). An endpoint which answers with an entity not migrated yet (here the `Task` of `POST /devices`) keeps returning the entity, with a comment: a later wave replaces it.

!!! warning "Do not give the new resource a narrower @Path than the paths it takes over"
    JAX-RS selects the root resource class *before* looking at the methods, with no fallback. A new class annotated `@Path("/devices")` hides every method of the other resources under `/devices/...` (they answer 404 or 405). Use `@Path("/")` with the full path on each method like the existing resources do (several root resources with the same `@Path("/")` and different method paths work fine), or move a whole path prefix at once. Declaring the same method and path in both classes fails at startup.

### 9. Remove the legacy code

In the same change, delete the migrated methods from where they were, the request classes, and every helper nothing uses anymore (the compiler and `checkstyle:check` report the unused imports). Keep what other endpoints still use. Classes only the tests still need (a former response class, the former exception) move to the test tree, as the reference the new code is compared with.

### 10. Entities

Entities carry no `@JsonView`, JAXB or Jackson annotation: do not add any. If a new endpoint or payload needs a representation, write a DTO and a mapper.

### 11. Run the checks

`./mvnw checkstyle:check`, then `ApiContractTest`, `ApiContractCoverageTest`, `WebHookContractTest`, your two new test classes, `ArchitectureTest`, and finally the whole `./mvnw test`. For `ArchitectureTest`: the `apiDoesNotUseSession` entries of the old code disappear and the store only shrinks for this rule. The `noCyclesBetweenFeatureSlices` rule, on the other hand, gets one entry per dependency of each new class of the feature on the other slices of the entity model (`inventory` -> `access`, `credentials`, `driver`, `task`, `compliance`...): those slices form one big cycle through the entities, so any class of the entity model that uses another one is counted, until the entities are decoupled. Refreeze and check that the other rules neither grow nor get a new entry:

```bash
./mvnw test -Dtest=ArchitectureTest -DargLine=-Darchunit.freeze.refreeze=true
git diff --stat src/test/resources/archunit_store
```

The refreeze rewrites the line numbers of the stored entries; compare the entries without them (they are ignored when matching).

### 12. Document

Update [REST API](../api/rest-api.md) if the behavior changed (it should not), this page if a convention changed or a pitfall was found, and report what remains: other places which serialize the entities, entities returned by the migrated endpoints (here `Task`), anything deliberately preserved (the XML request quirk).

### Pitfalls met on the device slice

- **Lazy loading.** The entity is serialized after the session is closed: anything the DTO reads must be in the fetch plan. `Device.getOwnerGroups()` is computed from the memberships, `isCompliant()` from the check results: read the *getters* the entity serialization called, not only the fields.
- **XML root names.** A record is published under its class name (`DeviceDto`): `@JacksonXmlRootElement(localName = "Device")`.
- **`@Path` shadowing.** See the warning above.
- **Polymorphic attributes and credentials.** `type` values are the contract; the list endpoints need the concrete generic type.
- **Request defaults.** A bean field initialized to a value, or a primitive, behaves differently from a record component on an absent property (`autoDiscover` was true when absent, `sshHostKeyVerification` was `TRUST_ANY` when absent but `TRUST_KNOWN` when null). The contract goldens caught one of them: write the test for each.
- **Status codes.** `DELETE` answered 204, or 404 with an empty body when the device does not exist, through `suggestReturnCode`: the service tells (`delete` returns a boolean), the resource chooses the status.
- **Swallowed exceptions.** The legacy `deleteDevice` ended with `catch (Exception e) { log.error(...); }` (answering 204). The service does not reproduce it: an unexpected exception is a 500.

### More pitfalls, met on the compliance slice

- **Lists in a DTO test.** The XML root element of a list is the class name of the collection (`ArrayList`): write an `ArrayList` of DTOs, not a `List.of(...)`.
- **A shape which is both composed and projected.** `select new ExemptedDevice(new DeviceSummary(...), e.expirationDate)` works in HQL, so a REST type made of a summary and extra columns (`@JsonUnwrapped` around `DeviceSummaryDto`) has a service-level record made of the summary record and the extra components.
- **Records can't extend.** A request which used to extend another one (`RsRuleTest extends RsRule`) repeats the components of the first (`RuleTestRequest`, `RuleRequest`).
- **`Session.get` of an unknown ID returns null.** Code which puts the result in a collection without checking (the target groups of a policy, the group of a software or hardware rule) silently accepts an ID that doesn't exist. The migrated use cases keep that, and the mappers have to cope with the null.

### Pitfalls met on the task slice

- **A polymorphic family shares its common part.** `TaskDto` is a sealed interface of twelve records, each composing the properties common to all tasks (`TaskBaseDto`, `@JsonUnwrapped`) with the ones of its type, and published under the simple name of the task class (`@JacksonXmlRootElement`). `TaskMapper.toDto(Task)` is the single way to represent a task: reuse it from any endpoint answering with a task (`POST /devices` does).
- **Protected getters.** Some task getters (`getDeviceId`) are protected and serialized anyway by the entity mapper: read the value through public accessors (`task.getDevice().getId()`) in the mapper.
- **A request with many defaults stays a bean.** `TaskRequest` (about thirty properties, each with a default for an absent property and another outcome for an explicit `null`) is not a record: a bean with initialized fields reproduces the three cases for free, a record would need one deserializer per property.
- **Authorization depending on the payload.** The role needed to create a task depends on its `type`: the resource checks it before calling the service (the service knows nothing of roles), which is the order of the legacy code.
- **An unknown id is not always a 404.** `GET /tasks/{id}` answers 204 for an unknown task (the service returns `null`), `PUT /tasks/{id}` answers 400 `NETSHOT_INVALID_TASK`, and the debug log answers 404: the goldens record them, keep them.


### More pitfalls, met on the reports slice

- **`MINIMAL_CLASS` type ids are not `NAME` ids in XML.** Jackson's XML module rewrites the `$` of a `CLASS`/`MINIMAL_CLASS` type id as `..` (`.RestService..RsHardwareSupportEoSStat`) while JSON keeps it (`.RestService$RsHardwareSupportEoSStat`); a `NAME` id is the same in both. A DTO that must keep both forms (`HardwareSupportStatDto`) therefore carries the type as an explicit property with a format-aware serializer instead of `@JsonTypeInfo`.
- **Streaming exports.** `ReportService.export` pushes rows to a `ReportExportSink` inside one read transaction, page by page (with `session.clear()` as before), and the REST layer's `XlsxReportWriter` turns them into a streaming workbook: data access stays in the service, presentation in `api`, and memory use is unchanged.

### Pitfalls met on the diagnostic and script slices

- **An entity used as a request body reads more than its setters.** `POST /scripts` read a `DeviceJsScript`: Jackson also writes to a private field behind a getter (`userInputDefinitions`), so a posted map was accepted, kept (the script was then not evaluated) and returned. Probe the former class with a matrix of bodies before writing the request record (and before stripping its annotations), and store what the matrix is read into (`DiagnosticDtoTest`). The read-only `realDeviceType`, on the other hand, is refused as an unknown property.
- **A default for an absent property, null for an explicit null.** A bean field initialized to `""` (`name`, `folder`) is `""` when absent and `null` when explicit: use `api.rest.EmptyWhenAbsentDeserializer` rather than a compact constructor.
- **Polymorphic results.** `DiagnosticResultDto` is published under the `NUMERIC`/`TEXT`/`LONGTEXT`/`BINARY` type ids (the entity's `@JsonSubTypes`), whereas `DiagnosticDto` uses the simple class names; the long text value is not part of the representation.
- **An unknown script is not an error.** `GET /scripts/{id}` answers 204 (the service returns `null`), `DELETE` answers 400 `NETSHOT_SCRIPT_NOT_FOUND`, and an unknown diagnostic group is silently ignored (the diagnostic is created without group).

### Pitfalls met when moving the execution logic out of the entities

- **Entities are equal by id, caches must be keyed by identity.** The compiled state of a rule (text patterns, validity of a script) used to live in transient fields, so it lasted as long as the instance. The evaluators keep it in `RuleStateCache`, a weak map keyed by the identity of the rule instance: a `WeakHashMap` would use `Rule.equals` (the id) and share a stale state between two instances of the same rule (another session, or after an edit). A cached value must not reference the rule, or it is never released.
- **Evaluators and adapters are looked up by the real class.** A rule or a diagnostic can be a Hibernate proxy: the registries (`RuleEvaluators`, `DiagnosticAdapters`) unproxy it first, and refuse to be built when a type listed in `Rule.getRuleClasses()` / `Diagnostic.getDiagnosticClasses()` has no evaluator or adapter (`ComplianceCheckTest` and `DiagnosticAdaptersTest` check it against a scan of the classes).
- **Runtimes read their settings when built.** `JsRuleRuntime` and `PyRuleRuntime` load `netshot.javascript.maxexecutiontime` / `netshot.python.maxexecutiontime` in their constructor (tests call `Netshot.loadModuleConfigs()` before `Wiring.init()`), and again on a configuration reload (`Wiring.reloadRuleRuntimeConfigs()`, called by `Netshot.loadModuleConfigs()`).

- **A handler works on the task, not on a copy.** `TaskJob` saves what the handler wrote on the task (`copyResultsTo`), so a handler keeps writing the status and the log on the `Task` it receives, and reads the entity fields through its getters (the fields stay `protected`). When the handler replaces the device by a fresh one loaded from the database, it also calls `task.setDevice(...)`.
- **Settings belong to the component that uses them.** `netshot.snapshots.auto.*` is read by `AutoSnapshotScheduler` (not by the task), which `Netshot.loadModuleConfigs()` reloads through `Wiring.reloadTaskConfigs()`. The syslog server, the SNMP trap receiver and the cluster manager receive the scheduler from `Netshot.main`.

### Pitfalls met on the inventory slice

- **The class of a list is part of the XML.** A query with a collection fetch and a page (`GET /devices/{id}/configs?limit=...`) is paginated in memory by Hibernate, which answers a sub list: XML publishes it as `<SubList>` instead of `<ArrayList>`. The mappers keep that (`ConfigMapper.toDtos`) by answering a sub list when the service result is not a plain `ArrayList`.
- **A download is not a JSON error.** `GET /configs/{id}/{item}` answers its errors with bare `WebApplicationException` statuses (no body), not with an `ErrorCode`. The service throws a feature exception (`ConfigItemException`, with a reason) and the resource turns it into the same statuses.
- **Two shapes for one entity.** A domain is `DomainDto` when nested (complete) and `DomainSummaryDto` when served by `/domains` (id, name, description and IPv4 address only). Check the golden of each endpoint before reusing a DTO.
- **A dynamic group is evaluated inside the transaction.** Creating or editing one runs its query (`refreshCache`) before the commit, and a failing query rolls everything back; the order of the checks (devices or query before the name, on edit) is observable.
- **Read-only sessions.** `POST /devices/search` has always used a read-write session; it is kept so (`Transactions.write`), not moved to the read pool.

### Pitfalls met on the credential set, Vault instance and hook slices

- **Secrets stay masked, and unchanged means null.** `HideSecretSerializer` writes `=` for any non-null secret (a missing one stays `null`) and `HideSecretDeserializer` reads `=` as `null`. In the services a `null` secret means "keep the stored value" for the passwords, communities, keys and AppRole/JWT secrets, but not for the username (cleared when not given) nor for the private key of a SSH key account, which is wiped when it is not given (a quirk of the legacy code, kept). The DTO tests assert that no secret value appears in the JSON, XML or YAML of any kind, and that every secret of every kind is read as `null` for `=`.
- **Do not replace the duplicate rule.** These use cases recognize a duplicate name by the message of the cause of the Hibernate exception ("uplicate"), and a missing domain by the column name in it: `DatabaseErrors.causeMentions` keeps that rule. `Database.isDuplicateException` is not equivalent (it takes any constraint violation, a foreign key for instance, for a duplicate).
- **Known but ignored properties.** The entities have properties which are not part of the representation (`version`, `changeDate`, the `...Secret` bundles of the credential sets): they are silently skipped when a request holds them, whereas a really unknown property is refused. A request record or bean lists them in `@JsonIgnoreProperties` to keep that. Find them by introspecting the entity, and store what a matrix of bodies is read into (`CredentialSetDtoTest`).
- **A XML root element per record.** The XML of a single credential set (`POST`, `PUT`) is rooted at the class name of the entity, whereas a list uses `item`: every record of a polymorphic family needs its `@JacksonXmlRootElement` (`DeviceSshAccount`, `HashicorpVaultKv2Instance`, `WebHook`...). The contract goldens only had JSON for these writes.
- **A request with defaults stays a bean, here too.** The Vault instance (mount paths, CA trust mode) and the hook (`enabled`, the triggers, the CA trust mode) requests are beans with initialized fields: absent gives the default, explicit `null` gives `null` (`enabled: null` is `false`; `triggers: null` is accepted when creating a hook, and makes the update fail, as it did).
- **The order of the triggers means nothing.** `HookTrigger.hashCode()` uses the identity hash of an enum, so the iteration order of a set of triggers changes from one JVM to the next: compare triggers as a set. The request keeps them in a list, to store them in the order of the request.
- **Side effects through ports.** The cache invalidation and cluster notification of a Vault instance change (`VaultInstanceChangeNotifier`) and the login attempt of the connection test (`VaultConnectionTester`) are ports given by `Wiring`, so that the service tests can record and fake them.
- **Dead error paths.** Deleting a credential set "in use" is practically unreachable now: the foreign keys of the device accesses and of the tasks cascade or set null. Deleting a Vault instance referenced by a credential field is the one which still fails (`NETSHOT_VAULT_INSTANCE_IN_USE`). The service tests inject a failing repository to cover the translation of the constraint violation.
- **A throwaway HTTP probe finds what the goldens miss.** Before moving a group, send a few hundred requests (every kind, absent/null/`=` secrets, refused bodies, XML and JSON, paged lists) to the unchanged server, record the answers, and compare them with the ones of the migrated server (ignoring the order of the keys and elements). That is how the XML root names above were found.
