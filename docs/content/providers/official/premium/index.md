---
title: Premium
description: Premium account authentication with optional runtime feature integrations.
---

The official `premium` provider authenticates Minecraft premium accounts. Install it using the [installation guide](../../../installation/normal/index.mdx), enable it in [Providers](../../../configuration/providers/index.mdx), and configure its [provider settings](./configuration/settings/index.mdx).

Premium declares `ProviderTrait.AUTHORITATIVE_USERNAME`. Identica follows its verified username, subject to manual authority and conflict policy; this identity behavior is not a feature toggle. See [Username authority](../../../configuration/identity/username/index.mdx).

Premium supports recognition, verification, restrictions, and sentinel checks. Configure shared defaults and its `providers[].features` overrides to choose which behavior applies.

## Moving an account to another provider

Premium asks every player who joins with the name of a Premium account to sign in with that premium account, so an offline client cannot take the name. A pending migration does not change that.

The owner moves the account to Credential with `/credential` and `/credential confirm` while signed in. Identica then disconnects the player. On the next join the player signs in with the premium account once more, and that login lets them choose the password they use from then on. Premium follows the general rule for [moving an account between providers](../../index.md#moving-an-account-between-providers).
