---
title: Premium
description: Premium account authentication with optional runtime feature integrations.
---

The official `premium` provider authenticates Minecraft premium accounts. Install it using the [installation guide](../../../installation/normal/index.mdx), enable it in [Providers](../../../configuration/providers/index.mdx), and configure its [provider settings](./configuration/settings/index.mdx).

Premium declares `ProviderTrait.AUTHORITATIVE_USERNAME`. Identica follows its verified username, subject to manual authority and conflict policy; this identity behavior is not a feature toggle. See [Username authority](../../../configuration/identity/username/index.mdx).

Premium supports recognition, verification, restrictions, and sentinel checks. Configure shared defaults and its `providers[].features` overrides to choose which behavior applies.
