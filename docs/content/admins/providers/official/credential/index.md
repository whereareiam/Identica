---
title: Overview
description: Let players register and log in with a password.
---

Credential lets players register with a password and log in with it. It is the provider for players without a premium Minecraft account. Install it as described in [Installation](../../../installation/index.mdx) and enable it in `providers/providers.yml`.

Its files are in `providers/Credential/`: `settings.yml`, `messages.yml` and `commands.yml`.

## What players do

| Command | Does |
| --- | --- |
| `/pass <password>` | registers with a password |
| `/passconfirm <password>` | confirms it, when repeating the password is required |
| `/login <password>`, `/l <password>` | logs in |
| `/changepassword <current> <new> [repeat]` | changes the password |
| `/credential` | starts moving the account to Credential; `/credential confirm` or `/credential cancel` follow |

[Passwords](./passwords/index.mdx) covers the password rules and how passwords are stored, and [Brute-force protection](./brute-force/index.mdx) covers wrong passwords.

## Usernames

Players choose their username by joining with it. A Credential account belongs to its username without regard to case: a player who registered as `MrBeast` and joins as `mrbeast` logs in to the same account, with the same password and UUID.

Credential does not own its names the way Premium does, so under the default [username rules](../../../guides/accounts/usernames/index.mdx) a name another account holds is refused, and the real premium owner of a name takes it over from a Credential account.

## Console commands

| Command | Permission | Does |
| --- | --- | --- |
| `/identica credential register <username> <password>` | `identica.admin` | registers an account for a player who has not joined yet |
| `/identica credential setpassword <username> <password>` | `identica.admin` | sets a new password for an existing account, whatever the case of the username |

`register` gives the account the UUID reserved for its username, which is how accounts from another plugin keep their UUIDs; see [Coming from another plugin](../../../guides/upgrading/from-another-plugin/index.mdx). It only creates accounts: when an account holds the username already, in any case and through any provider, it answers with `commands.admin.accountExists` (placeholders `{target}`, `{username}`, `{uniqueId}`) and registers nothing; when the reserved UUID belongs to another account, it answers with `commands.admin.reservationTaken` (placeholders `{target}`, `{uniqueId}`, `{username}`).

## Features

Credential supports every [feature](../../../features/index.mdx). Many networks [require verification](../../../features/verification/provider-policy/index.mdx) for Credential, since a password alone can be guessed or reused.

See [Settings](./settings/index.mdx) for every setting.
