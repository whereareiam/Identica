---
title: Dialogs
description: Show Identica's prompts as Minecraft dialogs, with chat as the fallback.
---

The dialogs addon shows Identica's prompts as Minecraft dialogs: a window with a password field and a button,
instead of a chat message asking the player to type `/login <password>`. The button runs the same command a
player would type, so Identica handles the answer exactly as it handles chat.

Players keep Identica's chat prompts underneath. A player whose client has no dialogs, such as Minecraft 1.21.5 or
older, or who presses **Use chat instead**, answers in chat as before.

## Install it

| Proxy | Needs |
|---|---|
| Velocity | Identica and [PacketEvents](https://modrinth.com/plugin/packetevents) 2.14 or newer, because Velocity cannot show dialogs itself |
| BungeeCord, Waterfall | Identica, on a version with dialogs (1.21.6 clients or newer) |

1. Download `Identica-Dialogs-<version>.jar` from the same release as Identica.
2. On Velocity, install PacketEvents.
3. Put the jar into the proxy's `plugins/` folder and restart the proxy.

On the first start the addon writes its files to `plugins/identica-dialogs/` on Velocity and
`plugins/Identica-Dialogs/` on BungeeCord. They already cover the official providers: Credential's login,
registration and migration prompts, and two-factor codes. With them, players of a network that uses Credential see
dialogs right away.

To check it works, join with a 1.21.6 or newer client: instead of the registration message in chat, a window titled
**Identica** asks for a password. If it does not appear, check the proxy log for `identica-dialogs` on Velocity, and
that PacketEvents loaded before it, or for `Identica-Dialogs` on BungeeCord.

## How it decides which dialog to show

When an Identica step waits for the player, such as Credential's password step, the addon tries the rules in
`rules.yml` from top to bottom. The first rule whose conditions all match picks the dialog. If no rule matches, the
player gets no dialog and answers in chat. When the step stops waiting, because the player answered or left, the
addon closes the dialog.

```yaml
rules:
  - when:
      step: password-authentication-password
    dialog: login
  - when:
      step: password-registration
      host: event.example.net
    dialog: event-register
  - when:
      step: password-registration
    dialog: register
  - when:
      step: password-authentication-verification
    dialog: none
```

The second rule shows its own registration dialog to players who connect through `event.example.net`; the third
covers everyone else. The last one keeps two-factor codes in chat: `dialog: none` shows no dialog.

| Condition | Matches |
|---|---|
| `step` | the waiting step, such as `password-registration`; see [the shipped rules](#the-shipped-rules) for Credential's steps |
| `scenario` | `REGISTRATION`, `AUTHENTICATION` or `MIGRATION` |
| `provider` | the provider's ID, such as `credential` |
| `stage` | the journey stage, such as `provider` |
| `host` | the hostname the player connected through |
| `server` | the server the player wants to reach |

Values are compared without regard to letter case. A rule without `on` reacts to waiting steps; other values of
`on` belong to triggers that other plugins add.

### The shipped rules

| Step | Dialog |
|---|---|
| `password-authentication-password`, `password-credential-presence`, `password-migration-authentication` | `login` |
| `password-registration`, `password-migration-registration` | `register` |
| `password-registration-confirm`, `password-migration-confirm` | `register-confirm` |
| `password-authentication-verification`, `premium-authentication-verification` | `verification` |

A rule for a provider you do not use never matches. Delete it or leave it.

## Change a dialog

Each file in `dialogs/` is one dialog, named after the file: `dialogs/login.yml` is `login`. The shipped dialogs are
written only on the first start, so a dialog you delete stays deleted. Add a file to create your own.

```yaml
type: MULTI_ACTION
title: "<green><bold>Identica"
body:
  - "<white>Welcome back! Enter your password to log in."
  - "{message}"
inputs:
  - key: password
    type: TEXT
    label: "<white>Password"
    maxLength: 64
actions:
  - label: "<green>Log in"
    type: COMMAND
    value: "login $(password)"
exitAction:
  label: "<gray>Use chat instead"
  type: CHAT
closeWithEscape: false
afterAction: NONE
```

Text is MiniMessage, as in Identica's messages, with Identica's placeholders such as `{prefix}`.

- `{message}` holds Identica's answer to the player's previous attempt at the same step, such as **Invalid
  password**. On the first prompt it is empty, and a body line that renders empty is left out.
- `value` of a `COMMAND` button is the command without its slash. `$(password)` is replaced with what the player
  typed into the input with the key `password`. If you renamed Identica's commands, change the command here too.
- `afterAction: NONE` keeps the dialog open after a button: after a wrong password the addon shows it again with
  `{message}`, and once the step is done the addon closes it.

| Field | Values |
|---|---|
| `type` | `MULTI_ACTION` (any number of `actions`, `exitAction` below them), `NOTICE` (the first action only), `CONFIRMATION` (the first action and `exitAction`) |
| `inputs[].type` | `TEXT` (`initial`, `maxLength`), `BOOLEAN` (`checked`, `onTrue`, `onFalse`), `SINGLE_OPTION` (`options` with `id`, `display`, `initial`), `NUMBER_RANGE` (`start`, `end`, `step`) |
| `actions[].type` | `COMMAND`, `URL`, `COPY` (copies `value`), `CLOSE`, `CHAT` |
| `afterAction` | `NONE`, `CLOSE`, `WAIT_FOR_RESPONSE` |

A text input does not hide what the player types, as typing a password into chat does not either.

After editing, run `/identica admin reload`. The new texts appear the next time a dialog is shown.

## Let players choose chat

A button with `type: CHAT` closes the dialog and shows that player no more dialogs until they reconnect. The
shipped dialogs have it as **Use chat instead** below the form. Remove it from a dialog to keep players in
dialogs.

## Limits

- A wrong two-factor code leaves the verification dialog open, because Identica answers it only in chat. The player
  can try again in the same dialog.
- A client sees a dialog only once it is on a server, so a dialog appears just after the player arrives on the
  authentication server.

## Use it from another plugin

Other plugins can show the configured dialogs and add moments that rules react to. Depend on
`me.whereareiam.identica.addon:dialogs-api` and on the addon as a plugin dependency.

```java
Dialogs.whenReady(dialogs -> {
    dialogs.show(player.getUniqueId(), "rules", Map.of());
    dialogs.register(new MyTrigger());
});
```

A `DialogTrigger` reports moments to the `TriggerSink` it is started with: `fire` with a `TriggerContext` (the
player, the trigger's ID and the attributes rules match against) shows the dialog the first matching rule names,
and `settle` closes it. Rules name the trigger in `on`.
