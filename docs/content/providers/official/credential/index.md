---
title: Credential
description: Password-based authentication with optional runtime feature integrations.
---

The official `credential` provider supplies credential registration, authentication, and credential changes. Install it using the [installation guide](../../../installation/normal/index.mdx), enable it in [Providers](../../../configuration/providers/index.mdx), and configure its [provider settings](./configuration/settings/index.mdx).

Credential supports recognition, verification, restrictions, and provider-owned sentinel checks. Configure shared defaults and its `providers[].features` overrides to choose which behavior applies. Its detailed brute-force policy remains in Credential's settings.

Credential does not declare the `OWNED_USERNAME` guarantee: its players choose their usernames, so a name another account holds is refused under the default [username conflict rules](../../../configuration/identity/username/index.mdx#default-rules). Configuration cannot grant a guarantee.

## Usernames and accounts

A Credential account belongs to its username without regard to case. A player who registered as `MrBeast` and joins as `mrbeast` logs in to the same account, with the same password and UUID.

## Registering accounts from the console

`/identica credential register <username> <password>` registers a new Credential account for a player who has not joined yet, for example when moving accounts from another authentication plugin. The account gets its UUID as a registering player's account would: the UUID reserved for the username with `/identica admin reservation set`, or else the one [`uniqueIdMode`](../../../configuration/settings/index.mdx#uniqueidmode) assigns. To keep the UUIDs players had on the previous plugin, reserve each UUID right before registering the account, within [`reservationTtl`](../../../configuration/settings/index.mdx#reservationttl):

```
identica admin reservation set Steve 5627dd98-e6be-3c21-b8a8-e92344183641
identica credential register Steve <password>
```

The account keeps that UUID from then on, whatever `uniqueIdMode` is set to.

The command only creates accounts. When an account holds the username already, in any case and through any provider, nothing is registered and the command names that account with the `commands.admin.accountExists` message of Credential's `messages.yml` (placeholders `{target}`, `{username}`, `{uniqueId}`). When the reserved UUID belongs to another account already, it answers with `commands.admin.reservationTaken` (placeholders `{target}`, `{uniqueId}`, `{username}`) and registers nothing either.

`/identica credential setpassword <username> <password>` sets a new password for an existing Credential account, whatever the case of the username.
