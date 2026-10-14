# Backend architecture

This page is for developers working on the Netshot core server (Java). It describes the target layering the code base is being migrated to, the rules that protect it, and the conventions to follow when adding or moving code. For the map of the components, see [Architecture](../architecture.md).

!!! note "Work in progress"
    The REST API is being moved out of the monolithic `RestService` class, one feature at a time. Until that is finished both worlds coexist: old code is still in `api.rest.v0.RestService`, new code follows this page. The checklist at the end tells how to migrate an endpoint.

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
compliance/ diagnostic/ task/ report/ hook/ aaa/ cluster/ tools/
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

The rules are enforced by `ArchitectureTest` (ArchUnit): `platform` depends on no feature; only `boot` (and `Netshot`) depend on `api`; `api.rest` and `api.hook` do not depend on each other; JAX-RS server classes are used only by the REST layer; entities do not use `Session`, `Database`, `@JsonView` or JAXB annotations; and there is no cycle between top-level packages.

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

When adding an error: add the `ErrorCode` (never change an existing numeric value or status; the web UI and the API clients depend on them).

## DTO conventions

DTOs are the types written to and read from the wire by an API channel: Java records, mapped from entities (or projections) by mappers in the same channel package. These facts hold today (they are verified by `DtoSerializationProbeTest`, with Jackson 2.22 and swagger-core 2.2):

- The providers use the default view `RestApiView` with `DEFAULT_VIEW_INCLUSION` disabled, so a plain record serializes as `{}`. During the transition every DTO record carries a class-level `@JsonView(DefaultView.class)`, including the outer record of an `@JsonUnwrapped` composition (the view on the inner record is not enough).
- `@XmlRootElement`, `@XmlElement` and `@XmlAccessorType` are no-ops with the real providers (the custom mapper has no XML-bind introspector). The XML root element is the simple class name: a DTO whose name differs from today's class or entity needs `@JacksonXmlRootElement(localName = "<old name>")` (e.g. `DeviceDto` is published as `Device`).
- A "light" type that used to be a subclass of the full type is a separate record composing the common part with `@JsonUnwrapped` (JSON and YAML are byte-identical, XML identical given the root element name).
- Polymorphic DTOs use `@JsonTypeInfo(use = Id.NAME, property = "type")` with explicit `@JsonSubTypes.Type(name = "<old simple name>")`, never `SIMPLE_NAME`. List endpoints must declare the concrete generic type (`List<TaskDto>`, not `List<Object>`), otherwise the type id is lost.
- `Date` values are serialized as epoch milliseconds, like on the entities.
- OpenAPI: resource methods keep `@JsonView(RestApiView.class)` during the transition so that component schema names keep the `_RestApiView` suffix; this will only change in a later, documented step.
- Entity simple class names are part of the contract (HQL entity names, `JsonTypeInfo.Id.SIMPLE_NAME` on `Task`, `Rule` and `Diagnostic`, default discriminators): do not rename entities.

Webhook payloads serialize the `Task` entities (including `Device`) with `HookView`, through the providers configured in `WebHook`. As long as they do, the JSON/XML annotations stay on the entities involved.

## Safety nets

- `ApiContractTest`: golden files of every REST endpoint (`src/test/resources/api-contract/`), with `ApiContractCoverageTest` failing when a resource method has no case. A diff means the change broke the API. Regenerate with `-Dnetshot.contract.update=true` only for a deliberate API change.
- `WebHookContractTest`: golden files of the webhook payloads (`src/test/resources/hook-contract/`), regenerated with `-Dnetshot.hook.contract.update=true`.
- `DtoSerializationProbeTest`: the serialization facts above.
- `DatabaseMigrationTest`: schema upgrades.
- `ArchitectureTest`: the dependency rules.

## Wiring

`boot.Wiring` is the composition root. `Wiring.init()` initializes the database access (with the persistent model from `boot.EntityModel`) and builds the long-lived objects (today `Transactions`; repositories and services are added there as they are created). `Netshot.main` calls it right after the schema update; tests needing a database call the same method.

- **Adding an entity**: register it in `boot.EntityModel` (task, rule and diagnostic subclasses are discovered by scanning).
- **REST resources** get their dependencies by injection: `NetshotWebApplication` registers the wiring into Jersey with an HK2 binder, so a resource declares `@Inject Transactions transactions;` (or a service, once bound).
- **Code outside Jersey** (Quartz jobs, cluster code) can reach the wiring through the static `Wiring.get()`. This accessor is **transitional**: it is a service locator that hides dependencies, kept only until such code gets its dependencies explicitly. Do not use it from code that can be injected.

Resources are discovered by Jersey package scanning of `net.netshot.netshot.api.rest` (sub-packages included): a new `@Path` class is picked up with no registration. Filters, providers and the exception mapper carry no `@Provider` annotation and are registered explicitly in `NetshotWebApplication`.

## Migrating a v0 endpoint out of RestService

*This checklist will be refined as the first slices are migrated.*

1. **Check the safety nets.** The endpoint must be covered by `ApiContractTest` (JSON, XML, and the error cases). Add cases first if not. If the endpoint's entities are also sent by webhooks, make sure `WebHookContractTest` covers them.
2. **Repository** in the feature package (for example `inventory.device.DeviceRepository`): the queries, with the `Session` as first parameter. No transaction handling.
3. **Service** in the feature package: the use case, with command records for the inputs, running in `Transactions`; validation moves here; failures are `NetshotException(ErrorCode, message)`. No JAX-RS type, no `Response`.
4. **DTOs and mapper** in `api.rest.v0.<feature>`: records following the [DTO conventions](#dto-conventions), preserving field names, view and XML root element. Check the output against the golden files.
5. **Resource** in `api.rest.v0.<feature>`: a class with `@Path("/")` and the full paths on the methods, injected with the service. Copy the `@RolesAllowed`/security annotations, the OpenAPI annotations and the `@JsonView(RestApiView.class)` of the original methods, then **delete the original methods from `RestService` in the same change**. Mind the pitfall below.
6. **Entities**: remove the Jackson/JAXB annotations from an entity only once nothing else serializes it (other endpoints, webhook payloads).
7. **Run** `ApiContractTest`, `ApiContractCoverageTest`, `WebHookContractTest`, `ArchitectureTest`, and refreeze the ArchUnit store if signatures moved (the diff must only remove entries or follow renames).
8. **Document**: update [REST API](../api/rest-api.md) if the behavior changed (it should not), and this page if a convention changed.

!!! warning "Do not give the new resource a narrower @Path than the paths it takes over"
    JAX-RS selects the root resource class *before* looking at the methods, with no fallback. A new class annotated `@Path("/devices")` hides every `RestService` method under `/devices/...` (they answer 404 or 405), even those that were not moved. Use `@Path("/")` with the full path on each method like `RestService` does (several root resources with the same `@Path("/")` and different method paths work fine), or move a whole path prefix at once. Declaring the same method and path in both classes fails at startup.
