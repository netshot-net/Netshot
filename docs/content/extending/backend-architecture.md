# Backend architecture

This page is for developers working on the Netshot core server (Java). It describes the layering of the code base, the rules that protect it, and the conventions to follow when adding or moving code. For the map of the components, see [Architecture](../architecture.md).

!!! note "History"
    The REST API used to be one monolithic class, `RestService`. It has been moved, one feature at a time, to the layered structure described here, and the class is gone: `api.rest.RestServer` only starts the web server, and every endpoint is a resource of `api.rest.v0.<feature>`. The last section is the step-by-step guide that was followed, with the device endpoints (`/devices`) as the worked example: use it when you add or change an endpoint, and for the same kind of work on another API.

## Layers and packages

Everything lives under `net.netshot.netshot`.

```text
Netshot            entry point only (main)
boot/              composition root (Wiring)
platform/          technical building blocks; depends on no feature
├── database/      connection pools, jOOQ set-up, Database, Transactions, Liquibase migrators
│   └── schema/    jOOQ classes generated from the schema (committed under src/main/jooq)
├── error/         ErrorCode, NetshotException
├── event/         DomainEvents: synchronous in-process publish/subscribe
├── crypto/ tls/ rest/ config/ lifecycle/ concurrent/ log/
├── net/           IPv4, IPv6 and MAC address value types
├── script/        script toolbox and JavaScript value helpers
api/               API channels: how the outside world talks to the core
├── rest/          web server (RestServer), Jersey application, filters, exception mapper, shared REST bits
│   └── v0/        the current (legacy, unversioned) REST API, served on /api
├── hook/          webhook channel: HookDispatcher (reacts to domain events), WebHookSender (HTTP), payload/ (payload DTOs and mapper)
└── grpc/          (later)
domain/ credentials/ driver/ inventory/ access/ vault/ diagnostic/ devicescript/
compliance/ userscript/ task/ report/ hook/ aaa/ cluster/ system/ tools/
                   features: domain records, repositories, services, engines
```

- **Feature packages** (`inventory`, `task`, `compliance`...) hold the domain model of the feature, its repositories and its services. The domain model is made of plain immutable records (with a Lombok `@Builder(toBuilder = true)` when they have many components), a sealed interface for a family of types (`Hook` and `WebHook`; `User` with `UiUser` and `ApiToken`; `Rule`, `DeviceCredentialSet`, `DeviceAttribute`, `TaskSpec`...), with no mapping annotation (see [Database access with jOOQ](#database-access-with-jooq)).
- **Domain records** carry the invariants on their own state only. No database access, no I/O, no static registry lookup, and no Jackson/JAXB serialization annotation (no API channel serializes a domain record: the DTOs and the webhook payloads are written by the mappers). Where some code must change an object step by step (the device scripts and drivers on a device or a configuration, a running task), a separate mutable class holds the working state: `DeviceDraft`, `ConfigDraft`, `TaskRun`.
- **Repositories** contain queries only; the `DSLContext` is passed explicitly as first parameter.
- **Services** implement the use cases and own the transaction boundaries (see [Transactions](#transactions)). They throw neutral exceptions (see [Errors](#errors)), and know nothing of HTTP.
- **`api.*`** are the API channels. Each channel owns its DTOs, even when two channels have similar ones (the REST task representation is not the webhook task payload). A channel translates between the wire format and the services. `api.rest` and `api.hook` do not depend on each other; they share services and domain records, not DTOs.
- **`platform`** knows nothing about the features.
- **`boot`** is the only place where everything is assembled.

The REST API must stay byte-for-byte equivalent (JSON, XML, YAML) while it is restructured.

## Dependency rules

The rules are enforced by `ArchitectureTest` (ArchUnit): `platform` depends on no feature; only `boot` (and `Netshot`) depend on `api`, and `api` does not depend on `boot`; `api.rest` and `api.hook` do not depend on each other; `api` does not reach the database (neither jOOQ nor `platform.database`, except the `Page` window); JAX-RS server classes are used only by the REST layer, and the JAX-RS client and `core` types only by the packages making outgoing calls; `@JsonView` and the JAXB annotations are only used by the API channels and the cluster wire format; and there is no cycle between top-level packages.

The feature packages around the devices are layered, each one only depending on the ones before it:

1. `domain` (the management domains), `vault` (the Vault instances and the resolution of the Vault-backed secrets) and `credentials` (the credential sets, scoped by domain);
2. `driver`: the drivers, with the settings of their accesses (`driver.protocol`: SSH, Telnet and HTTP), looked up for a device through the `DrivenDevice` port;
3. `inventory`: the devices and what is known about them, including their per-access settings (`DeviceAccess`) and the last results of the diagnostics (`inventory.diagnostic`) and of the compliance checks (`inventory.compliance`). The device search refers to the policies, rules and diagnostics by name and ID only;
4. `access`: the device accesses and the receivers. They log into the log of the running task through `platform.log.TaskContext`, and the receivers report the configuration changes through the `access.collector.SnapshotRequester` port (implemented by the automatic snapshot scheduler);
5. `diagnostic`, `devicescript`, `compliance`, `userscript`: the definitions and the scripts run on the devices;
6. `task`: the task engine, the handlers, and the use cases which create tasks (`TaskService`, `DeviceCreationService` which adds a device).

Above them: `report` and `cluster` (which depend on `task`), then the API channels (`api`) and the composition root (`boot`). `aaa`, `hook` and `system` only depend on `platform` (and `system` on `aaa`).

When a lower package needs something of an upper one, the type belongs lower (a value type or a result, as the address types moved to `platform.net`), or the lower package declares a port that the upper one implements (`DrivenDevice`, `SnapshotRequester`, `ClusterCoordinator`), or the use case belongs to the upper package (`DeviceCreationService`).

All the rules are *strict*: any violation fails the build, including the rule against the cycles between top-level packages (it reports each class dependency which belongs to a cycle) and the one against the mutable static fields. Should a new rule have violations which can't be removed at once, it can be *frozen* (ArchUnit's `FreezingArchRule`): its existing violations are then recorded in `src/test/resources/archunit_store/` and tolerated, only new ones fail, and that store must only shrink (`./mvnw test -Dtest=ArchitectureTest -DargLine=-Darchunit.freeze.refreeze=true` after a cleanup, then review the diff).

## Transactions

Services open their transactions through `platform.database.Transactions`, never by taking a connection themselves. The wiring builds one `Transactions` on the `DatabaseService` (which owns the connection pools while it runs) and gives it to the services, the task engine and handlers, and the device scripts (as part of `access.Connectors` for the scripts); nothing builds its own. Each method takes a connection from a pool, gives the work a jOOQ `DSLContext` on it and gives the connection back:

| Method | Pool | Transaction |
|---|---|---|
| `<T> T read(Function<DSLContext, T>)` | read-only | none committed: rolled back at the end |
| `<T> T write(Function<DSLContext, T>)` | read-write | committed on success |
| `void run(Consumer<DSLContext>)` | read-write | same as `write`, without result |
| `<T> T system(Function<DSLContext, T>)` | system (small) | committed on success; for the task dispatch and the cluster bookkeeping only |

On any exception (including a failing commit) the transaction is rolled back and the exception is rethrown unchanged. Note that the read-only pool only points to a different database when `netshot.db.readurl` is set: otherwise it is the read-write database, and what a `read` writes is rolled back.

```java
public Domain rename(long id, String name) {
    return transactions.write(dsl -> {
        Domain domain = repository.find(dsl, id);
        if (domain == null) {
            throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Unable to find the domain.");
        }
        return repository.save(dsl, domain.toBuilder().name(name).build());
    });
}
```

!!! warning
    Do not hold a transaction across device I/O, hooks or other long operations. A snapshot, a script or diagnostics load the device in one transaction, talk to the device without any, then save it in another one; the save checks the version of the device (`DataChangedException` when it was changed meanwhile).

## Database access with jOOQ

The persistence uses [jOOQ](https://www.jooq.org) (it replaced Hibernate, one feature at a time; the history and the pitfalls of that move are kept below). A repository maps the generated records to the domain records itself: there is no mapping layer, no session cache and no lazy loading, so a repository method loads exactly what its domain record holds (a device with its attributes, accesses, modules and interfaces, in one query with nested `multiset`s), and the related aggregates (configurations, diagnostic results, groups) are loaded by their own repositories.

- **Generated classes**: the tables, records and keys are generated from the schema into `net.netshot.netshot.platform.database.schema`, committed under `src/main/jooq` (a source folder of the build, not checked by checkstyle). After a change of the Liquibase changelog, regenerate them with `./mvnw -Pjooq-codegen process-test-classes` (`JooqCodegen`: a PostgreSQL container, migrated by `Database.update()`, then the jOOQ generator) and commit the result: `JooqSchemaTest` fails when they don't match the changelog. The classes are not bound to a schema (the tables are found through the search path).
- **Types**: the `inet` columns are `InetAddress` (`InetBinding`), the `timestamp` columns are `Instant` (`UtcInstantConverter`: the dates are stored in UTC), the secrets are decrypted and encrypted by `EncryptedStringConverter`. The enumerations are stored by name: they are strings in the generated classes, since the generated package depends on no feature, and the repository converts them (`valueOf`, `name()`).
- **Names**: the foreign keys, unique constraints and indexes of foreign keys are named `fk_`, `uk_` and `ix_<table>_<columns>`; the generated keys and join paths are named after them. Name the new ones the same way.
- **Versions and dates**: an update of a versioned row increments its `version` and sets `change_date` to the time of the change, to the second. The save of a device also checks the version it was loaded with (`DataChangedException` when the device was changed meanwhile), since it ends the long device sessions.
- **Errors**: a failing statement throws a `org.jooq.exception.DataAccessException`: catch it in the services. `DatabaseErrors.isUniqueViolation` and `isForeignKeyViolation` tell the cause from its SQL state.
- **PostgreSQL versions**: the open source edition of jOOQ only officially supports the latest PostgreSQL version (18 for jOOQ 3.21), while Netshot supports PostgreSQL 15 and later. The database tests therefore run on PostgreSQL 15 by default, and the CI also runs them on the latest version (`-Dnetshot.test.postgres=postgres:18`); the warning jOOQ logs about the version is silenced. Don't use the jOOQ API of features which are more recent than PostgreSQL 15.

## Errors

`platform.error.ErrorCode` lists the errors reported to API clients: each code has a stable numeric value and the HTTP status the REST API answers with. Services signal them with `platform.error.NetshotException(ErrorCode, message)`, a runtime exception that knows nothing of the transport. The REST exception mapper renders it exactly like the legacy `NetshotBadRequestException`: HTTP status of the code, body `{ "errorMsg": ..., "errorCode": ... }` (`RsErrorBean`). Other API channels translate it to their own error format.

The former REST code threw `NetshotBadRequestException(message, ErrorCode)`, a JAX-RS `WebApplicationException` (it only survives in the tests, as the reference the rendering of the errors is compared with): services must not use JAX-RS types. To detect a duplicate key, use `DatabaseErrors.isUniqueViolation()` on the `DataAccessException`.

A helper shared by several services (for example `credentials.CredentialSets`) throws `NetshotException` too.

Inside a service, a failing query is a `DataAccessException`: catch it around the `Transactions` call and report it as `NETSHOT_DATABASE_ACCESS_ERROR` (or a more specific code when the cause is known, like a duplicate key), keeping the message the clients know. Check the failure of the business rules *inside* the transaction (the exception rolls it back) and keep the order of the checks, as it decides which error a client gets when several apply.

When adding an error: add the `ErrorCode` (never change an existing numeric value or status; the web UI and the API clients depend on them).

## Repositories and services

The device use cases (`inventory.device`) show how the layers fit together.

- **`DeviceRepository`** only holds queries. Every method takes the `DSLContext` first, none opens a transaction or catches an exception. `find` loads the whole `Device` record; the other aggregates the use case needs (domain, credential sets, configurations, groups) are read by their own repositories.
- **`DeviceService`** has one public method per use case, named after it (`list`, `get`, `create`, `update`, `delete`). It is constructed with its collaborators (`Transactions`, repository, ports): no static lookup, no `Wiring.get()`. Inputs are *command records* (`DeviceCreation`, `DeviceUpdate`): plain data, no `api` type. In a command, a `null` component means "not provided / unchanged".
- **What a service returns** is a domain record (complete: there is nothing left to load), a service-level record (`DeviceSummary`, a projection), or a primitive (`delete` returns whether there was a device). Never a DTO, never a `Response`.
- **Side effects** which must not happen if the transaction fails (refresh of the dynamic groups, scheduling of tasks, removal of files) are explicit statements *after* the `Transactions` call. A service reaches the task engine only through a port, here `task.TaskSubmitter`, which `Wiring` implements with `TaskManager::addTask` and the tests with a recording lambda.
- **Pagination**: a list use case takes a `platform.database.Page` (offset and limit, validated by its constructor); `PaginationParams.toPage()` builds it in the REST layer.
- **Input objects which are domain records**: some inputs are too rich to duplicate as service records (the accesses of a device and their credential sets). The API mapper builds the domain records from the request (`DeviceAccess`, `DeviceSshAccount`, a `Domain.reference(id)`...), the command carries them, and the service validates them and resolves them against the stored ones (global credential set by ID, Vault instance by ID...). They are never stored as they are.
- **Validation** (`DeviceValidation`) is made of static, side-effect free functions which throw `NetshotException`. Other services which need the same checks call them.

```java
// DeviceService.update, abridged
Device device = transactions.write(dsl -> {
    Device existing = repository.find(dsl, id);
    if (existing == null) {
        throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
    }
    Device edited = applyUpdate(dsl, existing, update);   // validation and changes, in the order of the legacy code
    // ... duplicate address check ...
    repository.save(dsl, existing, edited);              // version-checked update
    return edited;
});
DynamicGroups.refreshAllGroupsOfOneDevice(device.id());   // after the commit
return get(id);
```

## DTO conventions

DTOs are the types written to and read from the wire by an API channel: Java records, mapped from the domain records (or projections) by mappers in the same channel package. These facts hold today (Jackson 2.22 and swagger-core 2.2; the DTO tests and the contract goldens pin the representation):

- There are no Jackson views: the providers of the REST API (`NetshotWebApplication`) and of the webhook sender (`WebHookSender`) are plain `JsonMapper`/`XmlMapper`/`YAMLMapper` ones, and a record serializes all its components. What must not be written is left out of the record (or marked `@JsonIgnore`). `RestViews` only holds the views of the cluster wire format (`ClusterMessage`, `ClusterMember`), which is not an API channel.
- `@XmlRootElement`, `@XmlElement` and `@XmlAccessorType` are no-ops with the real providers (the custom mapper has no XML-bind introspector). The XML root element is the simple class name: a DTO whose name differs from today's class or entity needs `@JacksonXmlRootElement(localName = "<old name>")` (e.g. `DeviceDto` is published as `Device`).
- A "light" type that used to be a subclass of the full type is a separate record composing the common part with `@JsonUnwrapped` (JSON and YAML are byte-identical, XML identical given the root element name).
- Polymorphic DTOs use `@JsonTypeInfo(use = Id.NAME, property = "type")` with explicit `@JsonSubTypes.Type(name = "<old simple name>")`, never `SIMPLE_NAME`. List endpoints must declare the concrete generic type (`List<TaskDto>`, not `List<Object>`), otherwise the type id is lost.
- Dates are serialized as epoch milliseconds, as the former entities were.
- OpenAPI: the generated document has no component schemas for the DTOs (`/openapi.json` is pinned by the API goldens); declaring them is a backlog item.
- **Nested polymorphic types** (device attributes, credential sets, groups) are a `sealed interface` with the `@JsonTypeInfo`/`@JsonSubTypes` annotations and its subtypes as nested records (`DeviceAttributeDto.Text`, `CredentialSetDto.Ssh`...). The `type` property (an attribute in XML) keeps today's values, written explicitly in `@JsonSubTypes.Type(name = ...)`.
- **Request records** (`NewDeviceRequest`, `UpdateDeviceRequest`, `DeviceAccessDto`) read exactly the JSON the former request classes read: same property names, and an unknown property is refused (a record is strict like the beans were). `@JsonUnwrapped` doesn't work on the properties which are *read*, so a shape which is both read and written (`CredentialSetDto`) repeats the common components in each subtype. The default a bean field had when the property was absent is applied in a compact constructor. When an absent property and an explicit `null` must differ (as they did for a primitive or for a field with an initial value), use a `JsonDeserializer` overriding `getAbsentValue()` and `getNullValue()` (see `DeviceAccessDto.SshHostKeyVerificationDeserializer`); `DeviceDtoTest` pins these defaults.
- **A record read from XML is read by Jackson, provided it is not annotated with `@XmlRootElement`.** A class annotated with it is read by Jersey's JAXB provider instead, which fails with a 500 since there is no JAXB runtime (that was the behavior of the former request classes). Request classes (and any class read as a request body) must therefore not carry it: the XML root element name of a request is ignored. The XML shape of a request is the one of the responses (wrapped lists, `type` attribute of the polymorphic bodies); the values of `Map<String, Object>` properties are strings in XML.
- A DTO is mapped from a domain record or a projection by a mapper in the same package (`DeviceMapper`, `DeviceAccessMapper`: final classes of static functions). What the DTO shows beside the record (the owner groups of a device, the description of a driver) is given to the mapper by the service or the resource; a mapper never reads the database.
- The simple class names of the former entities are part of the contract (the discriminators stored in the `type` columns, the `type` values and the XML root names the DTOs reproduce): do not rename the domain records which kept them (the task specifications of `task.type`, the rules, the credential sets...).

Webhook payloads are the DTOs of `api.hook.payload` (a sealed `TaskPayload` family, one record per task type, with the device, groups and credential sets it embeds), written by the plain providers of `WebHookSender`. They hold the same properties as the `Task` entity did when it was serialized; `HookPayloadTest` compares them with stored files. The payload never holds a secret: the CLI/HTTP credential sets only carry their common properties, and the other secrets are written as `=`.

### Task handlers

A `Task` record holds a task as stored: its header (target, schedule, status, log...) and the `TaskSpec` of its type, a sealed interface whose records in `task.type` keep the names of the former task classes (`TakeSnapshotTask`, `RunDeviceScriptTask`...); it does not run itself. While a task runs, its mutable state (status, logs, debug log, result attributes) is a `TaskRun`, saved by the engine at the end. Each task specification has one `task.TaskHandler<S>` in `task.handler` (`taskType()` and `run(Task task, S spec, TaskRun run)`). A handler sets the status, the log and the result on the run exactly as the engine expects, and takes its collaborators by constructor (`DriverRegistry`, `ComplianceChecker`, `DiagnosticAdapters`, `TaskSubmitter`, `ChildTaskOrchestrator`). `task.TaskHandlers` is the registry: it refuses to be built when a permitted subtype of `TaskSpec` has no handler (or has two), and `TaskHandlers.run(task, run)` dispatches on the type of the specification.

`TaskJob` is built by Quartz through `TaskJobFactory` (built by `Wiring`, which sets it on the `TaskManager`), which gives it the registry and `DomainEvents`; a test runs a task with `TestWiring.get().taskJobFactory().newTaskJob().runTask(id)` (`testing.TestWiring` holds the wiring built for the database tests). The handler tests (`task.handler.*HandlerTest`) use `TaskRunner` instead: the same task engine and handlers, with the follow-up tasks recorded rather than scheduled, and the child tasks of the group tasks given a terminal status rather than run; the handlers which connect to a device run against the device simulator. The group tasks (`TakeGroupSnapshotTask`, `RunGroupDiagnosticsTask`, `RunDeviceGroupScriptTask`, `ScanSubnetsTask`) delegate the pre-creation, promotion and polling of their child tasks to `task.engine.ChildTaskOrchestrator`; the pure decision functions stay in `Task.SequentialScheduling`. To add a task type: the specification record in `task.type` (a permitted subtype of `TaskSpec`), its handler, its DTO and payload (with their `@JsonSubTypes` entries), and its line in `Wiring`.

### Domain events

`platform.event.DomainEvents` is a synchronous, in-process publish/subscribe: `publish(event)` calls the listeners of the event type, in subscription order, in the publisher's thread; an exception thrown by a listener is logged and does not stop the others nor reach the publisher. A feature publishes an event of its own package (`task.event.TaskFinished(taskId, taskType)`, published by `TaskJob` once the result is saved, before the next occurrence is scheduled); a channel subscribes to it in `Wiring` (`HookDispatcher` runs the enabled `POST_TASK` hooks of the task type). `TaskJob` receives the publisher from the `TaskJobFactory`. A listener loads what it needs itself, through services: `TaskService.mapTask(id, mapper)` loads and prepares the task in a read transaction and applies the mapper inside it, so an `api` class maps the task without reaching the database.

## Safety nets

- `ApiContractTest`: golden files of every REST endpoint (`src/test/resources/api-contract/`), with `ApiContractCoverageTest` failing when a resource method has no case. A diff means the change broke the API. Regenerate with `-Dnetshot.contract.update=true` only for a deliberate API change.
- `WebHookContractTest`: golden files of the webhook payloads (`src/test/resources/hook-contract/`), regenerated with `-Dnetshot.hook.contract.update=true`. It goes through the whole path: `TaskFinished` published, `HookDispatcher`, the payload mapper and the HTTP sender, against a local receiver.
- `HookPayloadTest`: for each task type (and the credential set kinds the goldens don't reach), the payload DTO is written in JSON, XML and YAML and compared with the stored files of `src/test/resources/dto-contract/`.
- The `*DtoTest` classes (`DeviceDtoTest`, `ComplianceDtoTest`...): the representation of objects made of every kind of attribute, credential set, group, rule... (JSON and XML), and the way the requests are read (values, defaults, refused bodies), are compared with stored files under `src/test/resources/dto-contract/<test>/`. They are regenerated with `-Dnetshot.dto.contract.update=true`, only for a deliberate change. The contract goldens only cover the kinds of objects the seed data holds: add a case here for the types they don't reach.
- `DeviceServiceTest`: the use cases against a real database (error codes, update semantics, side effects), with a recording `TaskSubmitter`.
- `DatabaseMigrationTest`: schema upgrades.
- `JooqSchemaTest`: the committed jOOQ classes match the schema built by the changelog.
- `ArchitectureTest`: the dependency rules.

### Test layout

A test lives in the package of the code it tests: `inventory.device.DeviceServiceTest`, `compliance.JsRuleTest`, `api.rest.v0.device.DeviceDtoTest` and `DeviceResourceTest` (the REST resource over HTTP), `driver.CiscoIOS12DriverTest`... Write one class per subject; keep `@Nested` classes only for facets of the same subject sharing a fixture. The root package `net.netshot.netshot` holds the tests of the whole application (`ArchitectureTest`, `NetshotConfigTest`, `TestLayoutTest`); the shared helpers are in `net.netshot.netshot.testing`.

#### Kinds of tests

Each test class carries an annotation of `net.netshot.netshot.testing` giving its kind. The annotation is a JUnit tag (to select the tests to run) and the extensions setting up what the kind needs, so a test class extends nothing:

| Annotation | Tag | Uses | Set up before the class |
|---|---|---|---|
| `@UnitTest` | `unit` | the JVM only (the architecture and layout tests too) | nothing |
| `@ScriptEngineTest` | `component` | the script engines, the drivers, servers on the loopback interface | empty configuration (plus the `@TestProperty` values), module configurations, drivers |
| `@DatabaseTest` | `db` | PostgreSQL (15, the oldest supported version, unless `-Dnetshot.test.postgres=postgres:18`) | a database named after the class in a container shared by the run, copied from a template database migrated once per run (`Database.update()`), the configuration pointing at it with a temporary binary snapshot folder, then `Wiring.init(...)` (see `boot`: `MIGRATED` stops before the wiring, `MANUAL` sets up nothing, for the tests driving the schema themselves in schemas of the main database) |
| `@ApiTest` | `api` | the REST API over HTTP | as `@DatabaseTest`, plus the task manager and the REST server on a free port; the API tokens of `TestServer.API_TOKENS` replace the users around each test |
| `@ContractTest` | `contract` | golden files (`src/test/resources`) | as `@ApiTest`, but the tasks are never run (schedulers in standby), the time zone is UTC and no token is managed |
| `@SimulatorTest` | `simulator` | the device simulator containers (`dev/device-simulator`) | as `@ScriptEngineTest`; the containers are static `@Container` fields built by `DeviceSimulators` |

Add configuration properties with `@TestProperty(name = ..., value = ...)` (repeatable). The test methods, including `@BeforeAll` and `@BeforeEach`, receive what the kind provides as parameters: `TestDatabase` (database name, binary folder, a copy of the configuration of the class, configurations of schemas of the main database), `TestServer` (API URL, client for a permission level, `UNKNOWN_ID`) and an admin `NetshotApiClient`. The kinds needing Docker can be combined: a task handler test running against a device simulator is `@DatabaseTest` and `@SimulatorTest`. Only the class carrying the annotation is set up: a `@Nested` class needing its own database carries `@DatabaseTest` too (see `vault.VaultTest`).

The kinds decide where a test can run: the unit and script engine tests need no Docker, `./mvnw test -Pfast` runs them only (`-Pdocker` runs the others, `-Dgroups=db` a single kind).

#### Helpers

- `testing`: the kind annotations and their extensions, `TestDrivers` (the drivers of the resource folder, loaded once), `TestNetwork` (free ports), `DeviceSimulators`;
- `testing.fake`: the test doubles (`FakeDeviceFactory`, `FakeTaskContext`, `FakeOidcIdpServer`);
- `testing.api`: `NetshotApiClient` (REST client of the tests) and `NetshotBadRequestException` (the former exception, as a reference);
- `testing.contract`: the golden file helpers (`ApiContractSeed`, `ApiContractCases`, `ApiContractGolden`, `DtoContract`);
- `TestConfig.init` (replaces the configuration) stays in the root package, as it calls the protected `Netshot.initConfig`.

`TestLayoutTest` fails when a test class or a helper (other than `TestConfig`) is left in the root package, when two test classes share a simple name (the expected files of `DtoContract` are stored under the simple name of the class), when a test class has no kind, or a unit or script engine kind with another one, and when a unit or script engine test uses the database, the REST server, the wiring or a container (it would fail without Docker).

## Wiring

`boot.Wiring` is the composition root: it builds the long-lived objects (`Transactions`, then each repository and service, in dependency order) without side effect, and lists the [services](#configuration-and-services) of the server in start order (`Wiring.services()`). `Netshot.main` builds it with `Wiring.create(config)` right after the schema update, and hands the services to a `ServiceManager`. Tests needing a database call `Wiring.init()`, which also initializes the database access (the connection pools).

- **Adding a service** (a use case class): build it in the `Wiring` constructor, give it an accessor, and bind it in `boot.WiringBinder`.
- **REST resources** get their dependencies by injection. The dependency goes from `boot` to `api`, never the other way: `Wiring.binder()` returns the HK2 binder, given to the `RestServer` service which gives it to `NetshotWebApplication`, which registers it into Jersey. A resource declares its services in an `@Inject` constructor (`DeviceResource(DeviceService)`). A rule of `ArchitectureTest` forbids `api` to depend on `boot`.
- **Code outside Jersey** receives its dependencies explicitly from the wiring, which builds the services with them: `new SyslogServer(driverRegistry, snapshotRequester, config)`, `new SnmpTrapReceiver(...)`, `new ClusterManager(...)`, `new TaskManager(config, autoSnapshots, lockManager)`. No static facade is left: the device drivers are looked up in the injected `DriverRegistry` (`forDevice(device)`, `getDriverByName(name)`), which the handlers pass to the device scripts and helpers they create, and the automatic snapshots are scheduled through `AutoSnapshotScheduler`.
- **Descriptions of the drivers belong to the mappers.** The description of the driver of a rule, a diagnostic, a script, a discovered type or a device is computed by the mapper (`api/rest/v0`, and `TaskPayloads` for the webhooks) from the `DriverRegistry` it receives as first argument; the resources get the registry by injection and the hook dispatcher by constructor. Domain records have no accessor that looks a driver up.

Resources are discovered by Jersey package scanning of `net.netshot.netshot.api.rest` (sub-packages included): a new `@Path` class is picked up with no registration. Filters, providers and the exception mapper carry no `@Provider` annotation and are registered explicitly in `NetshotWebApplication`.

## Configuration and services

### Settings

The configuration is an immutable snapshot (`platform.config.Config`: the configuration file overridden by the environment variables, the secrets read from their files once per snapshot), held by the `ConfigStore` (`Netshot.config()`), which replaces it as a whole on a reload (`HUP` signal). A reload is all or nothing: if the configuration file or a secret file can't be read, the current snapshot is kept.

- **A setting is declared once**, as a `static final Setting<T>` field of the class which uses it, with its key, type, default value, range, whether it's a secret, and whether a reload applies it: `Setting.integer("netshot.syslog.port").defaultValue(514).range(1, 65535).restart()`. Settings read together go in a nested `Settings` record of the component, built by a `from(Config)` method (which logs the invalid values). Template keys have a placeholder: `netshot.aaa.radiusN.ip` (`SECRET.at(2)` is `netshot.aaa.radius2.secret`), `netshot.log.class.<ClassName>`.
- **Reading**: `config.get(SETTING)`, never by key (an `ArchitectureTest` rule forbids `Config.get(String)` outside `platform.config`, except in the database migrations).
  - A **live** setting is read through a binding, `config.bind(Settings::from)`: a supplier of the settings mapped from the current snapshot, mapped again once after each change (`Ssh.settings()`, `UiUser.settings()`...).
  - A **restart** setting is read once, when the component starts (`SshServer`, `RestServer`, the database pools...); a reload which changes it logs a warning.
  - A component which must act on a change (rebuild a client, restart a listener) adds a listener to the store (`ConfigStore.addListener`): the SNMP trap receiver, the OIDC discovery, the shared SSH client, the logging. The listeners are called after the bindings are refreshed, in registration order (the logging first).
- **Catalog**: `boot.NetshotSettings` lists the classes declaring settings. Building it initializes these classes, the SSH ones included: `Netshot.main` registers the crypto libraries (Bouncy Castle) before, from a first load of the configuration without catalog, because the SSH library checks the available crypto providers (EdDSA...) only once. The configuration is checked against it: the secret files of the secret settings are read when the configuration is loaded, and a warning is logged for each unknown `netshot.*` item (or `NETSHOT_*` variable). `NetshotSettingsTest` checks that every class declaring settings is listed, `ConfigurationReferenceTest` that the [configuration reference](../configuration-reference.md) documents exactly the declared settings, with the right **Reload** value.
- **Tests** replace the configuration with `TestConfig.init(properties)` (the bindings and listeners follow), and read a copy of it with `TestConfig.properties()`.

### Services

The long-running components (listeners, schedulers, background refreshers, connection pools) implement `platform.lifecycle.Service`: `start()` returns once the service runs, `stop()` once its threads are stopped and its resources released, `isHealthy()` and `stats()` give its status. `Wiring.services()` lists them in start order, leaving out the ones disabled by the configuration: database, device drivers, OIDC IdP discovery, task lock manager, cluster manager, Vault token refresher, SSH client of the device accesses, task manager (`TaskManager` itself), then the receivers (Syslog server, SNMP trap receiver, SSH server) which submit tasks, and the Web/REST server last.

- **`ServiceManager`** (built by `Netshot.main`) starts them in order; if one fails to start, the started ones are stopped in the reverse order, and Netshot exits. A JVM shutdown hook (`SIGTERM`, `SIGINT`) stops them in the reverse order; a stop failure doesn't prevent the next services from being stopped. `ServiceManager.status()` gives the state (`NEW`, `STARTING`, `RUNNING`, `STOPPING`, `STOPPED`, `FAILED`, with the error), the health and the statistics of each service (not exposed by the API yet).
- **Statistics** (`Service.stats()`, counters since the start): connections of each database pool (total, busy, idle, waiting threads), loads of the device drivers, held task locks, IdP metadata fetches, Vault logins, reads and cache, tasks started, finished (by status) and failed by a stop, cluster messages and masterships, SSH client sessions, messages and traps received and matched by the receivers, SSH logins and upload tickets, HTTP requests by status class and sessions.
- **Stopping the task manager** (`TaskManager.shutdown()`): the schedulers stop starting tasks, the running tasks get `netshot.shutdown.timeout` seconds to end, then the ones still running are marked as failed.
- **Threads** are created by executors, with `platform.concurrent.NamedThreadFactory` (named, daemon, logging the uncaught exceptions). `ArchitectureTest` forbids `extends Thread`, `new Thread(...)` outside `platform.concurrent`/`platform.lifecycle`, and the `Executors` factories without thread factory. A receive loop (Syslog server) keeps trying to open its socket with an increasing delay instead of dying, and reports itself unhealthy meanwhile.
- **No static state for the services**: the task manager, the task lock manager, the cluster manager, the SSH server, the SSH client and the RADIUS, TACACS+ and OIDC authentications are instances, built by the wiring and given to what uses them (constructor parameters, the job factory or the scheduler context for the Quartz jobs, HK2 injection for the REST layer). The task engine only knows the clustering through `ClusterCoordinator` (implemented by `ClusterManager`, `ClusterCoordinator.NONE` without clustering), so the `cluster` slice depends on `task`, not the other way round.
- **Queries**: `Finder` parses a query against the drivers given to its constructor (bound to a `ScopedValue` while the static parsing methods run), and `DynamicGroups` (built by the wiring with the drivers) refreshes the members of the dynamic groups.
- **Secrets and SSH client**: the device accesses get what they need from the server as `access.Connectors`: the `vault.SecretResolver` which resolves the Vault-backed credential fields (the `VaultManager` instance built by the wiring, with its token and secret caches) and the SSH client (`access.SshClientService`, a service of the wiring). The wiring gives them to the task handlers, which pass them to the device scripts and their `AccessManager` (`AccessManager.secrets()` and `sshClient()` for the script helpers). `Connectors.OFFLINE` resolves the local values only and has no SSH client (input definitions of the user scripts); the tests use `testing.TestConnectors`.
- **No mutable static field** (the `STATIC_FIELDS_ARE_FINAL` rule of `ArchitectureTest`), except the `Instrumentation` given by the JVM to the static agent entry point. The values read once (the hostname, the database encryption password of the jOOQ converter) are held by lazily initialized holder classes, and the wiring built for the tests by `testing.TestWiring`. The value-like helpers called from the device scripts and the entities (`Ssh`, `Telnet`, `UiUser`, `PasswordPolicy`...) read their settings through `static final` bindings to `Netshot.config()`.

## Adding or moving a v0 endpoint

This is the procedure that was followed to move every group of endpoints out of the former `RestService` (while the model was still made of Hibernate entities: read *domain record* for *entity* and `DSLContext` for `Session`), starting with `GET /devices`, `GET /devices/{id}`, `POST /devices`, `PUT /devices/{id}` and `DELETE /devices/{id}`. It also tells how to add an endpoint (skip what only concerns the removal of legacy code). Follow the steps in this order, and keep the API contract tests green at each step: **a golden file must never change** (never run the tests with the update flags to make them pass).

The files of the worked example:

| File (under `net.netshot.netshot`) | Role |
|---|---|
| `inventory/device/DeviceRepository` | queries and fetch plan |
| `inventory/device/DeviceService` | use cases, transactions, side effects |
| `task/DeviceCreationService` | the use case which adds a device (it creates the discovery or initial snapshot task) |
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
| `DeviceServiceTest`, `DeviceCreationServiceTest`, `DeviceDtoTest` (tests) | service tests, entity/DTO differential test |

### 1. Check the safety nets

The endpoints must be covered by `ApiContractTest` (JSON and XML, the error cases, the status codes: look at `_status.properties`), and `ApiContractCoverageTest` must pass. Add the missing cases *first*, on the unchanged code, to create their goldens. Read the goldens: they tell what the representation holds (this is the list of DTOs to write) and which quirks you must preserve (a 500 recorded for a XML request is a quirk, see [DTO conventions](#dto-conventions)). Also list which other code serializes the entities you are about to replace by DTOs (other endpoints, `WebHookContractTest` for the webhook payloads): nothing else must write them any more (step 10).

### 2. Read the legacy code like a specification

For each endpoint note the status codes and the way they are chosen (`suggestReturnCode`), the error codes and the order in which the checks happen, the transaction boundaries (what is read in which session), the side effects after the commit, the audit log lines (`AAA` logger), the quirks (swallowed exceptions, fallbacks). The migrated code must do the same, in the same order. Mark the helpers that other endpoints use too.

### 3. Repository

`<feature>/<Entity>Repository`: one method per query, `DSLContext` first, no transaction, no exception handling. For a query building a light object, project into a record of the feature package (`DeviceSummary`), *never* an `api` type. Give each list query an `order by` (a unique one: end with the ID), so that the order and the pages are defined.

### 4. Commands and service

`<feature>/<Entity>Service`, constructed with `Transactions`, the repository and the ports it needs (see [Repositories and services](#repositories-and-services)). One public method per endpoint. Commands are records (`DeviceCreation`); the author of a change (user name) is a component of the command, since the service knows nothing of the security context. Copy the legacy logic *including its order*; replace `NetshotBadRequestException` by `NetshotException` with the same `ErrorCode` and message; make each unit of work one `Transactions` call; put the side effects after it. Move the validation helpers the use cases need to a `<Entity>Validation` class (static, throwing `NetshotException`); if other code still has its own copy of one, delete the copy and call the new one. A service method never receives a `DSLContext`, a DTO or a JAX-RS type.

### 5. Wiring

In `boot.Wiring.init()` build the repository and the service (a `new` chain, dependencies first), add an accessor, and bind the service in `boot.WiringBinder`.

### 6. Service tests

Write `<Entity>ServiceTest` in the package of the service, annotated `@DatabaseTest` (see `inventory.device.DeviceServiceTest`: its own database, migrated and wired by the extension, the service built in `@BeforeAll` with the transaction support of the test wiring, `TestWiring.get().transactions()`, its repository and a recording `TaskSubmitter`; see [Test layout](#test-layout)). Cover each error code with its trigger, the order of the checks, the semantics of the null components, that a failing use case leaves nothing behind, and the side effects. This is where the logic of the use cases is tested; the contract tests only prove that the HTTP surface did not move.

### 7. DTOs and mapper

In `api.rest.v0.<feature>`, following the [DTO conventions](#dto-conventions):

- one record per shape of the goldens, with the components named like the properties (the property names are the contract; the order does not matter, the goldens are normalized). A representation made of several nested types needs one record per nested type: let the golden of every case (and the entity annotations, for the cases the seed data doesn't hold) tell what is there;
- the full device is `DeviceDto` with `@JacksonXmlRootElement(localName = "Device")` (the XML root element is the simple class name otherwise); a summary is a separate record, not a subclass;
- the request records replace the request classes: same property names, same defaults (compact constructor or deserializer, see the conventions), without `@XmlRootElement` (see above);
- the mapper: `toDto(...)` from the domain record or the projection, `to<Command>(request, author)` for the requests. Compare against the domain records, not only against the goldens: write the DTO test (`DeviceDtoTest`) building an in-memory record with every variant, mapping it to its DTO, writing it with a plain `JsonMapper`/`XmlMapper` and comparing the canonical form (`ApiContractGolden`) with a stored file (`DtoContract`).

### 8. Resource

A class in `api.rest.v0.<feature>` annotated `@Path("/")`, with the **full path on each method**, and the service injected by an `@Inject` constructor. Copy for every method: `@RolesAllowed`, `@Consumes`/`@Produces`, `@Operation`, `@Tag`, `@Parameter` (also on the parameters the method ignores, like `details` of `GET /devices`) and the method *name* (it is the OpenAPI `operationId`). Declare concrete generic return types (`List<DeviceSummaryDto>`). The resource only: reads the principal (`SecurityContext`) for the author, builds the command, calls the service, maps the result, and chooses a status code other than 200 with `ResponseCodeFilter.suggest(request, status)`. It has no `DSLContext`, no repository, no `try`/`catch` of its own (a `NetshotException` is rendered by the exception mapper exactly like a `NetshotBadRequestException`). It answers with DTOs only.

!!! warning "Do not give the new resource a narrower @Path than the paths it takes over"
    JAX-RS selects the root resource class *before* looking at the methods, with no fallback. A new class annotated `@Path("/devices")` hides every method of the other resources under `/devices/...` (they answer 404 or 405). Use `@Path("/")` with the full path on each method like the existing resources do (several root resources with the same `@Path("/")` and different method paths work fine), or move a whole path prefix at once. Declaring the same method and path in both classes fails at startup.

### 9. Remove the legacy code

In the same change, delete the migrated methods from where they were, the request classes, and every helper nothing uses anymore (the compiler and `checkstyle:check` report the unused imports). Keep what other endpoints still use. Classes only the tests still need (a former response class, the former exception) move to the test tree, as the reference the new code is compared with.

### 10. Domain records

Domain records carry no `@JsonView`, JAXB or Jackson annotation: do not add any (`ArchitectureTest` refuses `@JsonView` and JAXB outside the API channels and the cluster wire format). If a new endpoint or payload needs a representation, write a DTO and a mapper.

### 11. Run the checks

`./mvnw checkstyle:check`, then `ApiContractTest`, `ApiContractCoverageTest`, `WebHookContractTest`, your two new test classes, `ArchitectureTest`, and finally the whole `./mvnw test`. For `ArchitectureTest`: the `NO_CYCLES_BETWEEN_FEATURE_SLICES` rule fails on any dependency of the new classes which closes a cycle between top-level packages; place the classes according to the layering described in [Dependency rules](#dependency-rules).

### 12. Document

Update [REST API](../api/rest-api.md) if the behavior changed (it should not), this page if a convention changed or a pitfall was found, and report what remains: anything deliberately preserved (the XML request quirk).

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
- **Runtimes read their settings through bindings.** `JsRuleRuntime` and `PyRuleRuntime` get the configuration store in their constructor and bind their settings (`netshot.javascript.maxexecutiontime`, `netshot.python.*`), so a configuration reload applies them (see [Configuration and services](#configuration-and-services)).

- **A handler works on the task, not on a copy.** `TaskJob` saves what the handler wrote on the task (`copyResultsTo`), so a handler keeps writing the status and the log on the `Task` it receives, and reads the entity fields through its getters (the fields stay `protected`). When the handler replaces the device by a fresh one loaded from the database, it also calls `task.setDevice(...)`.
- **Settings belong to the component that uses them.** `netshot.snapshots.auto.*` is declared and read by `AutoSnapshotScheduler` (not by the task). The syslog server, the SNMP trap receiver and the cluster manager receive the scheduler from the wiring.

### Pitfalls met on the inventory slice

- **The class of a list is part of the XML.** A query with a collection fetch and a page (`GET /devices/{id}/configs?limit=...`) is paginated in memory by Hibernate, which answers a sub list: XML publishes it as `<SubList>` instead of `<ArrayList>`. The mappers keep that (`ConfigMapper.toDtos`) by answering a sub list when the service result is not a plain `ArrayList`.
- **A download is not a JSON error.** `GET /configs/{id}/{item}` answers its errors with bare `WebApplicationException` statuses (no body), not with an `ErrorCode`. The service throws a feature exception (`ConfigItemException`, with a reason) and the resource turns it into the same statuses.
- **Two shapes for one entity.** A domain is `DomainDto` when nested (complete) and `DomainSummaryDto` when served by `/domains` (id, name, description and IPv4 address only). Check the golden of each endpoint before reusing a DTO.
- **A dynamic group is evaluated inside the transaction.** Creating or editing one runs its query (`refreshCache`) before the commit, and a failing query rolls everything back; the order of the checks (devices or query before the name, on edit) is observable.
- **Read-only sessions.** `POST /devices/search` has always used a read-write session; it is kept so (`Transactions.write`), not moved to the read pool.

### Pitfalls met on the credential set, Vault instance and hook slices

- **Secrets stay masked, and unchanged means null.** `HideSecretSerializer` writes `=` for any non-null secret (a missing one stays `null`) and `HideSecretDeserializer` reads `=` as `null`. In the services a `null` secret means "keep the stored value" for the passwords, communities, keys and AppRole/JWT secrets, but not for the username (cleared when not given) nor for the private key of a SSH key account, which is wiped when it is not given (a quirk of the legacy code, kept). The DTO tests assert that no secret value appears in the JSON, XML or YAML of any kind, and that every secret of every kind is read as `null` for `=`.
- **Do not replace the duplicate rule.** These use cases recognize a duplicate name by the message of the cause of the Hibernate exception ("uplicate"), and a missing domain by the column name in it: `DatabaseErrors.causeMentions` keeps that rule. `Database.isDuplicateException` is not equivalent (it takes any constraint violation, a foreign key for instance, for a duplicate). Once migrated to jOOQ, the same cases are recognized by the SQL state of a unique violation (`DatabaseErrors.isUniqueViolation`, `23505`), which also doesn't depend on the language of the messages of the server.
- **Known but ignored properties.** The entities have properties which are not part of the representation (`version`, `changeDate`, the `...Secret` bundles of the credential sets): they are silently skipped when a request holds them, whereas a really unknown property is refused. A request record or bean lists them in `@JsonIgnoreProperties` to keep that. Find them by introspecting the entity, and store what a matrix of bodies is read into (`CredentialSetDtoTest`).
- **A XML root element per record.** The XML of a single credential set (`POST`, `PUT`) is rooted at the class name of the entity, whereas a list uses `item`: every record of a polymorphic family needs its `@JacksonXmlRootElement` (`DeviceSshAccount`, `HashicorpVaultKv2Instance`, `WebHook`...). The contract goldens only had JSON for these writes.
- **A request with defaults stays a bean, here too.** The Vault instance (mount paths, CA trust mode) and the hook (`enabled`, the triggers, the CA trust mode) requests are beans with initialized fields: absent gives the default, explicit `null` gives `null` (`enabled: null` is `false`; `triggers: null` is accepted when creating a hook, and makes the update fail, as it did).
- **The order of the triggers means nothing.** `HookTrigger.hashCode()` uses the identity hash of an enum, so the iteration order of a set of triggers changes from one JVM to the next: compare triggers as a set. The request keeps them in a list, to store them in the order of the request.
- **Side effects through ports.** The cache invalidation and cluster notification of a Vault instance change (`VaultInstanceChangeNotifier`) and the login attempt of the connection test (`VaultConnectionTester`) are ports given by `Wiring`, so that the service tests can record and fake them.
- **Dead error paths.** Deleting a credential set "in use" is practically unreachable now: the foreign keys of the device accesses and of the tasks cascade or set null. Deleting a Vault instance referenced by a credential field is the one which still fails (`NETSHOT_VAULT_INSTANCE_IN_USE`). The service tests inject a failing repository to cover the translation of the constraint violation.
- **A throwaway HTTP probe finds what the goldens miss.** Before moving a group, send a few hundred requests (every kind, absent/null/`=` secrets, refused bodies, XML and JSON, paged lists) to the unchanged server, record the answers, and compare them with the ones of the migrated server (ignoring the order of the keys and elements). That is how the XML root names above were found.

### Pitfalls met on the first jOOQ slices (hooks, users and API tokens)

- **Regenerate first.** The generator (`-Pjooq-codegen`) runs from the compiled project: change the changelog, regenerate the jOOQ classes while the code still compiles (stash the code changes if needed), then adapt the code to the new classes.
- **An order which was none.** A Hibernate query without `order by` answered in the order the database chose, which the goldens recorded (`GET /hooks` gave the hooks 2 then 1). A jOOQ list query is ordered explicitly (by ID): update the goldens of the lists whose order changes, and note it in the upgrade notes.
- **Keep the quirks the goldens hold.** A paginated Hibernate query with a collection fetch answered a `SubList`, which XML publishes as `<SubList>`: the repository returns a sub list when the page restricts the result. The audit log describes a hook as `Hook [id=..., name=...]`: the record overrides `toString()` (which would otherwise also print the URL, possibly with credentials).
- **Records have no identity, and their own way to fail.** A service returned the very object it was given (the update definition) and the tests checked it with `assertSame`: compare with `equals`. A null trigger in a request used to fail with a `NullPointerException` before the validation (create) or after the lookup (update): the service reproduces it at the same place, since the order of the checks decides the answer.
- **Describe records by their components.** The stored descriptions of the DTO tests were built from the getters of the entities: describe the records from their components (same names), so that the stored files prove that the requests are still read the same way.
- **Run the nested classes.** `-Dtest=DatabaseMigrationTest` runs none of its `@Nested` migration tests: use `-Dtest='DatabaseMigrationTest*'`. And after changing an API used by the tests, build from clean (`./mvnw clean test-compile`): the incremental build doesn't recompile the unchanged test classes.
- **Test the data migration.** A changeset which reshapes a table (here `hook` + `web_hook` into `hook`, the ordinals into names) gets its `DatabaseMigrationTest` case: stop before it, insert rows in the former shape, resume and check.
- **Rename the getters with a tool.** A record answers `username()`, not `getUsername()`: rename the getters of the entities to the component names with OpenRewrite (`org.openrewrite.java.ChangeMethodName`, with `matchOverrides` on the methods of an interface such as `User`) before turning the classes into records. It is type-aware, so the getters of the DTOs and other classes with the same name are left alone.
- **A record changed is a new record.** The setters become `with...` methods, the password changes included (`withPassword(password, policy)` returns the user with the new hash and history): store what they return. A service which changed the object it was given (the level of a remote user overridden by its local entry) now returns a copy, which the tests compare with `equals`. The user kept in the HTTP session is the record read at login, as the detached entity was.
- **Keep secrets out of `toString()`.** The default `toString()` of a record prints all its components: `UiUser` and `ApiToken` override it, as the entities did, so that the audit log doesn't get the password and token hashes.
