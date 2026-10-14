package me.whereareiam.identica.identity.actor;

import lombok.*;
import me.whereareiam.identica.model.identity.IdentityReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Lightweight identity representation for connection/authentication identities.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConnectionIdentity {
	private @NotNull IdentityReference identityReference;
	private @NotNull String username;
	/**
	 * Username the connection was identified by before the first {@link #setUsername rename}, or {@code null}
	 * while it was never renamed.
	 */
	private @Nullable String connectionUsername;
	private @Nullable String ip;
	/**
	 * Address the client used to connect, if available.
	 */
	private @Nullable Origin origin;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	public static class Origin {
		private @NotNull String host;
		private @Nullable Integer port;
	}

	/**
	 * Creates a connection identity without a preassigned unique id.
	 *
	 * @param username username for the connection
	 * @param ip optional IP address
	 */
	public ConnectionIdentity(@NotNull String username, @Nullable String ip) {
		this(new IdentityReference(), username, ip, null);
	}

	/**
	 * Creates a connection identity with a preassigned account UUID.
	 *
	 * @param accountUniqueId resolved account UUID
	 * @param username username for the connection
	 * @param ip optional IP address
	 */
	public ConnectionIdentity(@Nullable UUID accountUniqueId, @NotNull String username, @Nullable String ip) {
		this(IdentityReference.account(accountUniqueId), username, ip, null);
	}

	public ConnectionIdentity(
			@Nullable UUID accountUniqueId,
			@Nullable UUID observedUniqueId,
			@NotNull String username,
			@Nullable String ip
	) {
		this(IdentityReference.builder()
				.accountUniqueId(accountUniqueId)
				.observedUniqueId(observedUniqueId)
				.build(), username, ip, null);
	}

	private ConnectionIdentity(
			@NotNull IdentityReference identityReference,
			@NotNull String username,
			@Nullable String ip
	) {
		this(identityReference, username, ip, null);
	}

	/**
	 * Creates a connection identity.
	 *
	 * @param identityReference unique ids of the connection and its account
	 * @param username username the connection is identified by
	 * @param ip optional IP address
	 * @param origin optional address the client used to connect
	 */
	public ConnectionIdentity(
			@NotNull IdentityReference identityReference,
			@NotNull String username,
			@Nullable String ip,
			@Nullable Origin origin
	) {
		this.identityReference = identityReference;
		this.username = username;
		this.ip = ip;
		this.origin = origin;
	}

	/**
	 * Changes the username the identity goes by, such as the account's stored spelling or a name a conflict rule
	 * gave it. The connection keeps the username it was identified by, which {@link #getConnectionUsername()}
	 * returns, so what belongs to the connection stays found under it.
	 *
	 * @param username new username
	 */
	public void setUsername(@NotNull String username) {
		if (connectionUsername == null && this.username != null && !this.username.equals(username))
			connectionUsername = this.username;

		this.username = username;
	}

	/**
	 * Returns the username the connection was identified by when it was created, whatever it was renamed to
	 * since. Login progress belongs to the connection under this name and its address.
	 *
	 * @return username of the connection
	 */
	public @NotNull String getConnectionUsername() {
		return connectionUsername != null ? connectionUsername : username;
	}

	/**
	 * Keeps the username the connection was identified by, for a copy of another connection's identity.
	 *
	 * @param connectionUsername username of the connection, or {@code null} to use the current username
	 */
	public void setConnectionUsername(@Nullable String connectionUsername) {
		this.connectionUsername = connectionUsername;
	}

	public @Nullable UUID getConnectionUniqueId() {
		return identityReference.getConnectionUniqueId();
	}

	public void setConnectionUniqueId(@Nullable UUID connectionUniqueId) {
		identityReference.setConnectionUniqueId(connectionUniqueId);
	}

	public @Nullable UUID getClaimedUniqueId() {
		return identityReference.getClaimedUniqueId();
	}

	public void setClaimedUniqueId(@Nullable UUID claimedUniqueId) {
		identityReference.setClaimedUniqueId(claimedUniqueId);
	}

	public @Nullable UUID getObservedUniqueId() {
		return identityReference.getObservedUniqueId();
	}

	public void setObservedUniqueId(@Nullable UUID observedUniqueId) {
		identityReference.setObservedUniqueId(observedUniqueId);
	}

	public @Nullable UUID getAccountUniqueId() {
		return identityReference.getAccountUniqueId();
	}

	public void setAccountUniqueId(@Nullable UUID accountUniqueId) {
		identityReference.setAccountUniqueId(accountUniqueId);
	}

	/**
	 * Resolves the UUID that should back the attached live identity.
	 *
	 * <p>When an account UUID has already been resolved, it becomes the canonical
	 * live identity UUID. Otherwise the caller can provide the current
	 * connection-scoped UUID as a temporary fallback.</p>
	 *
	 * @param fallbackConnectionUniqueId live connection UUID to use when the account UUID is still unresolved
	 * @return attached identity UUID
	 */
	public @NotNull UUID resolveAttachedUniqueId(@NotNull UUID fallbackConnectionUniqueId) {
		return getAccountUniqueId() != null
				? getAccountUniqueId()
				: fallbackConnectionUniqueId;
	}

	/**
	 * Builds the canonical connection key for staged prepare/pipeline resume.
	 *
	 * @return connection key or {@code null} when username is missing
	 */
	public @Nullable String connectionKey() {
		if (username.isBlank()) return null;

		Origin origin = this.origin;
		String host = origin != null ? origin.getHost() : "";
		Integer port = origin != null ? origin.getPort() : null;

		return String.join("|",
				username,
				ip == null ? "" : ip,
				host,
				port == null ? "" : Integer.toString(port));
	}
}
