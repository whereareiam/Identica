---
version: 3.0.1
---

# Update 3.0.1 - Fixes for Logins, Clusters and SQLite

Identica 3.0.1 fixes players left without a prompt or reason on the auth server, join restrictions and recognition that went stale across the proxies of a cluster, and SQLite keeping the rows of deleted accounts, old password hashes included.

## Upgrading

* Replace the plugin and provider jars; the configuration and the database need no changes from you.
* **SQLite:** the first start deletes the rows that deleted accounts left behind, such as provider profiles and the Credential password history with its old password hashes. From then on SQLite deletes them with the account, like the other databases.
