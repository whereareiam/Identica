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
  Public API, contracts, events, shared models, and extension points. Its persistence contracts in `me.whereareiam.identica.persistence` are technology-free ports; it carries no database technology: no Dialectica, Strata or Jdbi.
- `identica-common`
  Shared runtime behavior, configuration defaults, provider coordination, routing, migration, replication integration, username conflict handling, compiled feature lifecycle coordination, and general domain logic.
- `identica-engine`
  Pipeline, step, prepare, and completion orchestration.
- `identica-adapter-*`
  Infrastructure adapters such as command handling, database persistence, and replication transport.
- `identica-adapter-database/*`
  The database family. `database-api` is published and holds the database contracts: `DatabaseService`, `DatabaseSchema`, `SchemaInstaller` and `AccountTable`. `database-common` is the implementation: the connection, the schema installer, the core schema with its entities, repositories and persistence services, and the runtime libraries it loads through Attache; its test fixtures run schema tests on every database for the other owners. `database` is the assembly that binds them into Identica's injector. Every owner of tables (core, feature, provider, integration) declares its own `DatabaseSchema` as a Strata migration stream. Table structure is declared only with Dialectica (entities, and Dialectica schema changes in migrations), runtime reads and writes go through Dialectica's `EntityAccess` (hand-written Jdbi only for custom queries through the entity mapper), and plain standard SQL is only for one-time, rerunnable data steps inside a migration; see `docs/content/developer/advanced/database-schema`.
- `identica-platform/*`
  The platform family. `type/platform-*` are the platforms themselves: Velocity and BungeeCord/Waterfall wiring, APIs, listeners, adapters, and bootstraps. `common` holds what every platform shares: `PlatformConfiguration`, which names the adapters a platform has to provide, the event-typed adapter contracts, and the decision processors behind them. `bundle` packages the platforms into one jar.
- `identica-provider/*`
  Provider-specific behavior, including the official credential and premium providers.
- `identica-feature/*`
  Compiled features: verification, recognition, sentinel, restriction, and restriction-join. Feature API, implementation, persistence, commands, and pipeline contributions stay in their owning feature modules; join restriction is nested under `feature-restriction/restriction-join`.
- `identica-integration/*`
  External integrations, such as usage statistics and error reports sent to bStats and FastStats through Statistica.
- `examples/*`
  The example addon (a separate Velocity plugin on the public API) and the example provider (a provider jar) that the developer documentation walks through. Not shipped; `identica-testing` runs both on a real proxy, so keep them working when the API changes.
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
- For persistence, replication, or command infrastructure, start in the relevant `identica-adapter-*` module. For an owner's tables and their migrations, start with its `DatabaseSchema` and the contracts in `identica-adapter-database/database-api`.
- For proxy-specific behavior, bootstrap flow, or listeners, start in `identica-platform/type/*`. For what a platform has to provide, start in `identica-platform/common`.
- For credential-specific or premium-specific behavior, start in `identica-provider/*`.
- For username behavior (synchronization, uniqueness, conflict rules), start in `identica-api` (`identity/username`, `model/identity/username`) and `identica-common` (`identity/username`). Its phases are in `identica-engine`, and its tables are in `identica-adapter-database/database-common`. Providers declare identity guarantees as `ProviderTrait`s in `identica-api`.
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

- run them with `./gradlew :identica-testing:anvilTest`; `./gradlew :identica-testing:anvil` runs it and every suite; `test` and `pluginJars` do not start them
- they need an installed Java 21 and network access on first use; a test takes about 5 seconds and several run at once, each on its own network (`-Pidentica.journeys.parallelism=1` runs them one after another)
- every test gets fresh proxies, while the `auth` and `lobby` servers start once and serve one test after another, so a journey must not depend on the state of a backend world
- add or update a journey when a change affects what a joining player experiences
- declare the network with `@Identica`, describe the player with `Journey`, and name player-facing messages through Identica's message models (`.sees(m -> m.credential()...)`) instead of copying their text
- a player who announces a hostname or connects from another loopback address joins with `Journey.of(anvil, Client.offline("Alice").through(host).from("127.0.0.2"))`; every other player connects from `127.0.0.1`
- a failed test keeps its network under `identica-testing/build/anvil/`; read the `anvil-console.log` files there, where Identica's debug logging is enabled
- change a test proxy's files through `@Identica(configure = ...)` (both proxies of a cluster through `@IdenticaCluster(configure = ...)`), a `Consumer<ProxyConfiguration>` working on Identica's models; `omit` and `corrupt` cover new installations and unreadable files; `ProxyConfiguration.editGenerated` and `corruptGenerated` change the files of a running proxy before `Administrator` reloads it
- `@IdenticaCluster` starts two proxies on a shared PostgreSQL and, unless turned off, one Redis; a journey names its proxy (`Journey.offline(anvil, "Alice", PROXY_B)`) and `Administrator` runs console commands on one; these tests need Docker and are skipped without it
- journeys that start from what an earlier version of Identica left are tagged `@Tag("upgrade")` and form the `upgrade` suite (`anvil { suites { ... } }`); `anvilTest` leaves them out and `./gradlew :identica-testing:anvilUpgradeTest` runs them, by hand and in the release workflow; `ProxyConfiguration.seed(path, file)` places such a file, like the SQLite database built from the `identica-2.2.1` layouts that `EarlierInstallation` creates, before the proxy first starts
- premium accounts come from the network's own stand-in for Mojang, a `YggdrasilMock` test parameter (`yggdrasil.register(...)`), which also verifies premium logins; never add real account credentials to the repository or CI

See `docs/content/developer/testing/index.mdx`.

---

## Branching And Pull Requests

### Branch Flow

- Build each feature, fix, or isolated piece of work in its own `feature/...` branch.
- Merge feature work into `dev` through a pull request. Pull requests are merged by rebase: each commit lands on `dev` as written, so a branch holds clean conventional commits and no fixups.
- Releases are cut from `dev`: publishing the release draft tags the current `dev`.

### Pull Request Checks

- Every pull request gets the metadata check (title and label) and the quick checks: tests and `assembleDistribution`.
- Label a pull request with exactly one of `feature`, `change`, `bug`, `dependencies`, or with `skip-changelog`; `major` may accompany a category.
- Add `provider:credential` or `provider:premium` when a pull request or issue concerns that provider's jar; work on the plugin itself carries no such label.
- A run keeps as artifacts only what someone downloads: `identica-plugins-<version>` from a development build or release, and `test-results` when a test failed.
- Development builds are started manually and publish a branch-qualified version. A published GitHub release publishes Maven artifacts, attaches the distribution, and publishes to the stores; a manual run of the release workflow only republishes to the stores.
- Entry workflows only select a trigger and call `reusable-verify.yml` and `reusable-publish.yml`; shared steps come from `whereareiam/devops` and `whereareiam/blockops`.

### Issues

- A suggestion opens with `suggestion`, the label of an idea not decided on yet. An accepted one trades it for `feature` or `change`; a declined one is closed as not planned.
- A bug report opens with `bug`.

### Release Notes

A merged pull request lands in the release draft as `* <title> ([#<number>](<url>))`, under the category of its label. The draft is written for server admins and players, so the title and the label decide what they read.

#### Titles

- use the strict format `Area: Description`, with a past-tense description of what changed for the reader: `Introduced`, `Changed`, `Fixed`, `Removed`
- describe the effect, not the implementation: `Sessions: Fixed logins through another proxy disconnecting the wrong connection`, not `Session: Tied a session to its connection`
- the draft sorts by the exact area, so spell an area exactly as listed below

Areas for admins and players:

`Authentication`, `Commands`, `Configuration`, `Credential`, `Database`, `Messages`, `Migration`, `Platform`, `Premium`, `Recognition`, `Replication`, `Restriction`, `Routing`, `Sessions`, `Statistics`

- reuse an existing area whenever one fits; a new admin command is `Commands`, a new setting in a feature's file belongs to that feature's area
- add a new area only for something admins recognise by that name, such as a new feature, provider, or config file, and add it to this list in the same pull request
- never use module, package, or class names as an area: no `Engine`, `Pipeline`, `Capability`, `Adapter`, `Common`, `Config`

`API:` is for changes a plugin or provider developer compiles against or listens to: public types, events, provider and platform contracts. The drafter moves `API:` changes out of their category into a closing "For developers" section. A change that matters to both admins and developers takes the admin area; mention the API part in the pull request body.

#### Labels

| Label | Use for | Draft section |
| --- | --- | --- |
| `feature` | something new for admins, players, or developers | What’s new |
| `change` | changed behavior, configuration, or API | Enhancements |
| `bug` | a fix for a bug that shipped in a release | Fixes |
| `dependencies` | a library update | Dependencies |
| `skip-changelog` | anything a reader of the release would not notice | not listed |

Use `skip-changelog` for:

- internal refactors with no effect on admins, players, or the public API
- build, CI, test, and documentation-only changes
- a fix for a bug that never shipped: when the broken code was introduced after the last release, the fix belongs to the work that introduced it, not to the release notes; compare against the last release tag (`git grep <symbol> <tag>`, `git log <tag>..HEAD -- <path>`) to decide
- changes to something that was itself added after the last release and never shipped

Add `major` next to the category when admins or developers must act on upgrade: configuration moved or reset without a migration, stored data that is not carried over, or an incompatible public API change. One `major` pull request makes the draft a major version.

#### Release notes file

The drafter rebuilds the draft on every push to `dev`, so text typed into the draft is lost. Everything written by hand, such as the subtitle, the summary, upgrade notes, and security callouts, lives in `.github/release-notes.md` and is changed through pull requests like any other file.

```markdown
---
version: 3.0.0
---

# Update 3.0.0 - Shared Proxies

One or two sentences about the release.

## Upgrading

* Update from 2.2.1; older versions are not supported.
```

- the content replaces the subtitle and summary placeholders of the draft only while `version` matches the version the draft resolves to; notes of a published release therefore never reach the next draft
- when a change needs a note, add it to this file in the same pull request; if the file names an already published version, start the notes of the next release by replacing the whole file and setting `version` to the version the draft shows
- keep the heading as `# Update <version> - <subtitle>`; the stores take the subtitle as the version name
- bullets in this file are not sorted into the changelog

#### Publishing a release

- fix the changelog part of the draft by editing titles and labels of merged pull requests, then rerun the `Release drafter` workflow
- the subtitle and summary come from `.github/release-notes.md`; the release workflow stops while a placeholder is left
- the summary names what admins must do on upgrade (everything labelled `major`) and calls out security fixes
- the release workflow attaches `release-notes-bbcode.txt` for stores without automatic upload, such as spigotmc.ru

---

## Documentation Rules

- When changing features, behavior, configuration, installation, provider flows, or other user-visible behavior, update the relevant documentation in the same work.
- Documentation in `docs/content/` should describe the **current state** of the product.
- Write normal present-state documentation for users and operators.
- Do not write historical “used to” documentation unless the task explicitly calls for migration notes or release notes.
- Prefer updating the existing relevant page over leaving behavior undocumented.
- `docs/content/` is the English source. Translations live under `docs/i18n/<language>/` with the same paths and only for translated pages.
- When you change a page or partial under `docs/content/` that has a translation, update the translation in the same work, then run `docs/i18n/check.sh stamp docs/i18n/<language>/<path>`. `docs/i18n/check.sh` lists outdated translations with the English diff; the pre-commit hook and CI run it too.
- Translate into natural language for server operators, following `docs/i18n/<language>.glossary.md`. Keep code, config keys, commands, file names and link targets unchanged.
- Put text that is the same in every language, such as configuration examples, into `_partials/` so a translation does not have to repeat it.

---

## Working Style

- Match the conventions already established in the surrounding module.
- Prefer small, scoped changes over broad cleanup unless cleanup is part of the task.
- If a change touches authentication flow, migration, replication, routing, or verification, be especially careful because regressions there can break real player access.
