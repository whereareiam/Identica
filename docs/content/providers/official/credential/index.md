---
title: Credential
description: Password-based authentication with optional runtime feature integrations.
---

The official `credential` provider supplies credential registration, authentication, and credential changes. Install it using the [installation guide](../../../installation/normal/index.mdx), enable it in [Providers](../../../configuration/providers/index.mdx), and configure its [provider settings](./configuration/settings/index.mdx).

Credential supports recognition, verification, restrictions, and provider-owned sentinel checks. Configure shared defaults and its `providers[].features` overrides to choose which behavior applies. Its detailed brute-force policy remains in Credential's settings.

Credential does not declare authoritative usernames. Configuration cannot grant that identity trait.
