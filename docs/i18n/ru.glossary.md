# Russian translation glossary

How the Russian documentation under `docs/i18n/ru/` names things. Follow it so pages read as one text, and extend it when a new term needs a fixed translation.

## Voice

- Write for server administrators the way an experienced Russian technical writer would: short, direct sentences, not a word-for-word copy of the English. Rebuild sentences where Russian word order needs it.
- Address the reader as «вы», lowercase. Instructions use the imperative: «Откройте», «Скопируйте», «Перезапустите».
- Use «ёлочки» for quotes and a spaced em dash ( — ) between clauses. Write «ё» where it is standard (ещё, её, всё).
- Keep the English product and component names: Identica, Velocity, BungeeCord, Waterfall, Redis, Modrinth, GitHub, Discord, PicoLimbo, PlayerBalancer, Strata, Dialectica, Jdbi, Gradle.

## Never translate

- Code, inline code, code blocks, config keys and values (`journeyMode`, `INTERACTIVE`, `"10m"`), commands (`/identica admin reload`), file and folder names, permissions, placeholders (`{username}`), class and method names.
- Link targets and component props that are identifiers (`<Tab value="YAML">`, `<Badge type="warning">`, `<Callout type="error">`).
- Provider names when they name the provider: Credential, Premium. Write «провайдер Credential», «провайдер Premium».

## Headings

Keep the English anchor of every heading you translate by adding it in brackets, so links into the page work in both languages:

```mdx
## Common value formats            →  ## Распространённые форматы значений [#common-value-formats]
```

The anchor is the English heading in lowercase with spaces as hyphens and punctuation removed. Headings that are config keys (`### bridgeTtl`) stay as they are and need no anchor. Installation steps end in `[step]`; translate them and keep `[step]` last, without an anchor: `### Скачайте файлы [step]`.

## Field descriptions

Configuration pages describe each setting with the same labels:

| English | Russian |
| --- | --- |
| Takes: | Принимает: (then the accusative: «строку», «положительную длительность») |
| Current default: | По умолчанию: |
| Affects: | Влияет на: |
| a positive duration | положительная длительность |
| `true` or `false` | `true` или `false` |

Setting flags are badges; translate their text, keep `type`:

| English | Russian |
| --- | --- |
| Advanced | Для опытных |
| Warning | Осторожно |
| Danger | Опасно |
| Reserved | Зарезервировано |

## Terms

| English | Russian | Notes |
| --- | --- | --- |
| authentication | аутентификация | The category of plugins like AuthMe is «плагины авторизации», as the community calls them. |
| authentication flow | процесс входа | |
| registration | регистрация | |
| account migration | перенос аккаунта, миграция | «миграция» for the scenario and config (`migration`). |
| provider | провайдер | |
| provider-oriented | построенный на провайдерах | |
| premium account | лицензионный аккаунт | |
| offline account | пиратский (офлайн) аккаунт | Spell out «пиратский (офлайн)» on first use in a page, then «офлайн-аккаунт». |
| premium username | ник лицензионного аккаунта | |
| identity | идентичность | «плагин идентификации» for "identity plugin". |
| provider guarantee | гарантия провайдера | Keep `ProviderGuarantee` as code. |
| engine | движок | |
| stage | этап | Kept apart from step («шаг»). |
| handshake | рукопожатие | |
| listener | слушатель | |
| username | ник | «имя пользователя» only where the API means a username field. |
| username authority | право на ник | Which source decides a player's nickname. |
| owned username | ник, которым владеет провайдер | Keep `OWNED_USERNAME` as code. |
| holder (of a username) | держатель ника | The account that holds the name in a conflict; the arriving side is «входящий». |
| credential | учётные данные | Usually the password. |
| password | пароль | |
| session | сессия | |
| player | игрок | |
| server operator, operator | администратор сервера, администратор | |
| network | сеть серверов, сеть | |
| proxy | прокси | Velocity or BungeeCord. Neuter: «прокси запущен», «на прокси». |
| backend server | бэкенд-сервер | |
| runtime (a running Identica) | экземпляр Identica | «несколько экземпляров» for multiple runtimes. |
| runtime jar | основной jar-файл | |
| bundle jar | универсальный jar-файл (BUNDLE) | |
| platform-specific jar | jar-файл для конкретной платформы | |
| jar, artifact | jar-файл, файл сборки | |
| release | релиз | |
| development build | сборка для разработки | |
| first boot | первый запуск | |
| pipeline | конвейер | Keep `pipelineTtl` and other keys as code. |
| journey | прохождение | The player's path through a scenario. |
| journey mode | режим прохождения | |
| journey policy | политика прохождения | |
| seamless | бесшовный | Value stays `SEAMLESS`. |
| interactive | интерактивный | Value stays `INTERACTIVE`. |
| scenario | сценарий | authentication, registration and migration scenarios. |
| step | шаг | |
| prompt | запрос | What the player is asked to enter. |
| enrollment | привязка | Choosing a provider for a new account. |
| resume | продолжение, возобновление | «продолжить прохождение». |
| routing | маршрутизация | |
| route | маршрут | |
| target | цель, целевой сервер | |
| step routing / completion routing | маршрутизация шагов / маршрутизация завершения | |
| entrypoint | точка входа | |
| feature | функция | Built-in features: «встроенные функции». |
| provider override | переопределение для провайдера | |
| shared defaults | общие значения по умолчанию | |
| recognition | распознавание | |
| verification | подтверждение, верификация | «подтверждение» in prose, «верификация» for the feature and 2FA context. |
| verification code | код подтверждения | |
| two-factor authentication, 2FA | двухфакторная аутентификация, 2FA | |
| Sentinel | Sentinel | Feature name, not translated. |
| restriction | ограничение | |
| join restriction | ограничение входа | |
| brute-force | перебор паролей | |
| conflict | конфликт | |
| resolver | обработчик конфликтов | Keep `resolvers` as code. |
| conflict key | ключ конфликта | |
| replication | репликация | |
| shared state | общее состояние | |
| restart continuity | сохранение состояния после перезапуска | |
| cache | кэш | |
| namespace | пространство имён | |
| channel | канал | |
| persistence | хранение данных | Section title «Хранилище». |
| database | база данных | |
| schema | схема | |
| TTL | TTL, время жизни | «время жизни (TTL)» on first use in a page. |
| duration | длительность | |
| default | значение по умолчанию | |
| reload | перезагрузка | `/identica admin reload` stays code. |
| command alias | псевдоним команды | |
| permission | право, разрешение | Keep permission nodes as code. |
| message | сообщение | |
| placeholder | плейсхолдер | |
| event | событие | |
| addon | аддон | |
| API | API | |
| statistics | статистика | |
| limbo server | лимбо-сервер | |
| load balancing | балансировка нагрузки | |
| command filter, command blocker | фильтр команд, блокировщик команд | |

## Navigation

| English | Russian |
| --- | --- |
| Docs | Документация |
| Getting Started | Начало работы |
| Extensions | Расширения |
| Reference | Справочник |
| Overview | Обзор |
| Installation | Установка |
| Normal Installation | Обычная установка |
| Advanced Installation | Расширенная установка |
| Providers | Провайдеры |
| Addons | Аддоны |
| Configuration | Конфигурация |
| Developer | Разработчикам |
| Support | Поддержка |
| FAQ | Вопросы и ответы |
| Official | Официальные |
| Community | От сообщества |
| Settings | Настройки |
| Features | Функции |
| Restriction | Ограничения |
| Verification | Подтверждение |
| Identity | Идентичность |
| Advanced (developer topics) | Продвинутые темы |
