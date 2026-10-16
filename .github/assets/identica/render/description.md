![Identica, Identity Provider. A modern identity plugin for Minecraft networks that handles authentication, registration, verification and account migration through one flexible core. Runs on Velocity and BungeeCord. Supported versions: 1.7+.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/01-header.png?raw=true)

![Dependencies & Information. Platform: Identica runs on Velocity, BungeeCord and Waterfall. Install it on your proxy, not on your backend servers. Java: Identica requires Java 21 or newer. Persistence: SQLite works out of the box. Switch to H2, MySQL, MariaDB or PostgreSQL whenever you need to. Replication: Optional Redis replication shares sessions, reservations, conflicts and events between your proxies. Good to know: Identica is the core only. Login methods come from providers, which you download and install separately.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/02-board-dependencies-information.png?raw=true)

![Providers, not modes. Most authentication plugins hard-code premium and password login. Identica treats every login method as a provider that plugs into one core. Pick your providers: install only the login methods your network needs. One flow: Identica picks the provider, guides the player and keeps sessions consistent. Room to grow: add a new provider later without rebuilding your setup.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/03-feature-providers-not-modes.png?raw=true)

![Accounts that move with players. Players switch between providers without losing their account. Provider migration: a player who switches login method keeps their account, even under a new username. Conflict resolution: when two login methods claim the same name, Identica settles it. Verification: protect accounts with a second step such as TOTP 2FA.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/04-feature-accounts-that-move-with-players.png?raw=true)

![Built-in features. Verification: A second step after the provider identifies the player. Recognition: Returning players skip the full login while their signals match. Sentinel: Watches for repeated or risky actions. Restriction: Lets admins temporarily block new joins through a provider.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/05-grid-built-in-features.png?raw=true)

![Official providers](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/06-list-official-providers-head.png?raw=true)

[![Credential Provider, Free: Players register and log in with a password.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/06-list-official-providers-credential.png?raw=true)](https://modrinth.com/plugin/identica-credential-provider)

[![Premium Provider, Free: Players with a Minecraft account join without a password and keep their identity when they change their name.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/06-list-official-providers-premium.png?raw=true)](https://modrinth.com/plugin/identica-premium-provider)

![](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/06-list-official-providers-bottom.png?raw=true)

![Getting started. Step 1, Install: Put Identica into your proxy's plugins folder. Step 2, Add providers: Put the providers you want into Identica's providers folder. Step 3, Configure: Start the proxy once, then adjust the generated files.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/07-steps-getting-started.png?raw=true)

[![whereareiam says: Stuck? The team and the community answer fast.](https://github.com/whereareiam/Identica/blob/dev/.github/assets/identica/render/08-support.png?raw=true)](https://discord.arcadeya.com/)
