---
title: Overview
description: Provider-oriented Minecraft identity plugin documentation.
---

# Identica

Identica is an authentication, registration, and account migration plugin built around one core idea:
authentication methods are providers, not hard-coded modes.

Most authentication plugins treat ideas like password auth, premium auth, or website auth as built-in product features.
Identica takes a different approach. In Identica, each authentication strategy can live in its own provider with
its own metadata, lifecycle, priority, eligibility rules, profile resolution, and migration logic.

That means password authentication is just a provider.
Premium authentication is just a provider.
A website-based flow can be another provider.
If your network needs something different later, the core model does not need to be redesigned first.

## Why provider-oriented matters

- You are not locked into one opinionated "premium vs password" architecture.
- Providers can be added, removed, reordered, or replaced based on the network's needs.
- Migration is part of the model instead of being bolted onto one built-in login mode.
- Future authentication ideas can fit the same model instead of becoming special cases in the core.

## Compared to other authentication plugins

The point of this comparison is not that other plugins are bad. Several of them are mature and feature-rich.
The difference is architectural: most plugins present premium/offline authentication as built-in behavior,
while Identica treats authentication methods as replaceable providers.

| Topic                                                        | Identica                                                        | nLogin                                   | OpeNLogin                            | AuthMe                                   | LimboAuth                            | JPremium                             | LibreLogin                                   |
|--------------------------------------------------------------|-----------------------------------------------------------------|------------------------------------------|--------------------------------------|------------------------------------------|--------------------------------------|--------------------------------------|----------------------------------------------|
| Architecture                                                 | Provider-based system where auth methods are separate providers | One integrated authentication plugin     | One integrated authentication plugin | One integrated authentication plugin     | One integrated authentication plugin | One integrated authentication plugin | One integrated authentication plugin         |
| Per-platform support                                         | Velocity (current repo)                                         | Velocity, BungeeCord, Waterfall, Folia, Paper, Spigot, Mohist | Spigot / Paper                       | Spigot / Paper / Folia                   | Velocity                             | BungeeCord / Velocity                | Velocity / BungeeCord / Paper                |
| Password authentication                                       | Yes, via provider                                               | Yes                                      | Yes                                  | Yes                                      | Yes                                  | Yes                                  | Yes                                          |
| Premium authentication                                       | Yes, via provider                                               | Yes                                      | No                                   | No built-in premium auth                 | Yes                                  | Yes                                  | Yes                                          |
| Migration between authentication methods                     | Yes                                                             | Yes                                      | No                                   | No                                       | Yes                                  | Yes                                  | Yes                                          |
| Premium migration with username change without losing account | Yes                                                            | Yes                                      | No                                   | No                                       | No                                   | Yes                                  | No                                           |
| Automatic conflict resolution between auth methods           | Yes, via multiple methods                                       | Yes, via one method                      | No                                   | No                                       | No                                   | No                                   | No                                           |
| 2FA                                                          | Yes, via verification methods                                   | Yes                                      | Yes                                  | Yes                                      | Yes                                  | Yes                                  | Yes                                          |
| Verification methods are pluggable                           | Yes                                                             | No                                       | No                                   | No                                       | No                                   | No                                   | No                                           |
| Can premium/password logic be removed entirely                | Yes                                                             | No                                       | No                                   | No                                       | No                                   | No                                   | No                                           |
| Configuration flexibility                                    | Very high: messages, command aliases, descriptions, and structure | High: flexible config with command aliases and descriptions | Basic: settings and language customization | High: flexible settings and editable messages | High: flexible settings and messages | High: flexible config with command aliases | High: highly customizable configuration      |
| API / extensibility                                          | Very flexible, extensible, and well-documented                  | API available                            | Basic API and events available       | API available                            | Events and backend API available     | API available                        | API available                                |
| Source code available                                        | Yes                                                             | No                                       | Yes                                  | Yes                                      | Yes                                  | No                                   | Yes                                          |
| Support                                                      | Yes                                                             | Yes                                      | Yes                                  | Yes                                      | No                                   | No                                   | No                                           |
| Pricing                                                      | Free                                                            | Freemium                                 | Free                                 | Free                                     | Free                                 | Paid                                 | Free                                         |

The big takeaway is simple:
Identica is not trying to be "another password and premium plugin."
It is trying to be the provider-oriented foundation that password, premium, website, and future authentication
strategies can plug into.
