package me.whereareiam.identica.type.session;

/**
 * Policy for a login of an account that already has a session through another connection. An account has
 * one active session, so the policy decides which of the two connections keeps it.
 */
@SuppressWarnings("unused")
public enum SessionConcurrencyPolicy {
	/**
	 * Replace the existing session when a new one is opened, disconnecting the connection that held it.
	 */
	REPLACE_EXISTING,
	/**
	 * Reject opening a new session when one already exists.
	 */
	REJECT_NEW;

	/**
	 * Returns whether a new session replaces the existing one.
	 *
	 * @return {@code true} for {@link #REPLACE_EXISTING}
	 */
	public boolean replacesExisting() {
		return this == REPLACE_EXISTING;
	}

	/**
	 * Returns whether a new session is refused while another one exists.
	 *
	 * @return {@code true} for {@link #REJECT_NEW}
	 */
	public boolean rejectsNew() {
		return this == REJECT_NEW;
	}
}
