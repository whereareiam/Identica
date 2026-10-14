---
version: 3.0.0
---

# Update 3.0.0 - A more reliable Identica

Identica 3.0.0 fixes dozens of login, session and replication problems, reorganises the configuration into clear documents per area, and upgrades your 2.2.1 installation by itself on the first start.

## Highlights

* **Fewer ways for a login to go wrong.** Login progress stays with the connection that made it, so a second client under the same name can't take over a login in progress; while one connection logs in, others under its name are asked to try again shortly. Commands typed before their prompt are kept, authenticator codes work only once, and provider denials such as lockouts can't be bypassed through another provider.
* **Sessions and replication hold up across proxies.** An account has one active session, and a crashed proxy's sessions end within 30 seconds. Two proxies writing the same login or session at the same moment no longer overwrite each other: the one that comes second disconnects its player with "Reconnect to try again." (`engine.concurrentChangeKick`). Prompts and registration work with Redis replication, and proxies sharing a database start and save together without failing.
* **Configuration is split by area.** Settings, engine, routing, username conflicts and each feature have their own document, and files carry a version, so Identica upgrades them itself, starting with your 2.2.1 configuration and database.
* **Usernames are unique without regard to case.** A player can't register `alice` while an account holds `Alice`, and conflict rules decide who keeps a name when, for example, a premium owner joins under a name a Credential account holds.
* **Providers are picked more reliably.** A provider's hostname, a verified account link or a running migration decide the provider before name guesses do. Routing targets can follow the proxy's forced hosts, and Premium can detect premium clients by their profile (experimental, off by default).
* **Statistics.** Next to bStats, Identica sends anonymous statistics and error reports to FastStats, including logins by provider, 2FA adoption and client versions. Player names, unique ids and addresses are never sent.

## Upgrading

* **Before the first start:** stop every proxy that uses the database and back up the database and the data directory. The first proxy to start brings both up to date.
* **Versions:** Identica upgrades from 2.2.1 and newer. On an older installation, start 2.2.1 once first. MySQL 8.0.19 or newer is required; on an older server Identica stops before it changes any table. MariaDB has no minimum version.
* **Configuration:** every setting, message and command you changed in 2.2.1 moves to the document that holds it now, and the files an upgrade changes are copied to `.strata/backup/`.
  * The provider files in `providers/Credential/` and `providers/Premium/` are not upgraded: a value whose name or place changed falls back to its default, so compare them with the provider pages.
  * Removed settings: `connection.sessions.activeTtl` (sessions now expire after `replication.heartbeatTimeout` once their proxy stops), a scenario's `advanceLockTtl`, and the `advanceBusy` and `pipelineKick` messages. The username synchronization failure is one message, `pipeline.identity.synchronizationFailed`, with the text of 2.2.1's authentication scenario.
  * The session concurrency policy `ALLOW_MULTIPLE` no longer exists: set `REPLACE_EXISTING` or `REJECT_NEW`.
* **Usernames:** accounts that already share a name in two cases keep logging in through their own providers; the oldest holds the name. The console lists them on each start and `/identica admin username duplicates` shows them; nothing has to be changed.
  * The conflict rules of 2.2.1 (`format_display`) have no counterpart. Identica replaces them with the new default rules and deletes `providers/conflicts`; set your own rules again in the new form. The messages `pipeline.policy.conflictDenied` and `pipeline.policy.entrypointRequired` are removed.
* **Redis:** delete the session keys of 2.2.1 before the first start (`identica:sessions:*` with the default key names), for example with `redis-cli --scan --pattern 'identica:sessions:*' | xargs redis-cli del`. This version stores sessions differently.
* **Redis ACL:** a Redis user with restricted commands needs `evalsha` and `script` as well, for the writes that only succeed over what was read.
* **Statistics:** turn FastStats off in `plugins/faststats/config.properties`; the Statistics page lists what is sent.

## For addon developers

* **Access:** `IdenticaAPI` is an interface. Get it with `IdenticaAPI.get()`, or `IdenticaAPI.whenReady(...)` when your addon may start first: `IdenticaAPI.getEventManager()` becomes `IdenticaAPI.get().getEventManager()`, `getService(...)` became `findService(...)` returning an `Optional`, and `getPresenceService()` became `getIdentityService()`.
* **Startup:** Identica starts in phases, and a `RuntimeLifecycle` names its `StartupPhase`. `IdenticaBootstrappedEvent` is called once the features have started; `IdenticaReadyEvent` at the end, once providers are loaded and connections are handled.
* **Usernames:** a username lives in its own table, read and changed through `AccountUsernamePersistenceService` (`find`, `findHolder`, `setSource`, `rename`), which replaces `AccountUsernameStatePersistenceService`, `AccountUsernameHistoryPersistenceService` and `AccountPersistenceService.updateUsername`. `AccountService.create`, `AccountPersistenceService.create` and `rename` return a `UsernameWriteResult`, and `create` takes the username's source and provider. `AccountUsernameDuplicatePersistenceService` lists shared names.
* **Providers and conflicts:** `ProviderTrait` is `ProviderGuarantee`, with `OWNED_USERNAME` for `AUTHORITATIVE_USERNAME`, and `ProviderClaimOrigin.ENTRYPOINT` is `HOSTNAME`. Conflicts are settled by `ConflictService` rules and registrable `ConflictOutcome`s, which a provider adds through `IdenticaProvider.conflictOutcomes()`; `UsernameAssigner` replaces `UsernameConflictHandler`, and the resolvers, guards and conflict types are gone.
* **Sessions and logins:** an account has one session: `SessionConcurrencyPolicy.ALLOW_MULTIPLE` and `SessionService.findAllByUniqueId` are gone. `SessionService.open` fails with `ConcurrentSessionChangeException` when another login of the account was opened in between. `PipelineStateReference` names a connection. `UsernameHeldEvent` and `LoginSupersededEvent` report refused and superseded logins.
</content>
</invoke>
* **Replication:** `ReplicatedCache` reads with a revision (`read`, returning `Versioned`) and writes or removes only over what was read (`write`, `remove`); a `ReplicationAdapter` implements `compareAndPut` and `compareAndInvalidate`. `PipelineStateStore.save` returns whether it saved, `clear(reference, read)` clears only the state that was read, and `update` is gone.
