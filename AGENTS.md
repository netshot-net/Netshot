# AGENTS.md

Instructions for AI coding agents working in this repository.

## Project overview

Netshot is a network configuration and compliance management application: a
Java core server, a React web UI, and JavaScript device drivers. See
[docs/architecture.md](docs/architecture.md) for the component map before
making non-trivial changes — it explains what each Java package
(`work`, `device`, `compliance`, `aaa`, `cluster`, `vault`, `rest`, ...) is
responsible for.

## Repo layout

- `src/main/java/net/netshot/netshot/` — core server (Java)
- `src/test/java/net/netshot/netshot/` — core server tests (JUnit 5)
- `src/main/resources/drivers/` — device drivers (JavaScript, one file per platform)
- `src/main/resources/webui/` — React + TypeScript web UI (own `package.json`)
- `docs/` — user/admin/developer documentation, built with [Zensical](docs/index.md) (Python/uv)
- `dev/device-simulator/`, `dev/oidc-idp/` — containers used by the Docker Compose dev stack and by driver tests
- `compose.dev*.yaml` — dev stacks (basic, OIDC, clustering)

## Building and running

Always use the Maven wrapper (`./mvnw`), not a system-installed `mvn` — it pins the Maven version the build expects.

- Core server compile-only sanity check: `./mvnw package` (or `./mvnw compile` for a quicker check). Requires a JDK matching the version in `pom.xml`.
- **Live testing (needed to actually exercise a change end-to-end, backend or frontend): use the Docker Compose dev stack, not a bare host process.** It builds the core from source, live-reloads the web UI, and gives you simulated devices to test against:
  ```bash
  docker compose -f compose.dev.yaml up --build
  ```
  Add `-f compose.dev.oidc.yaml` for a local OIDC IdP, or `-f compose.dev.cluster.yaml` for two clustered nodes. UI at `https://localhost:8443/`. The `router1`/`router2` simulated devices can be added using their Compose hostname and `admin`/`admin`. Rebuild (`--build`) after backend changes; the web UI hot-reloads on its own.
- Docs: managed with `uv` (see `docs/pyproject.toml` / `docs/uv.lock`) — use `uv run`/`uv sync` rather than bare `pip`/`venv` for anything under `docs/`.

## Tests and checks

These mirror what CI (`.github/workflows/build.yml`) enforces. **Always run the relevant ones after making a change, before calling it done** — don't leave verification to the next person:

- Changed any Java code (`src/main/java`, `src/test/java`) → run **both**:
  - `./mvnw checkstyle:check` (style — config in `checkstyle.xml`; also catches a missing/incorrect license header)
  - `./mvnw test` (Java tests). Some tests (e.g. `DeviceDriverTest`) spin up Testcontainers-based device simulators, so **Docker must be running and reachable** (the CI job mounts `/var/run/docker.sock`).
- Changed any web UI code (`src/main/resources/webui/src`) → run **both**, from `src/main/resources/webui`:
  - `npm run lint` (eslint) — on the full project this can OOM the default Node heap; if it does, `export NODE_OPTIONS="--max-old-space-size=4096"` first, and prefer the default "stylish" formatter over `-f json` for full-project runs.
  - `npm run tsx` (TypeScript type-check, no emit) — if Chakra UI theme tokens/recipes changed, regenerate typings first with `npm run chakra:typegen` and commit the resulting diff under `src/`, or the CI type-check diverges from what's committed.
  - For UI-visible changes, also do a sanity build with `npm run build` and, where practical, check the change in the running dev stack.
- Changed a device driver (`src/main/resources/drivers/*.js`) → run `./mvnw test` (exercises drivers against the simulators in `dev/device-simulator/`) in addition to the Java checks above if you touched any Java driver-support code.

## Conventions

- **License header**: every Java source file must start with the GPL header in `.license-header`; checkstyle enforces this (`Header` module) — copy it verbatim into new files.
- **Java style**: tabs for indentation, max line length 200 (see `checkstyle.xml`).
- **Web UI style**: Prettier config in `src/main/resources/webui/.prettierrc.json` (2-space indent, no semicolons, double quotes, 100 print width). Follow existing component/hook patterns under `src/main/resources/webui/src/features/`.
- **i18n**: `src/main/resources/webui/src/i18n/en.json` and `fr.json` are kept in sync — when adding/changing a UI string, update both.
- **Device drivers**: are plain JavaScript files under `src/main/resources/drivers/`, one per platform, loaded (and reloadable) by the core server. See [docs/extending/writing-a-driver.md](docs/extending/writing-a-driver.md) before writing or modifying one, and [docs/user-guide/device-drivers.md](docs/user-guide/device-drivers.md) for the runtime concept. Driver behavior is exercised via `DeviceDriverTest` against the simulators in `dev/device-simulator/`.
- **REST API**: the web UI talks to the core exclusively through `net.netshot.netshot.rest.RestService`; keep it the single source of truth for the API surface (see [docs/api/rest-api.md](docs/api/rest-api.md)).
- **Documentation**: update the relevant page(s) under `docs/` in the same change whenever behavior users/admins/integrators rely on changes — new or changed config options (`docs/configuration-reference.md`), REST endpoints (`docs/api/rest-api.md`), webhooks (`docs/api/webhooks.md`), driver-writing conventions (`docs/extending/writing-a-driver.md`), install/upgrade steps (`docs/installation/`, `docs/upgrading.md`), or user-facing features (`docs/user-guide/`). Don't leave docs stale for the sake of a smaller diff.

## Commit style

History uses Conventional Commits, `type(scope): :emoji: summary`, e.g.:

```
feat(web): :lipstick: rework the clustering view
fix(driver): :package: fix backward compatibility for drivers with options
test(driver): :white_check_mark: update the driver tests to use simulator containers
build(core): :hammer: refactor dev Compose files
docs: :memo: update documentation
```

The gitmoji is optional but preferred when the type already implies a natural one (`:sparkles:` for a new feature, `:bug:`/`:package:` for a fix, `:zap:`/`:fire:` for perf, `:art:` for style, `:white_check_mark:`/`:bricks:` for tests, `:memo:` for docs, `:hammer:`/`:arrow_up:`/`:truck:` for build, `:rewind:` for revert...) — don't force one otherwise.

**Types** (in order of frequency in history): `feat`, `fix`, `refactor`, `build`, `perf`, `style`, `test`, `chore`, `revert`, `docs`, `ci`.

**Scopes** — use one of the following, matching the area actually touched; omit the scope entirely (`type: summary`, no parens) for changes that don't fit one of these (e.g. repo-wide docs or chores):

- `web` — web UI (`src/main/resources/webui`)
- `core` — core server Java code, excluding drivers/db/api specifics below
- `driver` — device drivers (`src/main/resources/drivers`)
- `db` — persistence/schema/migrations
- `api` — REST API surface specifically
- `docs` — documentation site (`docs/`)
- `deps` — dependency bumps

Match this format for new commits unless told otherwise.
