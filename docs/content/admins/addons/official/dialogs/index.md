---
title: Dialogs
description: Show Identica's prompts as Minecraft dialogs, with chat as the fallback.
---

The dialogs addon shows Identica's prompts as Minecraft dialogs: a window with a password field and a button,
instead of a chat message asking the player to type `/login <password>`. When the player presses the button,
the proxy runs the same command the player would type, as that player, so Identica handles the answer exactly as it
handles chat.

A dialog replaces Identica's chat message while it is open. A player whose client has no dialogs, such as Minecraft
1.21.5 or older, answers in chat as before.

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

To check it works, join with a 1.21.6 or newer client: a window greets you with **Welcome!** and asks for a
password. If it does not appear, check the proxy log for `identica-dialogs` on Velocity, and
that PacketEvents loaded before it, or for `Identica-Dialogs` on BungeeCord.

## How it decides which dialog to show

Everything Identica tells a player is a message with an ID, such as `credential:password-invalid` for a wrong
password. A message that asks the player for something also names what it asks for, its **prompt**, such as
`credential:password` for an account's password: the first ask and every ask again after a wrong password share it.

When Identica tells a player a message, the addon tries the rules in `rules.yml` from top to bottom, and the first
rule whose conditions all match picks the dialog, which the player sees instead of the chat message. If no rule
matches, or the rule shows none, the player gets the message in chat. When Identica tells the player its next message,
or the player has logged in, the addon closes the dialog.

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

Every rule names a `prompt` or a `message`; a rule with neither matches no message.

| Condition | Matches |
|---|---|
| `prompt` | what the message asks for; see [the prompts](#prompts-and-messages) |
| `message` | the message's ID, for one message of a prompt, such as only the ask again after a wrong password; see [the messages](#prompts-and-messages) |
| `scenario` | `REGISTRATION`, `AUTHENTICATION` or `MIGRATION`, what the player still has to do |
| `host` | the hostname the player connected through |

Values are compared without regard to letter case.

### The shipped rules

| Prompt | Dialog |
|---|---|
| `credential:password` | `login` |
| `credential:new-password` | `register` |
| `credential:repeat-password` | `register-confirm` |
| `verification:code` | `verification` |

Moving an account from Premium to Credential asks with the same prompts as registering, so it uses the same dialogs.
A rule for a provider you do not use never matches. Delete it or leave it.

### Prompts and messages

| Prompt | Asks for | Messages that ask again |
|---|---|---|
| `credential:password` | the password of an existing account | `credential:password-invalid` (wrong password), `credential:already-registered` (the player tried to register a name that already has an account) |
| `credential:new-password` | a new password | `credential:passwords-differ` (the repetition did not match), `credential:no-new-password` (the player repeated a password before choosing one), `credential:password-too-short`, `credential:password-too-long`, `credential:password-has-spaces`, `credential:password-missing-upper`, `credential:password-missing-lower`, `credential:password-missing-number`, `credential:password-missing-special` (the password breaks a rule of Credential's settings) |
| `credential:repeat-password` | the new password once more | |
| `verification:code` | a two-factor code | `verification:code-invalid` (wrong code), `verification:code-no-challenge` (no code was expected) |
| `identica:provider-choice` | the provider to register with | |

The first ask of each prompt has an ID of its own too, such as `credential:password` for the password of an existing
account.

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
    type: ANSWER
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

- `{answer}` holds the dialog's own text for the message it shows, from `answers`, such as **Wrong password. Try
  again.** after a wrong password. On the first ask, and for a message the dialog has no text for, it is empty, and a
  body line that renders empty is left out.
- `{message}` holds Identica's own text of the message, from its messages file, for a dialog that shows it instead of
  words of its own. The player does not see the message in chat while the dialog shows it.
- An `ANSWER` button answers what the message asks for, with the inputs whose keys name what is asked, such as
  `password` or `code`. Identica builds the command, so the button keeps working when you rename its commands. The
  proxy runs the command as the player, and only the buttons of the dialog the player was shown can run one.
- A `COMMAND` button runs its own command, without its slash, the same way. `$(password)` is replaced with what the
  player typed into the input with the key `password`.
- The `DISCONNECT` button below the form, **Leave**, lets a player who does not want to log in leave the server.
  `value` is the reason their client shows. Only the button of the dialog the player was shown disconnects them.
- `afterAction: NONE` keeps the dialog open after a button: after a wrong password the addon shows it again with
  `{answer}`, and once the player answered the addon closes it.

| Field | Values |
|---|---|
| `type` | `MULTI_ACTION` (any number of `actions`, `exitAction` below them), `NOTICE` (the first action only), `CONFIRMATION` (the first action and `exitAction`) |
| `inputs[].type` | `TEXT` (`initial`, `maxLength`), `BOOLEAN` (`checked`, `onTrue`, `onFalse`), `SINGLE_OPTION` (`options` with `id`, `display`, `initial`), `NUMBER_RANGE` (`start`, `end`, `step`) |
| `actions[].type` | `ANSWER`, `COMMAND`, `URL`, `COPY` (copies `value`), `CLOSE`, `CHAT`, `DISCONNECT` (with `value` as the reason) |
| `afterAction` | `NONE`, `CLOSE`, `WAIT_FOR_RESPONSE` |
| `answers` | text for `{answer}` by message ID; see [the messages](#prompts-and-messages) |

A text input does not hide what the player types, as typing a password into chat does not either.

After editing, run `/identica admin reload`. The new texts appear the next time a dialog is shown.

## Let players choose chat

The shipped dialogs keep players in dialogs. To let them answer in chat instead, add a button with `type: CHAT` to a
dialog's `actions`, next to its main button:

```yaml
actions:
  - label: "<green>Log in"
    type: ANSWER
  - label: "<white>Use chat instead"
    type: CHAT
```

The button closes the dialog, tells the player the message in chat, and shows them no more dialogs until they
reconnect.

## Limits

- A client sees a dialog only once it is on a server, so a dialog appears just after the player arrives on the
  authentication server.

## Use it from another plugin

Other plugins and providers can show their own messages as dialogs and show the configured dialogs directly. See
[the dialogs addon's API](../../../../developers/api/dialogs/index.mdx).
