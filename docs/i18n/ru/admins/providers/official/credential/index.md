---
title: Обзор
description: Регистрация и вход игроков по паролю.
sourceHash: 90954c144d55220a262a125f4207f784f0192ba7
---

Провайдер Credential позволяет игрокам зарегистрироваться с паролем и входить с ним. Это провайдер для игроков без лицензионного аккаунта Minecraft. Установите его, как описано в разделе [Установка](../../../installation/index.mdx), и включите в `providers/providers.yml`.

Его файлы лежат в `providers/Credential/`: `settings.yml`, `messages.yml` и `commands.yml`.

## Что делают игроки [#what-players-do]

| Команда | Что делает |
| --- | --- |
| `/pass <password>` | регистрирует аккаунт с паролем |
| `/passconfirm <password>` | подтверждает пароль, когда требуется ввести его повторно |
| `/login <password>`, `/l <password>` | выполняет вход |
| `/changepassword <current> <new> [repeat]` | меняет пароль |
| `/credential` | начинает перенос аккаунта на Credential; затем следует `/credential confirm` или `/credential cancel` |

О требованиях к паролям и о том, как они хранятся, рассказывает страница [Пароли](./passwords/index.mdx), а о неверных паролях — [Защита от перебора паролей](./brute-force/index.mdx).

## Ники [#usernames]

Игроки выбирают ник, заходя с ним. Аккаунт Credential принадлежит своему нику без учёта регистра: игрок, который зарегистрировался как `MrBeast` и заходит как `mrbeast`, входит в тот же аккаунт с тем же паролем и UUID.

Credential не владеет своими никами так, как Premium, поэтому по [правилам для ников](../../../guides/accounts/usernames/index.mdx) по умолчанию ник, который занят другим аккаунтом, ему не достаётся, а настоящий владелец лицензионного аккаунта с этим ником забирает его у аккаунта Credential.

## Консольные команды [#console-commands]

| Команда | Право | Что делает |
| --- | --- | --- |
| `/identica credential register <username> <password>` | `identica.admin` | регистрирует аккаунт для игрока, который ещё не заходил на сервер |
| `/identica credential setpassword <username> <password>` | `identica.admin` | задаёт новый пароль существующему аккаунту в любом регистре ника |

`register` выдаёт аккаунту UUID, зарезервированный за его ником, — так аккаунты из другого плагина сохраняют свои UUID; см. [Переход с другого плагина](../../../guides/upgrading/from-another-plugin/index.mdx). Команда только создаёт аккаунты. Если ник уже принадлежит аккаунту — в любом регистре и через любой провайдер, — она отвечает сообщением `commands.admin.accountExists` (плейсхолдеры `{target}`, `{username}`, `{uniqueId}`) и ничего не регистрирует. Если зарезервированный UUID принадлежит другому аккаунту, она отвечает сообщением `commands.admin.reservationTaken` (плейсхолдеры `{target}`, `{uniqueId}`, `{username}`).

## Функции [#features]

Credential поддерживает все [функции](../../../features/index.mdx). Многие сети [требуют подтверждения](../../../features/verification/provider-policy/index.mdx) для Credential, потому что пароль можно подобрать или использовать повторно.

Все настройки описаны на странице [Настройки](./settings/index.mdx).
