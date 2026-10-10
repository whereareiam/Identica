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
 * The player connection a session belongs to: the connection's unique id on the proxy that holds it, and that
 * proxy's replication server id.
 *
 * <p>Offline players and premium players keep their unique id on every proxy, so the connection unique id alone
 * does not tell two proxies' connections apart; the server id does. A connection whose server id is missing
 * means a connection held by this proxy, and the session service fills in this proxy's
 * {@code replication.serverId} when it opens or closes a session.</p>
 *
 * <pre>{@code
 * Session session = Session.builder()
 *         .uniqueId(accountUniqueId)
 *         .connection(SessionConnection.of(player.getUniqueId()))
 *         .build();
 * }</pre>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SessionConnection {
	/**
	 * Replication server id of the proxy that holds the connection, or {@code null} for this proxy.
	 */
	private @Nullable String serverId;
	/**
	 * Unique id of the player connection on that proxy.
	 */
	private @NotNull UUID connectionUniqueId;

	/**
	 * Describes a connection held by this proxy.
	 *
	 * @param connectionUniqueId unique id of the player connection
	 * @return connection whose server id the session service fills in
	 */
	public static @NotNull SessionConnection of(@NotNull UUID connectionUniqueId) {
		return new SessionConnection(null, connectionUniqueId);
	}

	/**
	 * Returns whether this and another value describe the same connection on the same proxy. Two connections
	 * are only the same when both name their proxy.
	 *
	 * @param other another connection
	 * @return {@code true} when server ids match (case-insensitive) and connection unique ids are equal
	 */
	public boolean sameAs(@Nullable SessionConnection other) {
		if (other == null || serverId == null || other.serverId == null) return false;
		if (serverId.isBlank() || !serverId.trim().equalsIgnoreCase(other.serverId.trim())) return false;

		return connectionUniqueId.equals(other.connectionUniqueId);
	}
}
