---
version: 3.0.0
---

# Update 3.0.0 - [Short subtitle]

[1-2 sentence summary of the release.]

## Upgrading

* Stop every proxy that uses the database, and back the database up, before the first start of this version. The first proxy to start brings the database up to date.
* Identica upgrades databases of Identica 2.2.1 and newer. On an older installation, start Identica 2.2.1 once first, then this version; on an older database the Credential provider refuses to load and says so in the console.
* Identica upgrades the configuration of 2.2.1 on the first start: every setting, message and command you changed moves to the document that holds it now, such as `engine`, `routing`, or a feature's document under `features/`. Back up the data directory with the database; the files an upgrade changes are also copied to `.strata/backup/`. Settings that no longer exist are removed: `connection.sessions.activeTtl` (sessions now expire with the proxy that holds them, after `settings.sessions.heartbeatTimeout`), a scenario's `advanceLockTtl` and its `advanceBusy` messages.
* The settings, messages and commands of the Credential and Premium providers, in `providers/Credential/` and `providers/Premium/`, are not upgraded. A value whose name or place changed in this version falls back to its default; compare these files with the provider's configuration pages after the update.
* The username synchronization failure is one message for every scenario now, `pipeline.identity.synchronizationFailed` in `identity/username/messages`; it keeps the text of the authentication scenario from 2.2.1.
* With Redis replication, delete the session keys of 2.2.1 before the first start, `identica:sessions:*` with the default key names, for example with `redis-cli --scan --pattern 'identica:sessions:*' | xargs redis-cli del`. This version stores sessions differently and does not read the old ones.
* Usernames are unique without regard to case now: a player cannot register `alice` while an account holds `Alice`. Accounts that already share a username in two cases keep logging in through their own providers, and the oldest of them holds the username. Each start lists them in the console, and `/identica admin username duplicates` shows them; nothing has to be changed.
* The username conflict rules are new and decide who keeps a username, not only the name shown while two players are online. The rules of 2.2.1 and the development builds (`format_display` in `identity/username/conflicts` or in `providers`) have no counterpart: on the first start, Identica replaces them with the new default rules and deletes `providers/conflicts`. With the defaults, a premium owner who joins through Premium gets the name a Credential account holds, and that account is renamed, such as to `Steve_credential`. Set your own rules again in the new form; the messages `pipeline.policy.conflictDenied` and `pipeline.policy.entrypointRequired` are removed.
* MySQL 8.0.19 or newer is required. On an older MySQL server Identica stops its startup with an error naming the version it found, before it changes any table; upgrade MySQL before updating Identica. MariaDB has no minimum version.

## Statistics

* Identica also sends anonymous usage statistics and error reports to FastStats, next to bStats. Player names, unique ids and addresses are never sent. Turn them off in `plugins/faststats/config.properties`; the Statistics page of the documentation lists what is sent.

## For addon developers

* `IdenticaAPI` is now an interface. Get it with `IdenticaAPI.get()`, or `IdenticaAPI.whenReady(...)` when your addon may start first, and call the same getters on it: `IdenticaAPI.getEventManager()` becomes `IdenticaAPI.get().getEventManager()`. `getService(...)` became `findService(...)`, which returns an `Optional`, and `getPresenceService()` became `getIdentityService()`.
* `IdenticaReadyEvent` is now called at the end of startup, once the providers are loaded with their commands and documents and Identica handles connections; a listener of it no longer runs before the providers load. `IdenticaBootstrappedEvent` is called once the features have started, before the providers load. Identica starts in phases, and a `RuntimeLifecycle` names the `StartupPhase` it starts in.
* An account's username lives in its own table now. `AccountUsernamePersistenceService` reads it with its source (`find`), finds the account that holds a username (`findHolder`), changes the source (`setSource`) and renames the account together with its username history (`rename`); it replaces `AccountUsernameStatePersistenceService` and `AccountUsernameState`. `AccountPersistenceService.updateUsername` and `AccountUsernameHistoryPersistenceService` are gone. `AccountUsernameDuplicatePersistenceService` lists the usernames several accounts share.
* `AccountService.create`, `AccountPersistenceService.create` and `AccountUsernamePersistenceService.rename` return a `UsernameWriteResult`: written, or taken with the account that holds the username. A new account no longer shares a held username, and a storage failure throws. `create` takes the username's source and the provider the account registers through.
* `ProviderTrait` is now `ProviderGuarantee`, and `AUTHORITATIVE_USERNAME` is `OWNED_USERNAME`: `IdenticaProvider.guarantees()`, `ProviderDescriptor.hasGuarantee(...)` and `ProviderManager.findProvidersByGuarantees(...)` replace the trait methods. `ProviderClaimOrigin.ENTRYPOINT` is now `HOSTNAME`.
* Conflicts are settled by rules and registrable outcomes: `ConflictService` finds the rule that applies and the `ConflictOutcome` it names, which registers its id with a typed settings model and its defaults, and `IdenticaProvider.conflictOutcomes()` registers a provider's own outcomes. It replaces the resolvers, guards, conflict types and attribute contexts, `getConflictResolvers()` and `getConflictTypes()`. `UsernameAssigner` replaces `UsernameConflictHandler`.
