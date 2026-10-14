---
title: Credential
description: Аутентификация по паролю с необязательной интеграцией встроенных функций.
sourceHash: 55ccd465a9fdcc0037340ee175732971ff10772f
---

Официальный провайдер `credential` отвечает за регистрацию с учётными данными, аутентификацию и смену учётных данных. Установите его по [руководству по установке](../../../installation/normal/index.mdx), включите в разделе [Провайдеры](../../../configuration/providers/index.mdx) и настройте его [параметры провайдера](./configuration/settings/index.mdx).

Credential поддерживает распознавание, верификацию, ограничения и собственные проверки Sentinel. Какое поведение применяется, определяют общие значения по умолчанию и его переопределения в `providers[].features`. Подробная политика защиты от перебора паролей остаётся в настройках Credential.

Credential не объявляет гарантию `OWNED_USERNAME`: его игроки выбирают ники сами, поэтому по [правилам конфликтов ников](../../../configuration/identity/username/index.mdx#default-rules) по умолчанию ник, которым владеет другой аккаунт, им не достаётся. Выдать гарантию через конфигурацию нельзя.

## Ники и аккаунты [#usernames-and-accounts]

Аккаунт Credential принадлежит своему нику без учёта регистра. Игрок, который зарегистрировался как `MrBeast` и заходит как `mrbeast`, входит в тот же аккаунт с тем же паролем и UUID.

## Регистрация аккаунтов из консоли [#registering-accounts-from-the-console]

`/identica credential register <username> <password>` регистрирует новый аккаунт Credential для игрока, который ещё не заходил на сервер, например при переносе аккаунтов из другого плагина авторизации. Аккаунт получает UUID так же, как аккаунт игрока при регистрации: UUID, зарезервированный за ником командой `/identica admin reservation set`, а без резервирования — тот, который назначает [`uniqueIdMode`](../../../configuration/settings/index.mdx#uniqueidmode). Чтобы игроки сохранили UUID из прежнего плагина, резервируйте каждый UUID непосредственно перед регистрацией аккаунта, в пределах [`reservationTtl`](../../../configuration/settings/index.mdx#reservationttl):

```
identica admin reservation set Steve 5627dd98-e6be-3c21-b8a8-e92344183641
identica credential register Steve <password>
```

После этого аккаунт сохраняет этот UUID при любом значении `uniqueIdMode`.

Команда только создаёт аккаунты. Если ник уже принадлежит аккаунту — в любом регистре и через любой провайдер, — ничего не регистрируется, а команда называет этот аккаунт сообщением `commands.admin.accountExists` из `messages.yml` провайдера Credential (плейсхолдеры `{target}`, `{username}`, `{uniqueId}`). Если зарезервированный UUID уже принадлежит другому аккаунту, команда отвечает сообщением `commands.admin.reservationTaken` (плейсхолдеры `{target}`, `{uniqueId}`, `{username}`) и тоже ничего не регистрирует.

`/identica credential setpassword <username> <password>` задаёт новый пароль существующему аккаунту Credential в любом регистре ника.
