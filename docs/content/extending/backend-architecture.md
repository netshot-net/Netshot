# Backend architecture

This page is for developers working on the Netshot core server (Java). It describes the target layering the code base is being migrated to, the rules that protect it, and the conventions to follow when adding or moving code. For the map of the components, see [Architecture](../architecture.md).

!!! note "Work in progress"
    The REST API is being moved out of the monolithic `RestService` class, one feature at a time. Until that is finished both worlds coexist: old code is still in `api.rest.v0.RestService`, new code follows this page. The last section is a step-by-step guide to migrate an endpoint, with the device endpoints (`/devices`) as the worked example: they are the reference slice, copy it.

## Layers and packages

Everything lives under `net.netshot.netshot`.

```text
Netshot            entry point only (main)
boot/              composition root (Wiring), persistent model (EntityModel)
platform/          technical building blocks; depends on no feature
├── database/      Hibernate set-up, Database, Transactions, Liquibase migrators
├── error/         ErrorCode, NetshotException
├── crypto/ tls/ rest/
api/               API channels: how the outside world talks to the core
├── rest/          Jersey application, filters, exception mapper, shared REST bits
│   └── v0/        the current (legacy, unversioned) REST API, served on /api
├── hook/          (later) webhook payloads, mappers and HTTP sender
└── grpc/          (later)
inventory/ driver/ access/ credentials/ vault/ devicescript/ userscript/
compliance/ diagnostic/ task/ report/ hook/ aaa/ cluster/ system/ tools/
                   features: entities, repositories, services, engines
```

- **Feature packages** (`inventory`, `task`, `compliance`...) hold the entities (single JPA model, no separate domain model), and over time the repositories and services of the feature.
- **Entities** carry the JPA mapping and the invariants on their own state only. No `Session`, no I/O, no static registry lookup, and (end state) no Jackson/JAXB annotation.
- **Repositories** contain queries only; the `Session` is passed explicitly as first parameter.
- **Services** implement the use cases and own the transaction boundaries (see [Transactions](#transactions)). They throw neutral exceptions (see [Errors](#errors)), and know nothing of HTTP.
- **`api.*`** are the API channels. Each channel owns its DTOs, even when two channels have similar ones (the REST task representation is not the webhook task payload). A channel translates between the wire format and the services. `api.rest` and `api.hook` do not depend on each other; they share services and entities, not DTOs.
- **`platform`** knows nothing about the features. The persistent model (the list of entity classes) is therefore given to `Database.init()` by `boot`.
- **`boot`** is the only place where everything is assembled.

The REST API must stay byte-for-byte equivalent (JSON, XML, YAML) while it is restructured.

## Dependency rules

The rules are enforced by `ArchitectureTest` (ArchUnit): `platform` depends on no feature; only `boot` (and `Netshot`) depend on `api`, and `api` does not depend on `boot`; `api.rest` and `api.hook` do not depend on each other; JAX-RS server classes are used only by the REST layer; entities do not use `Session`, `Database`, `@JsonView` or JAXB annotations; and there is no cycle between top-level packages.

The rules are *frozen*: violations that existed when the rules were introduced are recorded in `src/test/resources/archunit_store/` and tolerated, only new ones fail. The store must only shrink. After a planned move or a cleanup, refreeze and review the diff, which must only remove entries (or follow renames):

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

Legacy `RestService` code still throws `NetshotBadRequestException(message, ErrorCode)`, which is a JAX-RS `WebApplicationException`: services must not use it. To detect a duplicate key on persist, use `Database.isDuplicateException()`.

A helper moved out of `RestService` to be shared (for example `credentials.CredentialSets`) throws `NetshotException` instead. Mind the legacy callers: a method which catches `NetshotBadRequestException` to roll back its transaction has to catch `NetshotException` as well (`catch (NetshotBadRequestException | NetshotException e)`).

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
- **Validation** (`DeviceValidation`) is made of static, side-effect free functions which throw `NetshotException`. Other callers (the legacy `RestService`) call them at their new location.

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

DTOs are the types written to and read from the wire by an API channel: Java records, mapped from entities (or projections) by mappers in the same channel package. These facts hold today (they are verified by `DtoSerializationProbeTest`, with Jackson 2.22 and swagger-core 2.2):

- The providers use the default view `RestApiView` with `DEFAULT_VIEW_INCLUSION` disabled, so a plain record serializes as `{}`. During the transition every DTO record carries a class-level `@JsonView(DefaultView.class)`, including the outer record of an `@JsonUnwrapped` composition (the view on the inner record is not enough).
- `@XmlRootElement`, `@XmlElement` and `@XmlAccessorType` are no-ops with the real providers (the custom mapper has no XML-bind introspector). The XML root element is the simple class name: a DTO whose name differs from today's class or entity needs `@JacksonXmlRootElement(localName = "<old name>")` (e.g. `DeviceDto` is published as `Device`).
- A "light" type that used to be a subclass of the full type is a separate record composing the common part with `@JsonUnwrapped` (JSON and YAML are byte-identical, XML identical given the root element name).
- Polymorphic DTOs use `@JsonTypeInfo(use = Id.NAME, property = "type")` with explicit `@JsonSubTypes.Type(name = "<old simple name>")`, never `SIMPLE_NAME`. List endpoints must declare the concrete generic type (`List<TaskDto>`, not `List<Object>`), otherwise the type id is lost.
- `Date` values are serialized as epoch milliseconds, like on the entities.
- OpenAPI: resource methods keep `@JsonView(RestApiView.class)` during the transition so that component schema names keep the `_RestApiView` suffix; this will only change in a later, documented step.
- **Nested polymorphic types** (device attributes, credential sets, groups) are a `sealed interface` with the `@JsonTypeInfo`/`@JsonSubTypes` annotations and its subtypes as nested records (`DeviceAttributeDto.Text`, `CredentialSetDto.Ssh`...). The `type` property (an attribute in XML) keeps today's values, written explicitly in `@JsonSubTypes.Type(name = ...)`.
- **Request records** (`NewDeviceRequest`, `UpdateDeviceRequest`, `DeviceAccessDto`) read exactly the JSON the former request classes read: same property names, and an unknown property is refused (a record is strict like the beans were). `@JsonUnwrapped` doesn't work on the properties which are *read*, so a shape which is both read and written (`CredentialSetDto`) repeats the common components in each subtype. The default a bean field had when the property was absent is applied in a compact constructor. When an absent property and an explicit `null` must differ (as they did for a primitive or for a field with an initial value), use a `JsonDeserializer` overriding `getAbsentValue()` and `getNullValue()` (see `DeviceAccessDto.SshHostKeyVerificationDeserializer`); `DeviceDtoTest` pins these defaults.
- **A record read from XML is read by Jackson.** A class annotated with `@XmlRootElement` is read by Jersey's JAXB provider instead, which fails with a 500 since there is no JAXB runtime. That is how the former request classes behaved (the contract goldens record the 500 of `POST /devices` and `POST /domains` in XML), and it is what `@XmlRootElement(name = "device")` on `NewDeviceRequest` and `UpdateDeviceRequest` keeps for now. Removing it is a deliberate fix of the API: it turns these XML requests into working ones (and changes their goldens).
- A DTO is mapped from a *detached* entity or a projection by a mapper in the same package (`DeviceMapper`, `DeviceAccessMapper`: final classes of static functions). The mapper only touches what the fetch plan of the service loaded: a `LazyInitializationException` means the plan is incomplete, not that the mapper should open a session. Use `Hibernate.unproxy()` before an `instanceof` on an association, as it can hold a proxy.
- Entity simple class names are part of the contract (HQL entity names, `JsonTypeInfo.Id.SIMPLE_NAME` on `Task`, `Rule` and `Diagnostic`, default discriminators): do not rename entities.

Webhook payloads serialize the `Task` entities (including `Device`) with `HookView`, through the providers configured in `WebHook`. As long as they do, the JSON/XML annotations stay on the entities involved.

## Safety nets

- `ApiContractTest`: golden files of every REST endpoint (`src/test/resources/api-contract/`), with `ApiContractCoverageTest` failing when a resource method has no case. A diff means the change broke the API. Regenerate with `-Dnetshot.contract.update=true` only for a deliberate API change.
- `WebHookContractTest`: golden files of the webhook payloads (`src/test/resources/hook-contract/`), regenerated with `-Dnetshot.hook.contract.update=true`.
- `DtoSerializationProbeTest`: the serialization facts above.
- `DeviceDtoTest`: for the device slice, the representation of a device made of every kind of attribute, credential set and group is the same through the entities and through the DTOs (JSON and XML), and the requests are read like the former classes did. The contract goldens only cover the kinds of objects the seed data holds: do the same kind of differential test for the types the goldens don't reach.
- `DeviceServiceTest`: the use cases against a real database (error codes, update semantics, side effects), with a recording `TaskSubmitter`.
- `DatabaseMigrationTest`: schema upgrades.
- `ArchitectureTest`: the dependency rules.

## Wiring

`boot.Wiring` is the composition root. `Wiring.init()` initializes the database access (with the persistent model from `boot.EntityModel`) and builds the long-lived objects: `Transactions`, then each repository and service (today `DeviceRepository` and `DeviceService`), in dependency order. `Netshot.main` calls it right after the schema update; tests needing a database call the same method.

- **Adding an entity**: register it in `boot.EntityModel` (task, rule and diagnostic subclasses are discovered by scanning).
- **Adding a service**: build it in `Wiring.init()`, give it an accessor, and bind it in `boot.WiringBinder`.
- **REST resources** get their dependencies by injection. The dependency goes from `boot` to `api`, never the other way: `Wiring.binder()` returns the HK2 binder, `Netshot.main` hands it to `RestService.init(binder)` which gives it to `NetshotWebApplication`, which registers it into Jersey. A resource declares its services in an `@Inject` constructor (`DeviceResource(DeviceService)`). A rule of `ArchitectureTest` forbids `api` to depend on `boot`.
- **Code outside Jersey** (Quartz jobs, cluster code) can reach the wiring through the static `Wiring.get()`. This accessor is **transitional**: it is a service locator that hides dependencies, kept only until such code gets its dependencies explicitly. Do not use it from code that can be injected.

Resources are discovered by Jersey package scanning of `net.netshot.netshot.api.rest` (sub-packages included): a new `@Path` class is picked up with no registration. Filters, providers and the exception mapper carry no `@Provider` annotation and are registered explicitly in `NetshotWebApplication`.

## Migrating a v0 endpoint out of RestService

This is the procedure to move a group of endpoints out of `RestService`, as it was done for `GET /devices`, `GET /devices/{id}`, `POST /devices`, `PUT /devices/{id}` and `DELETE /devices/{id}`. Follow the steps in this order, and keep the API contract tests green at each step: **a golden file must never change** (never run the tests with the update flags to make them pass).

The files of the worked example:

| File (under `net.netshot.netshot`) | Role |
|---|---|
| `inventory/device/DeviceRepository` | queries and fetch plan |
| `inventory/device/DeviceService` | use cases, transactions, side effects |
| `inventory/device/DeviceCreation`, `DeviceUpdate` | commands (inputs of the use cases) |
| `inventory/device/DeviceSummary` | projection of the list query |
| `inventory/device/DeviceValidation` | validation helpers moved out of `RestService` |
| `credentials/CredentialSets`, `platform/tls/CaCertificates`, `platform/database/Page` | helpers moved or created because `RestService` and the service share them |
| `task/TaskSubmitter` | port to the task engine |
| `boot/Wiring`, `boot/WiringBinder` | construction and injection |
| `api/rest/v0/device/DeviceResource` | the JAX-RS resource |
| `api/rest/v0/device/DeviceDto`, `DeviceSummaryDto`, `NewDeviceRequest`, `UpdateDeviceRequest` | the DTOs of the endpoints |
| `api/rest/v0/device/DeviceAccessDto`, `CredentialSetDto`, `DeviceAttributeDto`, `DeviceGroupDto`, `DomainDto`, `ModuleDto` | the DTOs of the nested types |
| `api/rest/v0/device/DeviceMapper`, `DeviceAccessMapper` | entity/projection to DTO, request to command |
| `DeviceServiceTest`, `DeviceDtoTest` (tests) | service tests, entity/DTO differential test |

### 1. Check the safety nets

The endpoints must be covered by `ApiContractTest` (JSON and XML, the error cases, the status codes: look at `_status.properties`), and `ApiContractCoverageTest` must pass. Add the missing cases *first*, on the unchanged code, to create their goldens. Read the goldens: they tell what the representation holds (this is the list of DTOs to write) and which quirks you must preserve (a 500 recorded for a XML request is a quirk, see [DTO conventions](#dto-conventions)). Also list which other code serializes the entities you are about to replace by DTOs (other endpoints, `WebHookContractTest` for the webhook payloads): their annotations stay (step 10).

### 2. Read the legacy code like a specification

For each endpoint note the status codes and the way they are chosen (`suggestReturnCode`), the error codes and the order in which the checks happen, the transaction boundaries (what is read in which session), the side effects after the commit, the audit log lines (`AAA` logger), the quirks (swallowed exceptions, fallbacks). The migrated code must do the same, in the same order. Mark the helpers that other endpoints use too.

### 3. Repository

`<feature>/<Entity>Repository`: one method per query, `Session` first, no transaction, no exception handling. For a query building a light object (`select new ...`), project into a record of the feature package (`DeviceSummary`), *never* an `api` type, and use the fully qualified name in the HQL (`DeviceSummary.class.getName()`). For a query whose result is serialized, write the fetch plan in the Javadoc and implement it explicitly (`findWithDetails`): this replaces the `.size()` calls the legacy code made to initialize collections before closing the session.

### 4. Commands and service

`<feature>/<Entity>Service`, constructed with `Transactions`, the repository and the ports it needs (see [Repositories and services](#repositories-and-services)). One public method per endpoint. Commands are records (`DeviceCreation`); the author of a change (user name) is a component of the command, since the service knows nothing of the security context. Copy the legacy logic *including its order*; replace `NetshotBadRequestException` by `NetshotException` with the same `ErrorCode` and message; replace each `Database.getSession()` block by one `Transactions` call; put the side effects after it. Move the validation helpers the use cases need to a `<Entity>Validation` class (static, throwing `NetshotException`); if `RestService` still uses one, delete its copy and call the new one (mind the `catch` blocks, see [Errors](#errors)). A service method never receives `Session`, a DTO or a JAX-RS type.

### 5. Wiring

In `boot.Wiring.init()` build the repository and the service (a `new` chain, dependencies first), add an accessor, and bind the service in `boot.WiringBinder`.

### 6. Service tests

Write `<Entity>ServiceTest` extending `WithDatabaseTest` (see `DeviceServiceTest`: own schema, `Netshot.initConfig`, `DeviceDriver.refreshDrivers()`, `Database.update()`, `Wiring.init()`, a recording `TaskSubmitter`). Cover each error code with its trigger, the order of the checks, the semantics of the null components, that a failing use case leaves nothing behind, the side effects, and that what the service returns can be read without a session. This is where the logic of the use cases is tested; the contract tests only prove that the HTTP surface did not move.

### 7. DTOs and mapper

In `api.rest.v0.<feature>`, following the [DTO conventions](#dto-conventions):

- one record per shape of the goldens, with the components named like the properties (the property names are the contract; the order does not matter, the goldens are normalized). A representation made of several nested types needs one record per nested type: let the golden of every case (and the entity annotations, for the cases the seed data doesn't hold) tell what is there;
- the full device is `DeviceDto` with `@JacksonXmlRootElement(localName = "Device")` (the XML root element is the simple class name otherwise); a summary is a separate record, not a subclass;
- `@JsonView(DefaultView.class)` on *every* record, nested ones included (without it a record serializes as `{}` or `<Name/>`);
- the request records replace the request classes: same property names, same defaults (compact constructor or deserializer, see the conventions), `@XmlRootElement` kept while the goldens record a 500 for the XML request;
- the mapper: `toDto(...)` from the detached entity or the projection, `to<Command>(request, author)` for the requests. Compare against the entities, not only against the goldens: write the differential test (`DeviceDtoTest`) building an in-memory entity with every variant, serializing it and its DTO with the providers' mapper configuration (`JsonMapper`/`XmlMapper` with `DEFAULT_VIEW_INCLUSION` disabled, `writerWithView(RestApiView.class)`), and comparing the canonical forms of `ApiContractGolden`.

### 8. Resource

A class in `api.rest.v0.<feature>` annotated `@Path("/")`, with the **full path on each method**, and the service injected by an `@Inject` constructor. Copy for every method: `@RolesAllowed`, `@Consumes`/`@Produces`, `@Operation`, `@Tag`, `@Parameter` (also on the parameters the method ignores, like `details` of `GET /devices`), the method *name* (it is the OpenAPI `operationId`), and `@JsonView(RestApiView.class)` (it keeps the schema names of the OpenAPI document). Declare concrete generic return types (`List<DeviceSummaryDto>`). The resource only: reads the principal (`SecurityContext`) for the author, builds the command, calls the service, maps the result, and chooses a status code other than 200 with `ResponseCodeFilter.suggest(request, status)`. It has no `Session`, no repository, no `try`/`catch` of its own (a `NetshotException` is rendered by the exception mapper exactly like a `NetshotBadRequestException`). An endpoint which answers with an entity not migrated yet (here the `Task` of `POST /devices`) keeps returning the entity, with a comment: a later wave replaces it.

!!! warning "Do not give the new resource a narrower @Path than the paths it takes over"
    JAX-RS selects the root resource class *before* looking at the methods, with no fallback. A new class annotated `@Path("/devices")` hides every `RestService` method under `/devices/...` (they answer 404 or 405), even those that were not moved. Use `@Path("/")` with the full path on each method like `RestService` does (several root resources with the same `@Path("/")` and different method paths work fine), or move a whole path prefix at once. Declaring the same method and path in both classes fails at startup.

### 9. Remove the legacy code

In the same change, delete the migrated methods of `RestService`, the request classes, and every helper nothing uses anymore (the compiler and `checkstyle:check` report the unused imports). Keep what other endpoints still use: for example `RsLightDevice` is the base class of other response classes, and stays until their own migration.

### 10. Entities

Do not remove `@JsonView`/JAXB annotations from an entity: webhook payloads (`HookView`) and other endpoints still serialize `Device`, `Module`, `Domain`, the credential sets, the groups... The annotations go once nothing serializes the entity (Wave 5).

### 11. Run the checks

`./mvnw checkstyle:check`, then `ApiContractTest`, `ApiContractCoverageTest`, `WebHookContractTest`, `DtoSerializationProbeTest`, your two new test classes, `ArchitectureTest`, and finally the whole `./mvnw test`. For `ArchitectureTest`: the `apiDoesNotUseSession` entries of the old code disappear and the store only shrinks for this rule. The `noCyclesBetweenFeatureSlices` rule, on the other hand, gets one entry per dependency of each new class of the feature on the other slices of the entity model (`inventory` -> `access`, `credentials`, `driver`, `task`, `compliance`...): those slices form one big cycle through the entities, so any class of the entity model that uses another one is counted, until the entities are decoupled. Refreeze and check that the other rules neither grow nor get a new entry:

```bash
./mvnw test -Dtest=ArchitectureTest -DargLine=-Darchunit.freeze.refreeze=true
git diff --stat src/test/resources/archunit_store
```

The refreeze rewrites the line numbers of the stored entries; compare the entries without them (they are ignored when matching).

### 12. Document

Update [REST API](../api/rest-api.md) if the behavior changed (it should not), this page if a convention changed or a pitfall was found, and report what remains: other places which serialize the entities, entities returned by the migrated endpoints (here `Task`), anything deliberately preserved (the XML request quirk).

### Pitfalls met on the device slice

- **Lazy loading.** The entity is serialized after the session is closed: anything the DTO reads must be in the fetch plan. `Device.getOwnerGroups()` is computed from the memberships, `isCompliant()` from the check results, `getRealDeviceType()` from the driver registry: read the *getters* the entity serialization called, not only the fields.
- **XML root names.** A record is published under its class name (`DeviceDto`): `@JacksonXmlRootElement(localName = "Device")`.
- **Views.** A record without `@JsonView(DefaultView.class)` is empty; the view of the resource method must stay `RestApiView`.
- **`@Path` shadowing.** See the warning above.
- **Polymorphic attributes and credentials.** `type` values are the contract; the list endpoints need the concrete generic type.
- **Request defaults.** A bean field initialized to a value, or a primitive, behaves differently from a record component on an absent property (`autoDiscover` was true when absent, `sshHostKeyVerification` was `TRUST_ANY` when absent but `TRUST_KNOWN` when null). The contract goldens caught one of them: write the test for each.
- **Status codes.** `DELETE` answered 204, or 404 with an empty body when the device does not exist, through `suggestReturnCode`: the service tells (`delete` returns a boolean), the resource chooses the status.
- **Swallowed exceptions.** The legacy `deleteDevice` ended with `catch (Exception e) { log.error(...); }` (answering 204). The service does not reproduce it: an unexpected exception is a 500.
