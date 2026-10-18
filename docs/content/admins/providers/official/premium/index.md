---
title: Overview
description: Let players log in with their premium Minecraft account.
---

Premium lets players log in with their premium Minecraft account, verified by Mojang, without a password. Install it as described in [Installation](../../../installation/index.mdx) and enable it in `providers/providers.yml`.

Its files are in `providers/Premium/`: `settings.yml`, `messages.yml` and `commands.yml`.

## Who is asked for a premium login

Premium decides for every joining player whether they own the premium account of their username. Such a player is asked for a premium login, which the official launcher completes on its own; everyone else continues with the next provider, such as Credential. [Premium detection](./detection/index.mdx) explains how Premium decides, and how to avoid disconnecting offline players who use a premium name.

A username linked to a Premium account always needs the premium login, so an offline client cannot take it.

## Usernames

Premium owns its players' names: only the owner of the Mojang account `Steve` can join through Premium as `Steve`. A player who renames their account at Mojang plays under the new name on their next join and keeps their account.

Under the default [username rules](../../../guides/accounts/usernames/index.mdx), the owner of a name takes it over from a Credential account that registered it first, and that account is renamed.

## Moving accounts

| Command | Does |
| --- | --- |
| `/premium` | starts moving the account to Premium |
| `/premium confirm` | confirms the move |
| `/premium cancel` | cancels it |

A player moves to Premium while logged in, then joins with their premium account. Moving from Premium to Credential needs one more premium login, after which the player chooses a password. See [Moving between providers](../../../guides/accounts/moving-between-providers/index.mdx).

## Features

Premium supports every [feature](../../../features/index.mdx). Premium accounts are already protected by Mojang's login, so many networks do not require verification for them.

See [Settings](./settings/index.mdx) for every setting.
