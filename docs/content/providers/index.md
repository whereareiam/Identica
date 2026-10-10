---
title: Providers
description: Discover the broader provider ecosystem around Identica.
---

Providers are the center of Identica's design.
Instead of treating authentication methods as hard-coded built-ins, Identica treats them as separate provider modules that can be installed, configured, prioritized, and replaced.

Identica has two provider groups:

- official providers maintained by Identica developers
- community providers made by the community and not maintained by Identica developers

Community providers may follow different release timing, support expectations, documentation quality, configuration style, or feature scope than official providers.
Review them carefully before you install them.

## Provider discovery

Use these pages to discover provider modules:

- [Official providers](./official/index.md)
- [Community providers](./community/index.md)

Some providers may be documented directly in this docs site.
Others may link out to separate documentation maintained elsewhere.

## Moving an account between providers

A signed-in player moves their account to another provider with that provider's command, such as `/credential` or `/premium`, and confirms it. Identica disconnects the player and keeps the migration for the [migration scenario's](../configuration/engine/index.mdx#migration) `pipelineTtl`, for joins with the same username from the same IP.

A pending migration never lowers the protection of the provider the account is leaving. The migration continues only on a join that a provider verified:

- as the identity the account is already linked with, which is the provider being left. Identica then selects the new provider, and the player sets it up in the same session.
- or as the new provider's own identity, when the new provider verifies its logins itself, as Premium does.

Any other join that reaches Identica with a pending migration cancels it, and the player reads `scenarios.migration.cancelled`. A join the provider being left refuses, such as an offline client using the name of a Premium account, changes nothing.

### Custom providers

The rule names no provider, so a provider you write takes part through its own `SubjectResolver`:

- Return `SubjectResolution.builder()...verified(true)` only when your provider or the platform authenticated the subject for this connection, before the profile is resolved. The UUID of an online login is an example.
- Leave `verified` unset for a subject you derive from what the client claims, such as its username, even when one of your steps checks a secret afterwards. Being seen with such a subject proves nothing, so it never continues a migration.
- Keep demanding your login in your handshake policy while a migration is pending. Identica does not relax it for you.

A provider that proves ownership only in a step, such as a password prompt, cannot yet prove it again on the join after the confirmation. An account leaves such a provider only for a provider that verifies its own logins.
