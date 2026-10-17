---
version: 3.0.2
---

# Update 3.0.2 - Command Cooldowns

Identica 3.0.2 makes the `cooldown` of a command in `commands.yml` work: a player who runs a command again within its cooldown is told how long to wait.

## Upgrading

* Replace the plugin and provider jars. Cooldowns now apply, at two seconds per command by default; the console is never limited.
* The first start gives Identica's own commands and the join restriction commands a cooldown of their own where they still had the untouched default of earlier versions, two seconds in the group `global`, so different commands can be used one after another. In `providers/Credential/commands.yml` and `providers/Premium/commands.yml` that default stays as it is, so their commands share one two-second cooldown; set `group: ""` there to give each command its own.
