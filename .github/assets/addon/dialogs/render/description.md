![Identica-Dialogs, Dialogs. Identica's prompts as Minecraft dialogs: whenever a provider waits for a player, they get a window with fields and buttons instead of typing commands into chat. Runs on Velocity and BungeeCord. Dialogs from 1.21.6, chat below.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/01-header.png?raw=true)

![Dependencies & Information. Identica: An addon for Identica. Install it on the proxy next to Identica, from the same release. Velocity: Needs PacketEvents 2.14 or newer, because Velocity cannot show dialogs itself. BungeeCord: Works with BungeeCord and Waterfall builds that support Minecraft 1.21.6 or newer. Good to know: Players on Minecraft 1.21.5 or older keep Identica's chat prompts.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/02-board-dependencies-information.png?raw=true)

![Prompts players can click. When a provider waits for the player, such as for a password or a code, the player gets a dialog with fields and buttons. A button runs the same command the player would type, so the provider handles the answer as before. A dialog in Minecraft asking for a password, with a Log in button and Use chat instead. Ready to use: shipped dialogs cover both official providers, from login and registration to two-factor codes. Answers inside: a wrong answer shows the dialog again with Identica's message. Chat stays: Use chat instead closes the dialog until the player reconnects.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/03-feature-prompts-players-can-click.png?raw=true)

![Your rules, your dialogs. Decide in rules.yml which waiting step shows which dialog, and write the dialogs yourself. Conditions: match by step, provider, scenario, host or target server. One file per dialog: MiniMessage text, text fields, toggles, options and sliders. Keep chat: a rule with dialog none leaves a step in chat.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/04-feature-your-rules-your-dialogs.png?raw=true)

![Configuration example from rules.yml: rules:; - when:; step: password-authentication-password; dialog: login; - when:; step: password-registration; host: event.example.net; dialog: event-register; - when:; step: password-authentication-verification; dialog: none](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/05-config-rules.png?raw=true)

![Getting started. Step 1, Install: Put Identica-Dialogs next to Identica; on Velocity add PacketEvents. Step 2, Start: Start the proxy once; the addon writes its rules and dialogs. Step 3, Adjust: Edit the files, then run /identica admin reload.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/06-steps-getting-started.png?raw=true)

![Works with](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/07-list-works-with-head.png?raw=true)

[![Identica, Required: The identity plugin whose prompts the addon shows, from any provider.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/07-list-works-with-identica.png?raw=true)](https://modrinth.com/plugin/identica)

[![Credential Provider, Free: Dialogs for login, registration, migration and two-factor codes come with the addon.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/07-list-works-with-credential.png?raw=true)](https://modrinth.com/plugin/identica-credential-provider)

[![Premium Provider, Free: Dialogs for its two-factor codes come with the addon.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/07-list-works-with-premium.png?raw=true)](https://modrinth.com/plugin/identica-premium-provider)

![](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/07-list-works-with-bottom.png?raw=true)

[![whereareiam says: Stuck? The team and the community answer fast.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/addon/dialogs/render/08-support.png?raw=true)](https://discord.arcadeya.com/)
