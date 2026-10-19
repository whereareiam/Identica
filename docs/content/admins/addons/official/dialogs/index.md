---
title: Dialogs
description: Show Identica's prompts as Minecraft dialogs, with chat as the fallback.
---

The dialogs addon shows Identica's prompts as Minecraft dialogs: a window with a password field and a button,
instead of a chat message asking the player to type `/login <password>`. When the player presses the button,
the proxy runs the same command the player would type, as that player, so Identica handles the answer exactly as it
handles chat.

Players are asked on the loading screen, before they enter any server: Identica keeps them there until they are in
and then sends them straight to where they play. A player whose client has no dialogs, such as Minecraft 1.21.5 or
older, answers on the authentication server in chat as before.

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

To check it works, join with a 1.21.6 or newer client: on the loading screen, a window greets you with
**Welcome!** and asks for a password. If it does not appear, check the proxy log for `identica-dialogs` on
Velocity, and that PacketEvents loaded before it, or for `Identica-Dialogs` on BungeeCord.

## How it decides which dialog to show

When an Identica step waits for the player, it names what it asks for, its **prompt**, such as `credential:password`
for an account's password. The addon tries the rules in `rules.yml` from top to bottom, and the first rule whose
conditions all match picks the dialog. If no rule matches, the player gets no dialog and answers in chat. When the step
stops waiting, because the player answered or left, the addon closes the dialog.

```yaml
rules:
  - when:
      prompt: credential:password
    dialog: login
  - when:
      prompt: credential:new-password
      host: event.example.net
    dialog: event-register
  - when:
      prompt: credential:new-password
    dialog: register
  - when:
      prompt: verification:code
    dialog: none
```

The second rule shows its own registration dialog to players who connect through `event.example.net`; the third
covers everyone else. The last one keeps two-factor codes in chat: `dialog: none` shows no dialog.

| Condition | Matches |
|---|---|
| `prompt` | what the step asks for; see [the prompts](#prompts-and-answers) |
| `answer` | why the step asks again, such as after a wrong password; see [the answers](#prompts-and-answers) |
| `scenario` | `REGISTRATION`, `AUTHENTICATION` or `MIGRATION` |
| `provider` | the provider's ID, such as `credential` |
| `stage` | the journey stage, such as `provider` |
| `step` | the waiting step, such as `password-registration` |
| `host` | the hostname the player connected through |
| `server` | the server the player wants to reach |

Values are compared without regard to letter case. A rule without `on` reacts to waiting steps; other values of
`on` belong to triggers that other plugins add.

### The shipped rules

| Prompt | Dialog |
|---|---|
| `credential:password` | `login` |
| `credential:new-password` | `register` |
| `credential:repeat-password` | `register-confirm` |
| `verification:code` | `verification` |

Moving an account from Premium to Credential asks with the same prompts as registering, so it uses the same dialogs.
A rule for a provider you do not use never matches. Delete it or leave it.

### Prompts and answers

| Prompt | Asks for | Answers |
|---|---|---|
| `credential:password` | the password of an existing account | `credential:password-invalid` (wrong password), `credential:already-registered` (the player tried to register a name that already has an account) |
| `credential:new-password` | a new password | `credential:passwords-differ` (the repetition did not match), `credential:no-new-password` (the player repeated a password before choosing one), `credential:password-too-short`, `credential:password-too-long`, `credential:password-has-spaces`, `credential:password-missing-upper`, `credential:password-missing-lower`, `credential:password-missing-number`, `credential:password-missing-special` (the password breaks a rule of Credential's settings) |
| `credential:repeat-password` | the new password once more | |
| `verification:code` | a two-factor code | `verification:code-invalid` (wrong code), `verification:no-challenge` (no code was expected) |
| `identica:provider-choice` | the provider to register with | |

### Ask on the loading screen

`loadingScreen` in `rules.yml` decides which players are asked before they enter any server, on both Velocity and
BungeeCord.

```yaml
loadingScreen:
  enabled: true
  when:
    host: play.example.net
```

The addon decides once per connection, when the player's first step waits, so `when` takes the conditions known
then: `scenario`, `provider`, `host` and `server`. Without `when`, every player with a 1.21.6 or newer client is
asked on the loading screen. Set `enabled: false` to ask everyone in the world of the authentication server instead.

While a player is on the loading screen:

- a prompt whose rule shows no dialog, such as one with `dialog: none`, sends the player to the authentication
  server, where they answer in chat and see the remaining dialogs there;
- the dialog shows Identica's answers, such as a wrong password, through `{answer}`; Identica's chat messages to the
  player wait until the player has entered a server;
- a player who does not answer is disconnected once their journey expires, after the scenario's `pipelineTtl` in
  Identica's engine settings.

On BungeeCord, the player waits in the configuration of the first server BungeeCord connects them to. That server
sees a connecting player, who enters its world only once they are in.

## Change a dialog

Each file in `dialogs/` is one dialog, named after the file: `dialogs/login.yml` is `login`. The shipped dialogs are
written only on the first start, so a dialog you delete stays deleted. Add a file to create your own.

```yaml
type: MULTI_ACTION
title: ""
body:
  - "<white>Welcome back!"
  - "<gray>Enter your password to continue to the server."
  - "{answer}"
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
  label: "<white>Leave"
  type: DISCONNECT
  value: "<white>You left the server."
answers:
  credential:password-invalid: "<red>Wrong password. Try again."
  credential:already-registered: "<red>This name is already registered. Enter its password to log in."
closeWithEscape: false
afterAction: NONE
```

Text is MiniMessage, as in Identica's messages, with Identica's placeholders such as `{prefix}`.

- `{answer}` holds the dialog's own text for why Identica asks again, from `answers`, such as **Wrong password. Try
  again.** after a wrong password. On the first prompt, and for an answer the dialog has no text for, it is empty, and
  a body line that renders empty is left out. The chat behind the dialog still shows Identica's own message.
- `value` of a `COMMAND` button is the command without its slash. `$(password)` is replaced with what the player
  typed into the input with the key `password`. The proxy runs the command as the player, so it works whether or not
  the player's client knows the command, and only the buttons of the dialog the player was shown can run one. If you
  renamed Identica's commands, change the command here too.
- The `DISCONNECT` button below the form, **Leave**, lets a player who does not want to log in leave the server.
  `value` is the reason their client shows. Only the button of the dialog the player was shown disconnects them.
- `afterAction: NONE` keeps the dialog open after a button: after a wrong password the addon shows it again with
  `{answer}`, and once the step is done the addon closes it.

| Field | Values |
|---|---|
| `type` | `MULTI_ACTION` (any number of `actions`, `exitAction` below them), `NOTICE` (the first action only), `CONFIRMATION` (the first action and `exitAction`) |
| `inputs[].type` | `TEXT` (`initial`, `maxLength`), `BOOLEAN` (`checked`, `onTrue`, `onFalse`), `SINGLE_OPTION` (`options` with `id`, `display`, `initial`), `NUMBER_RANGE` (`start`, `end`, `step`) |
| `actions[].type` | `COMMAND`, `URL`, `COPY` (copies `value`), `CLOSE`, `CHAT`, `DISCONNECT` (with `value` as the reason) |
| `afterAction` | `NONE`, `CLOSE`, `WAIT_FOR_RESPONSE` |
| `answers` | text for `{answer}` by answer ID; see [the answers](#prompts-and-answers) |

A text input does not hide what the player types, as typing a password into chat does not either.

After editing, run `/identica admin reload`. The new texts appear the next time a dialog is shown.

## Let players choose chat

The shipped dialogs keep players in dialogs. To let them answer in chat instead, add a button with `type: CHAT` to a
dialog's `actions`, next to its main button:

```yaml
actions:
  - label: "<green>Log in"
    type: COMMAND
    value: "login $(password)"
  - label: "<white>Use chat instead"
    type: CHAT
```

The button closes the dialog and shows that player no more dialogs until they reconnect. On the loading screen,
which has no chat, it sends the player to the authentication server to answer there.

## Limits

- A player who is not asked on the loading screen sees a dialog only once they are on a server, so it appears just
  after they arrive on the authentication server.

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
unique id of the player's connection, the trigger's ID and the attributes rules match against) shows the dialog the
first matching rule names, and `settle` closes it. Rules name the trigger in `on`.
