---
title: Overview
description: Plugins that extend Identica without being a way to log in.
---

Addons are plugins that build on Identica without being a provider themselves, such as a plugin that holds back chat until a player has logged in.

- [Official addons](./official/index.md) are maintained by Identica's developers. Each one is documented here in full: what it does, how to set it up, and its settings.
- [Community addons](./community/index.md) are made by others and documented by their authors. Review them before you install them, and report their issues to their authors.

## Installing an addon

An addon is an ordinary plugin of your proxy: put its jar into the proxy's `plugins/` directory, next to Identica, and restart the proxy. Its files go to its own data directory.

To write an addon of your own, see [Writing an addon](../../developers/extending/addons/index.mdx).
