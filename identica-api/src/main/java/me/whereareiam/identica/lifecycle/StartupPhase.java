package me.whereareiam.identica.lifecycle;

/**
 * The phases Identica starts in, in the order they run. Every {@link RuntimeLifecycle} names the phase it belongs
 * to; Identica starts all lifecycles of a phase before the first one of the next phase, and stops them phase by
 * phase in reverse.
 */
public enum StartupPhase {
	/** The data directory is migrated to the current layout and the configuration documents are loaded. */
	CONFIGURATION,
	/** Identica's log is connected to the proxy console, at the level the settings name. */
	LOGGING,
	/** The stores Identica keeps its state in are opened: the database with its core schema, and replication. */
	PERSISTENCE,
	/** The core subsystems start: the API, usernames, sessions, routing and the pipelines. */
	RUNTIME,
	/** The compiled features start. */
	FEATURES,
	/** The providers are loaded. */
	PROVIDERS,
	/** The integrations with outside services start, such as usage statistics. */
	INTEGRATIONS,
	/** Identica starts handling player connections and reports itself ready. */
	CONNECTIONS
}
