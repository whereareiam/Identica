package me.whereareiam.identica.model;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents an authenticated session stored in the session cache.
 * <p>
 * Sessions may be partially populated before storage. The session service
 * normalizes missing fields like {@code sessionId}, {@code createdAt},
 * {@code effectiveUsername} and the server id of {@code connection} during storage.
 * <p>
 * A session belongs to the player connection that opened it. Another login of the same
 * account from a different connection is a concurrent login, which the configured
 * session concurrency policy decides.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Session {
	/**
	 * Session identifier used for lookups; generated when missing during storage.
	 */
	private @Nullable String sessionId;
	/**
	 * Identica account UUID; required for persistence and indexing.
	 */
	private @NotNull UUID uniqueId;

	/**
	 * Provider ID that issued the session (e.g., Premium/Credential).
	 */
	private @Nullable String providerId;
	/**
	 * Provider subject identifier for lookups.
	 */
	private @Nullable String providerSubject;

	/**
	 * Username observed when the session was created.
	 */
	private @Nullable String originalUsername;
	/**
	 * Effective username after conflict resolution.
	 */
	private @Nullable String effectiveUsername;

	/**
	 * Player connection the session belongs to, or {@code null} when the session was opened
	 * without one.
	 */
	private @Nullable SessionConnection connection;

	/**
	 * Last known IP address for the session.
	 */
	private @Nullable String ip;
	/**
	 * Creation timestamp (epoch millis).
	 */
	private long createdAt;

	/**
	 * Returns whether this session belongs to a connection.
	 *
	 * @param connection connection to compare with
	 * @return {@code true} when this session's connection is the same connection on the same proxy
	 */
	public boolean belongsTo(@Nullable SessionConnection connection) {
		return this.connection != null && this.connection.sameAs(connection);
	}

	/**
	 * Continues another session when both belong to the same connection: the player who opened the
	 * existing session logs in again on the connection it holds, so this session takes over its id.
	 * A session of another connection is never adopted, since that is a concurrent login.
	 *
	 * @param existing existing session candidate
	 * @return {@code true} when the session id was adopted
	 */
	public boolean adoptSessionIdFrom(@Nullable Session existing) {
		if (hasText(sessionId)) return false;
		if (existing == null || !existing.belongsTo(connection)) return false;
		if (!hasText(existing.sessionId)) return false;

		sessionId = existing.sessionId;
		return true;
	}

	private static boolean hasText(@Nullable String value) {
		return value != null && !value.trim().isEmpty();
	}
}
