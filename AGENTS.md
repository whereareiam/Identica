# AGENTS.md

## Identica At A Glance

Identica is a **provider-oriented Minecraft identity and authentication plugin**.
Authentication methods are modeled as **providers**, not as hard-coded product modes.

When changing behavior:

- preserve the provider-oriented design
- prefer extending an existing provider or provider contract over introducing shared-core special cases
- keep changes in the module that owns the behavior

---

## Repository Orientation

### Core Module Map

- `identica-api`
  Public API, contracts, events, shared models, and extension points.
- `identica-common`
  Shared runtime behavior, configuration defaults, provider coordination, routing, migration, replication integration, compiled feature lifecycle coordination, and general domain logic.
- `identica-engine`
  Pipeline, step, prepare, and completion orchestration.
- `identica-adapter-*`
  Infrastructure adapters such as command handling, database persistence, and replication transport.
- `identica-platform/*`
  Velocity and BungeeCord/Waterfall platform wiring, APIs, listeners, adapters, and bootstraps.
- `identica-provider/*`
  Provider-specific behavior, including the official credential and premium providers.
- `identica-feature/*`
  Compiled features: verification, recognition, sentinel, restriction, and restriction-join. Feature API, implementation, persistence, commands, and pipeline contributions stay in their owning feature modules; join restriction is nested under `feature-restriction/restriction-join`.
- `identica-trait/*`
  Shared mandatory behavior driven by provider traits, including authoritative usernames. These modules are compiled into Identica and are not feature toggles.
- `identica-integration/*`
  External integrations such as bStats.
- `identica-testing`
  Live player journeys against the packaged plugin on a real proxy, run through Anvil. Not part of the shipped plugin.
- `build-logic/`
  Shared Gradle conventions. `module-*` says what kind of module a project is: `module-java` (Java 21 toolchain, Lombok, common dependencies), `module-api` (a published `-api` module), `module-feature` (a module published in the feature group). `packaging-*` says what it produces: `packaging-publication`, `packaging-relocations`, `packaging-shaded-jar` (a loadable plugin or provider jar), `packaging-platform-jar` (a proxy's plugin jar) and `packaging-bundle-jar`. A published module that is not an API applies `module-java` and `packaging-publication`.
- `docs/content/`
  User-facing and developer-facing documentation.
- `dev/`
  Local runtime scenarios, overlays, and server setup for manual validation.

### Where To Look First

- For contracts, public models, events, or extension points, start in `identica-api`.
- For shared behavior or domain rules, start in `identica-common`.
- For pipeline or orchestration behavior, start in `identica-engine`.
- For persistence, replication, or command infrastructure, start in the relevant `identica-adapter-*` module.
- For proxy-specific behavior, bootstrap flow, or listeners, start in `identica-platform/*`.
- For credential-specific or premium-specific behavior, start in `identica-provider/*`.
- For shared behavior driven by provider identity guarantees, start in `identica-trait/*`. Trait declarations remain in `identica-api`.
- For optional behavior, feature-specific APIs, settings, persistence, or pipeline contributions, start in `identica-feature/*`. Core owns the `IdenticaFeature` contract and startup lifecycle; providers declare identity traits and supported feature integrations.
- For build behavior or packaging conventions, check `build-logic/`.
- For user-visible behavior changes, check the matching pages under `docs/content/`.

---

## Architecture Rules

### Module Boundaries

- Keep responsibilities in the narrowest owning module.
- Do not introduce cross-module shortcuts that bypass existing abstractions.
- Keep provider-specific behavior out of shared core abstractions unless that pattern already exists.
- Respect the separation between API, common, engine, adapters, platforms, providers, and integrations.

### API Surfaces

- Treat `identica-api`, every `*-api` module, and packages containing `api` as **public API surfaces**.
- Public API changes are compatibility-sensitive.
- Public API code must have complete Javadocs for public classes, interfaces, enums, and public methods.

### Existing Patterns First

- Prefer existing pipeline, provider, listener, configuration, and persistence patterns over new abstractions.
- Follow neighboring naming, packaging, and registration patterns before inventing new ones.
- If the surrounding area already has a pattern for steps, migrations, serializers, commands, or listeners, extend it instead of starting a parallel style.

---

## Runtime And Library Conventions

### Messaging And Text

- Use **Adventure** components for messaging and text handling.
- Prefer the existing Adventure-based patterns already used in shared messaging, command output, and platform adapters.
- Avoid introducing alternate text systems or ad-hoc string formatting where an Adventure component flow already exists.

### Project Libraries

Identica already relies on several `whereareiam` libraries. Reuse them when working in the areas they already own.

- `attache`
  Runtime dependency management and descriptor-driven library loading. In this repository it is the default way to bring in libraries: declare runtime-managed dependencies in Gradle, generate packaged Attache descriptors, relocate dependencies when needed, and load declared libraries at runtime on supported platforms such as Velocity.
- `dialectica`
  Used for schema and migration-related database behavior.
- `keystone`
  Shared actor and message-serialization infrastructure built around Adventure. In this repository it provides the `Actor` abstraction, serializer engine, placeholder-aware message rendering, decorators, and related messaging utilities.
- `commandant`
  Command convenience infrastructure built around Cloud. In this repository it is used for definition-driven command configuration, alias/usage/permission/description overrides, help generation, pagination, and other command-facing quality-of-life behavior.

Do not replace these libraries with unrelated alternatives in the same area unless explicitly asked.

### Dependencies

- Use Java 21-compatible code and APIs.
- Follow the version catalog in `gradle/libs.versions.toml` when adding or changing dependencies.
- Prefer runtime-managed dependencies through Attache.
- Do not add `implementation` dependencies unless there is no practical runtime-loading alternative.
- Add dependencies in the narrowest owning module.
- Avoid pulling platform-specific dependencies into shared modules unless that boundary already exists.
- Be aware that some platforms already ship their own library versions. This can create conflicts when Identica needs newer or different versions of the same libraries.
- Relocate dependencies when needed to avoid namespace and version conflicts with platform-provided libraries or other runtime-loaded libraries.
- When a new dependency is required, check the latest available version instead of relying on memory.

---

## Build, Test, And Validation

### Common Commands

- `./gradlew test`
- `./gradlew pluginJars`
- `./gradlew test pluginJars`
- `./gradlew test assembleDistribution` (what the CI checks run)

### Verification Guidance

- Prefer targeted Gradle tasks for the module you changed before running broad root verification.
- `pluginJars` is the main packaging task for proxy bootstrap jars and bundled provider jars.
- `assembleDistribution` collects the shipped files under their final names in `build/distribution/`; the root `toolkitDistribution` block declares them, and workflows upload that directory as it is.
- Add or update automated tests when behavior changes.
- Prefer extending the closest existing test suite instead of creating a parallel testing style.
- Database and replication changes may need integration-style coverage in addition to unit tests.

### Live Tests

`identica-testing` starts the packaged plugin on a real Velocity proxy with Paper backends and drives simulated players through it.

- run them with `./gradlew :identica-testing:anvilTest`; `test` and `pluginJars` do not start them
- they need an installed Java 21, network access on first use, and take about 20 seconds per test
- add or update a journey when a change affects what a joining player experiences
- declare the network with `@Identica`, describe the player with `Journey`, and keep player-facing texts in `Prompt`
- a failed test keeps its network under `identica-testing/build/anvil/`; read the `anvil-console.log` files there, where Identica's debug logging is enabled
- premium accounts come from the local Mojang stand-in (`Mojang.service().register(...)`), which also verifies premium logins; never add real account credentials to the repository or CI

See `docs/content/developer/testing/index.mdx`.

---

## Branching And Pull Requests

### Branch Flow

- Build each feature, fix, or isolated piece of work in its own `feature/...` branch.
- Merge feature work into `dev` through a pull request.
- Release work is merged from `dev` into a `release` branch through a pull request.

### Pull Request Checks

- Every pull request gets the metadata check (title and label) and the quick checks: tests and `assembleDistribution`.
- Label a pull request with exactly one of `feature`, `change`, `bug`, `dependencies`, or with `skip-changelog`; `major` may accompany a category.
- A run keeps as artifacts only what someone downloads: `identica-plugins-<version>` from a development build or release, and `test-results` when a test failed.
- Development builds are started manually and publish a branch-qualified version. A published GitHub release publishes Maven artifacts, attaches the distribution, and publishes to the stores; a manual run of the release workflow only republishes to the stores.
- Entry workflows only select a trigger and call `reusable-verify.yml` and `reusable-publish.yml`; shared steps come from `whereareiam/devops` and `whereareiam/blockops`.

### Pull Request Titles

Pull request titles are important because they land in release changelogs.

- follow the strict format `Category: Description`
- start with the affected area or group
- describe what was introduced, changed, or fixed
- keep titles specific and release-note friendly

Examples:

- `Engine: Introduced a new pipeline type`
- `Platform: Fixed Velocity bootstrap provider loading`

---

## Documentation Rules

- When changing features, behavior, configuration, installation, provider flows, or other user-visible behavior, update the relevant documentation in the same work.
- Documentation in `docs/content/` should describe the **current state** of the product.
- Write normal present-state documentation for users and operators.
- Do not write historical “used to” documentation unless the task explicitly calls for migration notes or release notes.
- Prefer updating the existing relevant page over leaving behavior undocumented.

---

## Working Style

- Match the conventions already established in the surrounding module.
- Prefer small, scoped changes over broad cleanup unless cleanup is part of the task.
- If a change touches authentication flow, migration, replication, routing, or verification, be especially careful because regressions there can break real player access.
