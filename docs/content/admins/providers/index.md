---
title: Overview
description: What providers are, which ones exist, and how to add one to your network.
---

A provider is one way for players to log in, such as with a password or with their premium Minecraft account. Identica itself handles accounts, sessions, usernames and routing; each provider decides whether a player may continue. A network runs the providers it needs, side by side.

## Official and community providers

- [Official providers](./official/index.md) are maintained by Identica's developers and documented here: [Credential](./official/credential/index.md) for passwords and [Premium](./official/premium/index.md) for premium accounts.
- [Community providers](./community/index.md) are made by others. They may follow their own release timing, support and documentation; review them before you install them.

## Adding a provider

1. Put the provider's jar into the `providers/` directory of Identica's data directory, next to the official ones.
2. Add an entry for it to `providers/providers.yml` with its id, `enabled: true` and a `priority`. Identica loads a provider only when it has an enabled entry.
3. Start the proxy, or restart it. The provider writes its files into its own folder below `providers/`.

Which provider a joining player gets is explained in [How a provider is picked](../guides/logins/provider-selection/index.mdx). Players can move their account from one provider to another; see [Moving between providers](../guides/accounts/moving-between-providers/index.mdx).

To write a provider of your own, see [Writing a provider](../../developers/extending/providers/index.mdx).
