package me.whereareiam.identica.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents an authenticated session stored in the session cache.
 * <p>
 * Sessions may be partially populated before storage. The session service
 * normalizes missing fields like {@code sessionId}, {@code createdAt},
 * {@code effectiveUsername} during storage.
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
	 * Provider ID that issued the session (e.g., Premium/Password).
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
	 * Last known IP address for the session.
	 */
	private @Nullable String ip;
	/**
	 * Creation timestamp (epoch millis).
	 */
	private long createdAt;

	/**
	 * Returns whether this session represents the same provider subject as another session.
	 *
	 * @param other another session
	 * @return {@code true} if provider id and provider subject match (case-insensitive)
	 */
	public boolean matchesProviderSubject(@Nullable Session other) {
		if (other == null)
			return false;
		return equalsIgnoreCaseNonBlank(providerId, other.providerId)
				&& equalsIgnoreCaseNonBlank(providerSubject, other.providerSubject);
	}

	/**
	 * Tries to reuse the session id from another session when both describe the same provider subject.
	 *
	 * @param existing existing session candidate
	 * @return {@code true} when the session id was adopted
	 */
	public boolean adoptSessionIdFrom(@Nullable Session existing) {
		if (hasText(sessionId)) return false;
		if (existing == null || !matchesProviderSubject(existing)) return false;
		if (!hasText(existing.sessionId)) return false;

		sessionId = existing.sessionId;
		return true;
	}

	private static boolean equalsIgnoreCaseNonBlank(@Nullable String left, @Nullable String right) {
		if (left == null || right == null) return false;

		String normalizedLeft = left.trim();
		String normalizedRight = right.trim();
		if (normalizedLeft.isEmpty() || normalizedRight.isEmpty())
			return false;

		return normalizedLeft.equalsIgnoreCase(normalizedRight);
	}

	private static boolean hasText(@Nullable String value) {
		return value != null && !value.trim().isEmpty();
	}
}
