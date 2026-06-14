package me.whereareiam.identica.type.platform;

/**
 * Defines the required adapter roles that each platform runtime must provide.
 */
public enum PlatformAdapterRole {
	/**
	 * Applies platform-specific handshake instructions.
	 */
	HANDSHAKE_APPLIER_REGISTRY,
	/**
	 * Processes handshake-time connection decisions.
	 */
	HANDSHAKE_DECISION,
	/**
	 * Processes login-time connection decisions.
	 */
	LOGIN_DECISION,
	/**
	 * Processes first-connect resume decisions.
	 */
	RESUME_DECISION,
	/**
	 * Applies platform-specific profile preparation or rewriting.
	 */
	PROFILE,
	/**
	 * Coordinates platform delivery readiness.
	 */
	DELIVERY,
	/**
	 * Executes routing intents against the live proxy platform.
	 */
	ROUTING
}
